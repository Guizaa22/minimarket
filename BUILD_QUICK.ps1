# Build rapide sans clean
Write-Host "Build rapide..." -ForegroundColor Yellow

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
$jpackageExe = "$env:JAVA_HOME\bin\jpackage.exe"
$jlinkExe = "$env:JAVA_HOME\bin\jlink.exe"

# Build sans clean
Write-Host "Compilation..." -ForegroundColor Cyan
mvn compile dependency:copy-dependencies -DoutputDirectory=target/lib -DskipTests -q 2>&1 | Out-Null
mvn package -DskipTests -q 2>&1 | Out-Null

$jarFile = Get-ChildItem -Path "target" -Filter "*-SNAPSHOT.jar" | Where-Object { $_.Name -notmatch "original" } | Select-Object -First 1

if (-not $jarFile) {
    Write-Host "ERREUR: JAR non trouvé!" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] JAR: $($jarFile.Name)" -ForegroundColor Green

# Runtime rapide (réutiliser si existe)
$runtimeDir = "target\runtime"
if (-not (Test-Path "$runtimeDir\bin\java.exe")) {
    Write-Host "Création runtime..." -ForegroundColor Cyan
    $jmodsPath = "$env:JAVA_HOME\jmods"
    & $jlinkExe --module-path "$jmodsPath;target\lib" --add-modules "java.base,java.desktop,java.logging,java.sql,jdk.unsupported,javafx.controls,javafx.fxml,javafx.graphics,javafx.base" --output $runtimeDir --strip-debug --compress 2 --no-header-files --no-man-pages 2>&1 | Out-Null
}

# App-image
Write-Host "Création app-image..." -ForegroundColor Cyan
$outputDir = "target\installer"
$appName = "2M-Market-App"
$appPath = "$outputDir\$appName"

if (Test-Path $appPath) {
    Remove-Item -Path $appPath -Recurse -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

& $jpackageExe --type app-image --input target --dest $outputDir --name $appName --main-jar $jarFile.Name --main-class "app.MainApp" --runtime-image $runtimeDir --java-options "-Xmx512m" --app-version "1.0" --vendor "2M Market" 2>&1 | Out-Null

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  TERMINE!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "Emplacement: $outputDir\$appName" -ForegroundColor Cyan
Write-Host "Lancez: $appName.exe" -ForegroundColor Cyan
Write-Host ""

