# 2M Market — Installation de la base de données

Guide complet : PostgreSQL en local pour le développement, puis Railway pour la production.

---

## 0. Ce qui a changé

L'application utilisait SQLite (fichier `MarketDB.db`). Elle utilise désormais **PostgreSQL**.

| | Avant | Après |
|---|---|---|
| Base | SQLite, fichier local | PostgreSQL (local ou Railway) |
| Montants | `REAL` (flottant) | `NUMERIC(12,3)` (décimal exact) |
| Dates | entiers epoch-millis | `TIMESTAMP` réel |
| Index | aucun | 24 index créés |
| Connexions | une seule, partagée | pool HikariCP |
| Mot de passe | `admin123` en dur | créé au 1er lancement |
| Emplacement données | dossier de lancement | `%APPDATA%\2M-Market\` |

---

## 1. Dépendances

### Java (déjà géré par Maven — rien à installer)

Ajoutées dans `pom.xml`, téléchargées automatiquement :

| Dépendance | Version | Rôle |
|---|---|---|
| `org.postgresql:postgresql` | 42.7.4 | pilote JDBC PostgreSQL |
| `com.zaxxer:HikariCP` | 5.1.0 | pool de connexions |
| `org.slf4j:slf4j-api` + `slf4j-simple` | 2.0.13 | journalisation |

Conservées : JavaFX 21, jBCrypt 0.4, PDFBox 3.0.0.
`sqlite-jdbc` reste uniquement pour la migration ponctuelle ; il pourra être retiré ensuite.

### Outils

```bash
java -version
```
Java 17+ requis (vous avez Temurin 17.0.17 ✓)

```bash
mvn -v
```
Maven 3.9+ requis (vous avez 3.9.9 ✓)

PostgreSQL 16 et 17 sont déjà installés sur votre machine (ports 5432 et 5433).

### Python (uniquement pour migrer vos données existantes)

```bash
pip install psycopg2-binary
```
Déjà installé ✓ — et facultatif si vous utilisez la méthode par fichier `.sql`.

---

## 2. Base locale (développement)

### Étape 1 — créer la base et le compte applicatif

```powershell
cd "C:\Users\Mohamed guizeni\Desktop\2M-markettttfinaalll\2M-markettttfinaalll8865\2M-market-main"
powershell -ExecutionPolicy Bypass -File tools\setup-database.ps1
```

Le script demande :
1. le mot de passe de votre superutilisateur `postgres` ;
2. un **nouveau** mot de passe pour le compte applicatif `market_app`.

Il crée le rôle `market_app`, la base `market2m`, puis écrit
`%APPDATA%\2M-Market\database.properties` en le protégeant par ACL.

> Le mot de passe n'est jamais écrit dans le dépôt Git : `database.properties`
> est listé dans `.gitignore`.

Pour PostgreSQL 17 (port 5433) :
```powershell
powershell -ExecutionPolicy Bypass -File tools\setup-database.ps1 -Port 5433
```

### Étape 2 — créer le schéma

Le schéma est appliqué automatiquement au démarrage de l'application. Pour le
faire manuellement :

```powershell
$env:PGPASSWORD = "votre_mot_de_passe_market_app"
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -h localhost -p 5432 -U market_app -d market2m -f src\main\resources\database\schema_postgres.sql
Remove-Item Env:\PGPASSWORD
```

### Étape 3 — importer vos données existantes

Vos 12 ventes, 10 produits, 4 utilisateurs et 6 notes sont récupérables.

> ⚠️ **Importez avant le premier lancement de l'application.** Au tout premier
> démarrage sur une base vide, l'application propose de créer un compte
> administrateur ; ce compte entrerait ensuite en conflit avec le compte `admin`
> importé (contrainte d'unicité sur `username`).

**Méthode A — fichier SQL (recommandée, aucune dépendance)**

```powershell
python tools\export_sqlite_to_sql.py --sqlite MarketDB.db --out tools\data_postgres.sql
```
Puis :
```powershell
$env:PGPASSWORD = "votre_mot_de_passe_market_app"
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -h localhost -p 5432 -U market_app -d market2m -f tools\data_postgres.sql
Remove-Item Env:\PGPASSWORD
```

**Méthode B — migration directe**

```powershell
python tools\migrate_sqlite_to_postgres.py --sqlite MarketDB.db --pg "postgresql://market_app:MOTDEPASSE@localhost:5432/market2m" --dry-run
```
Retirez `--dry-run` pour appliquer.

> Les deux méthodes sont **idempotentes** (`ON CONFLICT DO NOTHING`) : les rejouer
> ne crée pas de doublons.

### Étape 4 — lancer

```bash
mvn javafx:run
```

Au premier démarrage, l'application demande de créer le compte administrateur
(minimum 8 caractères). Il n'y a plus de mot de passe par défaut.

> ⚠️ Vos anciens comptes (`wael`, `lwess`, `admin`, `employe`) sont importés avec
> leurs mots de passe d'origine. Si vous les importez, l'écran de création
> initiale ne s'affichera pas.

### Étape 5 — lancer les tests

```bash
mvn test
```

Les tests s'exécutent dans un **schéma isolé** (`test_2m`) créé automatiquement
dans la même base. Ils ne peuvent pas écrire dans le schéma `public` : un
garde-fou les fait s'arrêter si on tente de les y exécuter. Aucune base
supplémentaire n'est nécessaire.

9 tests couvrent les défauts corrigés : requêtes du jour, attribution des ventes,
historisation des ajouts de stock, précision monétaire, incrément atomique du
stock, présence des index, et non-fermeture du pool.

---

## 3. Production sur Railway

### Ce que Railway peut et ne peut pas faire

**Railway héberge la base, pas l'application.** 2M Market est une application de
bureau JavaFX : elle a besoin d'un écran, alors que Railway exécute des services
web sans affichage. Il n'est pas possible d'y « déployer » l'interface.

L'architecture correcte :

```
   Poste caisse 1  ─┐
   Poste caisse 2  ─┼──── Internet ────►  PostgreSQL sur Railway
   Poste bureau    ─┘        (JDBC/TLS)
