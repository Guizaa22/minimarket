-- ============================================
-- 2M MARKET - SQLite Database Schema
-- ============================================
-- Script pour créer la base de données SQLite
-- La base sera créée automatiquement dans MarketDB.db
-- ============================================

-- Table: utilisateurs
CREATE TABLE IF NOT EXISTS utilisateurs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('Admin', 'Employé')) DEFAULT 'Employé',
    date_creation DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_username ON utilisateurs(username);

-- Table: categories
CREATE TABLE IF NOT EXISTS categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nom TEXT NOT NULL UNIQUE,
    description TEXT,
    date_creation DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_nom ON categories(nom);

-- Table: produits
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
);

CREATE INDEX IF NOT EXISTS idx_code_barre ON produits(code_barre);
CREATE INDEX IF NOT EXISTS idx_nom ON produits(nom);
CREATE INDEX IF NOT EXISTS idx_category_id ON produits(category_id);

-- Table: ventes
CREATE TABLE IF NOT EXISTS ventes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date_vente DATETIME NOT NULL,
    total_vente REAL NOT NULL,
    id_utilisateur INTEGER NOT NULL,
    type_paiement TEXT CHECK(type_paiement IN ('Espèces', 'Carte', 'Autre')) DEFAULT 'Espèces',
    date_creation DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_utilisateur) REFERENCES utilisateurs(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_date_vente ON ventes(date_vente);
CREATE INDEX IF NOT EXISTS idx_id_utilisateur ON ventes(id_utilisateur);

-- Table: detailsvente
CREATE TABLE IF NOT EXISTS detailsvente (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    id_vente INTEGER NOT NULL,
    id_produit INTEGER NOT NULL,
    quantite INTEGER NOT NULL,
    prix_vente_unitaire REAL NOT NULL,
    prix_achat_unitaire REAL NOT NULL,
    FOREIGN KEY (id_vente) REFERENCES ventes(id) ON DELETE CASCADE,
    FOREIGN KEY (id_produit) REFERENCES produits(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_id_vente ON detailsvente(id_vente);
CREATE INDEX IF NOT EXISTS idx_id_produit ON detailsvente(id_produit);

-- Table: fournisseurs
CREATE TABLE IF NOT EXISTS fournisseurs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nom TEXT NOT NULL,
    contact TEXT,
    telephone TEXT,
    email TEXT,
    adresse TEXT,
    date_creation DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_nom ON fournisseurs(nom);

-- Table: credits_fournisseur
CREATE TABLE IF NOT EXISTS credits_fournisseur (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fournisseur_id INTEGER NOT NULL,
    montant REAL NOT NULL DEFAULT 0.00,
    date_creation DATETIME DEFAULT CURRENT_TIMESTAMP,
    date_maj DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (fournisseur_id) REFERENCES fournisseurs(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_fournisseur_id ON credits_fournisseur(fournisseur_id);

-- Table: paiements_fournisseur
CREATE TABLE IF NOT EXISTS paiements_fournisseur (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fournisseur_id INTEGER NOT NULL,
    employe_id INTEGER NOT NULL,
    montant REAL NOT NULL,
    notes TEXT,
    date_paiement DATETIME NOT NULL,
    FOREIGN KEY (fournisseur_id) REFERENCES fournisseurs(id) ON DELETE RESTRICT,
    FOREIGN KEY (employe_id) REFERENCES utilisateurs(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_fournisseur_id ON paiements_fournisseur(fournisseur_id);
CREATE INDEX IF NOT EXISTS idx_date_paiement ON paiements_fournisseur(date_paiement);

-- Table: ajouts_stock
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
);

CREATE INDEX IF NOT EXISTS idx_produit_id ON ajouts_stock(produit_id);
CREATE INDEX IF NOT EXISTS idx_date_ajout ON ajouts_stock(date_ajout);

-- Table: deplacements_employe
CREATE TABLE IF NOT EXISTS deplacements_employe (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    employe_id INTEGER NOT NULL,
    date_debut DATETIME NOT NULL,
    date_fin DATETIME,
    destination TEXT,
    notes TEXT,
    heures_travaillees REAL DEFAULT 0.00,
    FOREIGN KEY (employe_id) REFERENCES utilisateurs(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_employe_id ON deplacements_employe(employe_id);
CREATE INDEX IF NOT EXISTS idx_date_debut ON deplacements_employe(date_debut);

-- Table: notes_jour
CREATE TABLE IF NOT EXISTS notes_jour (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    employe_id INTEGER NOT NULL,
    type_note TEXT NOT NULL CHECK(type_note IN ('Credit', 'Sortie_Caisse', 'Autre')) DEFAULT 'Autre',
    montant REAL DEFAULT 0.00,
    description TEXT NOT NULL,
    date_note DATETIME NOT NULL,
    FOREIGN KEY (employe_id) REFERENCES utilisateurs(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_employe_id ON notes_jour(employe_id);
CREATE INDEX IF NOT EXISTS idx_date_note ON notes_jour(date_note);

-- ============================================
-- DONNÉES PAR DÉFAUT
-- ============================================

-- Catégories
INSERT OR IGNORE INTO categories (nom, description) VALUES
('Alimentaire', 'Produits alimentaires de base'),
('Boissons', 'Boissons et liquides'),
('Tabac', 'Produits de tabac'),
('Hygiène', 'Produits d''hygiène et de soins'),
('Divers', 'Autres produits');

-- Fournisseurs
INSERT OR IGNORE INTO fournisseurs (nom, contact, telephone) VALUES
('Fournisseur Principal', 'Contact Principal', '0123456789'),
('Grossiste Alimentaire', 'M. Grossiste', '0123456790'),
('Distributeur Tabac', 'M. Distributeur', '0123456791');

-- Crédits fournisseurs
INSERT OR IGNORE INTO credits_fournisseur (fournisseur_id, montant) 
SELECT id, 0.00 FROM fournisseurs;

-- Utilisateurs (mot de passe: admin123)
-- Hash BCrypt: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
INSERT OR REPLACE INTO utilisateurs (id, username, password_hash, role) VALUES
(1, 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Admin'),
(2, 'employe', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Employé');

-- Produits d'exemple
INSERT OR IGNORE INTO produits (code_barre, nom, categorie, category_id, prix_achat_actuel, prix_vente_defaut, quantite_stock, unite, seuil_alerte) VALUES
('1234567890123', 'Paquet de Pâtes', 'Alimentaire', 1, 0.50, 1.20, 150, 'unité', 20),
('1234567890124', 'Riz 1kg', 'Alimentaire', 1, 1.00, 2.50, 80, 'kg', 15),
('1234567890125', 'Huile d''olive 1L', 'Alimentaire', 1, 3.50, 6.00, 45, 'L', 10),
('1234567890126', 'Sucre 1kg', 'Alimentaire', 1, 0.80, 1.80, 120, 'kg', 25),
('1234567890127', 'Café 250g', 'Alimentaire', 1, 2.50, 5.00, 60, 'g', 12),
('1234567890128', 'Thé 100g', 'Alimentaire', 1, 1.20, 3.00, 90, 'g', 15),
('1234567890129', 'Lait 1L', 'Boissons', 2, 0.60, 1.50, 200, 'L', 30),
('1234567890130', 'Pain de mie', 'Alimentaire', 1, 0.40, 1.00, 100, 'unité', 20);

-- ============================================
-- VÉRIFICATION
-- ============================================
SELECT 'Database initialized successfully!' as Status;
SELECT COUNT(*) as nombre_utilisateurs FROM utilisateurs;
SELECT COUNT(*) as nombre_categories FROM categories;
SELECT COUNT(*) as nombre_produits FROM produits;
SELECT COUNT(*) as nombre_fournisseurs FROM fournisseurs;

