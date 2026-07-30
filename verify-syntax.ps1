# Verification finale de la syntaxe
Write-Host "Verifying build-exe.ps1 syntax..." -ForegroundColor Yellow

$scriptPath = "build-exe.ps1"
$content = Get-Content $scriptPath -Raw

# Test avec le parser PowerShell
try {
    $ast = [System.Management.Automation.Language.Parser]::ParseInput($content, [ref]$null, [ref]$null)
    
    if ($ast.Errors.Count -gt 0) {
        Write-Host ""
        Write-Host "ERRORS FOUND:" -ForegroundColor Red
        foreach ($err in $ast.Errors) {
            Write-Host "  Line $($err.Extent.StartLineNumber): $($err.Message)" -ForegroundColor Yellow
            Write-Host "    Text: $($err.Extent.Text)" -ForegroundColor Gray
        }
        exit 1
    } else {
        Write-Host "SUCCESS: No syntax errors!" -ForegroundColor Green
        Write-Host ""
        Write-Host "The script is ready to use." -ForegroundColor Cyan
        Write-Host "Run: .\build-exe.ps1" -ForegroundColor White
        exit 0
    }
} catch {
    Write-Host ""
    Write-Host "EXCEPTION:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Yellow
    exit 1
}

