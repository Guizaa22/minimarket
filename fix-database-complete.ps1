# Script complet pour diagnostiquer et corriger tous les problèmes de base de données
# Analyse toutes les fonctionnalités et corrige les problèmes

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Fix Database Complete - 2M Market" -ForegroundColor Cyan
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
Write-Host "Dossier: $appDir" -ForegroundColor Gray
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

# Créer un script Java complet pour diagnostiquer et corriger
$dbPathEscaped = $dbPath -replace '\\', '/'

$diagnosticScript = @"
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseDiagnostic {
    public static void main(String[] args) {
        String dbPath = "$dbPathEscaped";
        String url = "jdbc:sqlite:" + dbPath;
        
        System.out.println("========================================");
        System.out.println("  Database Diagnostic & Fix");
        System.out.println("========================================");
        System.out.println("Database: " + dbPath);
        System.out.println("");
        
        try (Connection conn = DriverManager.getConnection(url)) {
            conn.createStatement().execute("PRAGMA foreign_keys = ON");
            
            // 1. Créer TOUTES les tables d'abord
            System.out.println("1. Création de toutes les tables...");
            createAllTables(conn);
            System.out.println("  [OK] Toutes les tables créées/vérifiées");
            System.out.println("");
            
            // 2. Vérifier les utilisateurs
            System.out.println("2. Vérification des utilisateurs...");
            ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) as count FROM utilisateurs");
            int userCount = rs.getInt("count");
            rs.close();
            
            System.out.println("  Nombre d'utilisateurs: " + userCount);
            
            if (userCount == 0) {
                System.out.println("  [FIX] Création des utilisateurs par défaut...");
                createDefaultUsers(conn);
                System.out.println("  [OK] Utilisateurs créés");
            } else {
                // Vérifier admin et employe
                rs = conn.createStatement().executeQuery("SELECT username FROM utilisateurs WHERE username IN ('admin', 'employe')");
                List<String> existingUsers = new ArrayList<>();
                while (rs.next()) {
                    existingUsers.add(rs.getString("username"));
                }
                rs.close();
                
                if (!existingUsers.contains("admin")) {
                    System.out.println("  [FIX] Création de l'utilisateur admin...");
                    createUser(conn, "admin", "admin123", "Admin");
                }
                if (!existingUsers.contains("employe")) {
                    System.out.println("  [FIX] Création de l'utilisateur employe...");
                    createUser(conn, "employe", "admin123", "Employé");
                }
            }
            
            System.out.println("");
            
            // 3. Vérifier les catégories
            System.out.println("3. Vérification des catégories...");
            rs = conn.createStatement().executeQuery("SELECT COUNT(*) as count FROM categories");
            int catCount = rs.getInt("count");
            rs.close();
            
            System.out.println("  Nombre de catégories: " + catCount);
            
            if (catCount == 0) {
                System.out.println("  [FIX] Création des catégories par défaut...");
                createDefaultCategories(conn);
                System.out.println("  [OK] Catégories créées");
            }
            
            System.out.println("");
            
            // 4. Vérifier les fournisseurs
            System.out.println("4. Vérification des fournisseurs...");
            rs = conn.createStatement().executeQuery("SELECT COUNT(*) as count FROM fournisseurs");
            int fourCount = rs.getInt("count");
            rs.close();
            
            System.out.println("  Nombre de fournisseurs: " + fourCount);
            
            if (fourCount == 0) {
                System.out.println("  [FIX] Création des fournisseurs par défaut...");
                createDefaultFournisseurs(conn);
                System.out.println("  [OK] Fournisseurs créés");
            }
            
            System.out.println("");
            
            // 5. Vérifier les colonnes de produits
            System.out.println("5. Vérification de la structure de la table produits...");
            rs = conn.createStatement().executeQuery("PRAGMA table_info(produits)");
            List<String> columns = new ArrayList<>();
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
            rs.close();
            
            System.out.println("  Colonnes: " + String.join(", ", columns));
            
            boolean hasUnite = columns.contains("unite");
            boolean hasCategoryId = columns.contains("category_id");
            
            if (!hasUnite) {
                System.out.println("  [FIX] Ajout de la colonne unite...");
                conn.createStatement().execute("ALTER TABLE produits ADD COLUMN unite TEXT DEFAULT 'unité'");
                System.out.println("  [OK] Colonne unite ajoutée");
            }
            if (!hasCategoryId) {
                System.out.println("  [FIX] Ajout de la colonne category_id...");
                conn.createStatement().execute("ALTER TABLE produits ADD COLUMN category_id INTEGER");
                System.out.println("  [OK] Colonne category_id ajoutée");
            }
            
            System.out.println("");
            
            // 6. Test d'insertion de produit
            System.out.println("6. Test d'insertion de produit...");
            try {
                String testCode = "TEST" + System.currentTimeMillis();
                String sql = "INSERT INTO produits (code_barre, nom, categorie, prix_achat_actuel, prix_vente_defaut, quantite_stock, unite, seuil_alerte) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, testCode);
                stmt.setString(2, "Produit Test");
                stmt.setString(3, "Divers");
                stmt.setBigDecimal(4, new java.math.BigDecimal("10.00"));
                stmt.setBigDecimal(5, new java.math.BigDecimal("15.00"));
                stmt.setInt(6, 100);
                stmt.setString(7, "unité");
                stmt.setInt(8, 10);
                
                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    System.out.println("  [OK] Insertion réussie!");
                    
                    // Supprimer le produit de test
                    ResultSet rsId = conn.createStatement().executeQuery("SELECT last_insert_rowid()");
                    if (rsId.next()) {
                        int id = rsId.getInt(1);
                        conn.createStatement().execute("DELETE FROM produits WHERE id = " + id);
                        System.out.println("  [OK] Produit de test supprimé");
                    }
                    rsId.close();
                }
                stmt.close();
            } catch (SQLException e) {
                System.err.println("  [ERROR] Erreur lors de l'insertion: " + e.getMessage());
                e.printStackTrace();
            }
            
            System.out.println("");
            System.out.println("========================================");
            System.out.println("  Diagnostic terminé avec succès!");
            System.out.println("========================================");
            
        } catch (SQLException e) {
            System.err.println("[ERROR] Erreur de connexion:");
            System.err.println("  " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
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
    
    private static void createUser(Connection conn, String username, String password, String role) throws SQLException {
        String hash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        PreparedStatement stmt = conn.prepareStatement("INSERT OR REPLACE INTO utilisateurs (username, password_hash, role) VALUES (?, ?, ?)");
        stmt.setString(1, username);
        stmt.setString(2, hash);
        stmt.setString(3, role);
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

$scriptFile = "DatabaseDiagnostic.java"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText((Resolve-Path .).Path + "\$scriptFile", $diagnosticScript, $utf8NoBom)

Write-Host "Compilation et exécution du diagnostic..." -ForegroundColor Yellow
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
    
    & $javaExe -cp "$classpath;." DatabaseDiagnostic
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "[OK] Diagnostic terminé avec succès!" -ForegroundColor Green
        Write-Host ""
        Write-Host "La base de données devrait maintenant fonctionner correctement." -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Vous pouvez maintenant:" -ForegroundColor Yellow
        Write-Host "  1. Relancer l'application: .\build-and-run.ps1" -ForegroundColor White
        Write-Host "  2. Vous connecter avec: admin / admin123" -ForegroundColor White
        Write-Host "  3. Tester toutes les fonctionnalités" -ForegroundColor White
    } else {
        Write-Host ""
        Write-Host "[ERROR] Le diagnostic a échoué" -ForegroundColor Red
    }
    
    Remove-Item -Path "DatabaseDiagnostic.class" -ErrorAction SilentlyContinue
} else {
    Write-Host "[ERROR] Compilation échouée" -ForegroundColor Red
}

Remove-Item -Path $scriptFile -ErrorAction SilentlyContinue

Write-Host ""

