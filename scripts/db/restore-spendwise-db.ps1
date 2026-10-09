# M21 - restore a custom-format Spendwise backup into a Postgres database.
#
#   .\restore-spendwise-db.ps1 -DumpFile D:\spendwise-backups\x.dump -TargetDb spendwise_m21_restore
#
# SAFETY:
#   * refuses to restore into a database that already contains tables unless
#     -AllowNonEmpty is passed - recovery into a LIVE database is a Human
#     decision executed via the runbook, not this script;
#   * never uses --clean against an existing database;
#   * no password on the command line (container-local trust via docker exec).
param(
    [Parameter(Mandatory = $true)][string]$DumpFile,
    [Parameter(Mandatory = $true)][string]$TargetDb,
    [string]$Container = 'spendwise-m10-db',
    [string]$DbUser = 'spendwise_app',
    [string]$AdminUser = 'spendwise_app',
    [switch]$AllowNonEmpty
)
$ErrorActionPreference = 'Stop'

if (-not (Test-Path $DumpFile)) { throw "Dump not found: $DumpFile" }

# Create target if absent. CREATE DATABASE needs elevated rights, so it runs
# as $AdminUser; the new database is owned by $DbUser so pg_restore (which
# connects as $DbUser) can create objects inside it.
$exists = docker exec $Container psql -U $AdminUser -d postgres -tAc `
    "SELECT 1 FROM pg_database WHERE datname='$TargetDb'"
if ($exists -ne '1') {
    docker exec $Container psql -U $AdminUser -d postgres -c `
        "CREATE DATABASE $TargetDb OWNER $DbUser"
    if ($LASTEXITCODE -ne 0) { throw "CREATE DATABASE failed (exit $LASTEXITCODE)." }
    Write-Host "Created database $TargetDb (owner $DbUser)"
} else {
    $tables = docker exec $Container psql -U $DbUser -d $TargetDb -tAc `
        "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'"
    if ([int]$tables -gt 0 -and -not $AllowNonEmpty) {
        throw "Target '$TargetDb' already has $tables tables. Pass -AllowNonEmpty only for a deliberate overwrite-path test."
    }
}

# Copy the archive inside the container, restore, clean up.
$tmpName = "restore-$(Get-Date -Format 'yyyyMMddHHmmss').dump"
docker cp $DumpFile "${Container}:/tmp/$tmpName" | Out-Null
docker exec $Container pg_restore -U $DbUser -d $TargetDb --no-owner --no-privileges "/tmp/$tmpName"
$restoreExit = $LASTEXITCODE
docker exec $Container rm "/tmp/$tmpName" | Out-Null
if ($restoreExit -ne 0) { throw "pg_restore failed (exit $restoreExit)." }

# Report what landed - the operator verifies counts, not just the exit code.
Write-Host "`nRESTORED into $TargetDb - object inventory:"
docker exec $Container psql -U $DbUser -d $TargetDb -c `
    "SELECT table_name, (SELECT count(*) FROM information_schema.columns c WHERE c.table_name=t.table_name) FROM information_schema.tables t WHERE table_schema='public' ORDER BY table_name"
