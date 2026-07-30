# Simple syntax checker
$script = Get-Content "build-exe.ps1" -Raw
$errors = $null
$tokens = $null

try {
    [System.Management.Automation.PSParser]::Tokenize($script, [ref]$tokens, [ref]$errors)
    
    if ($errors.Count -gt 0) {
        Write-Host "ERRORS:" -ForegroundColor Red
        foreach ($err in $errors) {
            Write-Host "Line $($err.Token.StartLine): $($err.Message)" -ForegroundColor Yellow
        }
    } else {
        Write-Host "OK - No syntax errors" -ForegroundColor Green
    }
} catch {
    Write-Host "EXCEPTION: $($_.Exception.Message)" -ForegroundColor Red
}

