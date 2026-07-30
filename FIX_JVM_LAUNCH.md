# Solution : Erreur "Failed to Launch JVM"

## 🔍 Diagnostic

Si vous obtenez l'erreur **"failed to launch JVM"** lors de l'exécution de `2M-Market.exe`, cela signifie généralement que :

1. **Les DLLs JavaFX natives sont manquantes** dans le runtime
2. **Le fichier de configuration est incorrect**
3. **Le runtime Java est incomplet**

## ✅ Solution Rapide (Recommandée)

### Option 1 : Corriger l'application déjà construite

```powershell
# 1. Diagnostiquer le problème
.\diagnose-exe.ps1

# 2. Corriger les DLLs manquantes et la configuration
.\fix-jvm-launch.ps1

# 3. Tester à nouveau
.\dist\2M-Market\2M-Market.exe
```

### Option 2 : Reconstruire avec un runtime personnalisé (Meilleure solution)

```powershell
# 1. Créer un runtime personnalisé avec JavaFX
.\create-runtime-with-javafx.ps1

# 2. Reconstruire l'application
.\build-exe.ps1

# 3. Tester
.\dist\2M-Market\2M-Market.exe
```

## 🔧 Détails des Scripts

### `diagnose-exe.ps1`
- Vérifie que l'exécutable existe
- Vérifie le fichier de configuration (`.cfg`)
- Vérifie le runtime Java
- Vérifie la présence des DLLs JavaFX
- Vérifie les modules JavaFX
- Teste le lancement direct avec `java.exe`

### `fix-jvm-launch.ps1`
- Copie les DLLs JavaFX manquantes dans le runtime
- Corrige le fichier de configuration (supprime les doublons)
- Vérifie que `java.exe` fonctionne

### `create-runtime-with-javafx.ps1`
- Crée un runtime Java personnalisé avec `jlink`
- Inclut explicitement les modules JavaFX
- Copie les DLLs JavaFX natives dans le runtime
- Produit un runtime complet et autonome

## 📋 Vérifications Manuelles

### 1. Vérifier les DLLs JavaFX

```powershell
Get-ChildItem "dist\2M-Market\runtime\bin\javafx*.dll"
Get-ChildItem "dist\2M-Market\runtime\bin\prism*.dll"
```

Vous devriez voir des fichiers comme :
- `javafx_font.dll`
- `javafx_font_t2k.dll`
- `javafx_iio.dll`
- `javafx_media.dll`
- `javafx_swing.dll`
- `javafx_web.dll`
- `prism_common.dll`
- `prism_d3d.dll`
- `prism_es2.dll`
- `prism_sw.dll`

### 2. Vérifier le fichier de configuration

```powershell
Get-Content "dist\2M-Market\app\2M-Market.cfg"
```

Le fichier devrait contenir :
- Une seule ligne `app.classpath=` (pas de doublons)
- Une ligne `app.mainclass=app.MainApp`
- Les options Java appropriées

### 3. Tester le runtime Java directement

```powershell
cd dist\2M-Market
.\runtime\bin\java.exe -version
```

Cela devrait afficher la version de Java.

## 🚨 Problèmes Courants

### Problème 1 : Aucune DLL JavaFX trouvée

**Solution :**
```powershell
# Télécharger les dépendances
mvn dependency:copy-dependencies

# Puis corriger
.\fix-jvm-launch.ps1
```

### Problème 2 : Fichier de configuration avec doublons

**Solution :**
```powershell
# Le script fix-jvm-launch.ps1 corrige automatiquement
.\fix-jvm-launch.ps1
```

### Problème 3 : Runtime Java incomplet

**Solution :**
```powershell
# Créer un runtime personnalisé
.\create-runtime-with-javafx.ps1

# Reconstruire
.\build-exe.ps1
```

## 📝 Notes

- **Runtime personnalisé** : La meilleure solution est de créer un runtime personnalisé avec `jlink` qui inclut explicitement JavaFX. Cela garantit que toutes les DLLs sont présentes.

- **DLLs manquantes** : Si les DLLs ne sont pas copiées automatiquement, le script `fix-jvm-launch.ps1` les copiera depuis le repository Maven.

- **Configuration** : Le fichier `.cfg` peut avoir des doublons si `jpackage` a été exécuté plusieurs fois. Le script de correction les supprime automatiquement.

## 🎯 Workflow Recommandé

Pour éviter ce problème à l'avenir :

1. **Toujours créer un runtime personnalisé avant de builder :**
   ```powershell
   .\create-runtime-with-javafx.ps1
   ```

2. **Puis builder :**
   ```powershell
   .\build-exe.ps1
   ```

3. **Tester immédiatement :**
   ```powershell
   .\dist\2M-Market\2M-Market.exe
   ```

Si cela ne fonctionne toujours pas, exécutez `.\diagnose-exe.ps1` pour obtenir des informations détaillées.


