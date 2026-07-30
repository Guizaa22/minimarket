# 🔧 Correction Rapide - Problème de Connexion

## Problème
Vous ne pouvez pas vous connecter avec `admin` / `admin123` ou `employe` / `admin123`.

## Solution Rapide (Recommandée)

### Option 1 : Script Automatique

Exécutez simplement :

```powershell
.\fix-login.ps1
```

Le script va :
1. Vous demander où se trouve la base de données
2. Compiler `FixUsersSQLite.java` si nécessaire
3. Réinitialiser les utilisateurs `admin` et `employe` avec le mot de passe `admin123`
4. Tester l'authentification pour vérifier que ça fonctionne

### Option 2 : Supprimer la Base de Données

Si vous n'avez pas de données importantes à conserver :

1. **Fermez l'application** si elle est ouverte
2. **Trouvez le fichier `MarketDB.db`** :
   - Pour l'application déployée : `target\installer\2M-Market-App\MarketDB.db`
   - Pour le développement : `2M-market-main\MarketDB.db`
3. **Supprimez le fichier `MarketDB.db`**
4. **Relancez l'application**
5. La base de données sera recréée automatiquement avec les utilisateurs par défaut

### Option 3 : Import de l'Ancienne Base de Données

Si vous avez une ancienne base de données avec vos utilisateurs modifiés :

```powershell
.\import-database.ps1
```

Puis :

```powershell
.\fix-login.ps1
```

## Identifiants par Défaut

Après la correction, utilisez :

- **Admin** : `admin` / `admin123`
- **Employé** : `employe` / `admin123`

## Vérification

Après avoir exécuté `fix-login.ps1`, le script affichera :
- ✓ Authentification 'admin' / 'admin123' : RÉUSSIE
- ✓ Authentification 'employe' / 'admin123' : RÉUSSIE

Si vous voyez ces messages, vous pouvez vous connecter !

## Si le Problème Persiste

1. Vérifiez que la base de données existe : `MarketDB.db` dans le bon dossier
2. Vérifiez les permissions : l'application doit pouvoir lire/écrire le fichier
3. Vérifiez les logs : regardez la console pour des erreurs SQL

