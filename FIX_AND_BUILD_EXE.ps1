# Script pour créer un .exe portable qui fonctionne sur n'importe quel PC

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Création .exe Portable 2M Market" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Fixer JAVA_HOME
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
$jpackageExe = "$env:JAVA_HOME\bin\jpackage.exe"
$jlinkExe = "$env:JAVA_HOME\bin\jlink.exe"

if (-not (Test-Path $jpackageExe)) {
    Write-Host "[ERROR] jpackage non trouvé!" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Java JDK: $env:JAVA_HOME" -ForegroundColor Green
Write-Host ""

# Étape 1: Build Maven
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Étape 1/4: Build Maven" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

mvn package -DskipTests -q

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Build Maven échoué!" -ForegroundColor Red
    exit 1
}

# Vérifier le JAR
$jarFile = Get-ChildItem -Path "target" -Filter "*-SNAPSHOT.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch "original" } | Select-Object -First 1

if (-not $jarFile) {
    Write-Host "[ERROR] JAR non trouvé!" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] JAR: $($jarFile.Name)" -ForegroundColor Green
Write-Host ""

# Étape 2: Copier les dépendances JavaFX
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Étape 2/4: Copie des dépendances JavaFX" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path "target\lib")) {
    Write-Host "Copie des dépendances..." -ForegroundColor Yellow
    mvn dependency:copy-dependencies -DoutputDirectory=target/lib -DskipTests -q
}

Write-Host "[OK] Dépendances copiées" -ForegroundColor Green
Write-Host ""

# Étape 3: Créer le runtime avec jlink
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Étape 3/4: Création du Runtime (jlink)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$runtimeDir = "target\runtime"
if (Test-Path $runtimeDir) {
    Write-Host "Nettoyage de l'ancien runtime..." -ForegroundColor Yellow
    Remove-Item -Path $runtimeDir -Recurse -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

$jmodsPath = "$env:JAVA_HOME\jmods"
$modulePath = "target\lib"

# Trouver les modules JavaFX
$javafxModules = @()
$javafxJars = Get-ChildItem -Path $modulePath -Filter "javafx-*.jar" -ErrorAction SilentlyContinue
foreach ($jar in $javafxJars) {
    $moduleName = $jar.BaseName -replace '-.*$', ''
    if ($moduleName -match 'javafx\.(controls|fxml|graphics|base)') {
        $javafxModules += $moduleName
    }
}

$modulesList = "java.base,java.desktop,java.logging,java.scripting,java.sql,jdk.unsupported"
if ($javafxModules.Count -gt 0) {
    $modulesList += "," + ($javafxModules -join ",")
}

Write-Host "Modules: $modulesList" -ForegroundColor Gray
Write-Host "Exécution de jlink..." -ForegroundColor Yellow

$jlinkArgs = @(
    "--module-path", "$jmodsPath;$modulePath",
    "--add-modules", $modulesList,
    "--output", $runtimeDir,
    "--strip-debug",
    "--compress", "2",
    "--no-header-files",
    "--no-man-pages"
)

& $jlinkExe $jlinkArgs 2>&1 | Out-Null

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] jlink a échoué!" -ForegroundColor Red
    exit 1
}

