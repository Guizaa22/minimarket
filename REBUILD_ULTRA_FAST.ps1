# Script de rebuild ultra-rapide - Optimisé pour développement
# Utilise le cache Maven et réutilise le runtime si possible

param(
    [switch]$Clean = $false,
    [switch]$SkipRuntime = $false
)

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  REBUILD ULTRA-RAPIDE - 2M Market" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Configuration Java
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
if (-not (Test-Path $env:JAVA_HOME)) {
    Write-Host "ERREUR: JAVA_HOME introuvable: $env:JAVA_HOME" -ForegroundColor Red
    exit 1
}

$jpackageExe = "$env:JAVA_HOME\bin\jpackage.exe"
$jlinkExe = "$env:JAVA_HOME\bin\jlink.exe"
$mvnExe = "mvn"

# Vérifier Maven
if (-not (Get-Command $mvnExe -ErrorAction SilentlyContinue)) {
    Write-Host "ERREUR: Maven non trouvé!" -ForegroundColor Red
    exit 1
}

$startTime = Get-Date

# ============================================
# ÉTAPE 1: Clean (optionnel)
# ============================================
if ($Clean) {
    Write-Host "[1/5] Nettoyage..." -ForegroundColor Yellow
    & $mvnExe clean -q 2>&1 | Out-Null
} else {
    Write-Host "[1/5] Nettoyage: SKIP (utilise --Clean pour forcer)" -ForegroundColor Gray
}

# ============================================
# ÉTAPE 2: Compilation rapide
# ============================================
Write-Host "[2/5] Compilation..." -ForegroundColor Cyan
& $mvnExe compile -DskipTests -q 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: Compilation échouée!" -ForegroundColor Red
    exit 1
}

# ============================================
# ÉTAPE 3: Package JAR
# ============================================
Write-Host "[3/5] Création JAR..." -ForegroundColor Cyan
& $mvnExe package -DskipTests -q 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: Package échoué!" -ForegroundColor Red
    exit 1
}

# Copier dépendances
& $mvnExe dependency:copy-dependencies -DoutputDirectory=target/lib -DskipTests -q 2>&1 | Out-Null

$jarFile = Get-ChildItem -Path "target" -Filter "*-SNAPSHOT.jar" | Where-Object { $_.Name -notmatch "original" } | Select-Object -First 1

if (-not $jarFile) {
    Write-Host "ERREUR: JAR non trouvé!" -ForegroundColor Red
    exit 1
}

Write-Host "  [OK] JAR: $($jarFile.Name)" -ForegroundColor Green

