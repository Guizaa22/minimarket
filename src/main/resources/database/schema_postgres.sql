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

-- Détail des réapprovisionnements de tabac.
--   type_ajout_tabac    : 'paquet' ou 'cigarette' selon l'unité saisie au comptoir
--   quantite_cigarettes : nombre de cigarettes quand l'ajout est fait à l'unité
-- Ces deux colonnes étaient auparavant créées à chaud par AjoutStockDAO, au
-- moyen d'un ALTER TABLE non idempotent déclenché à la première écriture : sur
-- une base partagée par plusieurs caisses, deux postes pouvaient le jouer en
-- même temps, et une base neuve ne les avait pas tant qu'aucun ajout n'avait
-- été saisi. Elles font désormais partie du schéma, seule source de vérité.
--
-- Exécuté dynamiquement : PostgreSQL analyse tout un lot avant de l'exécuter,
-- une contrainte citant une colonne ajoutée dans le même lot échouerait à l'analyse.
DO $$
BEGIN
    ALTER TABLE ajouts_stock ADD COLUMN IF NOT EXISTS type_ajout_tabac    TEXT;
    ALTER TABLE ajouts_stock ADD COLUMN IF NOT EXISTS quantite_cigarettes INTEGER;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ajouts_stock_qte_cigarettes_check') THEN
        ALTER TABLE ajouts_stock
            ADD CONSTRAINT ajouts_stock_qte_cigarettes_check
            CHECK (quantite_cigarettes IS NULL OR quantite_cigarettes >= 0);
    END IF;

    -- Contrainte posée au mieux : sur une base déjà migrée à chaud par
    -- l'ancien code, une ligne au libellé inattendu ferait échouer l'ALTER, et
    -- avec lui tout le démarrage de l'application. Mieux vaut alors s'en
    -- passer et le signaler que d'empêcher l'ouverture de la caisse.
    -- La comparaison ignore la casse, comme les lectures côté Java.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ajouts_stock_type_tabac_check') THEN
        BEGIN
            ALTER TABLE ajouts_stock
                ADD CONSTRAINT ajouts_stock_type_tabac_check
                CHECK (type_ajout_tabac IS NULL
                       OR LOWER(type_ajout_tabac) IN ('paquet', 'cigarette'));
        EXCEPTION WHEN check_violation THEN
            RAISE NOTICE 'ajouts_stock.type_ajout_tabac contient des valeurs hors ''paquet''/''cigarette'' : contrainte non posée.';
        END;
    END IF;
END
$$;

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

-- ============================================================
-- Traçabilité et préparation de l'application mobile
-- ============================================================

