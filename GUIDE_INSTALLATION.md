# 📦 Guide d'Installation - 2M Market

## 🎯 Vue d'ensemble

Ce guide explique comment créer l'installateur Windows et déployer l'application sur un PC de caisse.

---

## 🔨 TÂCHE 1 : Créer l'Installateur Windows

### Prérequis pour créer l'installateur

Sur votre PC de développement, vous avez besoin de :

1. **Java JDK 17+** (avec jpackage inclus)
   - Télécharger : [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) ou [OpenJDK](https://adoptium.net/)
   - Vérifier : `java -version` et `jpackage --version`

2. **Maven 3.6+**
   - Télécharger : [Apache Maven](https://maven.apache.org/download.cgi)
   - Vérifier : `mvn -version`

### Étapes pour créer l'installateur

1. **Ouvrir PowerShell** dans le dossier du projet

2. **Exécuter le script de build** :
   ```powershell
   .\build-exe.ps1
   ```

3. **Le script va** :
   - ✅ Vérifier Java et Maven
   - ✅ Trouver automatiquement Java si JAVA_HOME n'est pas défini
   - ✅ Compiler le projet (Maven)
   - ✅ Créer un JAR avec toutes les dépendances
   - ✅ Créer l'installateur Windows (.exe) avec jpackage
   - ✅ Inclure Java Runtime dans l'installateur
   - ✅ Créer un package de déploiement

4. **Résultat** :
   - Fichier installateur : `dist\2M-Market_Setup.exe` (ou nom similaire)
   - Taille : ~200-300 MB (inclut Java Runtime)
   - Package de déploiement : `dist\Deployment-Package\`

### Fichiers créés

```
dist/
├── 2M-Market_Setup.exe          ← Installateur Windows (à copier sur USB)
└── Deployment-Package/
    ├── 2M-Market_Setup.exe      ← Installateur
    ├── README.md                ← Documentation
    └── GUIDE_INSTALLATION.txt   ← Guide d'installation
```

---

## 📋 TÂCHE 2 : Installer sur le PC de Caisse

### Ce qui est inclus dans l'installateur

✅ **Application JavaFX complète**
- Interface utilisateur
- Toutes les fonctionnalités Admin et Employé

✅ **Java Runtime (JRE)**
- **AUCUNE installation Java requise sur le PC cible!**
- Java est inclus dans l'installateur

✅ **Base de données SQLite**
- Script d'initialisation inclus dans les ressources
- Création automatique au premier lancement
- Fichier `MarketDB.db` créé dans le dossier d'installation

✅ **Toutes les dépendances**
- SQLite JDBC
- JavaFX
- BCrypt
- PDFBox
- Toutes les bibliothèques nécessaires

### Fichiers nécessaires pour l'exécution

**Fichier unique à copier :**
- `2M-Market_Setup.exe` (l'installateur)

**Fichiers créés automatiquement après installation :**
- `C:\Program Files\2M-Market\2M-Market.exe` (application)
- `C:\Program Files\2M-Market\MarketDB.db` (base de données SQLite - créée au premier lancement)
- Raccourci dans le Menu Démarrer
- Raccourci sur le Bureau

### Étapes d'installation sur PC de caisse

#### Étape 1 : Transférer l'installateur

1. Copiez **UNIQUEMENT** le fichier `2M-Market_Setup.exe` sur une clé USB
2. Ou transférez-le via réseau vers le PC de caisse

#### Étape 2 : Installer l'application

1. Sur le PC de caisse, **double-cliquez** sur `2M-Market_Setup.exe`
2. L'assistant d'installation s'ouvre
3. Suivez les instructions :
   - Acceptez la licence
   - Choisissez le dossier d'installation (par défaut : `C:\Program Files\2M-Market`)
   - L'installation peut prendre 2-3 minutes (extraction de Java Runtime)
4. Cliquez sur **"Installer"**
5. Attendez la fin de l'installation

#### Étape 3 : Lancer l'application

**Premier lancement :**

1. L'application est disponible via :
   - **Menu Démarrer** > 2M Market > 2M-Market
   - **Raccourci sur le Bureau**
   - Ou directement : `C:\Program Files\2M-Market\2M-Market.exe`

2. Au **premier lancement** :
   - La base de données SQLite sera créée **automatiquement**
   - Fichier créé : `C:\Program Files\2M-Market\MarketDB.db`
   - Les utilisateurs par défaut seront créés automatiquement
   - Aucune configuration requise!

3. **Identifiants par défaut** :
   - **Admin** : `admin` / `admin123`
   - **Employé** : `employe` / `admin123`

---

## ✅ Ce qui doit être installé sur le PC de caisse

### Obligatoire

✅ **Windows 10 ou Windows 11**
- L'application fonctionne uniquement sur Windows

✅ **Espace disque**
- ~300-400 MB pour l'application
- ~50-100 MB pour la base de données (croît avec les données)

### Optionnel (selon matériel)

⚠️ **Pilotes pour scanner de code-barres**
- Si vous utilisez un scanner USB
- Installer les pilotes fournis par le fabricant

⚠️ **Pilotes pour imprimante de tickets**
- Si vous imprimez des tickets
- Installer les pilotes de l'imprimante

### NON requis

❌ **Java JDK ou JRE** - Inclus dans l'installateur!
❌ **Maven** - Non nécessaire pour l'exécution
❌ **MySQL ou autre serveur de base de données** - SQLite est embarqué!
❌ **Configuration réseau** - Application autonome
❌ **Scripts SQL manuels** - Base créée automatiquement

---

## 🗄️ Base de Données SQLite

### Création automatique

La base de données SQLite est créée **automatiquement** au premier lancement :

- **Fichier** : `MarketDB.db`
- **Emplacement** : Dossier d'installation de l'application
  - Par défaut : `C:\Program Files\2M-Market\MarketDB.db`
- **Taille initiale** : ~50-100 KB
- **Croissance** : Selon les données (ventes, produits, etc.)

### Script d'initialisation

Le script SQLite (`init_sqlite.sql`) est inclus dans les ressources de l'application et est exécuté automatiquement par `SQLiteDatabaseSetup.java` au premier démarrage.

**Aucune action manuelle requise!**

### Sauvegarde et restauration

**Pour sauvegarder :**
1. Fermez l'application
2. Copiez le fichier `MarketDB.db` vers un emplacement de sauvegarde

**Pour restaurer :**
1. Fermez l'application
2. Remplacez `MarketDB.db` par votre fichier de sauvegarde
3. Relancez l'application

---

## 📁 Structure après installation

```
C:\Program Files\2M-Market\
├── 2M-Market.exe          ← Application principale
├── app\                   ← Modules de l'application
├── runtime\               ← Java Runtime (inclus)
├── MarketDB.db            ← Base de données SQLite (créée au premier lancement)
└── ...                    ← Autres fichiers système
```

---

## 🚀 Démarrage automatique (Optionnel)

Pour que l'application démarre automatiquement avec Windows :

### Méthode 1 : Dossier de démarrage

1. `Win + R` → `shell:startup`
2. Créer un raccourci vers : `C:\Program Files\2M-Market\2M-Market.exe`

### Méthode 2 : Planificateur de tâches

1. `Win + R` → `taskschd.msc`
2. Créer une tâche de base
3. Déclencheur : Au démarrage de l'ordinateur
4. Action : Démarrer un programme → `C:\Program Files\2M-Market\2M-Market.exe`

---

## 🔧 Dépannage

### L'application ne démarre pas

1. Vérifiez que Windows 10/11 est installé
2. Vérifiez les permissions d'écriture dans `C:\Program Files\2M-Market\`
3. Vérifiez les logs Windows Event Viewer

### La base de données n'est pas créée

1. Vérifiez les permissions d'écriture
2. Vérifiez l'espace disque disponible
3. Relancez l'application (la création peut prendre quelques secondes)

### Erreur "Java not found"

- **Impossible** si vous utilisez l'installateur créé avec jpackage
- Java est inclus dans l'installateur
- Si cette erreur apparaît, réinstallez l'application

---

## 📝 Résumé des fichiers

### Pour créer l'installateur (PC de développement)

- `build-exe.ps1` - Script de build
- `pom.xml` - Configuration Maven
- `src/` - Code source
- `database/init_sqlite.sql` - Script SQL (copié dans les ressources)

### Pour installer (PC de caisse)

- **UNIQUEMENT** : `2M-Market_Setup.exe` (l'installateur)

### Créés automatiquement après installation

- Application dans `C:\Program Files\2M-Market\`
- Base de données `MarketDB.db` (au premier lancement)
- Raccourcis Windows

---

## ✅ Checklist de déploiement

- [ ] Installateur créé avec `build-exe.ps1`
- [ ] Fichier `2M-Market_Setup.exe` copié sur USB
- [ ] Installateur transféré sur PC de caisse
- [ ] Installation effectuée sur PC de caisse
- [ ] Application lancée au moins une fois (création de la base)
- [ ] Test de connexion avec `admin` / `admin123`
- [ ] Test d'une vente
- [ ] Vérification que `MarketDB.db` existe
- [ ] (Optionnel) Démarrage automatique configuré

---

**Version** : 1.0  
**Dernière mise à jour** : 2025-01-28  
**Base de données** : SQLite (créée automatiquement)

