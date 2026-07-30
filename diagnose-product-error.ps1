# Script pour diagnostiquer l'erreur d'ajout de produit
# Affiche l'erreur SQL exacte

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Diagnostic - Erreur Ajout Produit" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Vérifier JAVA_HOME
if ([string]::IsNullOrEmpty($env:JAVA_HOME)) {
    Write-Host "[ERROR] JAVA_HOME n'est pas défini!" -ForegroundColor Red
    exit 1
}

$javaExe = "$env:JAVA_HOME\bin\java.exe"
if (-not (Test-Path $javaExe)) {
    Write-Host "[ERROR] java.exe non trouvé: $javaExe" -ForegroundColor Red
    exit 1
}

Write-Host "JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Green
Write-Host ""

# Trouver la base de données
$dbPath = $null
$appDir = $null

$possibleAppDirs = @(
    "target\installer\2M-Market-App",
    "dist\2M-Market",
    "."
)

foreach ($dir in $possibleAppDirs) {
    $exePath = Join-Path $dir "2M-Market-App.exe"
    if (-not (Test-Path $exePath)) {
        $exePath = Join-Path $dir "2M-Market.exe"
    }
    
    if (Test-Path $exePath) {
        $appDir = Split-Path $exePath -Parent
        $dbPath = Join-Path $appDir "MarketDB.db"
        break
    }
}

if (-not $dbPath) {
    $dbPath = "MarketDB.db"
    $appDir = (Get-Location).Path
}

Write-Host "Base de données: $dbPath" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $dbPath)) {
    Write-Host "[ERROR] Base de données non trouvée!" -ForegroundColor Red
    Write-Host "  Lancez l'application une fois pour créer la base" -ForegroundColor Yellow
    exit 1
}

# Trouver le JAR
$jarFile = Get-ChildItem -Path "target" -Filter "*shaded*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jarFile) {
    $jarFile = Get-ChildItem -Path "target" -Filter "*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch "original" } | Select-Object -First 1
}

if (-not $jarFile) {
    Write-Host "[ERROR] JAR non trouvé!" -ForegroundColor Red
    exit 1
}

Write-Host "JAR: $($jarFile.Name)" -ForegroundColor Green
Write-Host ""

# Créer un script de diagnostic
$dbPathEscaped = $dbPath -replace '\\', '/'

