# 🏪 2M Market - Application de Gestion de Stock et Ventes

Application desktop JavaFX autonome pour la gestion de stock, ventes, fournisseurs et employés d'un marché.

## 📋 Table des Matières

- [Vue d'ensemble](#vue-densemble)
- [Fonctionnalités](#fonctionnalités)
- [Technologies](#technologies)
- [Prérequis](#prérequis)
- [Installation](#installation)
- [Utilisation](#utilisation)
- [Création du fichier .exe](#création-du-fichier-exe)
- [Démarrage Automatique Windows](#démarrage-automatique-windows)
- [Structure du Projet](#structure-du-projet)
- [Dépannage](#dépannage)

---

## 🎯 Vue d'ensemble

**2M Market** est une application desktop JavaFX autonome pour la gestion d'un marché, incluant :
- Gestion de stock avec codes-barres
- Point de vente (caisse)
- Gestion des ventes et statistiques
- Gestion des fournisseurs et crédits
- Gestion des utilisateurs (Admin/Employé)
- Rapports et recettes journalières
- Interface plein écran optimisée
- **Base de données SQLite** : Aucun serveur requis, fichier unique portable

---

## ✨ Fonctionnalités

### Interface Administrateur

- **Dashboard** : Vue d'ensemble avec statistiques
  - Nombre total de produits
  - Alertes de rupture de stock
  - Ventes du jour
  - Nombre de fournisseurs
  - Suivi des crédits
  - Paiements du jour
  - Notes journalières

- **Gestion de Stock** : CRUD complet des produits
  - Ajout, modification, suppression
  - Recherche par code-barres ou nom
  - Gestion des catégories
  - Seuils d'alerte

- **Gestion des Ventes** : Visualisation et analyse
  - Historique des ventes
  - Statistiques par produit
  - Filtres par date
  - Export PDF

- **Gestion des Utilisateurs** : Création et gestion
  - Créer/modifier/supprimer utilisateurs
  - Rôles Admin/Employé
  - Sécurité BCrypt

- **Gestion Tabac** : Interface spécialisée pour produits tabac

- **Visualisation Produits** : Vue détaillée de tous les produits

### Interface Employé

- **Point de Vente (Caisse)** : Interface de vente
  - Navigation par catégories
  - Scanner code-barres
  - Gestion du panier
  - Calcul automatique du total
  - Génération de tickets

- **Ajout de Stock** : Ajout de stock avec suivi fournisseur
  - Recherche en temps réel
  - Sélection de fournisseur
  - Gestion des paiements
  - Utilisation de crédit
  - Notes et commentaires

- **Recette du Jour** : Visualisation des recettes quotidiennes

- **Notes Journalières** : Enregistrement de notes et transactions

---

## 🛠️ Technologies

- **Java 17+** : Langage de programmation
- **JavaFX 21** : Framework UI
- **Maven 3.6+** : Gestion des dépendances
- **SQLite 3.44+** : Base de données embarquée (fichier unique, pas de serveur)
- **BCrypt** : Hachage des mots de passe
- **Apache PDFBox 3.0.0** : Génération de PDF

---

## 📦 Prérequis

### Pour le Développement

1. **Java JDK 17 ou supérieur**
   - Télécharger : [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) ou [OpenJDK](https://adoptium.net/)
   - Vérifier : `java -version`

2. **Maven 3.6+**
   - Télécharger : [Apache Maven](https://maven.apache.org/download.cgi)
   - Vérifier : `mvn -version`

### Pour l'Exécution (.exe)

- **Windows 10/11**
- Aucune installation Java requise (inclus dans le .exe)
- **Aucun serveur de base de données requis** (SQLite embarqué)

---

## 🚀 Installation

### 1. Cloner ou Télécharger le Projet

```bash
cd "C:\Users\Mohamed guizeni\Desktop\2M-markettttfinaalll\2M-markettttfinaalll\2M-market-main"
```

### 2. Installer les Dépendances Maven

```powershell
mvn clean install
```

### 3. Lancer l'Application

**Option 1 : Double-cliquer sur `run-app.bat`**

**Option 2 : Ligne de commande**
```powershell
mvn javafx:run
```

**La base de données SQLite sera créée automatiquement** au premier démarrage dans le fichier `MarketDB.db` à la racine du projet.

---

## 💻 Utilisation

### Démarrer l'Application

Double-cliquer sur **`run-app.bat`** ou exécuter :
```powershell
mvn javafx:run
```

### Connexion

**Identifiants par défaut** :
- **Admin** : `admin` / `admin123`
- **Employé** : `employe` / `admin123`

### Interface Plein Écran

- Toutes les interfaces s'affichent en plein écran
- Appuyer sur **Échap** pour quitter le mode plein écran
- Tous les boutons sont visibles et accessibles

### Base de Données SQLite

- **Fichier** : `MarketDB.db` (créé automatiquement)
- **Emplacement** : Même dossier que l'application
- **Portable** : Copiez simplement le fichier `.db` pour sauvegarder/restaurer
- **Aucune configuration requise** : Pas de serveur, pas de port, pas de mot de passe

---

## 📦 Création du fichier .exe

### Méthode Automatique (Recommandée)

1. **Exécuter le script de build** :
   ```powershell
   .\build-exe.ps1
   ```

2. **Le script va** :
   - Vérifier Java et Maven
   - Trouver automatiquement Java si JAVA_HOME n'est pas défini
   - Compiler le projet
   - Créer un JAR avec toutes les dépendances
   - Générer le fichier .exe avec jpackage

3. **Résultat** :
   - Fichier : `dist\2M-Market\2M-Market.exe`
   - Taille : ~100-200 MB (inclut Java runtime)
   - Autonome : Aucune installation Java requise sur le PC cible
   - **Base de données** : Le fichier `MarketDB.db` sera créé dans le même dossier que l'exe

### Déploiement

1. Copier le dossier `dist\2M-Market` sur le PC cible
2. Exécuter `2M-Market.exe`
3. Aucune installation Java ou serveur de base de données requise !
4. La base de données sera créée automatiquement au premier démarrage

---

## 🔄 Démarrage Automatique Windows

### Configuration Automatique

1. **Créer le .exe** (voir section précédente)

2. **Exécuter le script de configuration** (en tant qu'Administrateur) :
   ```powershell
   # Clic droit PowerShell -> Exécuter en tant qu'administrateur
   .\setup-startup.ps1
   ```

3. **Le script configure 3 méthodes** :
   - ✅ Registre Windows (HKCU Run)
   - ✅ Dossier de démarrage
   - ✅ Planificateur de tâches (le plus fiable)

### Configuration Manuelle

#### Option 1 : Registre Windows

1. `Win + R` → `regedit`
2. Naviguer vers : `HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run`
3. Nouveau → Valeur de chaîne
4. Nom : `2M-Market`
5. Valeur : Chemin complet vers `2M-Market.exe` (avec guillemets)

#### Option 2 : Dossier de Démarrage

1. `Win + R` → `shell:startup`
2. Créer un raccourci vers `2M-Market.exe`

#### Option 3 : Planificateur de Tâches (Recommandé)

1. `Win + R` → `taskschd.msc`
2. Créer une tâche de base
3. Nom : `2M-Market Startup`
4. Déclencheur : Au démarrage de l'ordinateur
5. Action : Démarrer un programme → Sélectionner `2M-Market.exe`
6. Options : Exécuter que l'utilisateur soit connecté ou non

### Désactiver le Démarrage Automatique

```powershell
.\setup-startup.ps1 -Remove
```

---

## 📁 Structure du Projet

```
2M-market-main/
├── src/main/java/
│   ├── app/
│   │   └── MainApp.java              # Point d'entrée
│   ├── controller/                    # Contrôleurs
│   ├── dao/                          # DAO (Data Access Objects)
│   ├── model/                         # Modèles
│   ├── util/                          # Utilitaires
│   │   ├── Config.java                # Configuration SQLite
│   │   └── SQLiteDatabaseSetup.java   # Initialisation automatique
│   └── view/
├── src/main/resources/
│   ├── view/                          # FXML
│   ├── styles/                        # CSS
│   ├── database/
│   │   └── init_sqlite.sql           # Schéma SQLite
│   ├── background/                    # Images de fond
│   └── icons/                         # Icônes SVG
├── database/
│   └── init_sqlite.sql                # Script SQLite (référence)
├── build-exe.ps1                      # Script de build .exe
├── setup-startup.ps1                  # Script de démarrage auto
├── run-app.bat                        # Lanceur simple
├── pom.xml                            # Configuration Maven
└── README.md                          # Ce fichier
```

---

## 🔧 Dépannage

### Erreur : "jpackage not found"

**Solution** :
- Vérifier que `JAVA_HOME` pointe vers JDK 17+ (pas JRE)
- Ou laisser le script le trouver automatiquement
- jpackage est inclus dans JDK 17+, pas dans JRE

### Erreur : "Connexion à la base de données échouée"

**Solutions** :
1. Vérifier que le dossier de l'application est accessible en écriture
2. Vérifier les permissions du fichier `MarketDB.db`
3. Supprimer `MarketDB.db` et redémarrer (la base sera recréée)

### Erreur : "Build failed"

**Solutions** :
1. Vérifier que Maven est installé : `mvn -version`
2. Nettoyer et reconstruire : `mvn clean install`
3. Vérifier la connexion Internet (téléchargement des dépendances)

### L'application ne démarre pas en plein écran

**Solution** :
- Les modifications ont été appliquées
- Redémarrer l'application
- Vérifier que la résolution d'écran est correcte

### Le .exe ne démarre pas

**Solutions** :
1. Exécuter depuis la ligne de commande pour voir les erreurs
2. Vérifier que le dossier de l'application est accessible en écriture
3. Vérifier les permissions Windows

### L'application ne démarre pas automatiquement

**Solutions** :
1. Vérifier le chemin dans le registre/dossier de démarrage
2. Utiliser le Planificateur de tâches (plus fiable)
3. Vérifier les logs Windows Event Viewer

### Sauvegarde de la base de données

**Pour sauvegarder** :
- Copier le fichier `MarketDB.db` vers un emplacement de sauvegarde

**Pour restaurer** :
- Remplacer `MarketDB.db` par votre sauvegarde
- Ou supprimer `MarketDB.db` pour repartir à zéro (la base sera recréée)

---

## 📝 Notes Importantes

- **SQLite** : Base de données embarquée, aucun serveur requis
- **Portable** : Copiez simplement le dossier de l'application et `MarketDB.db`
- **Plein Écran** : Toutes les interfaces s'affichent en plein écran
- **Premier Démarrage** : Peut être plus lent (initialisation JVM et création de la base)
- **Taille .exe** : ~100-200 MB (inclut Java runtime)
- **Sécurité** : Mots de passe hachés avec BCrypt
- **Monoposte** : Optimisé pour un seul PC de caisse

---

## 🎯 Checklist de Déploiement

- [ ] Java JDK 17+ installé (pour le développement)
- [ ] Maven 3.6+ installé (pour le développement)
- [ ] Application testée avec `run-app.bat` ou `mvn javafx:run`
- [ ] .exe créé avec `.\build-exe.ps1`
- [ ] .exe testé sur PC cible
- [ ] Base de données `MarketDB.db` créée automatiquement
- [ ] Démarrage automatique configuré (optionnel)
- [ ] Application testée au démarrage Windows
- [ ] Sauvegarde de `MarketDB.db` planifiée (recommandé)

---

## 📞 Support

Pour toute question ou problème :
1. Vérifier la section [Dépannage](#dépannage)
2. Vérifier les logs de l'application (console)
3. Vérifier les permissions du fichier `MarketDB.db`

---

**Version** : 1.0  
**Dernière mise à jour** : 2025-01-28  
**Base de données** : SQLite 3.44+ (embarquée, aucun serveur requis)  
**Java** : 17+  
**JavaFX** : 21
