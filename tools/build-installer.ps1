<#
.SYNOPSIS
    Construit l'application 2M Market en version autonome (JRE inclus).

.DESCRIPTION
    Appelle jpackage sur le jar produit par Maven. Les postes de caisse n'ont
    alors plus besoin d'avoir Java installe.

    Par defaut, produit un dossier executable (type app-image) : aucun prerequis.
    Avec -Type exe, produit un vrai programme d'installation avec raccourcis,
    ce qui necessite le WiX Toolset (https://wixtoolset.org).

.EXAMPLE
    .\build-installer.ps1
    .\build-installer.ps1 -Type exe
#>

[CmdletBinding()]
param(
    [ValidateSet("app-image", "exe", "msi")]
    [string] $Type = "app-image",

    [string] $AppVersion = "1.0",
    [switch] $SkipMaven
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

# --- Verifier jpackage -----------------------------------------------------
$jpackage = Get-Command jpackage -ErrorAction SilentlyContinue
if (-not $jpackage) {
    throw "jpackage introuvable. Il est fourni avec le JDK 17+ : verifiez que le dossier bin du JDK est dans le PATH."
}
Write-Host "jpackage : $($jpackage.Source)" -ForegroundColor DarkGray

if ($Type -ne "app-image") {
    Write-Host "Type '$Type' : le WiX Toolset doit etre installe, sinon jpackage echouera." -ForegroundColor Yellow
}

# --- Construire le jar -----------------------------------------------------
if (-not $SkipMaven) {
    Write-Host ""
    Write-Host "Construction du jar (mvn clean package)..." -ForegroundColor Yellow
    & mvn clean package -DskipTests -q
    if ($LASTEXITCODE -ne 0) { throw "Echec de la construction Maven (code $LASTEXITCODE)." }
}

$jar = Get-ChildItem "target\*.jar" |
       Where-Object { $_.Name -notlike "original-*" } |
       Select-Object -First 1
if (-not $jar) { throw "Aucun jar trouve dans target\." }

$sizeMb = [math]::Round($jar.Length / 1MB, 1)
Write-Host "  jar : $($jar.Name) ($sizeMb MB)" -ForegroundColor DarkGray

# Le jar doit embarquer ses dependances : en dessous de ~5 Mo, le plugin shade
# n'a pas fonctionne et l'application planterait au demarrage.
if ($jar.Length -lt 5MB) {
    throw ("Le jar ne fait que $sizeMb MB : il ne contient pas ses dependances.`n" +
           "  Le plugin maven-shade-plugin n'a pas produit le jar complet.`n" +
           "  Relancez 'mvn clean package' et verifiez qu'il n'y a pas d'erreur.")
}

# --- Preparer un dossier d'entree propre -----------------------------------
# jpackage embarque TOUT le contenu de --input : on n'y met que le jar final.
$staging = "target\jpackage-input"
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Force -Path $staging | Out-Null
Copy-Item $jar.FullName $staging

$dest = "target\installer"
if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }

# --- Lancer jpackage -------------------------------------------------------
Write-Host ""
Write-Host "Generation de l'application ($Type)..." -ForegroundColor Yellow

$jpackageArgs = @(
    "--type", $Type,
    "--name", "2M-Market",
    "--app-version", $AppVersion,
    "--vendor", "2M Market",
    "--description", "Gestion de stock et ventes",
    "--input", $staging,
    "--main-jar", $jar.Name,
    "--main-class", "app.MainApp",
    "--dest", $dest
)

if ($Type -ne "app-image") {
    $jpackageArgs += @("--win-shortcut", "--win-menu", "--win-dir-chooser")
}

& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "Echec de jpackage (code $LASTEXITCODE)." }

# --- Resultat --------------------------------------------------------------
Write-Host ""
Write-Host "Termine." -ForegroundColor Green

if ($Type -eq "app-image") {
    $appDir = Join-Path $dest "2M-Market"
    $total  = (Get-ChildItem $appDir -Recurse -File | Measure-Object Length -Sum).Sum
    Write-Host "  Dossier : $appDir ($([math]::Round($total / 1MB)) MB)"
    Write-Host "  Lancer  : $appDir\2M-Market.exe"
    Write-Host ""
    Write-Host "Copiez ce dossier sur chaque poste de caisse : aucun Java requis." -ForegroundColor Cyan
} else {
    Get-ChildItem $dest -File | ForEach-Object {
        Write-Host "  $($_.Name) ($([math]::Round($_.Length / 1MB)) MB)"
    }
}