-- ------------------------------------------------------------
-- Mouvements de stock
-- ------------------------------------------------------------
-- Journal unique de toutes les variations de stock, quelle qu'en soit
-- l'origine. La table ajouts_stock ne couvrait que les réapprovisionnements
-- saisis au comptoir : les ventes n'y figuraient pas, et rien ne permettait
-- de reconstituer l'évolution d'un stock ni de savoir d'où venait un écart.
--
-- quantite_delta est signé : négatif pour une sortie, positif pour une entrée.
CREATE TABLE IF NOT EXISTS stock_movements (
    id              SERIAL PRIMARY KEY,
    product_id      INTEGER   NOT NULL REFERENCES produits     (id) ON DELETE RESTRICT,
    user_id         INTEGER            REFERENCES utilisateurs (id) ON DELETE SET NULL,
    quantity_change INTEGER   NOT NULL CHECK (quantity_change <> 0),
    stock_apres     INTEGER,
    type            TEXT      NOT NULL
                    CHECK (type IN ('DESKTOP_SALE', 'MOBILE_ADD', 'DESKTOP_ADD',
                                    'INVENTORY_ADJUSTMENT', 'SALE_CANCELLED')),
    reference       TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_movements_product ON stock_movements (product_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_stock_movements_user    ON stock_movements (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_stock_movements_type    ON stock_movements (type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_stock_movements_date    ON stock_movements (created_at DESC);

-- ------------------------------------------------------------
-- Journal d'audit
-- ------------------------------------------------------------
-- Répond à « qui a changé ce prix », « qui a supprimé ce produit ».
-- user_id est nullable et ON DELETE SET NULL : supprimer un employé ne doit
-- pas effacer la trace de ses actions.
CREATE TABLE IF NOT EXISTS audit_logs (
    id          SERIAL PRIMARY KEY,
    user_id     INTEGER            REFERENCES utilisateurs (id) ON DELETE SET NULL,
    action      TEXT      NOT NULL,
    entity_name TEXT,
    entity_id   INTEGER,
    details     TEXT,
    timestamp   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_user   ON audit_logs (user_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs (entity_name, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_date   ON audit_logs (timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs (action, timestamp DESC);

-- ------------------------------------------------------------
-- Vue : produits en rupture ou sous le seuil d'alerte
-- ------------------------------------------------------------
-- Destinée à l'application mobile : une seule requête sans jointure à écrire
-- côté client. manquant indique la quantité à commander pour repasser au seuil.
CREATE OR REPLACE VIEW vw_products_low_stock AS
SELECT p.id,
       p.code_barre,
       p.nom,
       COALESCE(c.nom, p.categorie)       AS categorie,
       COALESCE(c.type, 'Standard')       AS categorie_type,
       p.quantite_stock,
       p.seuil_alerte,
       GREATEST(p.seuil_alerte - p.quantite_stock, 0) AS manquant,
       p.prix_achat_actuel,
       p.prix_vente_defaut,
       p.unite,
       CASE WHEN p.quantite_stock = 0 THEN 'RUPTURE' ELSE 'SOUS_SEUIL' END AS etat,
       p.date_derniere_maj
  FROM produits p
  LEFT JOIN categories c ON c.id = p.category_id
 WHERE p.quantite_stock <= p.seuil_alerte;

-- Index de couverture des recherches de l'API mobile.
-- Recherche insensible à la casse sur le nom : sans cet index fonctionnel,
-- un LOWER(nom) LIKE ... impose un parcours complet de la table.
CREATE INDEX IF NOT EXISTS idx_produits_nom_lower  ON produits (LOWER(nom));
CREATE INDEX IF NOT EXISTS idx_produits_stock_bas  ON produits (quantite_stock, seuil_alerte);
CREATE INDEX IF NOT EXISTS idx_produits_categorie  ON produits (categorie);

-- ============================================================
-- Photos et tarification du tabac
-- ============================================================
--
-- Les images sont stockées en base plutôt que sur disque : chaque poste de
-- caisse les voit immédiatement, sans dossier partagé à configurer, et la
-- future application mobile y accède par la même connexion. Le contenu est
-- redimensionné côté application avant insertion (voir util/ImageUtil).
--
-- Exécuté dynamiquement : PostgreSQL analyse tout un lot avant de l'exécuter,
-- une requête citant une colonne ajoutée dans le même lot échouerait à l'analyse.
DO $$
BEGIN
    -- Photos
    ALTER TABLE produits   ADD COLUMN IF NOT EXISTS image       BYTEA;
    ALTER TABLE produits   ADD COLUMN IF NOT EXISTS image_mime  TEXT;
    ALTER TABLE categories ADD COLUMN IF NOT EXISTS image       BYTEA;
    ALTER TABLE categories ADD COLUMN IF NOT EXISTS image_mime  TEXT;

    -- Prix de la cigarette vendue à l'unité.
    -- prix_vente_defaut reste le prix du paquet ; la cigarette n'est pas
    -- facturée au prorata (paquet / 20), les commerces appliquant une marge
    -- sur la vente au détail.
    ALTER TABLE produits ADD COLUMN IF NOT EXISTS prix_vente_cigarette NUMERIC(12,3);

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'produits_prix_cigarette_check') THEN
        ALTER TABLE produits
            ADD CONSTRAINT produits_prix_cigarette_check
            CHECK (prix_vente_cigarette IS NULL OR prix_vente_cigarette >= 0);
    END IF;

    -- Le détail de vente mémorise l'unité facturée : sans cela, une ligne de
    -- 7 « Marlboro » ne dit pas s'il s'agit de 7 paquets ou de 7 cigarettes,
    -- et le ticket comme les statistiques deviennent ambigus.
    ALTER TABLE detailsvente ADD COLUMN IF NOT EXISTS unite_vente TEXT NOT NULL DEFAULT 'unite';

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'detailsvente_unite_check') THEN
        ALTER TABLE detailsvente
            ADD CONSTRAINT detailsvente_unite_check
            CHECK (unite_vente IN ('unite', 'paquet', 'cigarette'));
    END IF;
END
$$;

-- ------------------------------------------------------------
-- Reliquat du paquet entamé
-- ------------------------------------------------------------
-- Le stock des produits de tabac est tenu en paquets, mais la vente se fait
-- aussi à la cigarette. Sans mémoire du paquet ouvert, chaque vente au détail
-- arrondissait au paquet supérieur : vendre 7 cigarettes cinq fois retirait
-- 5 paquets du stock — 100 cigarettes — pour 35 cigarettes réellement
-- sorties. Le stock dérivait donc à la baisse à chaque vente partielle.
--
-- cigarettes_restantes compte ce qui reste dans le paquet entamé. Une vente
-- y puise d'abord, et n'ouvre un paquet — donc ne décrémente le stock — que
-- lorsque le reliquat ne suffit plus.
DO $$
BEGIN
    ALTER TABLE produits
        ADD COLUMN IF NOT EXISTS cigarettes_restantes INTEGER NOT NULL DEFAULT 0;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'produits_cigarettes_restantes_check') THEN
        ALTER TABLE produits
            ADD CONSTRAINT produits_cigarettes_restantes_check
            CHECK (cigarettes_restantes >= 0);
    END IF;
END
$$;

-- ------------------------------------------------------------
-- Archivage des produits
-- ------------------------------------------------------------
-- Un produit déjà vendu ne peut pas être supprimé sans réécrire le passé :
-- ses lignes de detailsvente portent le prix d'achat et le prix de vente du
-- jour de la vente, dont dépend tout calcul de bénéfice. L'ancienne
-- « suppression forcée » les effaçait, si bien que le chiffre d'affaires
-- restait inchangé — il vient de ventes.total_vente — pendant que le bénéfice
-- des mois clos s'effondrait. Un produit retiré de la vente est donc archivé :
-- il disparaît de la caisse et du stock, mais son historique reste intact.
DO $$
BEGIN
    ALTER TABLE produits ADD COLUMN IF NOT EXISTS actif BOOLEAN NOT NULL DEFAULT TRUE;

    -- Les écrans de vente et de stock ne lisent que les produits actifs :
    -- l'index évite un parcours complet dès que des archives s'accumulent.
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_produits_actif ON produits (actif)';
END
$$;

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
