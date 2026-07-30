# Script pour importer une base de données SQLite existante
# Utilisez ce script pour copier votre ancienne base de données vers la nouvelle application

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Import Database - 2M Market" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Demander le chemin de l'ancienne base de données
Write-Host "Ce script va copier votre ancienne base de données vers la nouvelle application." -ForegroundColor Yellow
Write-Host ""

# Chercher automatiquement MarketDB.db dans les emplacements courants
$possibleLocations = @(
    "MarketDB.db",  # Dans le dossier actuel
    "..\MarketDB.db",  # Un niveau au-dessus
    "$env:USERPROFILE\Desktop\MarketDB.db",
    "$env:USERPROFILE\Documents\MarketDB.db",
    "C:\Program Files\2M-Market\MarketDB.db",
    "C:\Program Files (x86)\2M-Market\MarketDB.db"
)

Write-Host "Recherche de l'ancienne base de données..." -ForegroundColor Cyan
$oldDbPath = $null

foreach ($location in $possibleLocations) {
    if (Test-Path $location) {
        $oldDbPath = $location
        Write-Host "  [OK] Trouvé: $location" -ForegroundColor Green
        break
    }
}

if (-not $oldDbPath) {
    Write-Host "  [WARNING] Base de données non trouvée automatiquement" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Veuillez entrer le chemin complet de votre ancienne base de données:" -ForegroundColor Cyan
    Write-Host "  (Exemple: C:\Users\VotreNom\Desktop\MarketDB.db)" -ForegroundColor Gray
    Write-Host ""
    $oldDbPath = Read-Host "Chemin de l'ancienne base de données"
    
    if (-not (Test-Path $oldDbPath)) {
        Write-Host ""
        Write-Host "[ERROR] Le fichier n'existe pas: $oldDbPath" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "Ancienne base de données: $oldDbPath" -ForegroundColor Green
Write-Host ""

# Déterminer où copier la base de données
Write-Host "Où voulez-vous copier la base de données?" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Dans le dossier de l'application déployée (target\installer\2M-Market-App\)" -ForegroundColor White
Write-Host "2. Dans le dossier du projet (pour développement)" -ForegroundColor White
Write-Host "3. Autre emplacement (vous spécifierez)" -ForegroundColor White
Write-Host ""

$choice = Read-Host "Votre choix (1, 2 ou 3)"

$newDbPath = $null

switch ($choice) {
    "1" {
        $appDir = "target\installer\2M-Market-App"
        if (-not (Test-Path $appDir)) {
            Write-Host ""
            Write-Host "[ERROR] Le dossier de l'application n'existe pas: $appDir" -ForegroundColor Red
            Write-Host "  Assurez-vous d'avoir exécuté .\build-with-maven.ps1 d'abord" -ForegroundColor Yellow
            exit 1
        }
        $newDbPath = "$appDir\MarketDB.db"
    }
    "2" {
        $newDbPath = "MarketDB.db"
    }
    "3" {
        $customPath = Read-Host "Entrez le chemin complet où copier MarketDB.db"
        $newDbPath = $customPath
        if (-not $newDbPath.EndsWith("MarketDB.db")) {
            $newDbPath = Join-Path $newDbPath "MarketDB.db"
        }
    }
    default {
        Write-Host "[ERROR] Choix invalide" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "Nouvelle base de données: $newDbPath" -ForegroundColor Green
Write-Host ""

# Vérifier si une base de données existe déjà
if (Test-Path $newDbPath) {
    Write-Host "[WARNING] Une base de données existe déjà à cet emplacement!" -ForegroundColor Yellow
    Write-Host "  Fichier: $newDbPath" -ForegroundColor Gray
    Write-Host ""
    
    $backupChoice = Read-Host "Voulez-vous créer une sauvegarde? (Y/N)"
    if ($backupChoice -eq "Y" -or $backupChoice -eq "y") {
        $backupPath = "$newDbPath.backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        Copy-Item -Path $newDbPath -Destination $backupPath -Force
        Write-Host "  [OK] Sauvegarde créée: $backupPath" -ForegroundColor Green
    }
    
    $overwrite = Read-Host "Remplacer la base de données existante? (Y/N)"
    if ($overwrite -ne "Y" -and $overwrite -ne "y") {
        Write-Host "Opération annulée." -ForegroundColor Yellow
        exit 0
    }
}

# Copier la base de données
Write-Host ""
Write-Host "Copie de la base de données..." -ForegroundColor Cyan

try {
    # S'assurer que le dossier de destination existe
    $destDir = Split-Path $newDbPath -Parent
    if ($destDir -and -not (Test-Path $destDir)) {
        New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    }
    
    # Copier le fichier
    Copy-Item -Path $oldDbPath -Destination $newDbPath -Force
    
    if (Test-Path $newDbPath) {
        $fileSize = (Get-Item $newDbPath).Length / 1MB
        Write-Host "  [OK] Base de données copiée avec succès!" -ForegroundColor Green
        Write-Host "  Taille: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Gray
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "  Import Réussi!" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "Votre base de données a été copiée vers:" -ForegroundColor Cyan
        Write-Host "  $newDbPath" -ForegroundColor White
        Write-Host ""
        Write-Host "Vous pouvez maintenant lancer l'application avec vos utilisateurs modifiés!" -ForegroundColor Green
    } else {
        Write-Host "  [ERROR] La copie a échoué!" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "  [ERROR] Erreur lors de la copie: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

