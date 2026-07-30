<#
.SYNOPSIS
    Prepare la base PostgreSQL locale pour 2M Market.

.DESCRIPTION
    Cree le role applicatif "market_app", la base "market2m", puis ecrit le
    fichier de configuration %APPDATA%\2M-Market\database.properties.

    Le mot de passe superutilisateur n'est jamais stocke : il est demande de
    maniere securisee et transmis a psql via la variable PGPASSWORD, le temps
    de la commande uniquement.

.EXAMPLE
    .\setup-database.ps1
    .\setup-database.ps1 -Port 5433 -AppPassword (Read-Host -AsSecureString)
#>

[CmdletBinding()]
param(
    [string]       $PgHost      = "localhost",
    [int]          $Port        = 5432,
    [string]       $SuperUser   = "postgres",
    [string]       $Database    = "market2m",
    [string]       $AppUser     = "market_app",
    [SecureString] $AppPassword
)

$ErrorActionPreference = "Stop"

function Find-Psql {
    $cmd = Get-Command psql.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $candidates = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
                  Sort-Object FullName -Descending
    if ($candidates) { return $candidates[0].FullName }

    throw "psql.exe introuvable. Installez PostgreSQL ou ajoutez son dossier bin au PATH."
}

function ConvertFrom-SecureStringPlain([SecureString] $secure) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try   { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

$psql = Find-Psql
Write-Host "psql : $psql" -ForegroundColor DarkGray

# ---------------------------------------------------------------------------
# Mots de passe
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "1/2 - Mot de passe ACTUEL du superutilisateur '$SuperUser'" -ForegroundColor Cyan
Write-Host "      (celui qui existe deja - sert uniquement a se connecter," -ForegroundColor DarkGray
Write-Host "       ce script ne le modifie pas)" -ForegroundColor DarkGray
$superSecure = Read-Host -AsSecureString

if (-not $AppPassword) {
    Write-Host ""
    Write-Host "2/2 - NOUVEAU mot de passe pour le compte applicatif '$AppUser'" -ForegroundColor Cyan
    Write-Host "      (celui que l'application utilisera - vous le choisissez ici)" -ForegroundColor DarkGray
    $AppPassword = Read-Host -AsSecureString
}

$superPlain = ConvertFrom-SecureStringPlain $superSecure
$appPlain   = ConvertFrom-SecureStringPlain $AppPassword

if ([string]::IsNullOrWhiteSpace($appPlain)) {
    throw "Le mot de passe applicatif ne peut pas etre vide."
}

# ---------------------------------------------------------------------------
# Creation du role et de la base
# ---------------------------------------------------------------------------
$escaped = $appPlain.Replace("'", "''")

$sql = @"
DO `$`$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$AppUser') THEN
        CREATE ROLE $AppUser LOGIN PASSWORD '$escaped';
        RAISE NOTICE 'Role $AppUser cree';
    ELSE
        ALTER ROLE $AppUser LOGIN PASSWORD '$escaped';
        RAISE NOTICE 'Mot de passe du role $AppUser mis a jour';
    END IF;
END
`$`$;
"@

$env:PGPASSWORD = $superPlain
try {
    Write-Host ""
    Write-Host "Creation du role applicatif..." -ForegroundColor Yellow
    $sql | & $psql -h $PgHost -p $Port -U $SuperUser -d postgres -v ON_ERROR_STOP=1 -q -f -
    if ($LASTEXITCODE -ne 0) {
        throw ("Echec de la connexion en tant que '$SuperUser' (code $LASTEXITCODE).`n" +
               "  Le mot de passe demande en 1/2 est le mot de passe ACTUEL de '$SuperUser',`n" +
               "  pas un nouveau. Aucune modification n'a ete effectuee.")
    }

    Write-Host "Creation de la base $Database..." -ForegroundColor Yellow
    $exists = & $psql -h $PgHost -p $Port -U $SuperUser -d postgres -tAc `
              "SELECT 1 FROM pg_database WHERE datname = '$Database'"
    if ($exists -eq "1") {
        Write-Host "  La base existe deja, conservee." -ForegroundColor DarkGray
    } else {
        & $psql -h $PgHost -p $Port -U $SuperUser -d postgres -v ON_ERROR_STOP=1 -q `
                -c "CREATE DATABASE $Database OWNER $AppUser ENCODING 'UTF8'"
        if ($LASTEXITCODE -ne 0) { throw "Echec de la creation de la base (code $LASTEXITCODE)." }
    }

    & $psql -h $PgHost -p $Port -U $SuperUser -d $Database -v ON_ERROR_STOP=1 -q `
            -c "GRANT ALL ON SCHEMA public TO $AppUser"

    # Les tests automatises utilisent un schema dedie ("test_2m") cree a la
    # volee par l'application : aucune base supplementaire n'est necessaire.
}
finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    $superPlain = $null
}

# ---------------------------------------------------------------------------
# Fichier de configuration
# ---------------------------------------------------------------------------
$configDir  = Join-Path $env:APPDATA "2M-Market"
$configFile = Join-Path $configDir "database.properties"
New-Item -ItemType Directory -Force -Path $configDir | Out-Null

@"
# Configuration de la base 2M Market
# Genere par setup-database.ps1 le $(Get-Date -Format 'yyyy-MM-dd HH:mm')
# NE PAS COMMITER CE FICHIER : il contient un mot de passe.
PGHOST=$PgHost
PGPORT=$Port
PGDATABASE=$Database
PGUSER=$AppUser
PGPASSWORD=$appPlain
DB_POOL_SIZE=5
"@ | Set-Content -Path $configFile -Encoding UTF8

# Restreindre l'acces au seul utilisateur courant
$acl = Get-Acl $configFile
$acl.SetAccessRuleProtection($true, $false)
$rule = New-Object System.Security.AccessControl.FileSystemAccessRule(
    "$env:USERDOMAIN\$env:USERNAME", "FullControl", "Allow")
$acl.SetAccessRule($rule)
Set-Acl -Path $configFile -AclObject $acl

$appPlain = $null

Write-Host ""
Write-Host "Termine." -ForegroundColor Green
Write-Host "  Base          : $Database sur ${PgHost}:$Port"
Write-Host "  Utilisateur   : $AppUser"
Write-Host "  Config        : $configFile (acces restreint)"
Write-Host ""
Write-Host "Etapes suivantes :" -ForegroundColor Cyan
Write-Host "  1. Importer vos donnees AVANT le premier lancement :" -ForegroundColor White
Write-Host "     python tools\export_sqlite_to_sql.py --sqlite MarketDB.db --out tools\data_postgres.sql"
Write-Host "  2. Lancer l'application (le schema est cree automatiquement) :" -ForegroundColor White
Write-Host "     mvn javafx:run"
Write-Host "  3. Lancer les tests (schema isole, sans risque pour vos donnees) :" -ForegroundColor White
Write-Host "     mvn test"
