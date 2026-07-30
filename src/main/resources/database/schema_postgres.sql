-- ============================================================
-- 2M MARKET - Schéma PostgreSQL
-- ============================================================
-- Corrections par rapport à l'ancien schéma SQLite :
--   * Montants en NUMERIC(12,3) au lieu de REAL (plus d'arrondi flottant)
--   * Dates en TIMESTAMP réel au lieu d'entiers epoch-millis
--   * Index avec des noms uniques (sous SQLite, les noms dupliqués
--     faisaient silencieusement échouer la création via IF NOT EXISTS)
--   * Contraintes CHECK sur les quantités et montants
-- Ce script est idempotent : il peut être rejoué sans risque.
-- ============================================================

-- ------------------------------------------------------------
-- Utilisateurs
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS utilisateurs (
    id             SERIAL PRIMARY KEY,
    username       TEXT        NOT NULL UNIQUE,
    password_hash  TEXT        NOT NULL,
    role           TEXT        NOT NULL DEFAULT 'Employé'
                               CHECK (role IN ('Admin', 'Employé')),
    date_creation  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_utilisateurs_username ON utilisateurs (username);

-- ------------------------------------------------------------
-- Catégories
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id             SERIAL PRIMARY KEY,
    nom            TEXT        NOT NULL UNIQUE,
    description    TEXT,
    date_creation  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_categories_nom ON categories (nom);

-- ------------------------------------------------------------
-- Produits
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS produits (
    id                  SERIAL PRIMARY KEY,
    code_barre          TEXT           NOT NULL UNIQUE,
    nom                 TEXT           NOT NULL,
    categorie           TEXT,
    category_id         INTEGER        REFERENCES categories (id) ON DELETE SET NULL,
    prix_achat_actuel   NUMERIC(12,3)  NOT NULL CHECK (prix_achat_actuel >= 0),
    prix_vente_defaut   NUMERIC(12,3)  NOT NULL CHECK (prix_vente_defaut  >= 0),
    quantite_stock      INTEGER        NOT NULL DEFAULT 0 CHECK (quantite_stock >= 0),
    unite               TEXT           NOT NULL DEFAULT 'unité',
    seuil_alerte        INTEGER        NOT NULL DEFAULT 10 CHECK (seuil_alerte >= 0),
    date_derniere_maj   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_produits_code_barre  ON produits (code_barre);
CREATE INDEX IF NOT EXISTS idx_produits_nom         ON produits (nom);
CREATE INDEX IF NOT EXISTS idx_produits_category_id ON produits (category_id);

-- ------------------------------------------------------------
-- Ventes
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ventes (
    id              SERIAL PRIMARY KEY,
    date_vente      TIMESTAMP      NOT NULL,
    total_vente     NUMERIC(12,3)  NOT NULL CHECK (total_vente >= 0),
    id_utilisateur  INTEGER        NOT NULL REFERENCES utilisateurs (id) ON DELETE RESTRICT,
    type_paiement   TEXT           NOT NULL DEFAULT 'Espèces'
                                   CHECK (type_paiement IN ('Espèces', 'Carte', 'Autre')),
    date_creation   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ventes_date_vente     ON ventes (date_vente);
CREATE INDEX IF NOT EXISTS idx_ventes_utilisateur    ON ventes (id_utilisateur);
-- Index composite : la requête "recette du jour par employé" filtre sur les deux.
CREATE INDEX IF NOT EXISTS idx_ventes_user_date      ON ventes (id_utilisateur, date_vente);

-- ------------------------------------------------------------
-- Détails de vente
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS detailsvente (
    id                    SERIAL PRIMARY KEY,
    id_vente              INTEGER        NOT NULL REFERENCES ventes   (id) ON DELETE CASCADE,
    id_produit            INTEGER        NOT NULL REFERENCES produits (id) ON DELETE RESTRICT,
    quantite              INTEGER        NOT NULL CHECK (quantite > 0),
    prix_vente_unitaire   NUMERIC(12,3)  NOT NULL CHECK (prix_vente_unitaire  >= 0),
    prix_achat_unitaire   NUMERIC(12,3)  NOT NULL CHECK (prix_achat_unitaire  >= 0)
);

CREATE INDEX IF NOT EXISTS idx_detailsvente_vente   ON detailsvente (id_vente);
CREATE INDEX IF NOT EXISTS idx_detailsvente_produit ON detailsvente (id_produit);

-- ------------------------------------------------------------
-- Fournisseurs
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fournisseurs (
    id             SERIAL PRIMARY KEY,
    nom            TEXT        NOT NULL,
    contact        TEXT,
    telephone      TEXT,
    email          TEXT,
    adresse        TEXT,
    date_creation  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fournisseurs_nom ON fournisseurs (nom);

-- ------------------------------------------------------------
-- Crédits fournisseur
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS credits_fournisseur (
    id              SERIAL PRIMARY KEY,
    fournisseur_id  INTEGER        NOT NULL UNIQUE
                                   REFERENCES fournisseurs (id) ON DELETE CASCADE,
    montant         NUMERIC(12,3)  NOT NULL DEFAULT 0 CHECK (montant >= 0),
    date_creation   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_maj        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_credits_fournisseur_fid ON credits_fournisseur (fournisseur_id);

-- ------------------------------------------------------------
-- Paiements fournisseur
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS paiements_fournisseur (
    id              SERIAL PRIMARY KEY,
    fournisseur_id  INTEGER        NOT NULL REFERENCES fournisseurs (id) ON DELETE RESTRICT,
    employe_id      INTEGER        NOT NULL REFERENCES utilisateurs (id) ON DELETE RESTRICT,
    montant         NUMERIC(12,3)  NOT NULL CHECK (montant >= 0),
    notes           TEXT,
    date_paiement   TIMESTAMP      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_paiements_fournisseur_fid  ON paiements_fournisseur (fournisseur_id);
CREATE INDEX IF NOT EXISTS idx_paiements_fournisseur_date ON paiements_fournisseur (date_paiement);
CREATE INDEX IF NOT EXISTS idx_paiements_fournisseur_emp  ON paiements_fournisseur (employe_id, date_paiement);

-- ------------------------------------------------------------
-- Ajouts de stock
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ajouts_stock (
    id                SERIAL PRIMARY KEY,
    produit_id        INTEGER        NOT NULL REFERENCES produits     (id) ON DELETE RESTRICT,
    employe_id        INTEGER        NOT NULL REFERENCES utilisateurs (id) ON DELETE RESTRICT,
    fournisseur_id    INTEGER        REFERENCES fournisseurs (id) ON DELETE SET NULL,
    quantite          INTEGER        NOT NULL CHECK (quantite > 0),
    montant_paiement  NUMERIC(12,3)  NOT NULL DEFAULT 0 CHECK (montant_paiement >= 0),
    credit_utilise    NUMERIC(12,3)  NOT NULL DEFAULT 0 CHECK (credit_utilise   >= 0),
    notes             TEXT,
    date_ajout        TIMESTAMP      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ajouts_stock_produit ON ajouts_stock (produit_id);
CREATE INDEX IF NOT EXISTS idx_ajouts_stock_date    ON ajouts_stock (date_ajout);
CREATE INDEX IF NOT EXISTS idx_ajouts_stock_emp     ON ajouts_stock (employe_id, date_ajout);

-- ------------------------------------------------------------
-- Déplacements employé
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS deplacements_employe (
    id                 SERIAL PRIMARY KEY,
    employe_id         INTEGER        NOT NULL REFERENCES utilisateurs (id) ON DELETE RESTRICT,
    date_debut         TIMESTAMP      NOT NULL,
    date_fin           TIMESTAMP,
    destination        TEXT,
    notes              TEXT,
    heures_travaillees NUMERIC(8,2)   NOT NULL DEFAULT 0 CHECK (heures_travaillees >= 0)
);

CREATE INDEX IF NOT EXISTS idx_deplacements_employe     ON deplacements_employe (employe_id);
CREATE INDEX IF NOT EXISTS idx_deplacements_date_debut  ON deplacements_employe (date_debut);
CREATE INDEX IF NOT EXISTS idx_deplacements_emp_date    ON deplacements_employe (employe_id, date_debut);

-- ------------------------------------------------------------
-- Notes du jour
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notes_jour (
    id           SERIAL PRIMARY KEY,
    employe_id   INTEGER        NOT NULL REFERENCES utilisateurs (id) ON DELETE RESTRICT,
    type_note    TEXT           NOT NULL DEFAULT 'Autre'
                                CHECK (type_note IN ('Credit', 'Sortie_Caisse', 'Autre')),
    montant      NUMERIC(12,3)  NOT NULL DEFAULT 0,
    description  TEXT           NOT NULL,
    date_note    TIMESTAMP      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notes_jour_employe ON notes_jour (employe_id);
CREATE INDEX IF NOT EXISTS idx_notes_jour_date    ON notes_jour (date_note);
CREATE INDEX IF NOT EXISTS idx_notes_jour_emp_date ON notes_jour (employe_id, date_note);

-- ------------------------------------------------------------
-- Données de référence (idempotent, aucun mot de passe ici)
-- ------------------------------------------------------------
INSERT INTO categories (nom, description) VALUES
    ('Alimentaire', 'Produits alimentaires de base'),
    ('Boissons',    'Boissons et liquides'),
    ('Tabac',       'Produits de tabac'),
    ('Hygiène',     'Produits d''hygiène et de soins'),
    ('Divers',      'Autres produits')
ON CONFLICT (nom) DO NOTHING;
