# Script simple pour créer un .exe qui fonctionne vraiment

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Création .exe Portable 2M Market" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
$jpackageExe = "$env:JAVA_HOME\bin\jpackage.exe"
$jlinkExe = "$env:JAVA_HOME\bin\jlink.exe"

# Build
Write-Host "Build Maven..." -ForegroundColor Yellow
mvn package dependency:copy-dependencies -DoutputDirectory=target/lib -DskipTests -q

$jarFile = Get-ChildItem -Path "target" -Filter "*-SNAPSHOT.jar" | Where-Object { $_.Name -notmatch "original" } | Select-Object -First 1

# Créer runtime
Write-Host "Création du runtime..." -ForegroundColor Yellow
$runtimeDir = "target\runtime"
if (Test-Path $runtimeDir) {
    Remove-Item -Path $runtimeDir -Recurse -Force -ErrorAction SilentlyContinue
}

$jmodsPath = "$env:JAVA_HOME\jmods"
& $jlinkExe --module-path "$jmodsPath;target\lib" --add-modules "java.base,java.desktop,java.logging,java.sql,jdk.unsupported,javafx.controls,javafx.fxml,javafx.graphics,javafx.base" --output $runtimeDir --strip-debug --compress 2 --no-header-files --no-man-pages 2>&1 | Out-Null

# Créer app-image
Write-Host "Création de l'app-image..." -ForegroundColor Yellow
$outputDir = "target\installer"
$appName = "2M-Market-App"
$appPath = "$outputDir\$appName"

# Forcer la suppression du dossier existant
if (Test-Path $appPath) {
    Write-Host "Nettoyage de l'ancien app-image..." -ForegroundColor Yellow
    
    # Arrêter les processus qui pourraient utiliser les fichiers
    Get-Process | Where-Object { $_.Path -like "*$appName*" } | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
    
    # Supprimer avec retry
    $maxRetries = 5
    $retryCount = 0
    while ((Test-Path $appPath) -and ($retryCount -lt $maxRetries)) {
        try {
            Remove-Item -Path $appPath -Recurse -Force -ErrorAction Stop
            break
        } catch {
            $retryCount++
            Write-Host "  Tentative $retryCount/$maxRetries..." -ForegroundColor Gray
            Start-Sleep -Seconds 2
        }
    }
    
    # Si toujours présent, utiliser robocopy pour nettoyer
    if (Test-Path $appPath) {
        $emptyDir = "$env:TEMP\empty_$(Get-Random)"
        New-Item -ItemType Directory -Path $emptyDir -Force | Out-Null
        robocopy $emptyDir $appPath /MIR /NFL /NDL /NJH /NJS | Out-Null
        Remove-Item -Path $emptyDir -Recurse -Force -ErrorAction SilentlyContinue
        Remove-Item -Path $appPath -Recurse -Force -ErrorAction SilentlyContinue
    }
    
    Start-Sleep -Seconds 2
}

& $jpackageExe --type app-image --input target --dest $outputDir --name $appName --main-jar $jarFile.Name --main-class "app.MainApp" --runtime-image $runtimeDir --java-options "-Xmx512m" --app-version "1.0" --vendor "2M Market" 2>&1 | Out-Null

# Vérifier et corriger le runtime
$runtimeInApp = "$outputDir\$appName\runtime"
if (-not (Test-Path "$runtimeInApp\bin\java.exe")) {
    Write-Host "Correction du runtime..." -ForegroundColor Yellow
    if (Test-Path $runtimeInApp) {
        Remove-Item -Path $runtimeInApp -Recurse -Force -ErrorAction SilentlyContinue
    }
    Copy-Item -Path $runtimeDir -Destination $runtimeInApp -Recurse -Force
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  .EXE CRÉÉ AVEC SUCCÈS!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "📁 Emplacement: $outputDir\$appName" -ForegroundColor Cyan
Write-Host "🚀 Lancez: $appName.exe" -ForegroundColor Cyan
Write-Host "📦 Copiez le dossier complet sur un autre PC" -ForegroundColor Cyan
Write-Host ""

Start-Process explorer.exe -ArgumentList "$outputDir\$appName"

