[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$privateRoot = Join-Path $PSScriptRoot '.private/local'
$runtimeFile = Join-Path $privateRoot 'runtime.json'
if (-not (Test-Path -LiteralPath $runtimeFile)) { Write-Output 'No local FamilyGuard session recorded.'; return }
$runtime = Get-Content -LiteralPath $runtimeFile -Raw | ConvertFrom-Json
function Matches-Workspace($process, [string]$expectedPath) {
    return $process -and $process.CommandLine -and $process.CommandLine.Replace('\', '/').IndexOf($expectedPath.Replace('\', '/'), [StringComparison]::OrdinalIgnoreCase) -ge 0
}
foreach ($processId in @($runtime.frontendPid, $runtime.backendPid)) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
    if (Matches-Workspace $process $projectRoot) {
        Stop-Process -Id $processId
    } elseif ($process) { throw "Process $processId no longer belongs to this FamilyGuard workspace; it was not stopped." }
}
$expectedData = [System.IO.Path]::GetFullPath((Join-Path $privateRoot 'postgres-data'))
if ([System.IO.Path]::GetFullPath($runtime.dataDirectory) -ne $expectedData) { throw 'Recorded database path does not match this workspace.' }
$postmasterFile = Join-Path $expectedData 'postmaster.pid'
if (Test-Path -LiteralPath $postmasterFile) {
    $databaseProcessId = [int](Get-Content -LiteralPath $postmasterFile -TotalCount 1)
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$databaseProcessId" -ErrorAction SilentlyContinue
    if (Matches-Workspace $process $expectedData) {
        & (Join-Path $runtime.postgresBin 'pg_ctl.exe') stop -D $expectedData -m fast -w
        if ($LASTEXITCODE -ne 0) { throw 'The local PostgreSQL cluster did not stop cleanly.' }
    }
}
Write-Output 'Local FamilyGuard services stopped. Local database and credentials are preserved.'