$diagnosticScript = @"
import java.sql.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DiagnoseProductError {
    public static void main(String[] args) {
        String dbPath = "$dbPathEscaped";
        String url = "jdbc:sqlite:" + dbPath;
        
        System.out.println("========================================");
        System.out.println("  Diagnostic - Erreur Ajout Produit");
        System.out.println("========================================");
        System.out.println("Database: " + dbPath);
        System.out.println("");
        
        try (Connection conn = DriverManager.getConnection(url)) {
            conn.createStatement().execute("PRAGMA foreign_keys = ON");
            
            // 1. Vérifier la structure de la table produits
            System.out.println("1. Structure de la table produits:");
            ResultSet rs = conn.createStatement().executeQuery("PRAGMA table_info(produits)");
            List<String> columns = new ArrayList<>();
            while (rs.next()) {
                String colName = rs.getString("name");
                String colType = rs.getString("type");
                columns.add(colName);
                System.out.println("  - " + colName + " (" + colType + ")");
            }
            rs.close();
            System.out.println("");
            
            boolean hasUnite = columns.contains("unite");
            boolean hasCategoryId = columns.contains("category_id");
            
            System.out.println("  Colonne 'unite': " + (hasUnite ? "EXISTE" : "MANQUANTE"));
            System.out.println("  Colonne 'category_id': " + (hasCategoryId ? "EXISTE" : "MANQUANTE"));
            System.out.println("");
            
            // 2. Tester la détection de colonnes (comme ProduitDAO.columnExists)
            System.out.println("2. Test de détection de colonnes:");
            System.out.println("  Test 'unite': " + columnExists(conn, "unite"));
            System.out.println("  Test 'category_id': " + columnExists(conn, "category_id"));
            System.out.println("");
            
            // 3. Construire la requête SQL comme ProduitDAO
            System.out.println("3. Construction de la requête SQL:");
            StringBuilder sqlBuilder = new StringBuilder("INSERT INTO produits (code_barre, nom, categorie");
            if (hasCategoryId) {
                sqlBuilder.append(", category_id");
            }
            sqlBuilder.append(", prix_achat_actuel, prix_vente_defaut, quantite_stock");
            if (hasUnite) {
                sqlBuilder.append(", unite");
            }
            sqlBuilder.append(", seuil_alerte) VALUES (?, ?, ?");
            
            int paramCount = 3;
            if (hasCategoryId) {
                sqlBuilder.append(", ?");
                paramCount++;
            }
            sqlBuilder.append(", ?, ?, ?");
            paramCount += 3;
            if (hasUnite) {
                sqlBuilder.append(", ?");
                paramCount++;
            }
            sqlBuilder.append(", ?)");
            paramCount++;
            
            String sql = sqlBuilder.toString();
            System.out.println("  SQL: " + sql);
            System.out.println("  Nombre de paramètres: " + paramCount);
            System.out.println("");
            
            // 4. Tester l'insertion
            System.out.println("4. Test d'insertion:");
            try {
                String testCode = "DIAG_TEST_" + System.currentTimeMillis();
                PreparedStatement stmt = conn.prepareStatement(sql);
                
                int idx = 1;
                stmt.setString(idx++, testCode);
                stmt.setString(idx++, "Produit Diagnostic");
                stmt.setString(idx++, "Divers");
                if (hasCategoryId) {
                    stmt.setNull(idx++, java.sql.Types.INTEGER);
                }
                stmt.setBigDecimal(idx++, new BigDecimal("10.00"));
                stmt.setBigDecimal(idx++, new BigDecimal("15.00"));
                stmt.setInt(idx++, 100);
                if (hasUnite) {
                    stmt.setString(idx++, "unité");
                }
                stmt.setInt(idx++, 10);
                
                System.out.println("  Paramètres définis: " + (idx - 1));
                System.out.println("  Exécution...");
                
                int rows = stmt.executeUpdate();
                System.out.println("  [OK] Insertion réussie! Lignes: " + rows);
                
                // Supprimer le test
                ResultSet rsId = conn.createStatement().executeQuery("SELECT last_insert_rowid()");
                if (rsId.next()) {
                    int id = rsId.getInt(1);
                    conn.createStatement().execute("DELETE FROM produits WHERE id = " + id);
                    System.out.println("  [OK] Produit de test supprimé");
                }
                rsId.close();
                stmt.close();
                
            } catch (SQLException e) {
                System.err.println("  [ERROR] Erreur SQL:");
                System.err.println("    Message: " + e.getMessage());
                System.err.println("    Code: " + e.getSQLState());
                System.err.println("    Erreur SQLite: " + e.getErrorCode());
                System.err.println("");
                System.err.println("  Stack trace:");
                e.printStackTrace();
            }
            
            System.out.println("");
            System.out.println("========================================");
            System.out.println("  Diagnostic terminé");
            System.out.println("========================================");
            
        } catch (SQLException e) {
            System.err.println("[ERROR] Erreur de connexion:");
            System.err.println("  " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean columnExists(Connection conn, String columnName) {
        try {
            ResultSet rs = conn.createStatement().executeQuery("PRAGMA table_info(produits)");
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    rs.close();
                    return true;
                }
            }
            rs.close();
        } catch (SQLException e) {
            // En cas d'erreur, essayer une requête SELECT
            try {
                conn.createStatement().executeQuery("SELECT " + columnName + " FROM produits LIMIT 1");
                return true;
            } catch (SQLException e2) {
                return false;
            }
        }
        return false;
    }
}
"@

$scriptFile = "DiagnoseProductError.java"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText((Resolve-Path .).Path + "\$scriptFile", $diagnosticScript, $utf8NoBom)

Write-Host "Compilation et exécution..." -ForegroundColor Yellow
Write-Host ""

$javacExe = "$env:JAVA_HOME\bin\javac.exe"
$sqliteJdbc = Get-ChildItem -Path "target\lib" -Filter "*sqlite*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
$classpath = $jarFile.FullName
if ($sqliteJdbc) {
    $classpath = "$classpath;$($sqliteJdbc.FullName)"
}

& $javacExe -cp $classpath $scriptFile 2>&1 | ForEach-Object {
    Write-Host "  $_" -ForegroundColor Gray
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] Compilation réussie" -ForegroundColor Green
    Write-Host ""
    
    & $javaExe -cp "$classpath;." DiagnoseProductError
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "[OK] Diagnostic terminé" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "[ERROR] Le diagnostic a échoué" -ForegroundColor Red
    }
    
    Remove-Item -Path "DiagnoseProductError.class" -ErrorAction SilentlyContinue
} else {
    Write-Host "[ERROR] Compilation échouée" -ForegroundColor Red
}

Remove-Item -Path $scriptFile -ErrorAction SilentlyContinue

Write-Host ""

