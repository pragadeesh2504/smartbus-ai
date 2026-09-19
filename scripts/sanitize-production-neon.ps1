<#
.SYNOPSIS
    One-time authorized production data cleanup for SmartBus AI on Neon PostgreSQL.

.DESCRIPTION
    Safely purges all operational and tenant data from the production Neon deployment,
    preserving ONLY the singleton SUPER_ADMIN (superadmin@smartbus.com, college_id = NULL),
    Flyway migration history, system settings, and schema.

.PARAMETER NeonConnectionString
    The direct PostgreSQL connection string for Neon (port 5432, sslmode=require).
    Example: postgres://<user>:<password>@<neon-host>:5432/<dbname>?sslmode=require

.PARAMETER ConfirmProduction
    Safety switch required to execute the cleanup against production.
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$NeonConnectionString,

    [Parameter(Mandatory = $true)]
    [switch]$ConfirmProduction
)

$ErrorActionPreference = "Stop"

Write-Host '==================================================' -ForegroundColor Cyan
Write-Host ' SmartBus AI — Production Neon Cleanup Runner     ' -ForegroundColor Cyan
Write-Host '==================================================' -ForegroundColor Cyan

# 1. Safety verification: Reject localhost / local environments
if ($NeonConnectionString -match "localhost" -or $NeonConnectionString -match "127\.0\.0\.1" -or $NeonConnectionString -match "::1") {
    Write-Error 'ABORTED: This script is restricted to production Neon databases. It CANNOT and WILL NOT run against localhost or 127.0.0.1.'
    exit 1
}

if (-not $ConfirmProduction) {
    Write-Error 'ABORTED: You must provide the -ConfirmProduction switch to execute production cleanup.'
    exit 1
}

$scriptPath = Join-Path $PSScriptRoot 'production-neon-init.sql'
if (-not (Test-Path $scriptPath)) {
    Write-Error "SQL script not found at: $scriptPath"
    exit 1
}

Write-Host 'Target Database: Direct Neon PostgreSQL' -ForegroundColor Yellow
Write-Host 'Executing production cleanup SQL...' -ForegroundColor Yellow

try {
    & psql "$NeonConnectionString" -f "$scriptPath"
    Write-Host ''
    Write-Host 'Production cleanup completed successfully!' -ForegroundColor Green
    Write-Host 'Confirmed target state:' -ForegroundColor Green
    Write-Host '  - SUPER_ADMIN = 1 (superadmin@smartbus.com, college_id = NULL)' -ForegroundColor Green
    Write-Host '  - All tenant and operational tables = 0' -ForegroundColor Green
    Write-Host '  - Schema, indexes, and Flyway history preserved' -ForegroundColor Green
} catch {
    $err = $_.Exception.Message
    Write-Error "Failed to execute psql command: $err"
}
