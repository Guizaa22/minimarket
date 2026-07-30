# Script pour corriger les colonnes manquantes dans la base de données

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Fix Database Columns - 2M Market" -ForegroundColor Cyan
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
$possibleDirs = @(
    "target\installer\2M-Market-App",
    "dist\2M-Market",
    "."
)

foreach ($dir in $possibleDirs) {
    $dbFile = Join-Path $dir "MarketDB.db"
    if (Test-Path $dbFile) {
        $dbPath = $dbFile
        break
    }
}

if (-not $dbPath) {
    $dbPath = "MarketDB.db"
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
    Write-Host "[ERROR] JAR non trouvé! Exécutez d'abord: .\build-with-maven.ps1" -ForegroundColor Red
    exit 1
}

Write-Host "JAR: $($jarFile.Name)" -ForegroundColor Green
Write-Host ""

# Créer un script Java pour corriger les colonnes
$dbPathEscaped = $dbPath -replace '\\', '/'

$fixScript = @"
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FixDatabaseColumns {
    public static void main(String[] args) {
        String dbPath = "$dbPathEscaped";
        String url = "jdbc:sqlite:" + dbPath;
        
        System.out.println("========================================");
        System.out.println("  Fix Database Columns");
        System.out.println("========================================");
        System.out.println("Database: " + dbPath);
        System.out.println("");
        
        try (Connection conn = DriverManager.getConnection(url)) {
            conn.createStatement().execute("PRAGMA foreign_keys = ON");
            
            // 1. Vérifier la structure actuelle
            System.out.println("1. Vérification de la structure de la table produits:");
            ResultSet rs = conn.createStatement().executeQuery("PRAGMA table_info(produits)");
            List<String> existingColumns = new ArrayList<>();
            while (rs.next()) {
                String colName = rs.getString("name");
                existingColumns.add(colName.toLowerCase());
                System.out.println("  [OK] " + colName);
            }
            rs.close();
            System.out.println("");
            
            // 2. Vérifier et ajouter les colonnes manquantes
            System.out.println("2. Vérification des colonnes requises:");
            
            boolean needsFix = false;
            
            // Colonne 'unite'
            if (!existingColumns.contains("unite")) {
                System.out.println("  [FIX] Ajout de la colonne 'unite'...");
                try {
                    conn.createStatement().execute("ALTER TABLE produits ADD COLUMN unite TEXT DEFAULT 'unité'");
                    System.out.println("  [OK] Colonne 'unite' ajoutée");
                    needsFix = true;
                } catch (SQLException e) {
                    System.err.println("  [ERROR] Impossible d'ajouter 'unite': " + e.getMessage());
                }
            } else {
                System.out.println("  [OK] Colonne 'unite' existe");
            }
            
            // Colonne 'category_id'
            if (!existingColumns.contains("category_id")) {
                System.out.println("  [FIX] Ajout de la colonne 'category_id'...");
                try {
                    conn.createStatement().execute("ALTER TABLE produits ADD COLUMN category_id INTEGER");
                    System.out.println("  [OK] Colonne 'category_id' ajoutée");
                    needsFix = true;
                } catch (SQLException e) {
                    System.err.println("  [ERROR] Impossible d'ajouter 'category_id': " + e.getMessage());
                }
            } else {
                System.out.println("  [OK] Colonne 'category_id' existe");
            }
            
            System.out.println("");
            
            // 3. Vester l'insertion d'un produit
            System.out.println("3. Test d'insertion d'un produit:");
            try {
                String testCode = "FIX_TEST_" + System.currentTimeMillis();
                
                // Construire la requête SQL dynamiquement
                boolean hasUnite = columnExists(conn, "unite");
                boolean hasCategoryId = columnExists(conn, "category_id");
                
                StringBuilder sql = new StringBuilder("INSERT INTO produits (code_barre, nom, categorie");
                if (hasCategoryId) {
                    sql.append(", category_id");
                }
                sql.append(", prix_achat_actuel, prix_vente_defaut, quantite_stock");
                if (hasUnite) {
                    sql.append(", unite");
                }
                sql.append(", seuil_alerte) VALUES (?, ?, ?");
                if (hasCategoryId) {
                    sql.append(", ?");
                }
                sql.append(", ?, ?, ?");
                if (hasUnite) {
                    sql.append(", ?");
                }
                sql.append(", ?)");
                
                PreparedStatement stmt = conn.prepareStatement(sql.toString());
                int idx = 1;
                stmt.setString(idx++, testCode);
                stmt.setString(idx++, "Produit Test");
                stmt.setString(idx++, "Divers");
                if (hasCategoryId) {
                    stmt.setNull(idx++, java.sql.Types.INTEGER);
                }
                stmt.setBigDecimal(idx++, new java.math.BigDecimal("10.00"));
                stmt.setBigDecimal(idx++, new java.math.BigDecimal("15.00"));
                stmt.setInt(idx++, 100);
                if (hasUnite) {
                    stmt.setString(idx++, "unité");
                }
                stmt.setInt(idx++, 10);
                
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
                System.err.println("  [ERROR] Erreur lors du test d'insertion:");
                System.err.println("    Message: " + e.getMessage());
                System.err.println("    Code: " + e.getSQLState());
                System.err.println("    Erreur SQLite: " + e.getErrorCode());
                e.printStackTrace();
            }
            
            System.out.println("");
            System.out.println("========================================");
            if (needsFix) {
                System.out.println("  [OK] Corrections appliquées!");
                System.out.println("  Relancez l'application pour tester");
            } else {
                System.out.println("  [OK] Aucune correction nécessaire");
            }
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
            return false;
        }
        return false;
    }
}
"@

$scriptFile = "FixDatabaseColumns.java"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText((Resolve-Path .).Path + "\$scriptFile", $fixScript, $utf8NoBom)

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
    
    & $javaExe -cp "$classpath;." FixDatabaseColumns
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "[OK] Correction terminée!" -ForegroundColor Green
        Write-Host ""
        Write-Host "Relancez l'application pour tester l'ajout de produits" -ForegroundColor Cyan
    } else {
        Write-Host ""
        Write-Host "[ERROR] La correction a échoué" -ForegroundColor Red
    }
    
    Remove-Item -Path "FixDatabaseColumns.class" -ErrorAction SilentlyContinue
} else {
    Write-Host "[ERROR] Compilation échouée" -ForegroundColor Red
}

Remove-Item -Path $scriptFile -ErrorAction SilentlyContinue

Write-Host ""

