# Script pour trouver Java installé
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Finding Java JDK Installation" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$searchPaths = @(
    "C:\Program Files\Java",
    "C:\Program Files (x86)\Java",
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Microsoft",
    "C:\Program Files\Amazon Corretto"
)

$foundAny = $false

foreach ($basePath in $searchPaths) {
    if (Test-Path $basePath) {
        Write-Host "Searching in: $basePath" -ForegroundColor Yellow
        
        $jdkDirs = Get-ChildItem -Path $basePath -Recurse -Directory -Filter "*jdk*" -ErrorAction SilentlyContinue | 
                   Where-Object { Test-Path "$($_.FullName)\bin\java.exe" } |
                   Sort-Object FullName -Descending
        
        if ($jdkDirs) {
            $foundAny = $true
            foreach ($jdk in $jdkDirs) {
                Write-Host ""
                Write-Host "  [FOUND] $($jdk.FullName)" -ForegroundColor Green
                
                # Test java.exe
                $javaExe = "$($jdk.FullName)\bin\java.exe"
                if (Test-Path $javaExe) {
                    $version = & $javaExe -version 2>&1 | Select-Object -First 1
                    Write-Host "    Java: $version" -ForegroundColor White
                }
                
                # Test jpackage.exe
                $jpackageExe = "$($jdk.FullName)\bin\jpackage.exe"
                if (Test-Path $jpackageExe) {
                    Write-Host "    jpackage.exe: FOUND" -ForegroundColor Green
                    Write-Host "    [OK] This JDK can be used!" -ForegroundColor Green
                } else {
                    Write-Host "    jpackage.exe: NOT FOUND" -ForegroundColor Yellow
                    Write-Host "    [WARN] This is a JRE, not a JDK" -ForegroundColor Yellow
                }
            }
        } else {
            Write-Host "  No JDK found" -ForegroundColor Gray
        }
    } else {
        Write-Host "Path not found: $basePath" -ForegroundColor Gray
    }
}

Write-Host ""
if ($foundAny) {
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  Java JDK Found!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "You can now run: .\build-exe.ps1" -ForegroundColor Cyan
} else {
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "  No Java JDK Found!" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install Java JDK 17 or 21 from:" -ForegroundColor Yellow
    Write-Host "  https://adoptium.net/" -ForegroundColor White
}

