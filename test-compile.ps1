# Simple compile script to test Java 8 compatibility
$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
Set-Location $ProjectRoot

# Find Maven
$MavenCmd = $null
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    $MavenCmd = "mvn"
} else {
    $mvnBat = Join-Path $ProjectRoot ".maven-cache\apache-maven-3.9.9\bin\mvn.cmd"
    if (Test-Path $mvnBat) {
        $MavenCmd = "& `"$mvnBat`""
    }
}

if (-not $MavenCmd) {
    Write-Host "Maven not found" -ForegroundColor Red
    exit 1
}

# Compile without clean
Write-Host "Compiling project..." -ForegroundColor Cyan
if ($MavenCmd -eq "mvn") {
    mvn compile test-compile
} else {
    Invoke-Expression "$MavenCmd compile test-compile"
}

exit $LASTEXITCODE
