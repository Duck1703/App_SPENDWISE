# M21 - build the Human-signed Personal V1 release APK.
#
#   .\build-signed-release.ps1 [-Alias <alias>] [-BaseUrl <url>]
#
# Run this in YOUR OWN console. It prompts interactively for the keystore
# store/key passwords (SecureString - never echoed, never logged, never
# written to disk) and injects the SPENDWISE_RELEASE_* env vars into THIS
# process only. Nothing is persisted; nothing is committed.
#
# On success it prints: APK path, size, SHA-256, apksigner verification
# and the public signing certificate fingerprint.

param(
    [string]$KeystorePath = (Join-Path $env:USERPROFILE '.spendwise-release\spendwise-release.jks'),
    [string]$Alias = 'spendwise-release',
    [string]$BaseUrl = 'https://192.168.1.236:8443/'
)
$ErrorActionPreference = 'Stop'
$repo = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $repo

if (-not (Test-Path $KeystorePath)) {
    throw "Release keystore not found: $KeystorePath`nRun scripts\lan\create-release-keystore.ps1 first."
}

$store = Read-Host -AsSecureString 'Store password'
$keyIn = Read-Host -AsSecureString 'Key password (press Enter to reuse store password)'
function Unsec([securestring]$s) {
    if (-not $s -or $s.Length -eq 0) { return $null }
    $b = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b) }
}
$storeP = Unsec $store
$keyP = Unsec $keyIn; if (-not $keyP) { $keyP = $storeP }

$env:SPENDWISE_RELEASE_KEYSTORE_PATH   = $KeystorePath
$env:SPENDWISE_RELEASE_KEY_ALIAS       = $Alias
$env:SPENDWISE_RELEASE_STORE_PASSWORD  = $storeP
$env:SPENDWISE_RELEASE_KEY_PASSWORD    = $keyP
$storeP = $null; $keyP = $null

try {
    & .\gradlew.bat assembleRelease lintVitalRelease "-Pspendwise.api.base.url=$BaseUrl"
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)" }
} finally {
    Remove-Item Env:SPENDWISE_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:SPENDWISE_RELEASE_KEY_PASSWORD   -ErrorAction SilentlyContinue
}

$apk = 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw "APK not produced: $apk" }
$hash = (Get-FileHash $apk -Algorithm SHA256).Hash
$size = (Get-Item $apk).Length

$bt = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory -ErrorAction SilentlyContinue |
      Sort-Object Name -Descending | Select-Object -First 1
if ($bt) {
    & (Join-Path $bt.FullName 'apksigner.bat') verify --verbose --print-certs $apk
}

Write-Host ''
Write-Host '=== HUMAN-SIGNED RELEASE BUILT ==='
Write-Host "APK:    $((Resolve-Path $apk).Path)"
Write-Host "SIZE:   $size bytes"
Write-Host "SHA256: $hash"
Write-Host 'Fingerprint line above (SHA-256 of signing cert) is PUBLIC metadata - safe to share.'
