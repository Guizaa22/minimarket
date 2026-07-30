# Script pour lancer l'application immédiatement

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Lancement de l'application" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Fixer JAVA_HOME
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
$javaExe = "$env:JAVA_HOME\bin\java.exe"

# Trouver le JAR
$jarFile = Get-ChildItem -Path "target" -Filter "*-SNAPSHOT.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch "original" } | Select-Object -First 1

if (-not $jarFile) {
    Write-Host "[ERROR] JAR non trouvé!" -ForegroundColor Red
    exit 1
}

Write-Host "JAR: $($jarFile.Name)" -ForegroundColor Green
Write-Host "JAVA: $javaExe" -ForegroundColor Green
Write-Host ""

# Trouver les modules JavaFX
$javafxPath = "target\lib"
if (Test-Path $javafxPath) {
    Write-Host "Modules JavaFX: $javafxPath" -ForegroundColor Green
} else {
    Write-Host "[WARNING] Modules JavaFX non trouvés" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Instructions" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Connectez-vous avec: admin / admin123" -ForegroundColor White
Write-Host "2. Allez dans 'Gestion Stock'" -ForegroundColor White
Write-Host "3. Testez l'ajout d'un produit:" -ForegroundColor White
Write-Host "   - Code-barres: TEST123" -ForegroundColor Gray
Write-Host "   - Nom: Produit Test" -ForegroundColor Gray
Write-Host "   - Catégorie: Divers" -ForegroundColor Gray
Write-Host "   - Prix achat: 10" -ForegroundColor Gray
Write-Host "   - Prix vente: 15" -ForegroundColor Gray
Write-Host "   - Quantité: 100" -ForegroundColor Gray
Write-Host "   - Unité: unité" -ForegroundColor Gray
Write-Host "   - Seuil: 10" -ForegroundColor Gray
Write-Host ""
Write-Host "Si l'ajout fonctionne: LE BUG EST CORRIGÉ! ✅" -ForegroundColor Green
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Lancer l'application avec JavaFX
$modulePath = "$javafxPath"
$modules = "javafx.controls,javafx.fxml"

& $javaExe --module-path $modulePath --add-modules $modules -jar $jarFile.FullName

Write-Host ""
Write-Host "Application fermée" -ForegroundColor Cyan

