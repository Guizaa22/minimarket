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
-- Le "type" détermine le comportement métier de la catégorie ; il ne dépend
-- plus de son libellé. Auparavant, une catégorie était considérée comme du
-- tabac si son nom contenait « tabac », « puff », « terrea » ou « cigarette » :
-- renommer une catégorie changeait donc silencieusement la façon dont le stock
-- était décrémenté.
--   Standard      : produit ordinaire
--   Tabac         : vendu au paquet, peut être décliné à l'unité
--   FrakCigarette : cigarettes vendues à l'unité, décrémentent le paquet associé
CREATE TABLE IF NOT EXISTS categories (
    id             SERIAL PRIMARY KEY,
    nom            TEXT        NOT NULL UNIQUE,
    description    TEXT,
    type           TEXT        NOT NULL DEFAULT 'Standard'
                               CHECK (type IN ('Standard', 'Tabac', 'FrakCigarette')),
    date_creation  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_categories_nom ON categories (nom);
-- L'index sur « type » est créé dans le bloc de migration ci-dessous : sur une
-- base antérieure, la colonne n'existe pas encore à ce stade.

-- Migration d'une base antérieure à ce changement.
--
-- Le tout est exécuté dynamiquement : PostgreSQL analyse l'intégralité d'un lot
-- d'instructions avant d'en exécuter la moindre, si bien qu'un UPDATE citant
-- « type » échouerait à l'analyse alors que l'ALTER qui crée la colonne se
-- trouve dans le même lot.
DO $$
BEGIN
    ALTER TABLE categories ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'Standard';

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'categories_type_check') THEN
        ALTER TABLE categories
            ADD CONSTRAINT categories_type_check
            CHECK (type IN ('Standard', 'Tabac', 'FrakCigarette'));
    END IF;

    -- Reprise unique de l'existant : l'ancienne heuristique sur le libellé sert
    -- une dernière fois à initialiser le type. Limitée aux catégories encore en
    -- 'Standard', pour ne pas écraser un choix fait depuis l'application.
    EXECUTE $sql$
        UPDATE categories
           SET type = 'FrakCigarette'
         WHERE type = 'Standard'
           AND LOWER(nom) LIKE '%frak%'
           AND LOWER(nom) LIKE '%cigarette%'
    $sql$;

    EXECUTE $sql$
        UPDATE categories
           SET type = 'Tabac'
         WHERE type = 'Standard'
           AND (LOWER(nom) LIKE '%tabac%'
             OR LOWER(nom) LIKE '%puff%'
             OR LOWER(nom) LIKE '%terrea%'
             OR LOWER(nom) LIKE '%cigarette%')
    $sql$;

    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_categories_type ON categories (type)';
END
$$;

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