```

C'est un vrai gain : plusieurs caisses partagent enfin la même base, ce que le
fichier SQLite ne permettait pas.

### Étape 1 — créer la base

1. [railway.app](https://railway.app) → **New Project**
2. **Provision PostgreSQL**
3. Onglet **Variables** → copier `DATABASE_URL`

Elle ressemble à :
```
postgresql://postgres:MOTDEPASSE@centerbeam.proxy.rlwy.net:34567/railway
```

### Étape 2 — créer le schéma et importer

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" "postgresql://postgres:...@...rlwy.net:34567/railway" -f src\main\resources\database\schema_postgres.sql
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" "postgresql://postgres:...@...rlwy.net:34567/railway" -f tools\data_postgres.sql
```

### Étape 3 — configurer les postes

Sur **chaque** poste, éditez `%APPDATA%\2M-Market\database.properties` :

```properties
DATABASE_URL=postgresql://postgres:MOTDEPASSE@centerbeam.proxy.rlwy.net:34567/railway
PGSSLMODE=require
DB_POOL_SIZE=5
```

Ou par variable d'environnement :
```powershell
[Environment]::SetEnvironmentVariable("DATABASE_URL", "postgresql://...", "User")
```

`PGSSLMODE=require` est **important** : sans lui, les identifiants transitent en
clair sur Internet.

### Étape 4 — distribuer l'application

```bash
mvn clean package
```
Produit `target/2M-market-1.0-SNAPSHOT.jar` (~29 Mo, JavaFX inclus).

```bash
java -jar 2M-market-1.0-SNAPSHOT.jar
```

---

## 4. Point d'attention : la connexion Internet

Avec la base sur Railway, **plus d'Internet = plus de vente possible**. Pour un
commerce, c'est un risque réel à peser.

Options :
- **Base locale** sur le poste principal, les autres s'y connectent en réseau
  local (pas de dépendance Internet, mais sauvegardes à votre charge) ;
- **Railway** (accessible partout, sauvegardes gérées, mais dépend du réseau) ;
- **Compromis** : Railway + un PostgreSQL local en secours.

Ma recommandation pour un commerce de proximité : **PostgreSQL local sur le poste
principal**, avec une sauvegarde automatique quotidienne. Railway est le bon choix
si vous avez plusieurs points de vente à consolider.

---

## 5. Sauvegardes

```powershell
# Sauvegarde
& "C:\Program Files\PostgreSQL\16\bin\pg_dump.exe" -h localhost -U market_app -d market2m -F c -f "backup_$(Get-Date -Format 'yyyyMMdd').dump"

# Restauration
& "C:\Program Files\PostgreSQL\16\bin\pg_restore.exe" -h localhost -U market_app -d market2m --clean "backup_20260730.dump"
```

Railway effectue des sauvegardes automatiques (onglet **Backups**).

---

## 6. Dépannage

| Message | Cause | Solution |
|---|---|---|
| `Mot de passe ou utilisateur PostgreSQL incorrect` | mauvais identifiants | vérifier `database.properties` |
| `La base de données n'existe pas` | base non créée | relancer `setup-database.ps1` |
| `Serveur PostgreSQL injoignable` | service arrêté / réseau | `sc query postgresql-x64-16` |
| `Table manquante` | schéma non appliqué | rejouer `schema_postgres.sql` |
| `Base de données non configurée` | pas de config | créer `database.properties` |

Vérifier la connexion :
```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -h localhost -U market_app -d market2m -c "\dt"
```

Vérifier que les index existent (ils manquaient totalement avant) :
```sql
SELECT tablename, indexname FROM pg_indexes
WHERE schemaname = 'public' ORDER BY tablename;
```
