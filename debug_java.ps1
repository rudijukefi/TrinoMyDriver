$searchPaths = @(
    "${env:ProgramFiles}\Java\jdk*",
    "C:\Program Files\Java\jdk*"
)

foreach ($pattern in $searchPaths) {
    $dirs = Get-Item $pattern -ErrorAction SilentlyContinue 
    if ($dirs) {
            foreach ($d in $dirs) {
            $javaExe = Join-Path $d.FullName "bin\java.exe"
            if (Test-Path $javaExe) {
                    Write-Host "Found java at $javaExe"
                    try {
                        $ver = & $javaExe -version 2>&1 | Out-String
                        Write-Host "Version output: [$ver]"
                        if ($ver -match "version `"(\d+)") {
                            Write-Host "Regex MATCHED: $($matches[0])"
                            Write-Host "Major version: $($matches[1])"
                        } else {
                            Write-Host "Regex NO MATCH"
                        }
                    } catch {
                        Write-Host "Error running java: $_"
                    }
            }
            }
    }
}
