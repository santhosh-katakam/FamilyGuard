<# Start isolated, persistent local PostgreSQL, API and React development servers. #>
[CmdletBinding()]
param(
    [string]$AdminEmail = 'parent@familyguard.local',
    [string]$PostgresBin
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$toolsRoot = Join-Path (Split-Path $projectRoot -Parent) '.tools'
$privateRoot = Join-Path $PSScriptRoot '.private/local'
[System.IO.Directory]::CreateDirectory($privateRoot) | Out-Null
$environmentFile = Join-Path $privateRoot 'backend.env'
$runtimeFile = Join-Path $privateRoot 'runtime.json'
$utf8 = New-Object System.Text.UTF8Encoding($false)
function Get-FreePort([int]$first) {
    foreach ($candidate in $first..($first + 20)) {
        if (-not (Get-NetTCPConnection -State Listen -LocalPort $candidate -ErrorAction SilentlyContinue)) { return $candidate }
    }
    throw "No free local port starting at $first."
}
function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
function Read-LocalEnvironment {
    $result = @{}
    foreach ($line in Get-Content -LiteralPath $environmentFile) {
        if ($line -match '^([^=]+)=(.*)$') { $result[$matches[1]] = $matches[2] }
    }
    return $result
}
function Own-Process([int]$processId, [string]$expectedPath) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
    if ($null -eq $process -or $null -eq $process.CommandLine) { return $false }
    return $process.CommandLine.Replace('\', '/').IndexOf($expectedPath.Replace('\', '/'), [StringComparison]::OrdinalIgnoreCase) -ge 0
}
if (Test-Path -LiteralPath $runtimeFile) {
    $old = Get-Content -LiteralPath $runtimeFile -Raw | ConvertFrom-Json
    if ((Own-Process $old.backendPid $projectRoot) -and (Own-Process $old.frontendPid $projectRoot)) {
        Write-Output "FamilyGuard is already running: $($old.frontendUrl)"
        Write-Output "Local login details: $(Join-Path $privateRoot 'parent-login.txt')"
        return
    }
    if ((Own-Process $old.backendPid $projectRoot) -or (Own-Process $old.frontendPid $projectRoot)) {
        throw 'A partial FamilyGuard session is running. Run deployment/stop-local.ps1 before restarting.'
    }
}
if (-not $PostgresBin) {
    $pgCandidates = @(Get-ChildItem 'C:/Program Files/PostgreSQL' -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending)
    foreach ($directory in $pgCandidates) {
        $candidate = Join-Path $directory.FullName 'bin'
        if (Test-Path -LiteralPath (Join-Path $candidate 'initdb.exe')) { $PostgresBin = $candidate; break }
    }
}
if (-not $PostgresBin -or -not (Test-Path -LiteralPath (Join-Path $PostgresBin 'pg_ctl.exe'))) {
    throw 'Install PostgreSQL tools or pass -PostgresBin with the directory containing initdb.exe and pg_ctl.exe.'
}
$javaCandidates = @()
if ($env:JAVA_HOME) { $javaCandidates += Join-Path $env:JAVA_HOME 'bin/java.exe' }
$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
if ($javaCommand) { $javaCandidates += $javaCommand.Source }
$javaCandidates += @(Get-ChildItem (Join-Path $toolsRoot 'jdk') -Directory -ErrorAction SilentlyContinue | ForEach-Object { Join-Path $_.FullName 'bin/java.exe' })
$javaExe = $javaCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $javaExe) { throw 'Java 21 is required. Set JAVA_HOME or install Java 21.' }
$nodeExe = (Get-Command node.exe -ErrorAction Stop).Source
$jar = Join-Path $projectRoot 'backend/target/familyguard-api-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the backend first: cd backend; mvn verify' }
$vite = Join-Path $projectRoot 'frontend/node_modules/vite/bin/vite.js'
if (-not (Test-Path -LiteralPath $vite)) { throw 'Install frontend dependencies first: cd frontend; npm ci' }
if (Get-NetTCPConnection -State Listen -LocalPort 5173 -ErrorAction SilentlyContinue) { throw 'Port 5173 is occupied. Stop the old development server first.' }
if (-not (Test-Path -LiteralPath $environmentFile)) {
    $backendPort = Get-FreePort 8081
    $databasePort = Get-FreePort 5433
    $password = (New-LocalSecret).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $databasePassword = (New-LocalSecret).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $values = @(
        "PORT=$backendPort", 'SERVER_ADDRESS=127.0.0.1',
        "DATABASE_URL=jdbc:postgresql://127.0.0.1:$databasePort/familyguard",
        'DATABASE_USER=familyguard', "DATABASE_PASSWORD=$databasePassword",
        "ADMIN_EMAIL=$AdminEmail", "ADMIN_PASSWORD=$password",
        "JWT_SECRET=$(New-LocalSecret)", "DATA_ENCRYPTION_KEY=$(New-LocalSecret)",
        'FRONTEND_ORIGIN=http://localhost:5173,http://127.0.0.1:5173',
        'SECURE_COOKIES=false', 'OFFLINE_MINUTES=45', 'DEBUG=false'
    ) -join "`n"
    [System.IO.File]::WriteAllText($environmentFile, $values + "`n", $utf8)
}
$settings = Read-LocalEnvironment
$dbUri = [uri]($settings.DATABASE_URL -replace '^jdbc:', '')
$databasePort = $dbUri.Port
$backendPort = [int]$settings.PORT
if (Get-NetTCPConnection -State Listen -LocalPort $backendPort -ErrorAction SilentlyContinue) { throw "API port $backendPort is occupied; no process was stopped." }
$dataDirectory = Join-Path $privateRoot 'postgres-data'
$passwordFile = Join-Path $privateRoot 'postgres-password.txt'
[System.IO.File]::WriteAllText($passwordFile, $settings.DATABASE_PASSWORD + "`n", $utf8)
if (-not (Test-Path -LiteralPath (Join-Path $dataDirectory 'PG_VERSION'))) {
    Write-Output 'Initializing an isolated local PostgreSQL cluster...'
    $initArguments = @('-D', ('"' + $dataDirectory + '"'), '-U', 'familyguard', '--auth=scram-sha-256', '--encoding=UTF8', '--locale=C', ('--pwfile="' + $passwordFile + '"'))
    $initialization = Start-Process -FilePath (Join-Path $PostgresBin 'initdb.exe') -ArgumentList $initArguments -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput (Join-Path $privateRoot 'initdb.log') -RedirectStandardError (Join-Path $privateRoot 'initdb-error.log')
    if ($initialization.ExitCode -ne 0) { throw "PostgreSQL initialization failed. See $privateRoot/initdb-error.log" }
}
$savedEnvironment = @{}
foreach ($key in @($settings.Keys) + @('PGPASSWORD')) { $savedEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
try {
    foreach ($key in $settings.Keys) { [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process') }
    $env:PGPASSWORD = $settings.DATABASE_PASSWORD
    $pgCtl = Join-Path $PostgresBin 'pg_ctl.exe'
    $databaseRunning = $false
    $postmasterFile = Join-Path $dataDirectory 'postmaster.pid'
    if (Test-Path -LiteralPath $postmasterFile) {
        $databaseProcessId = [int](Get-Content -LiteralPath $postmasterFile -TotalCount 1)
        $databaseRunning = Own-Process $databaseProcessId $dataDirectory
    }
    if (-not $databaseRunning) {
        if (Get-NetTCPConnection -State Listen -LocalPort $databasePort -ErrorAction SilentlyContinue) { throw "Database port $databasePort is occupied; no process was stopped." }
        $pgArguments = @('start', '-D', ('"' + $dataDirectory + '"'), '-l', ('"' + (Join-Path $privateRoot 'postgres.log') + '"'), '-o', ('"-p ' + $databasePort + ' -h 127.0.0.1"'), '-w', '-t', '30')
        $pgStart = Start-Process -FilePath $pgCtl -ArgumentList $pgArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $privateRoot 'postgres-start.log') -RedirectStandardError (Join-Path $privateRoot 'postgres-start-error.log')
        # -Wait waits for descendants on Windows, including the persistent database server.
        $null = $pgStart.Handle
        if (-not $pgStart.WaitForExit(40000)) { throw "PostgreSQL startup timed out. See $privateRoot/postgres.log" }
        if ($null -ne $pgStart.ExitCode -and $pgStart.ExitCode -ne 0) { throw "Local PostgreSQL failed to start. See $privateRoot/postgres.log" }
        # The SQL connection below also verifies startup when Windows PowerShell cannot retrieve ExitCode.
    }
    $databaseExists = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $databasePort -U familyguard -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='familyguard'"
    if ($LASTEXITCODE -ne 0) { throw 'Could not connect to the isolated local database.' }
    if ($databaseExists -ne '1') {
        & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $databasePort -U familyguard familyguard
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the isolated FamilyGuard database.' }
    }
    [System.IO.File]::WriteAllText((Join-Path $projectRoot 'frontend/.env.local'), "VITE_API_URL=`nVITE_DEV_API_TARGET=http://127.0.0.1:$backendPort`n", $utf8)
    $backend = Start-Process -FilePath $javaExe -ArgumentList @('-jar', ('"' + $jar + '"')) -WorkingDirectory (Join-Path $projectRoot 'backend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $privateRoot 'backend.log') -RedirectStandardError (Join-Path $privateRoot 'backend-error.log')
    $frontend = Start-Process -FilePath $nodeExe -ArgumentList @(('"' + $vite + '"'), '--host', '127.0.0.1', '--port', '5173', '--strictPort') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $privateRoot 'frontend.log') -RedirectStandardError (Join-Path $privateRoot 'frontend-error.log')
    $runtime = @{ backendPid=$backend.Id; frontendPid=$frontend.Id; frontendUrl='http://localhost:5173'; backendUrl="http://127.0.0.1:$backendPort"; databasePort=$databasePort; dataDirectory=$dataDirectory; postgresBin=$PostgresBin }
    [System.IO.File]::WriteAllText($runtimeFile, ($runtime | ConvertTo-Json), $utf8)
    $loginText = "FamilyGuard LOCAL Parent Admin`nWebsite: http://localhost:5173/`nEmail: $($settings.ADMIN_EMAIL)`nPassword: $($settings.ADMIN_PASSWORD)`n`nThese credentials and this database are separate from Render.`n"
    [System.IO.File]::WriteAllText((Join-Path $privateRoot 'parent-login.txt'), $loginText, $utf8)
    Write-Output 'Waiting for the local API and frontend...'
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    $apiReady = $false
    $frontendReady = $false
    do {
        if (-not (Own-Process $backend.Id $projectRoot) -or -not (Own-Process $frontend.Id $projectRoot)) {
            throw "A local server exited. Inspect logs in $privateRoot, then run deployment/stop-local.ps1 before restarting."
        }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/actuator/health" -TimeoutSec 3
            $apiReady = $health.status -eq 'UP'
        } catch { $apiReady = $false }
        try {
            $frontendReady = (Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5173/' -TimeoutSec 3).StatusCode -eq 200
        } catch { $frontendReady = $false }
        if ($apiReady -and $frontendReady) { break }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    if (-not ($apiReady -and $frontendReady)) { throw "Local services are still starting or unhealthy. Inspect logs in $privateRoot." }
    Write-Output 'Local frontend: http://localhost:5173/'
    Write-Output "Local API: http://127.0.0.1:$backendPort"
    Write-Output "Isolated PostgreSQL: 127.0.0.1:$databasePort"
    Write-Output "Local login details: $(Join-Path $privateRoot 'parent-login.txt')"
    Write-Output "Startup logs: $privateRoot"
} finally {
    foreach ($key in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $savedEnvironment[$key], 'Process') }
}
