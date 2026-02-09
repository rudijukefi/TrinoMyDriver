$javaExe = "C:\Program Files\Java\jdk-17\bin\java.exe"

$ErrorActionPreference = "Stop"
try {
    Write-Host "Running java -version with Stop..."
    $ver = & $javaExe -version 2>&1 | Out-String
    Write-Host "Success: $ver"
} catch {
    Write-Host "CAUGHT ERROR: $_"
}

$ErrorActionPreference = "Continue"
try {
    Write-Host "Running java -version with Continue..."
    $ver = & $javaExe -version 2>&1 | Out-String
    Write-Host "Success: $ver"
} catch {
    Write-Host "CAUGHT ERROR: $_"
}
