# 🏪 2M Market - Application de Gestion de Stock et Ventes

Application desktop JavaFX pour la gestion de stock, ventes, fournisseurs et employés d'un marché.

## 📋 Table des Matières

- [Vue d'ensemble](#vue-densemble)
- [Fonctionnalités](#fonctionnalités)
- [Technologies](#technologies)
- [Prérequis](#prérequis)
- [Installation et configuration](#installation-et-configuration)
- [Lancement](#lancement)
- [Base de données](#base-de-données)
- [Déploiement](#déploiement)
- [Structure du Projet](#structure-du-projet)
- [Documentation détaillée](#documentation-détaillée)

---

## 🎯 Vue d'ensemble

**2M Market** est une application desktop JavaFX pour la gestion d'un marché, incluant :
- Gestion de stock avec codes-barres
- Point de vente (caisse)
- Gestion des ventes et statistiques
- Gestion des fournisseurs et crédits
- Gestion des utilisateurs (Admin/Employé)
- Rapports et recettes journalières
- Journal d'audit des opérations sensibles
- **Base de données PostgreSQL** : locale sur un poste, ou partagée entre plusieurs caisses

---

## ✨ Fonctionnalités

### Interface Administrateur

- **Dashboard** : produits, alertes de rupture, ventes du jour, fournisseurs, crédits, paiements du jour, notes journalières
- **Gestion de Stock** : CRUD complet des produits (recherche par code-barres ou nom, catégories, seuils d'alerte)
- **Gestion des Ventes** : historique, statistiques par produit, filtres par date, export PDF
- **Gestion des Utilisateurs** : création/modification/suppression, rôles Admin/Employé, mots de passe hachés BCrypt
- **Gestion Tabac** : interface spécialisée pour les produits tabac et cigarettes à l'unité
- **Journal d'audit** : traçabilité des opérations sensibles

### Interface Employé

- **Point de Vente (Caisse)** : navigation par catégories, scan code-barres, panier, calcul du total, tickets
- **Ajout de Stock** : réapprovisionnement avec suivi fournisseur, paiements et crédits, notes
- **Recette du Jour** : recettes quotidiennes
- **Notes Journalières** : enregistrement de notes et transactions

---

## 🛠️ Technologies

- **Java 17+** : langage
- **JavaFX 21** : framework UI
- **Maven 3.6+** : build et dépendances
- **PostgreSQL 14+** : base de données
- **HikariCP** : pool de connexions
- **BCrypt** : hachage des mots de passe
- **Apache PDFBox 3.0** : génération de PDF
- **SLF4J + Logback** : journalisation

---

## 📦 Prérequis

1. **Java JDK 17+** — [Adoptium](https://adoptium.net/) — vérifier : `java -version`
2. **Maven 3.6+** — [Apache Maven](https://maven.apache.org/download.cgi) — vérifier : `mvn -version`
3. **PostgreSQL 14+** installé et démarré (local ou distant)

---

## 🚀 Installation et configuration

### 1. Créer la base et l'utilisateur PostgreSQL

Un script d'installation est fourni pour Windows :

```powershell
powershell -ExecutionPolicy Bypass -File tools\setup-database.ps1
```

Il crée la base, l'utilisateur applicatif, et écrit le mot de passe dans un fichier
protégé hors dépôt : `%APPDATA%\2M-Market\database.properties`.

Voir **[SETUP_DATABASE.md](SETUP_DATABASE.md)** pour la procédure détaillée (local et hébergé).

### 2. Configurer la connexion

La configuration est résolue dans cet ordre (du plus prioritaire au moins prioritaire) :

1. Propriété système `-Dcle=valeur`
2. Variable d'environnement
3. `%APPDATA%\2M-Market\database.properties` (secrets, hors dépôt)
4. `config.properties` à la racine (développement, **sans mot de passe**)
5. `/config.properties` du classpath (valeurs non sensibles)

Clés : `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, ou la forme URL
`DATABASE_URL`, plus `PGSSLMODE`, `DB_POOL_SIZE`, `PGSCHEMA`.
Voir **[config.properties.example](config.properties.example)** pour toutes les clés.

> ⚠️ Ne jamais mettre de mot de passe dans `config.properties` ni dans le classpath :
> ces fichiers sont versionnés et embarqués dans le jar livré.

### 3. Compiler

```bash
mvn clean install
```

Le schéma de la base est appliqué automatiquement au premier démarrage (script
idempotent `schema_postgres.sql`).

---

## 💻 Lancement

```bash
mvn javafx:run
```

**Premier démarrage** : aucun compte n'est livré par défaut. L'application demande
la création du compte administrateur initial (nom d'utilisateur + mot de passe d'au
moins 8 caractères).

L'application s'ouvre en plein écran ; appuyer sur **Échap** pour en sortir.

---

## 🗄️ Base de données

- **Moteur** : PostgreSQL (montants en `NUMERIC(12,3)`, dates en `TIMESTAMP` réel, index créés)
- **Schéma** : `src/main/resources/database/schema_postgres.sql`, appliqué au démarrage
- **Sauvegarde** : `pg_dump` (voir [DEPLOIEMENT.md](DEPLOIEMENT.md))
- **Données applicatives** (tickets, exports, logs) : `%APPDATA%\2M-Market\`

Le choix « un seul poste » vs « plusieurs caisses en réseau » est détaillé dans
**[DEPLOIEMENT.md](DEPLOIEMENT.md)**.

---

## 📦 Déploiement

- **Installateur Windows autonome (JRE inclus)** : produit par `tools\build-installer.ps1`,
  qui appelle `jpackage` sur le jar Maven. Exemples :

  ```powershell
  .\tools\build-installer.ps1          # image portable
  .\tools\build-installer.ps1 -Type exe # installateur .exe (nécessite WiX)
  ```

  (`jpackage` est fourni avec le JDK 17+ ; le dossier `bin` du JDK doit être dans le `PATH`.)
- **Mise en production** (monoposte ou réseau local, sauvegardes) :
  voir **[DEPLOIEMENT.md](DEPLOIEMENT.md)**.

---

## 📁 Structure du Projet

```
minimarket/
├── src/main/java/
│   ├── app/MainApp.java        # Point d'entrée JavaFX
│   ├── controller/             # Contrôleurs FXML (un par écran)
│   ├── service/                # Règles métier et validation
│   ├── dao/                    # Accès JDBC (un DAO par table) + DBConnector (pool)
│   ├── model/                  # Objets du domaine
│   ├── ui/                     # Widgets et dialogues réutilisables
│   ├── util/                   # Config, DatabaseSetup, PDF, sécurité…
│   └── exception/              # Exceptions applicatives
├── src/main/resources/
│   ├── view/                   # FXML
│   ├── styles/                 # CSS
│   ├── database/schema_postgres.sql
│   ├── background/  icons/     # Ressources graphiques
│   └── logback.xml
├── src/test/java/              # Tests unitaires (Mockito) et d'intégration (*IT)
├── tools/                      # setup-database.ps1, build-installer.ps1
├── config.properties.example
├── pom.xml
├── CLAUDE.md                   # Guide pour les agents Claude Code
└── README.md
```

L'architecture suit un découpage strict **controller → service → dao → base**.
Détails dans [CLAUDE.md](CLAUDE.md).

---

## 📚 Documentation détaillée

- **[SETUP_DATABASE.md](SETUP_DATABASE.md)** — installation et configuration de PostgreSQL (local et hébergé)
- **[DEPLOIEMENT.md](DEPLOIEMENT.md)** — mise en production : monoposte vs réseau local, sauvegardes
- **[CLAUDE.md](CLAUDE.md)** — architecture et conventions du code

---

**Version** : 1.0 · **Java** : 17+ · **JavaFX** : 21 · **Base** : PostgreSQL 14+
