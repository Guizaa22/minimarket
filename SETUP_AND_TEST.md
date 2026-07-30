# Guide : Setup & Test - 2M Market

## 🎯 Objectif

Ce guide vous aide à configurer la base de données et tester l'application après le build.

## 📋 Prérequis

1. ✅ Application construite avec `.\build-with-maven.ps1`
2. ✅ Java JDK 17+ installé
3. ✅ Base de données SQLite (sera créée automatiquement)

## 🚀 Utilisation Rapide

### Option 1 : Script Automatique (Recommandé)

```powershell
.\setup-and-test.ps1
```

Le script va :
1. ✅ Trouver automatiquement l'application déployée
2. ✅ Analyser la base de données existante
3. ✅ Vous proposer de supprimer/recréer la base de données
4. ✅ Lancer l'application pour test

### Option 2 : Correction Manuelle

Si vous avez des problèmes de connexion :

```powershell
# Corriger les utilisateurs
.\fix-login.ps1

# OU importer votre ancienne base de données
.\import-database.ps1
```

## 🔐 Identifiants par Défaut

Après la création/réinitialisation de la base de données, utilisez :

| Username | Password | Rôle |
|----------|----------|------|
| `admin` | `admin123` | Admin |
| `employe` | `admin123` | Employé |

## 📍 Emplacements de la Base de Données

La base de données `MarketDB.db` est créée dans :

- **Application déployée** : `target\installer\2M-Market-App\MarketDB.db`
- **Mode développement** : `.\MarketDB.db` (dossier du projet)

## 🔧 Dépannage

### Problème : "Base de données verrouillée"

**Solution** :
1. Fermez l'application si elle est ouverte
2. Attendez quelques secondes
3. Réessayez `.\setup-and-test.ps1`

### Problème : "Impossible de se connecter"

**Solution** :
1. Exécutez `.\fix-login.ps1`
2. Choisissez l'option 1 (application déployée) ou 2 (développement)
3. Les utilisateurs seront réinitialisés

### Problème : "Application non trouvée"

**Solution** :
1. Exécutez d'abord `.\build-with-maven.ps1`
2. Attendez la fin du build
3. Réessayez `.\setup-and-test.ps1`

## 📝 Notes

- La base de données est créée **automatiquement** au premier lancement de l'application
- Les utilisateurs par défaut sont créés avec le script SQL `init_sqlite.sql`
- Le mot de passe `admin123` est hashé avec BCrypt
- Vous pouvez importer votre ancienne base de données avec `.\import-database.ps1`

## ✅ Vérification

Après le setup, l'application devrait :
1. ✅ S'ouvrir automatiquement
2. ✅ Afficher l'écran de connexion
3. ✅ Accepter les identifiants `admin` / `admin123`

Si tout fonctionne, vous pouvez commencer à utiliser l'application ! 🎉

