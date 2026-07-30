# Script pour déboguer l'application et capturer les erreurs
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Debugging 2M-Market Application" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$exePath = "dist\2M-Market\2M-Market.exe"
$exeDir = "dist\2M-Market"
$logFile = "app-debug.log"

if (-not (Test-Path $exePath)) {
    Write-Host "ERROR: $exePath not found!" -ForegroundColor Red
    exit 1
}

Write-Host "Executable: $exePath" -ForegroundColor Green
Write-Host "Working directory: $exeDir" -ForegroundColor Green
Write-Host "Log file: $logFile" -ForegroundColor Green
Write-Host ""

# Vérifier la configuration
$cfgFile = "$exeDir\app\2M-Market.cfg"
Write-Host "Configuration file:" -ForegroundColor Yellow
if (Test-Path $cfgFile) {
    Get-Content $cfgFile | Write-Host
} else {
    Write-Host "  [MISSING]" -ForegroundColor Red
}

Write-Host ""
Write-Host "Attempting to run with error capture..." -ForegroundColor Yellow
Write-Host ""

# Créer un script batch temporaire pour capturer les erreurs
$tempBat = "$env:TEMP\run-2m-market-debug.bat"
@"
@echo off
cd /d "$(Resolve-Path $exeDir)"
"$exePath" > "$(Resolve-Path .)\stdout.log" 2> "$(Resolve-Path .)\stderr.log"
echo Exit code: %ERRORLEVEL% >> "$(Resolve-Path .)\stderr.log"
"@ | Out-File -FilePath $tempBat -Encoding ASCII

# Exécuter et capturer la sortie
$process = Start-Process -FilePath $tempBat -WorkingDirectory (Get-Location) -Wait -NoNewWindow -PassThru

# Lire les logs
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Application Output" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if (Test-Path "stdout.log") {
    Write-Host "STDOUT:" -ForegroundColor Yellow
    Get-Content "stdout.log" | Write-Host
    Write-Host ""
}

if (Test-Path "stderr.log") {
    Write-Host "STDERR:" -ForegroundColor Red
    Get-Content "stderr.log" | Write-Host
    Write-Host ""
}

# Essayer aussi de lancer directement et capturer
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Direct Execution Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Utiliser Start-Process avec redirection
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $exePath
$psi.WorkingDirectory = (Resolve-Path $exeDir).Path
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

$process2 = New-Object System.Diagnostics.Process
$process2.StartInfo = $psi

try {
    $process2.Start() | Out-Null
    $output = $process2.StandardOutput.ReadToEnd()
    $error = $process2.StandardError.ReadToEnd()
    $process2.WaitForExit(5000)  # Attendre 5 secondes max
    
    if (-not $process2.HasExited) {
        $process2.Kill()
        Write-Host "Application is running (killed after 5 seconds for testing)" -ForegroundColor Green
    } else {
        Write-Host "Exit code: $($process2.ExitCode)" -ForegroundColor $(if ($process2.ExitCode -eq 0) { "Green" } else { "Red" })
        
        if ($output) {
            Write-Host ""
            Write-Host "Output:" -ForegroundColor Yellow
            Write-Host $output
        }
        
        if ($error) {
            Write-Host ""
            Write-Host "Error output:" -ForegroundColor Red
            Write-Host $error
        }
    }
} catch {
    Write-Host "Error launching application: $($_.Exception.Message)" -ForegroundColor Red
}

# Nettoyer
Remove-Item $tempBat -ErrorAction SilentlyContinue

