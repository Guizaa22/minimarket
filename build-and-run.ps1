# Script simple : Build et Run l'application
# Crée l'application si elle n'existe pas, puis la lance

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Build & Run - 2M Market" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Vérifier JAVA_HOME
if ([string]::IsNullOrEmpty($env:JAVA_HOME)) {
    Write-Host "[ERROR] JAVA_HOME n'est pas défini!" -ForegroundColor Red
    exit 1
}

$javaExe = "$env:JAVA_HOME\bin\java.exe"
if (-not (Test-Path $javaExe)) {
    Write-Host "[ERROR] java.exe non trouvé: $javaExe" -ForegroundColor Red
    exit 1
}

Write-Host "JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Green
Write-Host ""

# Chercher l'application déployée
Write-Host "Recherche de l'application déployée..." -ForegroundColor Yellow
Write-Host ""

$appExe = $null
$appDir = $null

$possibleAppDirs = @(
    "target\installer\2M-Market-App",
    "dist\2M-Market"
)

foreach ($dir in $possibleAppDirs) {
    $exePath = Join-Path $dir "2M-Market-App.exe"
    if (-not (Test-Path $exePath)) {
        $exePath = Join-Path $dir "2M-Market.exe"
    }
    
    if (Test-Path $exePath) {
        $appExe = (Resolve-Path $exePath).Path
        $appDir = Split-Path $appExe -Parent
        Write-Host "  [OK] Application trouvée: $appExe" -ForegroundColor Green
        break
    }
}

# Si l'application n'existe pas, la créer
if (-not $appExe) {
    Write-Host "  [WARNING] Application déployée non trouvée" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Création de l'application..." -ForegroundColor Cyan
    Write-Host "  (Cela peut prendre 5-10 minutes...)" -ForegroundColor Yellow
    Write-Host ""
    
    if (Test-Path "build-with-maven.ps1") {
        & .\build-with-maven.ps1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host ""
            Write-Host "[OK] Application créée!" -ForegroundColor Green
            Write-Host ""
            
            # Chercher à nouveau l'application
            foreach ($dir in $possibleAppDirs) {
                $exePath = Join-Path $dir "2M-Market-App.exe"
                if (-not (Test-Path $exePath)) {
                    $exePath = Join-Path $dir "2M-Market.exe"
                }
                
                if (Test-Path $exePath) {
                    $appExe = (Resolve-Path $exePath).Path
                    $appDir = Split-Path $appExe -Parent
                    Write-Host "  [OK] Application trouvée: $appExe" -ForegroundColor Green
                    break
                }
            }
        } else {
            Write-Host ""
            Write-Host "[ERROR] Échec de la création de l'application!" -ForegroundColor Red
            Write-Host "  Vérifiez les erreurs ci-dessus" -ForegroundColor Yellow
            exit 1
        }
    } else {
        Write-Host "[ERROR] build-with-maven.ps1 non trouvé!" -ForegroundColor Red
        Write-Host "  Impossible de créer l'application" -ForegroundColor Yellow
        exit 1
    }
}

if (-not $appExe) {
    Write-Host ""
    Write-Host "[ERROR] Application non trouvée après build!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Vérifiez manuellement:" -ForegroundColor Yellow
    Write-Host "  - target\installer\2M-Market-App\2M-Market-App.exe" -ForegroundColor White
    Write-Host "  - dist\2M-Market\2M-Market.exe" -ForegroundColor White
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Lancement de l'application" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Application: $appExe" -ForegroundColor Green
Write-Host ""
Write-Host "  ========================================" -ForegroundColor Yellow
Write-Host "  IDENTIFIANTS DE CONNEXION" -ForegroundColor Yellow
Write-Host "  ========================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "  Username: admin" -ForegroundColor White
Write-Host "  Password: admin123" -ForegroundColor White
Write-Host ""
Write-Host "  OU" -ForegroundColor Gray
Write-Host ""
Write-Host "  Username: employe" -ForegroundColor White
Write-Host "  Password: admin123" -ForegroundColor White
Write-Host ""
Write-Host "  ========================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "  (Les erreurs SQL seront affichées dans cette console)" -ForegroundColor Gray
Write-Host ""

# Lancer l'application
Start-Process -FilePath $appExe -WorkingDirectory $appDir -NoNewWindow -Wait

Write-Host ""
Write-Host "Application fermée." -ForegroundColor Yellow
Write-Host ""