# ============================================
# ÉTAPE 4: Runtime (réutiliser si existe)
# ============================================
$runtimeDir = "target\runtime"
if ($SkipRuntime -and (Test-Path "$runtimeDir\bin\java.exe")) {
    Write-Host "[4/5] Runtime: REUTILISE (utilise --SkipRuntime:$false pour recréer)" -ForegroundColor Gray
} else {
    Write-Host "[4/5] Création runtime..." -ForegroundColor Cyan
    if (Test-Path $runtimeDir) {
        Remove-Item -Path $runtimeDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    
    $jmodsPath = "$env:JAVA_HOME\jmods"
    
    # Vérifier que les modules JavaFX sont disponibles
    $javafxJars = Get-ChildItem -Path "target\lib" -Filter "javafx-*.jar" -ErrorAction SilentlyContinue
    if (-not $javafxJars) {
        Write-Host "  ATTENTION: Modules JavaFX non trouvés dans target\lib" -ForegroundColor Yellow
        Write-Host "  Vérification des dépendances..." -ForegroundColor Gray
    }
    
    $modules = "java.base,java.desktop,java.logging,java.sql,jdk.unsupported"
    
    # Ajouter JavaFX si disponible
    if ($javafxJars) {
        $modules += ",javafx.controls,javafx.fxml,javafx.graphics,javafx.base"
    }
    
    Write-Host "  Modules: $modules" -ForegroundColor Gray
    $jlinkOutput = & $jlinkExe --module-path "$jmodsPath;target\lib" `
                --add-modules $modules `
                --output $runtimeDir `
                --strip-debug `
                --compress 2 `
                --no-header-files `
                --no-man-pages 2>&1
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERREUR: Création runtime échouée!" -ForegroundColor Red
        Write-Host "Détails:" -ForegroundColor Yellow
        $jlinkOutput | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
        exit 1
    }
    
    # Vérifier que le runtime est valide
    if (-not (Test-Path "$runtimeDir\bin\java.exe")) {
        Write-Host "ERREUR: Runtime créé mais java.exe introuvable!" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "  [OK] Runtime créé" -ForegroundColor Green
}

# ============================================
# ÉTAPE 5: App-image (jpackage)
# ============================================
Write-Host "[5/5] Création app-image..." -ForegroundColor Cyan

$outputDir = "target\installer"
$appName = "2M-Market-App"
$appPath = "$outputDir\$appName"

# Nettoyer COMPLÈTEMENT le dossier installer pour éviter la récursion
if (Test-Path $outputDir) {
    Write-Host "  Nettoyage complet de target\installer..." -ForegroundColor Gray
    # Arrêter les processus qui pourraient utiliser les fichiers
    Get-Process | Where-Object { $_.Path -like "*$appName*" } | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
    
    # Supprimer tout le dossier installer
    $maxRetries = 5
    $retryCount = 0
    while ((Test-Path $outputDir) -and ($retryCount -lt $maxRetries)) {
        try {
            Remove-Item -Path $outputDir -Recurse -Force -ErrorAction Stop
            break
        } catch {
            $retryCount++
            Write-Host "    Tentative $retryCount/$maxRetries..." -ForegroundColor Gray
            Start-Sleep -Seconds 2
        }
    }
    
    # Si toujours présent, utiliser robocopy pour nettoyer
    if (Test-Path $outputDir) {
        $emptyDir = "$env:TEMP\empty_$(Get-Random)"
        New-Item -ItemType Directory -Path $emptyDir -Force | Out-Null
        robocopy $emptyDir $outputDir /MIR /NFL /NDL /NJH /NJS | Out-Null
        Remove-Item -Path $emptyDir -Recurse -Force -ErrorAction SilentlyContinue
        Remove-Item -Path $outputDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    
    Start-Sleep -Seconds 2
}

# Créer un dossier temporaire propre pour l'input (évite la récursion)
$tempInputDir = "$env:TEMP\jpackage_input_$(Get-Random)"
Write-Host "  Création dossier input temporaire: $tempInputDir" -ForegroundColor Gray

$jpackageOutput = $null
$jpackageSuccess = $false

try {
    # Créer le dossier temporaire
    New-Item -ItemType Directory -Path $tempInputDir -Force | Out-Null
    
    # Copier uniquement le JAR et les libs nécessaires
    Copy-Item -Path $jarFile.FullName -Destination $tempInputDir -Force
    if (Test-Path "target\lib") {
        $tempLibDir = "$tempInputDir\lib"
        New-Item -ItemType Directory -Path $tempLibDir -Force | Out-Null
        Copy-Item -Path "target\lib\*" -Destination $tempLibDir -Recurse -Force
    }
    
    # Créer le dossier de destination
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
    
    # Vérifier que le runtime existe et est valide
    if (-not (Test-Path "$runtimeDir\bin\java.exe")) {
        Write-Host "ERREUR: Runtime invalide ou introuvable: $runtimeDir" -ForegroundColor Red
        exit 1
    }
    
    # Créer l'app-image avec affichage des erreurs
    Write-Host "  Exécution jpackage..." -ForegroundColor Gray
    Write-Host "    Input: $tempInputDir" -ForegroundColor Gray
    Write-Host "    Runtime: $runtimeDir" -ForegroundColor Gray
    Write-Host "    JAR: $($jarFile.Name)" -ForegroundColor Gray
    
    $jpackageOutput = & $jpackageExe --type app-image `
                  --input $tempInputDir `
                  --dest $outputDir `
                  --name $appName `
                  --main-jar $jarFile.Name `
                  --main-class "app.MainApp" `
                  --runtime-image (Resolve-Path $runtimeDir).Path `
                  --java-options "-Xmx512m" `
                  --app-version "1.0" `
                  --vendor "2M Market" 2>&1
    
    $jpackageSuccess = ($LASTEXITCODE -eq 0)
} finally {
    # Nettoyer le dossier temporaire
    if (Test-Path $tempInputDir) {
        Remove-Item -Path $tempInputDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if (-not $jpackageSuccess) {
    Write-Host "ERREUR: Création app-image échouée!" -ForegroundColor Red
    Write-Host "Détails de l'erreur:" -ForegroundColor Yellow
    if ($jpackageOutput) {
        $jpackageOutput | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    }
    exit 1
}

# Vérifier et corriger le runtime dans l'app-image
$runtimeInApp = "$appPath\runtime"
if (-not (Test-Path "$runtimeInApp\bin\java.exe")) {
    Write-Host "  Correction du runtime..." -ForegroundColor Yellow
    if (Test-Path $runtimeInApp) {
        Remove-Item -Path $runtimeInApp -Recurse -Force -ErrorAction SilentlyContinue
    }
    Copy-Item -Path $runtimeDir -Destination $runtimeInApp -Recurse -Force
    Write-Host "  [OK] Runtime corrigé" -ForegroundColor Green
}

# Vérifier que l'exécutable existe
if (-not (Test-Path "$appPath\$appName.exe")) {
    Write-Host "ERREUR: Exécutable non créé!" -ForegroundColor Red
    exit 1
}

Write-Host "  [OK] App-image créé" -ForegroundColor Green

# ============================================
# RÉSULTAT
# ============================================
$endTime = Get-Date
$duration = $endTime - $startTime

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  TERMINE EN $($duration.TotalSeconds.ToString('F1')) SECONDES!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Emplacement: $appPath" -ForegroundColor Cyan
Write-Host "Exécutable: $appPath\$appName.exe" -ForegroundColor Cyan
Write-Host ""
Write-Host "Pour lancer:" -ForegroundColor Yellow
Write-Host "  cd `"$appPath`"" -ForegroundColor White
Write-Host "  .\$appName.exe" -ForegroundColor White
Write-Host ""
Write-Host "Pour copier sur un autre PC:" -ForegroundColor Yellow
Write-Host "  Copiez tout le dossier: $appPath" -ForegroundColor White
Write-Host ""

