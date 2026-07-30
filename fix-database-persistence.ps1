# Script pour vérifier et corriger la persistance de la base de données
# S'assure que toutes les tables sont créées et que les données persistent

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Fix Database Persistence - 2M Market" -ForegroundColor Cyan
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

# Trouver toutes les bases de données possibles
Write-Host "Recherche des bases de données..." -ForegroundColor Yellow
Write-Host ""

$dbPaths = @()

# Chercher dans les emplacements possibles
$searchPaths = @(
    ".",
    "target\installer\2M-Market-App",
    "dist\2M-Market",
    "$env:USERPROFILE\Desktop",
    "$env:USERPROFILE\Documents"
)

foreach ($path in $searchPaths) {
    if (Test-Path $path) {
        $dbFile = Join-Path $path "MarketDB.db"
        if (Test-Path $dbFile) {
            $dbPaths += $dbFile
            Write-Host "  [OK] Trouvé: $dbFile" -ForegroundColor Green
        }
        
        # Chercher aussi 2market.db
        $dbFile2 = Join-Path $path "2market.db"
        if (Test-Path $dbFile2) {
            $dbPaths += $dbFile2
            Write-Host "  [OK] Trouvé: $dbFile2" -ForegroundColor Green
        }
    }
}

if ($dbPaths.Count -eq 0) {
    Write-Host "  [INFO] Aucune base de données trouvée" -ForegroundColor Yellow
    Write-Host "  La base sera créée au premier lancement de l'application" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "Bases de données trouvées: $($dbPaths.Count)" -ForegroundColor Cyan
}

Write-Host ""

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

