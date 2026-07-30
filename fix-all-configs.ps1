# Script pour corriger tous les fichiers de configuration dans dist
Write-Host "Fixing all 2M-Market.cfg files..." -ForegroundColor Yellow
Write-Host ""

$configFiles = @(
    "dist\2M-Market\app\2M-Market.cfg",
    "dist\Deployment-Package\2M-Market\app\2M-Market.cfg"
)

$fixedCount = 0

foreach ($cfgFile in $configFiles) {
    if (Test-Path $cfgFile) {
        Write-Host "Processing: $cfgFile" -ForegroundColor Cyan
        
        $content = Get-Content $cfgFile
        $fixedContent = @()
        $foundFirstClasspath = $false
        
        foreach ($line in $content) {
            if ($line -match "^app\.classpath=") {
                if (-not $foundFirstClasspath) {
                    # Garder uniquement la première ligne (le JAR shaded, pas original)
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
        
        $fixedContent | Out-File -FilePath $cfgFile -Encoding ASCII
        Write-Host "  [OK] Fixed!" -ForegroundColor Green
        $fixedCount++
    } else {
        Write-Host "  [SKIP] File not found" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Fixed $fixedCount configuration file(s)!" -ForegroundColor Green
Write-Host ""
Write-Host "You can now test the application by running:" -ForegroundColor Cyan
Write-Host "  .\test-app.ps1" -ForegroundColor White
Write-Host ""
Write-Host "Or directly:" -ForegroundColor Cyan
Write-Host "  .\dist\2M-Market\2M-Market.exe" -ForegroundColor White

