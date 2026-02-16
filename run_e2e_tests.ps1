# Run E2E tests for TrinoMyDriver
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
    $MavenVersion = "3.9.6"
    $MavenZip = "apache-maven-$MavenVersion-bin.zip"
    $CacheDir = Join-Path $ProjectRoot ".maven-cache"
    $MavenHome = Join-Path $CacheDir "apache-maven-$MavenVersion"
    $MavenBin = Join-Path $MavenHome "bin"
    $mvnBat = Join-Path $MavenBin "mvn.cmd"

    if (-not (Test-Path $mvnBat)) {
        Write-Host "Maven not found. Downloading Maven $MavenVersion to $CacheDir ..." -ForegroundColor Yellow
        if (-not (Test-Path $CacheDir)) { New-Item -ItemType Directory -Path $CacheDir -Force | Out-Null }
        $zipPath = Join-Path $CacheDir $MavenZip
        # Prefer TLS 1.2
        try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }
        $url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MavenVersion/apache-maven-$MavenVersion-bin.zip"
        
        try {
            Write-Host "Downloading $url ..." -ForegroundColor Gray
            Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing
        } catch {
            Write-Host "Download failed: $($_.Exception.Message)" -ForegroundColor Red
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
    
    # Relax error action for version check as java -version writes to stderr
    $oldEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    
    foreach ($pattern in $searchPaths) {
        $dirs = Get-Item $pattern -ErrorAction SilentlyContinue | Sort-Object { $_.Name } -Descending
        foreach ($d in $dirs) {
            $javaExe = Join-Path $d.FullName "bin\java.exe"
            if (Test-Path $javaExe) {
                try {
                    $ver = & $javaExe -version 2>&1 | Out-String
                    if ($ver -match "version `"(\d+)") { 
                        $v = [int]$matches[1]
                        if ($v -ge $minVersion) { 
                            $ErrorActionPreference = $oldEAP
                            return $d.FullName 
                        } 
                    }
                } catch { }
            }
        }
    }
    $ErrorActionPreference = $oldEAP
    return $null
}
$javaHome = Find-JavaHome
if ($javaHome) {
    if (-not $env:JAVA_HOME) {
        $env:JAVA_HOME = $javaHome
        Write-Host "Set JAVA_HOME to $javaHome"
    }
    # Ensure java is in path if not already
    if ($env:Path -notlike "*$javaHome\bin*") {
        $env:Path = "$javaHome\bin;$env:Path"
    }
} else {
    Write-Host "Java not found or JAVA_HOME not set. Install JDK 8+." -ForegroundColor Red
    exit 1
}

# --- Ensure environment variables ---
if (-not $env:TRINO_E2E_HOST) { $env:TRINO_E2E_HOST = "localhost" }
if (-not $env:TRINO_E2E_PORT) { $env:TRINO_E2E_PORT = "8080" }

Write-Host "Trino Host: $env:TRINO_E2E_HOST"
Write-Host "Trino Port: $env:TRINO_E2E_PORT"

# --- Run clean package ---
Write-Host "Building project (skipping unit tests)..." -ForegroundColor Cyan
# Prepare the command string. If $MavenCmd is strict "mvn", invoke directly, else invoke expression
if ($MavenCmd -eq "mvn") {
    mvn clean package -DskipTests
} else {
    Invoke-Expression "$MavenCmd clean package -DskipTests"
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

# --- Run E2E Runner ---
Write-Host "Running E2E Tests..." -ForegroundColor Cyan
$classpath = "target/trino-my-driver-1.0.0-SNAPSHOT-all.jar;target/test-classes"
java -cp $classpath com.demo.e2e.TrinoE2ERunner

if ($LASTEXITCODE -ne 0) {
    Write-Host "E2E Tests FAILED." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "E2E Tests PASSED." -ForegroundColor Green
exit 0