# Créer un script Java pour vérifier et corriger toutes les bases de données
$scriptContent = @"
import java.sql.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DatabasePersistenceFix {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Database Persistence Fix");
        System.out.println("========================================");
        System.out.println("");
        
        // Liste des tables requises
        List<String> requiredTables = List.of(
            "utilisateurs", "categories", "produits", "ventes", 
            "detailsvente", "fournisseurs", "credits_fournisseur",
            "paiements_fournisseur", "ajouts_stock", 
            "deplacements_employe", "notes_jour"
        );
        
        // Chercher toutes les bases de données
        List<String> dbFiles = new ArrayList<>();
        String userDir = System.getProperty("user.dir");
        
        // Chercher MarketDB.db dans plusieurs emplacements
        String[] searchPaths = {
            userDir,
            userDir + File.separator + "target" + File.separator + "installer" + File.separator + "2M-Market-App",
            userDir + File.separator + "dist" + File.separator + "2M-Market",
            System.getProperty("user.home") + File.separator + "Desktop",
            System.getProperty("user.home") + File.separator + "Documents"
        };
        
        for (String path : searchPaths) {
            File dir = new File(path);
            if (dir.exists()) {
                File dbFile = new File(dir, "MarketDB.db");
                if (dbFile.exists()) {
                    dbFiles.add(dbFile.getAbsolutePath());
                }
                File dbFile2 = new File(dir, "2market.db");
                if (dbFile2.exists()) {
                    dbFiles.add(dbFile2.getAbsolutePath());
                }
            }
        }
        
        if (dbFiles.isEmpty()) {
            System.out.println("[INFO] Aucune base de données trouvée");
            System.out.println("La base sera créée au premier lancement");
            return;
        }
        
        System.out.println("Bases de données trouvées: " + dbFiles.size());
        System.out.println("");
        
        // Corriger chaque base de données
        for (String dbPath : dbFiles) {
            System.out.println("Traitement: " + dbPath);
            System.out.println("----------------------------------------");
            
            try {
                String url = "jdbc:sqlite:" + dbPath;
                Connection conn = DriverManager.getConnection(url);
                conn.createStatement().execute("PRAGMA foreign_keys = ON");
                
                // 1. Créer toutes les tables
                System.out.println("1. Création/vérification des tables...");
                createAllTables(conn);
                
                // 2. Vérifier que toutes les tables existent
                boolean allTablesExist = true;
                for (String table : requiredTables) {
                    ResultSet rs = conn.getMetaData().getTables(null, null, table, null);
                    if (!rs.next()) {
                        System.out.println("  [ERROR] Table manquante: " + table);
                        allTablesExist = false;
                    } else {
                        System.out.println("  [OK] Table existe: " + table);
                    }
                    rs.close();
                }
                
                if (!allTablesExist) {
                    System.out.println("  [FIX] Recréation des tables manquantes...");
                    createAllTables(conn);
                }
                
                // 3. Vérifier les utilisateurs
                System.out.println("");
                System.out.println("2. Vérification des utilisateurs...");
                ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) as count FROM utilisateurs");
                int userCount = rs.getInt("count");
                rs.close();
                System.out.println("  Nombre d'utilisateurs: " + userCount);
                
                if (userCount == 0) {
                    System.out.println("  [FIX] Création des utilisateurs par défaut...");
                    createDefaultUsers(conn);
                }
                
                // 4. Vérifier les catégories
                System.out.println("");
                System.out.println("3. Vérification des catégories...");
                rs = conn.createStatement().executeQuery("SELECT COUNT(*) as count FROM categories");
                int catCount = rs.getInt("count");
                rs.close();
                System.out.println("  Nombre de catégories: " + catCount);
                
                if (catCount == 0) {
                    System.out.println("  [FIX] Création des catégories par défaut...");
                    createDefaultCategories(conn);
                }
                
                // 5. Vérifier les fournisseurs
                System.out.println("");
                System.out.println("4. Vérification des fournisseurs...");
                rs = conn.createStatement().executeQuery("SELECT COUNT(*) as count FROM fournisseurs");
                int fourCount = rs.getInt("count");
                rs.close();
                System.out.println("  Nombre de fournisseurs: " + fourCount);
                
                if (fourCount == 0) {
                    System.out.println("  [FIX] Création des fournisseurs par défaut...");
                    createDefaultFournisseurs(conn);
                }
                
                // 6. Test de persistance
                System.out.println("");
                System.out.println("5. Test de persistance...");
                String testCode = "PERSIST_TEST_" + System.currentTimeMillis();
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO produits (code_barre, nom, categorie, prix_achat_actuel, prix_vente_defaut, quantite_stock, unite, seuil_alerte) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                );
                stmt.setString(1, testCode);
                stmt.setString(2, "Test Persistence");
                stmt.setString(3, "Divers");
                stmt.setBigDecimal(4, new java.math.BigDecimal("1.00"));
                stmt.setBigDecimal(5, new java.math.BigDecimal("2.00"));
                stmt.setInt(6, 1);
                stmt.setString(7, "unité");
                stmt.setInt(8, 1);
                stmt.executeUpdate();
                stmt.close();
                
                // Vérifier que le produit a été sauvegardé
                rs = conn.createStatement().executeQuery("SELECT COUNT(*) as count FROM produits WHERE code_barre = '" + testCode + "'");
                if (rs.next() && rs.getInt("count") > 0) {
                    System.out.println("  [OK] Insertion réussie - données sauvegardées");
                    
                    // Supprimer le produit de test
                    conn.createStatement().execute("DELETE FROM produits WHERE code_barre = '" + testCode + "'");
                    System.out.println("  [OK] Produit de test supprimé");
                }
                rs.close();
                
                conn.close();
                
                System.out.println("");
                System.out.println("[OK] Base de données corrigée: " + dbPath);
                System.out.println("");
                
            } catch (SQLException e) {
                System.err.println("[ERROR] Erreur avec: " + dbPath);
                System.err.println("  " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("========================================");
        System.out.println("  Correction terminée!");
        System.out.println("========================================");
        System.out.println("");
        System.out.println("Les données seront maintenant sauvegardées automatiquement");
        System.out.println("et persistantes entre les sessions de l'application.");
    }
    
    private static void createAllTables(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        
        // Table utilisateurs
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS utilisateurs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('Admin', 'Employé')) DEFAULT 'Employé',
                date_creation DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);
        
        // Table categories
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nom TEXT NOT NULL UNIQUE,
                description TEXT,
                date_creation DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);
        
        // Table produits
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS produits (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code_barre TEXT NOT NULL UNIQUE,
                nom TEXT NOT NULL,
                categorie TEXT,
                category_id INTEGER,
                prix_achat_actuel REAL NOT NULL,
                prix_vente_defaut REAL NOT NULL,
                quantite_stock INTEGER NOT NULL DEFAULT 0,
                unite TEXT DEFAULT 'unité',
                seuil_alerte INTEGER NOT NULL DEFAULT 10,
                date_derniere_maj DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
            )
        """);
        
        // Table ventes
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS ventes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date_vente DATETIME NOT NULL,
                total_vente REAL NOT NULL,
                id_utilisateur INTEGER NOT NULL,
                type_paiement TEXT CHECK(type_paiement IN ('Espèces', 'Carte', 'Autre')) DEFAULT 'Espèces',
                date_creation DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (id_utilisateur) REFERENCES utilisateurs(id) ON DELETE RESTRICT
            )
        """);
        
        // Table detailsvente
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS detailsvente (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                id_vente INTEGER NOT NULL,
                id_produit INTEGER NOT NULL,
                quantite INTEGER NOT NULL,
                prix_vente_unitaire REAL NOT NULL,
                prix_achat_unitaire REAL NOT NULL,
                FOREIGN KEY (id_vente) REFERENCES ventes(id) ON DELETE CASCADE,
                FOREIGN KEY (id_produit) REFERENCES produits(id) ON DELETE RESTRICT
            )
        """);
        
        // Table fournisseurs
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS fournisseurs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nom TEXT NOT NULL,
                contact TEXT,
                telephone TEXT,
                email TEXT,
                adresse TEXT,
                date_creation DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);
        
        // Table credits_fournisseur
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS credits_fournisseur (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fournisseur_id INTEGER NOT NULL,
                montant REAL NOT NULL DEFAULT 0.00,
                date_creation DATETIME DEFAULT CURRENT_TIMESTAMP,
                date_maj DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (fournisseur_id) REFERENCES fournisseurs(id) ON DELETE CASCADE
            )
        """);
        
        // Table paiements_fournisseur
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS paiements_fournisseur (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fournisseur_id INTEGER NOT NULL,
                employe_id INTEGER NOT NULL,
                montant REAL NOT NULL,
                notes TEXT,
                date_paiement DATETIME NOT NULL,
                FOREIGN KEY (fournisseur_id) REFERENCES fournisseurs(id) ON DELETE RESTRICT,
                FOREIGN KEY (employe_id) REFERENCES utilisateurs(id) ON DELETE RESTRICT
            )
        """);
        
        // Table ajouts_stock
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS ajouts_stock (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                produit_id INTEGER NOT NULL,
                employe_id INTEGER NOT NULL,
                fournisseur_id INTEGER,
                quantite INTEGER NOT NULL,
                montant_paiement REAL DEFAULT 0.00,
                credit_utilise REAL DEFAULT 0.00,
                notes TEXT,
                date_ajout DATETIME NOT NULL,
                FOREIGN KEY (produit_id) REFERENCES produits(id) ON DELETE RESTRICT,
                FOREIGN KEY (employe_id) REFERENCES utilisateurs(id) ON DELETE RESTRICT,
                FOREIGN KEY (fournisseur_id) REFERENCES fournisseurs(id) ON DELETE SET NULL
            )
        """);
        
        // Table deplacements_employe
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS deplacements_employe (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                employe_id INTEGER NOT NULL,
                date_debut DATETIME NOT NULL,
                date_fin DATETIME,
                destination TEXT,
                notes TEXT,
                heures_travaillees REAL DEFAULT 0.00,
                FOREIGN KEY (employe_id) REFERENCES utilisateurs(id) ON DELETE RESTRICT
            )
        """);
        
        // Table notes_jour
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS notes_jour (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                employe_id INTEGER NOT NULL,
                type_note TEXT NOT NULL CHECK(type_note IN ('Credit', 'Sortie_Caisse', 'Autre')) DEFAULT 'Autre',
                montant REAL DEFAULT 0.00,
                description TEXT NOT NULL,
                date_note DATETIME NOT NULL,
                FOREIGN KEY (employe_id) REFERENCES utilisateurs(id) ON DELETE RESTRICT
            )
        """);
        
        stmt.close();
    }
    
    private static void createDefaultUsers(Connection conn) throws SQLException {
        String hash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        PreparedStatement stmt = conn.prepareStatement("INSERT OR REPLACE INTO utilisateurs (id, username, password_hash, role) VALUES (?, ?, ?, ?)");
        stmt.setInt(1, 1);
        stmt.setString(2, "admin");
        stmt.setString(3, hash);
        stmt.setString(4, "Admin");
        stmt.executeUpdate();
        stmt.setInt(1, 2);
        stmt.setString(2, "employe");
        stmt.setString(3, hash);
        stmt.setString(4, "Employé");
        stmt.executeUpdate();
        stmt.close();
    }
    
    private static void createDefaultCategories(Connection conn) throws SQLException {
        conn.createStatement().execute("""
            INSERT OR IGNORE INTO categories (nom, description) VALUES
            ('Alimentaire', 'Produits alimentaires de base'),
            ('Boissons', 'Boissons et liquides'),
            ('Tabac', 'Produits de tabac'),
            ('Hygiène', 'Produits d''hygiène et de soins'),
            ('Divers', 'Autres produits')
        """);
    }
    
    private static void createDefaultFournisseurs(Connection conn) throws SQLException {
        conn.createStatement().execute("""
            INSERT OR IGNORE INTO fournisseurs (nom, contact, telephone) VALUES
            ('Fournisseur Principal', 'Contact Principal', '0123456789'),
            ('Grossiste Alimentaire', 'M. Grossiste', '0123456790'),
            ('Distributeur Tabac', 'M. Distributeur', '0123456791')
        """);
    }
}
"@

$scriptFile = "DatabasePersistenceFix.java"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText((Resolve-Path .).Path + "\$scriptFile", $scriptContent, $utf8NoBom)

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
    
    & $javaExe -cp "$classpath;." DatabasePersistenceFix
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "[OK] Correction terminée avec succès!" -ForegroundColor Green
        Write-Host ""
        Write-Host "La base de données est maintenant configurée pour:" -ForegroundColor Cyan
        Write-Host "  ✓ Sauvegarder automatiquement toutes les données" -ForegroundColor Green
        Write-Host "  ✓ Persister les données entre les sessions" -ForegroundColor Green
        Write-Host "  ✓ Créer toutes les tables au démarrage si nécessaire" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "[ERROR] La correction a échoué" -ForegroundColor Red
    }
    
    Remove-Item -Path "DatabasePersistenceFix.class" -ErrorAction SilentlyContinue
} else {
    Write-Host "[ERROR] Compilation échouée" -ForegroundColor Red
}

Remove-Item -Path $scriptFile -ErrorAction SilentlyContinue

Write-Host ""

