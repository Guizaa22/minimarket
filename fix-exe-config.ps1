# Script pour corriger la configuration de l'application
Write-Host "Fixing 2M-Market.cfg configuration..." -ForegroundColor Yellow

$cfgPath = "dist\2M-Market\app\2M-Market.cfg"

if (-not (Test-Path $cfgPath)) {
    Write-Host "ERROR: Configuration file not found at $cfgPath" -ForegroundColor Red
    exit 1
}

# Lire le fichier
$content = Get-Content $cfgPath

# Supprimer la ligne dupliquée app.classpath avec original
$fixedContent = @()
$foundFirstClasspath = $false

foreach ($line in $content) {
    if ($line -match "^app\.classpath=") {
        if (-not $foundFirstClasspath) {
            # Garder la première ligne (le JAR shaded)
            if ($line -notmatch "original") {
                $fixedContent += $line
                $foundFirstClasspath = $true
            }
        }
        # Ignorer les autres lignes app.classpath
    } else {
        $fixedContent += $line
    }
}

# Écrire le fichier corrigé
$fixedContent | Out-File -FilePath $cfgPath -Encoding ASCII

Write-Host "[OK] Configuration file fixed!" -ForegroundColor Green
Write-Host ""
Write-Host "New configuration:" -ForegroundColor Cyan
Get-Content $cfgPath | Write-Host

