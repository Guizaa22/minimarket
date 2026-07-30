# Script PowerShell pour configurer le démarrage automatique de l'application
# Doit être exécuté en tant qu'administrateur

param(
    [string]$ExePath = "",
    [switch]$Remove
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  2M Market - Startup Configuration" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Vérifier les privilèges administrateur
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "ERROR: This script must be run as Administrator!" -ForegroundColor Red
    Write-Host "Right-click PowerShell and select 'Run as Administrator'" -ForegroundColor Yellow
    exit 1
}

if ($Remove) {
    # Supprimer le démarrage automatique
    Write-Host "Removing startup configuration..." -ForegroundColor Yellow
    
    # Supprimer de la clé de registre
    $regPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"
    $regName = "2M-Market"
    
    if (Test-Path $regPath) {
        Remove-ItemProperty -Path $regPath -Name $regName -ErrorAction SilentlyContinue
        Write-Host "Removed from registry: $regPath\$regName" -ForegroundColor Green
    }
    
    # Supprimer le raccourci du dossier de démarrage
    $startupFolder = [System.Environment]::GetFolderPath("Startup")
    $shortcutPath = Join-Path $startupFolder "2M-Market.lnk"
    if (Test-Path $shortcutPath) {
        Remove-Item $shortcutPath -Force
        Write-Host "Removed shortcut: $shortcutPath" -ForegroundColor Green
    }
    
    Write-Host ""
    Write-Host "Startup configuration removed successfully!" -ForegroundColor Green
    exit 0
}

# Configuration du démarrage automatique
if ([string]::IsNullOrEmpty($ExePath)) {
    # Chercher l'exe dans le répertoire courant
    $possiblePaths = @(
        "dist\2M-Market.exe",
        "target\2M-Market.exe",
        "2M-Market.exe",
        ".\2M-Market.exe"
    )
    
    foreach ($path in $possiblePaths) {
        if (Test-Path $path) {
            $ExePath = Resolve-Path $path
            break
        }
    }
    
    if ([string]::IsNullOrEmpty($ExePath)) {
        Write-Host "ERROR: EXE file not found!" -ForegroundColor Red
        Write-Host ""
        Write-Host "Please specify the path to the EXE file:" -ForegroundColor Yellow
        Write-Host "  .\setup-startup.ps1 -ExePath 'C:\Path\To\2M-Market.exe'" -ForegroundColor White
        Write-Host ""
        Write-Host "Or build the EXE first using:" -ForegroundColor Yellow
        Write-Host "  .\build-exe.ps1" -ForegroundColor White
        exit 1
    }
} else {
    if (-not (Test-Path $ExePath)) {
        Write-Host "ERROR: EXE file not found at: $ExePath" -ForegroundColor Red
        exit 1
    }
    $ExePath = Resolve-Path $ExePath
}

Write-Host "EXE Path: $ExePath" -ForegroundColor Green
Write-Host ""

# Méthode 1: Ajouter au registre Windows (démarrage automatique)
Write-Host "Method 1: Adding to Windows Registry..." -ForegroundColor Yellow
$regPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"
$regName = "2M-Market"
$regValue = "`"$ExePath`""

try {
    if (-not (Test-Path $regPath)) {
        New-Item -Path $regPath -Force | Out-Null
    }
    Set-ItemProperty -Path $regPath -Name $regName -Value $regValue -Type String
    Write-Host "✓ Added to registry: $regPath\$regName" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to add to registry: $_" -ForegroundColor Red
}

# Méthode 2: Créer un raccourci dans le dossier de démarrage
Write-Host ""
Write-Host "Method 2: Creating startup shortcut..." -ForegroundColor Yellow
$startupFolder = [System.Environment]::GetFolderPath("Startup")
$shortcutPath = Join-Path $startupFolder "2M-Market.lnk"

try {
    $WshShell = New-Object -ComObject WScript.Shell
    $Shortcut = $WshShell.CreateShortcut($shortcutPath)
    $Shortcut.TargetPath = $ExePath
    $Shortcut.WorkingDirectory = Split-Path $ExePath
    $Shortcut.Description = "2M Market - Gestion de Stock"
    $Shortcut.Save()
    Write-Host "✓ Created shortcut: $shortcutPath" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to create shortcut: $_" -ForegroundColor Red
}

# Méthode 3: Créer une tâche planifiée (optionnel - démarrage plus fiable)
Write-Host ""
Write-Host "Method 3: Creating scheduled task (optional)..." -ForegroundColor Yellow
$taskName = "2M-Market-Startup"
$taskDescription = "Start 2M Market application on Windows startup"

try {
    # Supprimer la tâche existante si elle existe
    $existingTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($existingTask) {
        Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
    }
    
    # Créer une nouvelle action
    $action = New-ScheduledTaskAction -Execute $ExePath -WorkingDirectory (Split-Path $ExePath)
    
    # Créer un déclencheur au démarrage
    $trigger = New-ScheduledTaskTrigger -AtLogOn
    
    # Créer les paramètres
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
    
    # Enregistrer la tâche
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Description $taskDescription -User $env:USERNAME | Out-Null
    
    Write-Host "✓ Created scheduled task: $taskName" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to create scheduled task: $_" -ForegroundColor Yellow
    Write-Host "  (This is optional, registry method should work)" -ForegroundColor Gray
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Configuration Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "The application will now start automatically when Windows boots." -ForegroundColor Cyan
Write-Host ""
Write-Host "To remove startup configuration, run:" -ForegroundColor Yellow
Write-Host "  .\setup-startup.ps1 -Remove" -ForegroundColor White
Write-Host ""

