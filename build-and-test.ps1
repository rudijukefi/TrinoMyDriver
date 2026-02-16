# Build and test TrinoMyDriver Maven project (works without Maven installed)
# Uses system mvn if available; otherwise downloads Maven to .maven-cache and runs it.

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
Set-Location $ProjectRoot

# --- Find or bootstrap Maven ---
$MavenCmd = $null

# 1) Prefer system Maven if available
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    $MavenCmd = "mvn"
}

# 2) Prefer Maven Wrapper if present
if (-not $MavenCmd) {
    $mvnw = Join-Path $ProjectRoot "mvnw.cmd"
    if (Test-Path $mvnw) {
        $MavenCmd = "& `"$mvnw`""
    }
}

# 3) Bootstrap: download Maven to .maven-cache
if (-not $MavenCmd) {
    $MavenVersion = "3.9.9"
    $MavenZip = "apache-maven-$MavenVersion-bin.zip"
    $CacheDir = Join-Path $ProjectRoot ".maven-cache"
    $MavenHome = Join-Path $CacheDir "apache-maven-$MavenVersion"
    $MavenBin = Join-Path $MavenHome "bin"
    $mvnBat = Join-Path $MavenBin "mvn.cmd"

    if (-not (Test-Path $mvnBat)) {
        Write-Host "Maven not found. Downloading Maven $MavenVersion to $CacheDir ..." -ForegroundColor Yellow
        if (-not (Test-Path $CacheDir)) { New-Item -ItemType Directory -Path $CacheDir -Force | Out-Null }
        $zipPath = Join-Path $CacheDir $MavenZip
        # Prefer TLS 1.2 (often required for downloads on Windows)
        try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }
        $urls = @(
            "https://downloads.apache.org/maven/maven-3/$MavenVersion/binaries/$MavenZip",
            "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/$MavenZip"
        )
        $downloaded = $false
        foreach ($url in $urls) {
            try {
                Write-Host "Trying $url ..." -ForegroundColor Gray
                Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing -MaximumRedirection 5
                $downloaded = $true
                break
            } catch {
                Write-Host "  Failed: $($_.Exception.Message)" -ForegroundColor DarkGray
            }
        }
        if (-not $downloaded) {
            Write-Host "Download failed from all mirrors." -ForegroundColor Red
            Write-Host "Install Maven manually: https://maven.apache.org/download.cgi or run: winget install Apache.Maven" -ForegroundColor Yellow
            exit 1
        }
        Expand-Archive -Path $zipPath -DestinationPath $CacheDir -Force
        Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
        Write-Host "Maven $MavenVersion ready." -ForegroundColor Green
    }
    $env:MAVEN_HOME = $MavenHome
    $MavenCmd = "& `"$mvnBat`""
}

# --- Ensure Java is available (JAVA_HOME, PATH, or common Windows locations) ---
function Find-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) { return $env:JAVA_HOME }
    $javaExe = Get-Command java -ErrorAction SilentlyContinue
    if ($javaExe) {
        $bin = Split-Path $javaExe.Source -Parent
        return (Resolve-Path (Join-Path $bin "..")).Path
    }
    $searchPaths = @(
        "${env:ProgramFiles}\Java\jdk*",
        "${env:ProgramFiles}\Java\jbr*",
        "${env:ProgramFiles}\Eclipse Adoptium\jdk*",
        "${env:ProgramFiles}\Microsoft\jdk*",
        "${env:ProgramFiles}\Amazon Corretto\*",
        "${env:LOCALAPPDATA}\Programs\Eclipse Adoptium\jdk*",
        "${env:LOCALAPPDATA}\Programs\Microsoft\jdk*"
    )
    $minVersion = 8
    foreach ($pattern in $searchPaths) {
        $dirs = Get-Item $pattern -ErrorAction SilentlyContinue | Sort-Object { $_.Name } -Descending
        foreach ($d in $dirs) {
            $javaExe = Join-Path $d.FullName "bin\java.exe"
            if (Test-Path $javaExe) {
                try {
                    $ver = & $javaExe -version 2>&1 | Out-String
                    if ($ver -match "version `"(\d+)") { $v = [int]$matches[1]; if ($v -ge $minVersion) { return $d.FullName } }
                } catch { }
            }
        }
    }
    return $null
}
$javaHome = Find-JavaHome
if ($javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
} else {
    Write-Host "Java not found. Install JDK 8+ and set JAVA_HOME or add java to PATH." -ForegroundColor Red
    Write-Host "Or install with: winget install Microsoft.OpenJDK.8" -ForegroundColor Yellow
    exit 1
}

# --- Run clean, test, package ---
Write-Host "Running: clean test package ..." -ForegroundColor Cyan
if ($MavenCmd -eq "mvn") {
    & mvn clean test package
} else {
    Invoke-Expression "$MavenCmd clean test package"
}
$exitCode = $LASTEXITCODE
if ($exitCode -eq 0) {
    Write-Host "Build and tests completed successfully." -ForegroundColor Green
    $jar = Get-ChildItem -Path (Join-Path $ProjectRoot "target") -Filter "*.jar" -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch "original" } | Select-Object -First 1
    if ($jar) { Write-Host "JAR: $($jar.FullName)" -ForegroundColor Gray }
} else {
    Write-Host "Build or tests failed (exit code $exitCode)." -ForegroundColor Red
}
exit $exitCode
