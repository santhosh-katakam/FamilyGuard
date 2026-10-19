<#
Creates private environment files for a first Render deployment.
Run only after Render has assigned your real frontend/backend HTTPS URLs.
This script never overwrites existing credentials or prints secrets.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[^\s@]+@[^\s@]+\.[^\s@]+$')]
    [string]$AdminEmail,
    [Parameter(Mandatory = $true)]
    [uri]$FrontendOrigin,
    [Parameter(Mandatory = $true)]
    [uri]$ApiOrigin
)
$ErrorActionPreference = 'Stop'
foreach ($origin in @($FrontendOrigin, $ApiOrigin)) {
    if (-not $origin.IsAbsoluteUri -or $origin.Scheme -ne 'https' -or
        $origin.AbsolutePath -ne '/' -or $origin.Query -or $origin.Fragment -or $origin.UserInfo) {
        throw 'Use HTTPS origins only, without a path, query, fragment, or credentials.'
    }
}
$outputDirectory = Join-Path $PSScriptRoot '.private'
$backendFile = Join-Path $outputDirectory 'backend.env'
$frontendFile = Join-Path $outputDirectory 'frontend.env'
if ((Test-Path -LiteralPath $backendFile) -or (Test-Path -LiteralPath $frontendFile)) {
    throw 'Private environment files already exist. Reuse them; do not regenerate encryption keys for an existing database.'
}
function New-RandomBase64 {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
$password = (New-RandomBase64).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$jwt = New-RandomBase64
$encryption = New-RandomBase64
$web = $FrontendOrigin.GetLeftPart([System.UriPartial]::Authority)
$api = $ApiOrigin.GetLeftPart([System.UriPartial]::Authority)
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
$encoding = New-Object System.Text.UTF8Encoding($false)
$backend = @(
    "ADMIN_EMAIL=$AdminEmail",
    "ADMIN_PASSWORD=$password",
    "JWT_SECRET=$jwt",
    "DATA_ENCRYPTION_KEY=$encryption",
    "FRONTEND_ORIGIN=$web",
    'SECURE_COOKIES=true',
    'OFFLINE_MINUTES=45'
) -join "`n"
[System.IO.File]::WriteAllText($backendFile, $backend + "`n", $encoding)
[System.IO.File]::WriteAllText($frontendFile, "VITE_API_URL=$api`n", $encoding)
Write-Output "Created private backend environment file: $backendFile"
Write-Output "Created private frontend environment file: $frontendFile"
Write-Output 'Import their values into the corresponding Render service environment settings. Keep an encrypted backup of the backend values.'
Write-Output 'Your parent login is ADMIN_EMAIL / ADMIN_PASSWORD from backend.env. Never commit or paste these secrets into chat.'
