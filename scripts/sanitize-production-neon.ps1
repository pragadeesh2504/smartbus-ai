<#
.SYNOPSIS
    One-time production sanitization for SmartBus AI on Neon PostgreSQL.

.DESCRIPTION
    Safely purges V2/V13 demo and seed records from a fresh production Neon deployment,
    leaving schema and Flyway migrations intact.
    Prepares database so SuperAdminBootstrapService creates the production platform SuperAdmin.

.PARAMETER NeonConnectionString
    The direct PostgreSQL connection string for Neon (port 5432, sslmode=require).

.PARAMETER ConfirmProduction
    Safety switch required to execute the sanitization against production.
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$NeonConnectionString,

    [Parameter(Mandatory = $true)]
    [switch]$ConfirmProduction
)

$ErrorActionPreference = "Stop"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " SmartBus AI — Production Neon Sanitization Script " -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Safety verification: Reject localhost / local environments
if ($NeonConnectionString -match "localhost" -or $NeonConnectionString -match "127\.0\.0\.1" -or $NeonConnectionString -match "::1") {
    Write-Error "ABORTED: This script is restricted to production Neon databases. It CANNOT and WILL NOT run against localhost or 127.0.0.1."
    exit 1
}

if (-not $ConfirmProduction) {
    Write-Error "ABORTED: You must provide the -ConfirmProduction switch to execute production sanitization."
    exit 1
}

$scriptPath = Join-Path $PSScriptRoot "production-neon-init.sql"
if (-not (Test-Path $scriptPath)) {
    Write-Error "SQL script not found at: $scriptPath"
    exit 1
}

Write-Host "Target Database: Direct Neon PostgreSQL" -ForegroundColor Yellow
Write-Host "Executing production sanitization SQL..." -ForegroundColor Yellow

# Execute via psql (or let user run psql directly)
try {
    & psql "$NeonConnectionString" -f "$scriptPath"
    Write-Host "`nProduction sanitization completed successfully!" -ForegroundColor Green
    Write-Host "All operational tables, demo colleges, and seed accounts are purged (count = 0)." -ForegroundColor Green
    Write-Host "Now restart the Render backend service with SUPERADMIN_EMAIL and SUPERADMIN_PASSWORD configured." -ForegroundColor Green
    Write-Host "SuperAdminBootstrapService will create the initial platform SuperAdmin account." -ForegroundColor Green
} catch {
    Write-Error "Failed to execute psql command: $_"
}
