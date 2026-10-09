# M21 - create the Human-owned Personal V1 release signing keystore.
#
#   .\create-release-keystore.ps1 [-KeystorePath <path>] [-Alias <name>]
#
# Safety contract:
#   - passwords are prompted by keytool itself - never passed on the
#     command line, never echoed, never written to files or logs;
#   - keystore is created OUTSIDE this repository (refused otherwise);
#   - only public certificate metadata is printed at the end;
#   - nothing is committed to Git; no env vars are modified.
#
# After creation, point the release build at it (see final output):
#   SPENDWISE_RELEASE_KEYSTORE_PATH / _KEY_ALIAS / _STORE_PASSWORD /
#   _KEY_PASSWORD - set them as User env vars, never in files under Git.

param(
    [string]$KeystorePath = (Join-Path $env:USERPROFILE '.spendwise-release\spendwise-release.jks'),
    [string]$Alias = 'spendwise-release'
)
$ErrorActionPreference = 'Stop'

# --- locate keytool ----------------------------------------------------------
$keytool = $null
foreach ($cand in @(
        (Join-Path $env:JAVA_HOME 'bin\keytool.exe'),
        'C:\Users\Lenovo\.jdks\ms-17.0.20.1\bin\keytool.exe',
        'keytool.exe')) {
    if ($cand -and (Get-Command $cand -ErrorAction SilentlyContinue)) { $keytool = $cand; break }
    if ($cand -and (Test-Path $cand)) { $keytool = $cand; break }
}
if (-not $keytool) { throw 'keytool not found - set JAVA_HOME or install a JDK.' }

# --- refuse in-repo paths ----------------------------------------------------
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$full = [IO.Path]::GetFullPath($KeystorePath)
if ($full.StartsWith($repo, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Keystore must live OUTSIDE the repository. Refused: $full"
}
if (Test-Path $full) { throw "Keystore already exists: $full (delete manually only if you are sure)" }
New-Item -ItemType Directory -Force -Path (Split-Path $full) | Out-Null

# --- generate (keytool prompts for passwords interactively) ------------------
Write-Host "Creating release keystore: $full  (alias: $Alias)"
Write-Host 'keytool will prompt for the store and key passwords now.'
& $keytool -genkeypair -v `
    -keystore $full -alias $Alias `
    -keyalg RSA -keysize 4096 -sigalg SHA256withRSA `
    -validity 10950 -storetype PKCS12
if ($LASTEXITCODE -ne 0) { throw "keytool failed (exit $LASTEXITCODE)" }

# --- verify + show ONLY public metadata --------------------------------------
& $keytool -list -v -keystore $full -alias $Alias -storetype PKCS12
if ($LASTEXITCODE -ne 0) { throw 'keystore verification failed' }

Write-Host ''
Write-Host '=== NEXT STEPS (manual, one time) ==='
Write-Host '1. Set User env vars (never write the passwords into any file):'
Write-Host "   SPENDWISE_RELEASE_KEYSTORE_PATH=$full"
Write-Host "   SPENDWISE_RELEASE_KEY_ALIAS=$Alias"
Write-Host '   SPENDWISE_RELEASE_STORE_PASSWORD=<the store password you just typed>'
Write-Host '   SPENDWISE_RELEASE_KEY_PASSWORD=<the key password you just typed>'
Write-Host '2. Back up the .jks to encrypted offline storage. Losing it means'
Write-Host '   you can never update the installed Personal V1 app again.'
Write-Host '3. Tell Devin the keystore is ready - the release build will pick up'
Write-Host '   the env vars automatically.'
