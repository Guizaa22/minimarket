# 2M Market — Déploiement (sans Railway)

Guide de mise en production pour un commerce, sans dépendance à un service cloud.

---

## 1. Choisir l'architecture

2M Market est une application **de bureau**. Le vrai choix porte sur l'endroit où
vit la base de données.

### Option A — Un seul poste (le plus simple)

```
   [ PC caisse ]
   application + PostgreSQL
```

Tout sur une machine. Aucun réseau, aucune dépendance externe.
**À retenir si vous avez une seule caisse.**

### Option B — Plusieurs caisses en réseau local ✅ recommandé

```
   [ PC principal ]  PostgreSQL + application
          │  réseau local (câble ou Wi-Fi)
          ├── [ Caisse 2 ]  application
          └── [ Bureau ]    application
```

Plusieurs postes, une seule base. **Aucune dépendance à Internet** : une coupure
de connexion n'empêche pas de vendre. C'est le point décisif pour un commerce.

### Option C — Base sur un serveur distant (VPS)

```
   [ Caisses ] ── Internet ──► [ VPS : Hetzner / OVH / Contabo ]
```

Utile seulement si vous avez **plusieurs points de vente** à consolider.
Inconvénient majeur : **plus d'Internet = plus de vente**. Pour un commerce de
proximité, le risque dépasse le bénéfice.

> **Ma recommandation : option B.** Vous gagnez le multi-poste sans introduire
> de dépendance réseau externe. On peut passer à l'option C plus tard sans
> retoucher le code : seule la configuration change.

---

## 2. Option B — mise en place

### 2.1 Sur le PC principal (serveur)

PostgreSQL est déjà installé et configuré (voir `SETUP_DATABASE.md`).
Il reste à autoriser les autres postes à s'y connecter.

**a. Écouter sur le réseau local**

Éditez `C:\Program Files\PostgreSQL\16\data\postgresql.conf` :
```conf
listen_addresses = '*'
```

**b. Autoriser votre réseau local uniquement**

Éditez `C:\Program Files\PostgreSQL\16\data\pg_hba.conf` et ajoutez, en adaptant
la plage à votre réseau (`ipconfig` pour la connaître) :
```conf
# Postes de caisse du magasin - jamais 0.0.0.0/0
host    market2m    market_app    192.168.1.0/24    scram-sha-256
```

> N'utilisez jamais `0.0.0.0/0` : cela exposerait la base à tout Internet.

**c. Redémarrer PostgreSQL**
```powershell
Restart-Service postgresql-x64-16
```

**d. Ouvrir le port dans le pare-feu, pour le réseau local seulement**
```powershell
New-NetFirewallRule -DisplayName "PostgreSQL 2M Market" `
  -Direction Inbound -Protocol TCP -LocalPort 5432 `
  -RemoteAddress 192.168.1.0/24 -Action Allow
```

**e. Fixer l'adresse IP du PC principal**

Dans votre box/routeur, réservez une IP fixe (bail DHCP statique) pour ce PC.
Sinon son adresse changera et les caisses perdront la base.

### 2.2 Sur chaque poste de caisse

Créez `%APPDATA%\2M-Market\database.properties` :

```properties
PGHOST=192.168.1.10
PGPORT=5432
PGDATABASE=market2m
PGUSER=market_app
PGPASSWORD=le_mot_de_passe_applicatif
DB_POOL_SIZE=3
```

Vérification :
```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -h 192.168.1.10 -U market_app -d market2m -c "SELECT COUNT(*) FROM produits"
```

---

## 3. Distribuer l'application

### Méthode recommandée — application autonome (JRE inclus)

```bash
mvn clean package -Pinstaller
```

Produit `target/installer/2M-Market/`. Copiez ce dossier sur chaque poste et
lancez `2M-Market.exe`. **Aucun Java à installer** : le runtime est embarqué.

Pour un vrai programme d'installation (`.exe` avec raccourcis) :
```bash
mvn clean package -Pinstaller -Djpackage.type=exe
```
Nécessite le [WiX Toolset](https://wixtoolset.org). Sans lui, jpackage échoue —
utilisez la forme `app-image` ci-dessus, qui n'a aucun prérequis.

### Méthode simple — le jar

```bash
mvn clean package
java -jar target/2M-market-1.0-SNAPSHOT.jar
```
Demande Java 17+ installé sur chaque poste.

---

## 4. Sauvegardes — à ne pas négliger

C'est le vrai risque de l'option B : tout est sur une machine. Un disque qui
lâche, et la comptabilité disparaît.

**Sauvegarde quotidienne automatique.** Créez `C:\2M-Market\backup.ps1` :

```powershell
$date   = Get-Date -Format 'yyyyMMdd-HHmm'
$dest   = "D:\Sauvegardes-2M"   # idéalement un autre disque
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$env:PGPASSWORD = "le_mot_de_passe_applicatif"
& "C:\Program Files\PostgreSQL\16\bin\pg_dump.exe" `
    -h localhost -U market_app -d market2m -F c `
    -f "$dest\market2m-$date.dump"
Remove-Item Env:\PGPASSWORD

# Conserver 30 jours
Get-ChildItem $dest -Filter *.dump |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-30) } |
    Remove-Item
```

Planifiez-la à la fermeture du magasin :
```powershell
$action  = New-ScheduledTaskAction -Execute "powershell.exe" `
           -Argument "-ExecutionPolicy Bypass -File C:\2M-Market\backup.ps1"
$trigger = New-ScheduledTaskTrigger -Daily -At 21:00
Register-ScheduledTask -TaskName "Sauvegarde 2M Market" -Action $action -Trigger $trigger
```

**Testez la restauration au moins une fois** — une sauvegarde jamais restaurée
n'est pas une sauvegarde :
```powershell
& "C:\Program Files\PostgreSQL\16\bin\pg_restore.exe" `
    -h localhost -U market_app -d market2m_essai --clean "D:\Sauvegardes-2M\market2m-20260730-2100.dump"
```

Copiez aussi régulièrement un `.dump` hors du magasin (clé USB, disque externe,
stockage en ligne) : un incendie ou un vol emporte le PC **et** le disque de
sauvegarde posé à côté.

---

## 5. Sécurité — l'essentiel

| Point | À faire |
|---|---|
| Mots de passe | `postgres` et `market_app` **différents**, 12+ caractères |
| `pg_hba.conf` | limiter à votre plage locale, jamais `0.0.0.0/0` |
| Pare-feu | port 5432 ouvert au réseau local uniquement |
| `database.properties` | contient un mot de passe : ne jamais le commiter (déjà dans `.gitignore`) |
| Comptes applicatifs | un compte nominatif par employé — c'est ce qui rend la recette du jour exploitable |

Changer un mot de passe applicatif oublié :
```bash
java -cp target/2M-market-1.0-SNAPSHOT.jar util.ResetPassword
```

---

## 6. Si vous passez plus tard à un serveur distant

Le code n'a pas besoin d'évoluer. Sur chaque poste :

```properties
DATABASE_URL=postgresql://market_app:motdepasse@serveur.exemple.com:5432/market2m
PGSSLMODE=require
```

`PGSSLMODE=require` est indispensable dès que la connexion sort du réseau local,
sinon les identifiants circulent en clair.

Hébergeurs PostgreSQL gérés, si le besoin se présente : Neon, Supabase,
DigitalOcean Managed Databases, Scaleway. Tous imposent la même contrainte :
**sans Internet, plus de vente**. Prévoyez alors une procédure de repli papier.
