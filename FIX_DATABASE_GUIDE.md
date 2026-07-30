# 🔧 Guide : Correction Complète de la Base de Données

## 🎯 Objectif

Ce guide explique comment diagnostiquer et corriger tous les problèmes de base de données pour que toutes les fonctionnalités fonctionnent correctement.

## 🐛 Problèmes Courants

1. **Tables manquantes** : Certaines tables n'existent pas
2. **Utilisateurs manquants** : Les utilisateurs admin/employe n'existent pas
3. **Colonnes manquantes** : Colonnes comme `unite` ou `category_id` manquantes
4. **Données par défaut manquantes** : Catégories, fournisseurs, etc.
5. **Contraintes de clés étrangères** : Problèmes avec les relations entre tables

## ✅ Solution Automatique

### Script de Diagnostic et Correction

Exécutez simplement :

```powershell
.\fix-database-complete.ps1
```

Ce script va :
1. ✅ Vérifier toutes les tables requises
2. ✅ Créer les tables manquantes
3. ✅ Vérifier et créer les utilisateurs par défaut
4. ✅ Vérifier et créer les catégories par défaut
5. ✅ Vérifier et créer les fournisseurs par défaut
6. ✅ Vérifier et ajouter les colonnes manquantes dans `produits`
7. ✅ Tester l'insertion de produits

## 📋 Tables Requises

L'application nécessite ces tables :

1. **utilisateurs** - Utilisateurs de l'application
2. **categories** - Catégories de produits
3. **produits** - Produits en stock
4. **ventes** - Ventes effectuées
5. **detailsvente** - Détails des ventes
6. **fournisseurs** - Fournisseurs
7. **credits_fournisseur** - Crédits des fournisseurs
8. **paiements_fournisseur** - Paiements aux fournisseurs
9. **ajouts_stock** - Historique des ajouts de stock
10. **deplacements_employe** - Déplacements des employés
11. **notes_jour** - Notes journalières

## 🔐 Utilisateurs par Défaut

Après la correction, ces utilisateurs seront créés :

| Username | Password | Rôle |
|----------|----------|------|
| `admin` | `admin123` | Admin |
| `employe` | `admin123` | Employé |

## 📦 Données par Défaut

### Catégories

- Alimentaire
- Boissons
- Tabac
- Hygiène
- Divers

### Fournisseurs

- Fournisseur Principal
- Grossiste Alimentaire
- Distributeur Tabac

## 🔍 Vérifications Effectuées

Le script vérifie :

1. ✅ **Existence des tables** : Toutes les tables requises existent
2. ✅ **Structure des tables** : Colonnes requises présentes
3. ✅ **Utilisateurs** : Admin et employe existent
4. ✅ **Données par défaut** : Catégories et fournisseurs créés
5. ✅ **Fonctionnalité d'insertion** : Test d'ajout de produit

## 🚀 Après la Correction

Une fois le script exécuté avec succès :

1. **Relancez l'application** :
   ```powershell
   .\build-and-run.ps1
   ```

2. **Connectez-vous** avec `admin` / `admin123`

3. **Testez toutes les fonctionnalités** :
   - ✅ Ajout de produits
   - ✅ Gestion de stock
   - ✅ Ventes
   - ✅ Fournisseurs
   - ✅ Etc.

## 📝 Notes

- Le script est **non-destructif** : Il ne supprime pas les données existantes
- Il utilise `INSERT OR IGNORE` et `INSERT OR REPLACE` pour éviter les doublons
- Les colonnes manquantes sont ajoutées avec `ALTER TABLE`
- Les utilisateurs sont créés avec le hash BCrypt correct

## 🔧 Correction Manuelle (si nécessaire)

Si le script automatique ne fonctionne pas, vous pouvez :

1. **Supprimer et recréer la base de données** :
   ```powershell
   # Supprimer l'ancienne base
   Remove-Item -Path "MarketDB.db" -Force
   
   # Lancer l'application (elle créera automatiquement la base)
   .\build-and-run.ps1
   ```

2. **Utiliser fix-login.ps1** pour corriger les utilisateurs :
   ```powershell
   .\fix-login.ps1
   ```

## ✅ Vérification Finale

Après la correction, vérifiez que :

- ✅ Vous pouvez vous connecter avec `admin` / `admin123`
- ✅ Vous pouvez ajouter des produits
- ✅ Vous pouvez voir la liste des produits
- ✅ Vous pouvez créer des ventes
- ✅ Toutes les fonctionnalités fonctionnent

Si tout fonctionne, la base de données est correctement configurée ! 🎉

