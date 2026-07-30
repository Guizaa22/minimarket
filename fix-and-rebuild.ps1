# Script complet pour corriger et reconstruire l'application
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Fix JavaFX and Rebuild Application" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Étape 1: Télécharger les dépendances JavaFX
Write-Host "Step 1: Downloading JavaFX dependencies..." -ForegroundColor Yellow
mvn dependency:copy-dependencies -DoutputDirectory=target/lib -DincludeScope=runtime
if ($LASTEXITCODE -ne 0) {
    Write-Host "WARNING: Failed to download dependencies" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Step 2: Copying JavaFX DLLs to existing application..." -ForegroundColor Yellow

$appDir = "dist\2M-Market"
$runtimeBin = "$appDir\runtime\bin"

if (-not (Test-Path $runtimeBin)) {
    Write-Host "ERROR: Application not found. Please run build-exe.ps1 first." -ForegroundColor Red
    exit 1
}

$dllsCopied = 0

# Chercher dans target/lib
if (Test-Path "target\lib") {
    $dlls = Get-ChildItem -Path "target\lib" -Recurse -Filter "*.dll" -ErrorAction SilentlyContinue
    foreach ($dll in $dlls) {
        $destPath = "$runtimeBin\$($dll.Name)"
        Copy-Item -Path $dll.FullName -Destination $destPath -Force -ErrorAction SilentlyContinue
        if (Test-Path $destPath) {
            Write-Host "  Copied: $($dll.Name)" -ForegroundColor Green
            $dllsCopied++
        }
    }
}

# Chercher dans Maven repository
$mavenRepo = "$env:USERPROFILE\.m2\repository"
$javafxBase = "$mavenRepo\org\openjfx"
$javafxVersion = "21"

$javafxPaths = @(
    "$javafxBase\javafx-controls\$javafxVersion",
    "$javafxBase\javafx-fxml\$javafxVersion",
    "$javafxBase\javafx-graphics\$javafxVersion",
    "$javafxBase\javafx-base\$javafxVersion"
)

foreach ($path in $javafxPaths) {
    if (Test-Path $path) {
        $dlls = Get-ChildItem -Path $path -Recurse -Filter "*.dll" -ErrorAction SilentlyContinue
        foreach ($dll in $dlls) {
            $destPath = "$runtimeBin\$($dll.Name)"
            if (-not (Test-Path $destPath)) {
                Copy-Item -Path $dll.FullName -Destination $destPath -Force -ErrorAction SilentlyContinue
                if (Test-Path $destPath) {
                    Write-Host "  Copied: $($dll.Name)" -ForegroundColor Green
                    $dllsCopied++
                }
            }
        }
    }
}

Write-Host ""
if ($dllsCopied -gt 0) {
    Write-Host "[OK] Copied $dllsCopied JavaFX DLL(s)!" -ForegroundColor Green
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  Application Fixed!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Try running the application now:" -ForegroundColor Yellow
    Write-Host "  .\dist\2M-Market\2M-Market.exe" -ForegroundColor White
} else {
    Write-Host "[WARNING] No JavaFX DLLs found!" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "You need to download JavaFX SDK manually:" -ForegroundColor Cyan
    Write-Host "  1. Go to: https://openjfx.io/" -ForegroundColor White
    Write-Host "  2. Download JavaFX SDK 21 for Windows" -ForegroundColor White
    Write-Host "  3. Extract and copy all .dll files from:" -ForegroundColor White
    Write-Host "     javafx-sdk-21\bin\*.dll" -ForegroundColor White
    Write-Host "  4. Copy to: $runtimeBin" -ForegroundColor White
}

