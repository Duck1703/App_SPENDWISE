# M21 - start the Spendwise backend bound to the LAN interface.
#
#   .\start-backend.ps1 [-EnvFile .\spendwise-lan.env] [-Jar D:\spendwise\build\libs\spendwise.jar]
#
# Secrets come from an untracked env file (copy spendwise-lan.env.example) or
# from variables already present in this shell. Nothing is echoed.
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot 'spendwise-lan.env'),
    [string]$Jar = 'D:\spendwise\build\libs\spendwise-0.0.1-SNAPSHOT.jar'
)
$ErrorActionPreference = 'Stop'

# --- load env file (KEY=VALUE lines, # comments, optional quotes) -----------
if (Test-Path $EnvFile) {
    foreach ($line in Get-Content $EnvFile) {
        $line = $line.Trim()
        if (-not $line -or $line.StartsWith('#')) { continue }
        $i = $line.IndexOf('=')
        if ($i -lt 1) { continue }
        $k = $line.Substring(0, $i).Trim()
        $v = $line.Substring($i + 1).Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($k, $v, 'Process')
    }
    Write-Host "Loaded env file: $EnvFile"
} else {
    Write-Host "No env file at $EnvFile - using current shell environment."
}

# --- required secrets --------------------------------------------------------
$missing = @()
foreach ($k in 'SPENDWISE_DB_URL','SPENDWISE_DB_USERNAME','SPENDWISE_DB_PASSWORD','SPENDWISE_JWT_SECRET') {
    if (-not [Environment]::GetEnvironmentVariable($k)) { $missing += $k }
}
if ($missing) { throw "Missing required env vars: $($missing -join ', ')" }

# --- listener config ---------------------------------------------------------
$lanHost = [Environment]::GetEnvironmentVariable('SPENDWISE_LAN_HOST')
if (-not $lanHost) { throw 'SPENDWISE_LAN_HOST is required (e.g. 192.168.1.236)' }
$httpPort  = [Environment]::GetEnvironmentVariable('SPENDWISE_HTTP_PORT');  if (-not $httpPort)  { $httpPort  = '8081' }
$httpsPort = [Environment]::GetEnvironmentVariable('SPENDWISE_HTTPS_PORT'); if (-not $httpsPort) { $httpsPort = '8443' }

$env:SERVER_ADDRESS = $lanHost
$env:SERVER_PORT    = $httpPort   # overridden below when TLS is enabled

# --- optional HTTPS ----------------------------------------------------------
$ks = [Environment]::GetEnvironmentVariable('SPENDWISE_TLS_KEYSTORE')
if ($ks) {
    if (-not (Test-Path $ks)) { throw "SPENDWISE_TLS_KEYSTORE not found: $ks" }
    if (-not $env:SERVER_SSL_KEY_STORE_PASSWORD -and -not $env:SPENDWISE_TLS_KEYSTORE_PASSWORD) {
        throw 'TLS keystore given but no password var (SERVER_SSL_KEY_STORE_PASSWORD or SPENDWISE_TLS_KEYSTORE_PASSWORD).'
    }
    $env:SERVER_SSL_ENABLED              = 'true'
    $env:SERVER_SSL_KEY_STORE            = $ks
    $env:SERVER_SSL_KEY_STORE_TYPE       = 'PKCS12'
    $env:SERVER_SSL_KEY_STORE_PASSWORD   = [Environment]::GetEnvironmentVariable('SPENDWISE_TLS_KEYSTORE_PASSWORD')
    $env:SERVER_SSL_KEY_ALIAS            = [Environment]::GetEnvironmentVariable('SPENDWISE_TLS_KEY_ALIAS')
    $env:SERVER_PORT                     = $httpsPort
    Write-Host "HTTPS enabled on ${lanHost}:$httpsPort (keystore outside repo)"
} else {
    Write-Host "SPENDWISE_TLS_KEYSTORE not set - HTTP-only debug mode on ${lanHost}:$httpPort"
}

if (-not (Test-Path $Jar)) { throw "Backend jar not found: $Jar (run ./gradlew bootJar in D:\spendwise)" }

# Spring Boot serves one connector: HTTP on $httpPort when TLS is off,
# HTTPS on $httpsPort when TLS is on. Run debug and release checks in
# separate launches, not simultaneously.
Write-Host "Starting Spendwise backend on ${lanHost}:$($env:SERVER_PORT)"
Write-Host 'Ctrl+C to stop. Logs stream to this console.'

java -jar $Jar
