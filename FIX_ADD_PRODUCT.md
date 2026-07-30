# 🔧 Correction : Problème d'Ajout de Produits

## 🐛 Problème Identifié

L'application ne pouvait pas ajouter de produits au stock à cause de plusieurs problèmes dans `ProduitDAO` :

1. **Méthode `columnExists()` incompatible avec SQLite** : Utilisait `INFORMATION_SCHEMA.COLUMNS` (syntaxe MySQL) au lieu de `PRAGMA table_info()` (SQLite)
2. **Gestion d'erreurs insuffisante** : Les erreurs SQL n'étaient pas affichées en détail, rendant le débogage difficile
3. **`category_id` non géré** : La colonne `category_id` (clé étrangère vers `categories`) n'était pas gérée lors de l'insertion

## ✅ Corrections Appliquées

### 1. Correction de `columnExists()` pour SQLite

**Avant** (MySQL) :
```java
String sql = "SELECT COUNT(*) as count FROM INFORMATION_SCHEMA.COLUMNS " +
             "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'produits' AND COLUMN_NAME = ?";
```

**Après** (SQLite) :
```java
ResultSet rs = stmt.executeQuery("PRAGMA table_info(produits)");
while (rs.next()) {
    String name = rs.getString("name");
    if (columnName.equalsIgnoreCase(name)) {
        return true;
    }
}
```

### 2. Amélioration de la gestion des erreurs

Les erreurs SQL sont maintenant affichées avec :
- Message d'erreur complet
- Code SQL state
- Code d'erreur SQLite
- Requête SQL exécutée
- Stack trace complète

### 3. Gestion de `category_id`

La méthode `create()` :
- Détecte automatiquement si la colonne `category_id` existe
- Trouve l'ID de la catégorie par son nom
- Insère `category_id` si disponible, sinon `NULL`

### 4. Ajout de la méthode `findCategoryIdByName()`

Nouvelle méthode pour trouver l'ID d'une catégorie par son nom, permettant de lier correctement les produits aux catégories.

## 🧪 Test des Corrections

### Option 1 : Script de Diagnostic

```powershell
.\test-add-product.ps1
```

Ce script :
- ✅ Vérifie la structure de la base de données
- ✅ Liste les colonnes de la table `produits`
- ✅ Affiche les catégories disponibles
- ✅ Teste l'insertion d'un produit de test
- ✅ Affiche les erreurs détaillées si échec

### Option 2 : Test Manuel

1. **Rebuild l'application** :
   ```powershell
   .\build-with-maven.ps1
   ```

2. **Lancer l'application** :
   ```powershell
   .\setup-and-test.ps1
   ```

3. **Tester l'ajout d'un produit** :
   - Connectez-vous avec `admin` / `admin123`
   - Allez dans "Gestion Stock"
   - Remplissez le formulaire :
     - Code-barres : `1234567890123`
     - Nom : `Produit Test`
     - Catégorie : `Divers` (ou une catégorie existante)
     - Prix d'achat : `10.00`
     - Prix de vente : `15.00`
     - Quantité stock : `100`
     - Seuil alerte : `10`
   - Cliquez sur "Ajouter"

4. **Vérifier les logs** :
   - Ouvrez la console/terminal où l'application a été lancée
   - Cherchez les messages :
     - `SQL: INSERT INTO produits ...`
     - `Produit créé avec succès, ID: X`
     - Ou les erreurs détaillées si échec

## 📋 Vérification de la Base de Données

### Structure Attendue

La table `produits` doit avoir ces colonnes :
- `id` (INTEGER PRIMARY KEY)
- `code_barre` (TEXT NOT NULL UNIQUE)
- `nom` (TEXT NOT NULL)
- `categorie` (TEXT)
- `category_id` (INTEGER, nullable, FK vers `categories.id`)
- `prix_achat_actuel` (REAL NOT NULL)
- `prix_vente_defaut` (REAL NOT NULL)
- `quantite_stock` (INTEGER NOT NULL DEFAULT 0)
- `unite` (TEXT DEFAULT 'unité')
- `seuil_alerte` (INTEGER NOT NULL DEFAULT 10)
- `date_derniere_maj` (DATETIME)

### Catégories par Défaut

Les catégories suivantes sont créées automatiquement :
- Alimentaire
- Boissons
- Tabac
- Hygiène
- Divers

## 🔍 Dépannage

### Problème : "Erreur lors de la création de produit"

**Solution** :
1. Vérifiez les logs de la console pour l'erreur SQL détaillée
2. Exécutez `.\test-add-product.ps1` pour un diagnostic complet
3. Vérifiez que la base de données n'est pas corrompue :
   ```powershell
   # Supprimer et recréer la base de données
   .\setup-and-test.ps1
   # Choisir l'option 1 (Supprimer et recréer)
   ```

### Problème : "Code-barres existant"

**Solution** :
- C'est normal si le code-barres existe déjà
- Utilisez un code-barres différent ou modifiez le produit existant

### Problème : "Contrainte de clé étrangère échoue"

**Solution** :
- Vérifiez que la catégorie existe dans la table `categories`
- Ou utilisez une catégorie existante (Alimentaire, Boissons, etc.)

## ✅ Résultat Attendu

Après les corrections :
- ✅ L'ajout de produits fonctionne correctement
- ✅ Les erreurs SQL sont affichées en détail dans la console
- ✅ Les produits sont correctement liés aux catégories
- ✅ La colonne `unite` est gérée correctement
- ✅ Toutes les fonctionnalités de gestion de stock fonctionnent

## 📝 Notes

- Les corrections sont rétrocompatibles avec les anciennes bases de données
- La méthode `columnExists()` détecte automatiquement les colonnes disponibles
- Les erreurs sont maintenant loggées avec tous les détails nécessaires au débogage

