# M21 - backup a Spendwise Postgres database inside the spendwise-m10-db
# container to a custom-format archive OUTSIDE all git repositories.
#
#   .\backup-spendwise-db.ps1 [-DbName spendwise_m21] [-OutDir D:\spendwise-backups]
#
# Binary note: pg_dump -Fc output MUST NOT pass through PowerShell's `>`
# (it re-encodes to UTF-16 and corrupts the archive). This script pipes via
# cmd.exe byte-stream redirection instead.
param(
    [string]$DbName = 'spendwise',
    [string]$OutDir = 'D:\spendwise-backups',
    [string]$Container = 'spendwise-m10-db',
    [string]$DbUser = 'spendwise_app'
)
$ErrorActionPreference = 'Stop'

docker inspect $Container --format '{{.State.Running}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Container '$Container' not running." }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$file = Join-Path $OutDir "$DbName-$stamp.dump"

# -Fc custom format: compressed, selectively restorable, pg_restore-readable.
cmd /c "docker exec $Container pg_dump -U $DbUser -Fc $DbName > `"$file`""
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed (exit $LASTEXITCODE)." }
if (-not (Test-Path $file) -or (Get-Item $file).Length -lt 512) {
    throw "Backup file missing or suspiciously small: $file"
}

# Integrity: copy into the container and list the archive TOC - proves the
# archive is structurally readable, not just non-empty.
$tmpName = "verify-$stamp.dump"
docker cp $file "${Container}:/tmp/$tmpName" | Out-Null
$toc = docker exec $Container pg_restore -l "/tmp/$tmpName"
docker exec $Container rm "/tmp/$tmpName" | Out-Null
if ($LASTEXITCODE -ne 0 -or -not ($toc | Select-String 'TABLE DATA')) {
    throw "Archive integrity check failed for $file"
}
$objects = ($toc | Measure-Object).Count

Write-Host "BACKUP_OK $file  ($([math]::Round((Get-Item $file).Length/1KB,1)) KB, $objects catalog entries)"
Write-Host "Retention: manual - rotate per runbooks/M21-LAN-DEPLOYMENT.md; never auto-delete without review."