# Vérifier que java.exe existe dans le runtime
if (-not (Test-Path "$runtimeDir\bin\java.exe")) {
    Write-Host "[ERROR] Runtime incomplet (java.exe manquant)!" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Runtime créé avec succès" -ForegroundColor Green
Write-Host ""

# Étape 4: Créer l'app-image avec jpackage
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Étape 4/4: Création de l'app-image" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$outputDir = "target\installer"
$appName = "2M-Market-App"

# Nettoyer l'ancien app-image
if (Test-Path "$outputDir\$appName") {
    Write-Host "Nettoyage de l'ancien app-image..." -ForegroundColor Yellow
    Remove-Item -Path "$outputDir\$appName" -Recurse -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
}

if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

Write-Host "Exécution de jpackage (app-image)..." -ForegroundColor Yellow
Write-Host ""

$jpackageArgs = @(
    "--type", "app-image",
    "--input", "target",
    "--dest", $outputDir,
    "--name", $appName,
    "--main-jar", $jarFile.Name,
    "--main-class", "app.MainApp",
    "--runtime-image", $runtimeDir,
    "--java-options", "-Xmx512m",
    "--app-version", "1.0",
    "--vendor", "2M Market",
    "--description", "Application de gestion de stock pour 2M Market"
)

& $jpackageExe $jpackageArgs 2>&1 | ForEach-Object {
    if ($_ -match "error" -or $_ -match "Error") {
        Write-Host "  $_" -ForegroundColor Red
    } elseif ($_ -match "warning" -or $_ -match "Warning") {
        Write-Host "  $_" -ForegroundColor Yellow
    } else {
        Write-Host "  $_" -ForegroundColor Gray
    }
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] jpackage a échoué!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[OK] App-image créé!" -ForegroundColor Green
Write-Host ""

# Vérifier que l'exe existe
$exePath = "$outputDir\$appName\$appName.exe"
if (-not (Test-Path $exePath)) {
    Write-Host "[ERROR] .exe non trouvé!" -ForegroundColor Red
    exit 1
}

# Vérifier que le runtime est inclus
$runtimeInApp = "$outputDir\$appName\runtime"
if (-not (Test-Path "$runtimeInApp\bin\java.exe")) {
    Write-Host "[WARNING] Runtime non trouvé dans l'app-image, copie manuelle..." -ForegroundColor Yellow
    
    # Copier le runtime
    if (Test-Path $runtimeInApp) {
        Remove-Item -Path $runtimeInApp -Recurse -Force -ErrorAction SilentlyContinue
    }
    
    Write-Host "Copie du runtime..." -ForegroundColor Yellow
    Copy-Item -Path $runtimeDir -Destination $runtimeInApp -Recurse -Force
    Write-Host "[OK] Runtime copié" -ForegroundColor Green
}

# Créer un README
$readmeContent = @"
========================================
  2M Market - Application Portable
========================================

VERSION: 1.0
DATE: $(Get-Date -Format "dd/MM/yyyy")

COMMENT UTILISER:
-----------------
1. Double-cliquez sur: $appName.exe
2. L'application se lance directement (portable)

POUR COPIER SUR UN AUTRE PC:
----------------------------
1. Copiez TOUT le dossier: $appName
2. Sur l'autre PC, double-cliquez sur: $appName.exe
3. Aucune installation nécessaire!

Comptes par défaut:
-------------------
- admin / admin123 (Administrateur)
- employe / admin123 (Employé)

BASE DE DONNÉES:
----------------
- Fichier: MarketDB.db
- Emplacement: Dans le dossier de l'application
- Les données sont sauvegardées automatiquement
- Faites des sauvegardes régulières de ce fichier

CONFIGURATION REQUISE:
----------------------
- Windows 10/11 (64-bit)
- 4 Go RAM minimum
- 500 Mo d'espace disque
- Aucun Java requis (inclus dans l'application)

FONCTIONNALITÉS:
----------------
✓ Gestion des produits (ajout, modification, suppression)
✓ Gestion des catégories
✓ Gestion du stock
✓ Point de vente (POS)
✓ Gestion des ventes
✓ Rapports et statistiques
✓ Gestion des fournisseurs
✓ Gestion des crédits
✓ Gestion des employés
✓ Génération de factures PDF

========================================
  © 2025 2M Market. Tous droits réservés.
========================================
"@

$readmeContent | Out-File -FilePath "$outputDir\$appName\README.txt" -Encoding UTF8 -Force

# Résumé final
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  .EXE PORTABLE CRÉÉ AVEC SUCCÈS!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "📁 Emplacement:" -ForegroundColor Cyan
Write-Host "   $outputDir\$appName" -ForegroundColor White
Write-Host ""
Write-Host "🚀 Pour lancer:" -ForegroundColor Cyan
Write-Host "   Double-cliquez sur: $appName.exe" -ForegroundColor White
Write-Host ""
Write-Host "📦 Pour copier sur un autre PC:" -ForegroundColor Cyan
Write-Host "   1. Copiez TOUT le dossier: $appName" -ForegroundColor White
Write-Host "   2. Sur l'autre PC, lancez: $appName.exe" -ForegroundColor White
Write-Host "   3. Aucune installation nécessaire!" -ForegroundColor White
Write-Host ""
Write-Host "✅ L'application est portable et fonctionne sans installation!" -ForegroundColor Green
Write-Host ""

# Ouvrir le dossier
Start-Process explorer.exe -ArgumentList "$outputDir\$appName"

Write-Host "========================================" -ForegroundColor Green
Write-Host "  Terminé!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""


