# Script pour copier les DLLs JavaFX dans le runtime
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Fixing JavaFX Native Libraries" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$appDir = "dist\2M-Market"
$runtimeBin = "$appDir\runtime\bin"

if (-not (Test-Path $runtimeBin)) {
    Write-Host "ERROR: Runtime directory not found: $runtimeBin" -ForegroundColor Red
    exit 1
}

Write-Host "Looking for JavaFX DLLs in Maven repository..." -ForegroundColor Yellow

$mavenRepo = "$env:USERPROFILE\.m2\repository"
$javafxBase = "$mavenRepo\org\openjfx"
$javafxVersion = "21"

$dllsFound = $false
$javafxPaths = @(
    "$javafxBase\javafx-controls\$javafxVersion",
    "$javafxBase\javafx-fxml\$javafxVersion",
    "$javafxBase\javafx-graphics\$javafxVersion",
    "$javafxBase\javafx-base\$javafxVersion"
)

foreach ($path in $javafxPaths) {
    if (Test-Path $path) {
        $dlls = Get-ChildItem -Path $path -Recurse -Filter "*.dll" -ErrorAction SilentlyContinue
        if ($dlls) {
            Write-Host "  Found DLLs in: $path" -ForegroundColor Green
            foreach ($dll in $dlls) {
                $destPath = "$runtimeBin\$($dll.Name)"
                Copy-Item -Path $dll.FullName -Destination $destPath -Force -ErrorAction SilentlyContinue
                if (Test-Path $destPath) {
                    Write-Host "    Copied: $($dll.Name)" -ForegroundColor Cyan
                    $dllsFound = $true
                }
            }
        }
    }
}

# Chercher d'abord dans target/lib (plus fiable)
if (-not $dllsFound) {
    Write-Host "Checking target/lib for JavaFX DLLs..." -ForegroundColor Yellow
    if (Test-Path "target\lib") {
        $dlls = Get-ChildItem -Path "target\lib" -Recurse -Filter "*.dll" -ErrorAction SilentlyContinue
        if ($dlls) {
            foreach ($dll in $dlls) {
                $destPath = "$runtimeBin\$($dll.Name)"
                Copy-Item -Path $dll.FullName -Destination $destPath -Force -ErrorAction SilentlyContinue
                if (Test-Path $destPath) {
                    Write-Host "    Copied: $($dll.Name)" -ForegroundColor Cyan
                    $dllsFound = $true
                }
            }
        } else {
            Write-Host "  No DLLs found in target/lib. Running mvn dependency:copy-dependencies..." -ForegroundColor Yellow
            mvn dependency:copy-dependencies -q
            if (Test-Path "target\lib") {
                $dlls = Get-ChildItem -Path "target\lib" -Recurse -Filter "*.dll" -ErrorAction SilentlyContinue
                if ($dlls) {
                    foreach ($dll in $dlls) {
                        $destPath = "$runtimeBin\$($dll.Name)"
                        Copy-Item -Path $dll.FullName -Destination $destPath -Force -ErrorAction SilentlyContinue
                        if (Test-Path $destPath) {
                            Write-Host "    Copied: $($dll.Name)" -ForegroundColor Cyan
                            $dllsFound = $true
                        }
                    }
                }
            }
        }
    } else {
        Write-Host "  target/lib not found. Running mvn dependency:copy-dependencies..." -ForegroundColor Yellow
        mvn dependency:copy-dependencies -q
        if (Test-Path "target\lib") {
            $dlls = Get-ChildItem -Path "target\lib" -Recurse -Filter "*.dll" -ErrorAction SilentlyContinue
            if ($dlls) {
                foreach ($dll in $dlls) {
                    $destPath = "$runtimeBin\$($dll.Name)"
                    Copy-Item -Path $dll.FullName -Destination $destPath -Force -ErrorAction SilentlyContinue
                    if (Test-Path $destPath) {
                        Write-Host "    Copied: $($dll.Name)" -ForegroundColor Cyan
                        $dllsFound = $true
                    }
                }
            }
        }
    }
}

if ($dllsFound) {
    Write-Host ""
    Write-Host "[OK] JavaFX DLLs copied to runtime!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Try running the application again:" -ForegroundColor Yellow
    Write-Host "  .\dist\2M-Market\2M-Market.exe" -ForegroundColor White
} else {
    Write-Host ""
    Write-Host "WARNING: Could not find JavaFX DLLs!" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Solution: Download JavaFX SDK and extract DLLs manually" -ForegroundColor Cyan
    Write-Host "  1. Download from: https://openjfx.io/" -ForegroundColor White
    Write-Host "  2. Extract javafx-sdk-21\bin\*.dll" -ForegroundColor White
    Write-Host "  3. Copy to: $runtimeBin" -ForegroundColor White
}

