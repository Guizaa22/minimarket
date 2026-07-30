# 🔧 Correction : Erreur lors de l'ajout de produit

## 🐛 Problème

Vous voyez l'erreur **"Erreur lors de l'ajout du produit"** dans l'interface.

## 🔍 Diagnostic

Pour identifier l'erreur exacte, exécutez le script de diagnostic :

```powershell
.\diagnose-product-error.ps1
```

Ce script va :
- ✅ Vérifier la structure de la table `produits`
- ✅ Tester la détection de colonnes (`unite`, `category_id`)
- ✅ Construire la requête SQL exacte
- ✅ Tester l'insertion d'un produit
- ✅ Afficher l'erreur SQL exacte si elle se produit

## 🔧 Correction Automatique

Pour corriger automatiquement les colonnes manquantes :

```powershell
.\fix-database-columns.ps1
```

Ce script va :
- ✅ Vérifier que toutes les colonnes existent (`unite`, `category_id`)
- ✅ Ajouter les colonnes manquantes si nécessaire
- ✅ Tester l'insertion d'un produit
- ✅ Confirmer que tout fonctionne

## 📋 Causes Possibles

### 1. Colonnes manquantes dans la table `produits`

Si la base de données a été créée avec une ancienne version, il peut manquer :
- `unite` (TEXT, DEFAULT 'unité')
- `category_id` (INTEGER, clé étrangère vers `categories`)

**Solution** : Exécutez `.\fix-database-columns.ps1`

### 2. Table `categories` manquante

Si la table `categories` n'existe pas, les clés étrangères peuvent échouer.

**Solution** : Relancez l'application pour que `SQLiteDatabaseSetup` crée toutes les tables.

### 3. Contrainte de clé étrangère

Si `category_id` référence une catégorie qui n'existe pas.

**Solution** : Assurez-vous que les catégories par défaut sont créées.

### 4. Code-barres dupliqué

Si le code-barres existe déjà dans la base.

**Solution** : Utilisez un code-barres unique.

## ✅ Vérification

Après correction, vérifiez que :

1. **Toutes les tables existent** :
   ```powershell
   .\fix-database-complete.ps1
   ```

2. **Toutes les colonnes existent** :
   ```powershell
   .\fix-database-columns.ps1
   ```

3. **L'application fonctionne** :
   ```powershell
   .\build-and-run.ps1
   ```

## 🚀 Solution Rapide

Si vous voulez tout corriger d'un coup :

```powershell
# 1. Corriger toutes les tables
.\fix-database-complete.ps1

# 2. Corriger les colonnes
.\fix-database-columns.ps1

# 3. Rebuild et lancer
.\build-and-run.ps1
```

## 📝 Notes

- Les erreurs SQL sont affichées dans la **console** (pas dans l'interface)
- Vérifiez toujours la console pour les détails de l'erreur
- La base de données est dans `MarketDB.db` dans le dossier de l'application
- Les données sont sauvegardées automatiquement

## 🔍 Vérification Manuelle

Si vous voulez vérifier manuellement la structure de la table :

1. Ouvrez `MarketDB.db` avec un outil SQLite (DB Browser for SQLite, etc.)
2. Vérifiez que la table `produits` contient :
   - `id` (INTEGER PRIMARY KEY)
   - `code_barre` (TEXT NOT NULL UNIQUE)
   - `nom` (TEXT NOT NULL)
   - `categorie` (TEXT)
   - `category_id` (INTEGER)
   - `prix_achat_actuel` (REAL NOT NULL)
   - `prix_vente_defaut` (REAL NOT NULL)
   - `quantite_stock` (INTEGER NOT NULL)
   - `unite` (TEXT DEFAULT 'unité')
   - `seuil_alerte` (INTEGER NOT NULL)
   - `date_derniere_maj` (DATETIME)

Si des colonnes manquent, exécutez `.\fix-database-columns.ps1`.

