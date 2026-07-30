# 📁 Fichiers Nécessaires - 2M Market

## 🔨 Pour CRÉER l'installateur (PC de développement)

### Fichiers du projet (déjà présents)

✅ **Scripts de build**
- `build-exe.ps1` - Script principal pour créer l'installateur
- `pom.xml` - Configuration Maven

✅ **Code source**
- `src/main/java/` - Code Java de l'application
- `src/main/resources/` - Ressources (FXML, CSS, images)
  - `src/main/resources/database/init_sqlite.sql` - **Script SQLite (inclus dans le JAR)**

✅ **Base de données**
- `database/init_sqlite.sql` - Script SQLite (référence, copié dans les ressources)

✅ **Documentation**
- `README.md` - Documentation principale
- `GUIDE_INSTALLATION.md` - Guide d'installation complet

### Prérequis sur PC de développement

- Java JDK 17+ (avec jpackage)
- Maven 3.6+
- Windows 10/11

---

## 📦 Pour INSTALLER (PC de caisse)

### Fichier unique à copier

✅ **`2M-Market_Setup.exe`** (l'installateur Windows)
- Taille : ~200-300 MB
- Contient : Application + Java Runtime + Toutes les dépendances

### Ce qui est inclus dans l'installateur

✅ **Application complète**
- Code JavaFX compilé
- Toutes les fonctionnalités

✅ **Java Runtime (JRE)**
- Inclus dans l'installateur
- **Aucune installation Java requise sur le PC cible!**

✅ **Base de données SQLite**
- Script d'initialisation inclus dans les ressources du JAR
- Création automatique au premier lancement
- Fichier `MarketDB.db` créé dans le dossier d'installation

✅ **Toutes les dépendances**
- SQLite JDBC
- JavaFX
- BCrypt
- PDFBox
- Toutes les bibliothèques nécessaires

---

## 📂 Fichiers créés après installation

### Sur le PC de caisse (après installation)

```
C:\Program Files\2M-Market\
├── 2M-Market.exe          ← Application principale
├── app\                   ← Modules de l'application
├── runtime\               ← Java Runtime (inclus)
├── MarketDB.db            ← Base SQLite (créée au premier lancement)
└── ...                    ← Autres fichiers système
```

### Raccourcis Windows

- Menu Démarrer > 2M Market > 2M-Market
- Raccourci sur le Bureau

---

## 🗄️ Base de Données SQLite

### Fichiers liés à la base de données

✅ **Script SQL** (inclus dans le JAR)
- `src/main/resources/database/init_sqlite.sql`
- Exécuté automatiquement par `SQLiteDatabaseSetup.java`
- Créé automatiquement au premier lancement

✅ **Fichier de base de données** (créé automatiquement)
- `MarketDB.db` - Base SQLite
- Emplacement : Dossier d'installation
- Création : Au premier lancement de l'application

### Aucun fichier SQL externe requis!

La base de données est créée automatiquement. Aucun script SQL à exécuter manuellement.

---

## ✅ Résumé

### Pour créer l'installateur

**Fichiers nécessaires :**
- Tous les fichiers du projet (déjà présents)
- `build-exe.ps1` (script de build)
- `database/init_sqlite.sql` (copié automatiquement dans les ressources)

**Commande :**
```powershell
.\build-exe.ps1
```

**Résultat :**
- `dist/2M-Market_Setup.exe` (installateur Windows)

### Pour installer sur PC de caisse

**Fichier unique :**
- `2M-Market_Setup.exe` (l'installateur)

**Action :**
- Double-cliquer sur l'installateur
- Suivre l'assistant d'installation

**Résultat :**
- Application installée dans `C:\Program Files\2M-Market\`
- Base de données créée automatiquement au premier lancement
- Raccourcis créés automatiquement

---

## 🎯 Points importants

✅ **Java inclus** - Aucune installation Java requise sur le PC cible
✅ **SQLite embarqué** - Aucun serveur de base de données requis
✅ **Base créée automatiquement** - Aucun script SQL à exécuter manuellement
✅ **Un seul fichier à copier** - L'installateur contient tout

---

**Version** : 1.0  
**Date** : 2025-01-28

