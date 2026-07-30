# Script pour trouver Maven installé
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Finding Maven Installation" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Chercher dans le PATH d'abord
$mavenPath = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if ($mavenPath) {
    Write-Host "[FOUND] Maven in PATH:" -ForegroundColor Green
    Write-Host "  Location: $mavenPath" -ForegroundColor White
    $version = mvn -version 2>&1 | Select-Object -First 1
    Write-Host "  Version: $version" -ForegroundColor White
    Write-Host ""
    Write-Host "[OK] Maven is ready to use!" -ForegroundColor Green
    exit 0
}

Write-Host "Maven not in PATH, searching installation directories..." -ForegroundColor Yellow
Write-Host ""

$mavenSearchPaths = @(
    "$env:USERPROFILE\.m2\wrapper\dists",
    "$env:USERPROFILE\apache-maven",
    "C:\Program Files\Apache\maven",
    "C:\apache-maven",
    "C:\maven",
    "$env:USERPROFILE\maven",
    "C:\Program Files",
    "C:\Program Files (x86)"
)

$foundAny = $false

foreach ($basePath in $mavenSearchPaths) {
    if (Test-Path $basePath) {
        Write-Host "Searching in: $basePath" -ForegroundColor Yellow
        
        $mvnFiles = Get-ChildItem -Path $basePath -Recurse -Filter "mvn.cmd" -ErrorAction SilentlyContinue | 
                    Select-Object -First 3
        
        if ($mvnFiles) {
            $foundAny = $true
            foreach ($mvn in $mvnFiles) {
                $mavenDir = Split-Path (Split-Path $mvn.FullName)
                Write-Host ""
                Write-Host "  [FOUND] $mavenDir" -ForegroundColor Green
                
                # Test mvn.cmd
                if (Test-Path $mvn.FullName) {
                    $version = & $mvn.FullName -version 2>&1 | Select-Object -First 1
                    Write-Host "    Version: $version" -ForegroundColor White
                    Write-Host "    [OK] This Maven can be used!" -ForegroundColor Green
                }
            }
        }
    }
}

Write-Host ""
if ($foundAny) {
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  Maven Found!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "The build script will find it automatically." -ForegroundColor Cyan
} else {
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "  No Maven Found!" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install Maven:" -ForegroundColor Yellow
    Write-Host "  1. Download from: https://maven.apache.org/download.cgi" -ForegroundColor White
    Write-Host "  2. Extract to: C:\apache-maven" -ForegroundColor White
    Write-Host "  3. Add C:\apache-maven\bin to PATH" -ForegroundColor White
    Write-Host ""
    Write-Host "Or install via Chocolatey:" -ForegroundColor Cyan
    Write-Host "  choco install maven" -ForegroundColor White
}

