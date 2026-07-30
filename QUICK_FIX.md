# 🔧 Solution Rapide - Corriger et Lancer l'Application

## Problèmes Identifiés

1. ❌ **DLLs JavaFX manquantes** - L'application ne peut pas afficher l'interface graphique
2. ❌ **JAR principal non trouvé** - Le fichier JAR de l'application est manquant
3. ❌ **java.exe manquant dans le runtime** - Le runtime Java n'est pas complet
4. ❌ **Configuration incorrecte** - Le fichier de configuration a des problèmes

## ✅ Solution Automatique (Recommandé)

### Option A : Correction Rapide (si le build existe déjà)

Si vous avez déjà un build mais qu'il ne fonctionne pas :

```powershell
.\fix-all.ps1
```

### Option B : Reconstruction Complète (si le runtime est incomplet)

Si `fix-all.ps1` indique que le runtime est incomplet (java.exe manquant), utilisez ce script qui fait **TOUT** automatiquement :

```powershell
.\rebuild-complete.ps1
```

Ce script va :
1. ✅ Nettoyer tous les fichiers de build
2. ✅ Télécharger les dépendances Maven
3. ✅ Builder l'application JAR
4. ✅ Créer un runtime personnalisé avec JavaFX (plus robuste)
5. ✅ Créer l'installateur/app-image avec jpackage
6. ✅ Corriger les DLLs JavaFX et la configuration
7. ✅ Vérifier que tout fonctionne

**C'est la solution la plus robuste et recommandée si vous avez des problèmes !**

Ce script va :
1. ✅ Télécharger les dépendances Maven si nécessaire (`mvn dependency:copy-dependencies`)
2. ✅ Copier les DLLs JavaFX dans le runtime (cherche dans `target/lib`, Maven repo, et JAR shaded)
3. ✅ Vérifier et copier le JAR principal si manquant
4. ✅ Corriger le fichier de configuration (supprime les doublons, ajoute JavaFX modules)
5. ✅ Vérifier le runtime (java.exe, DLLs, structure)
6. ✅ Afficher un résumé complet avec les solutions si des problèmes persistent

**Important :** Si le script indique que le runtime est incomplet (java.exe manquant), vous devez **reconstruire** l'application :
```powershell
.\clean-dist.ps1
mvn clean package
.\build-exe.ps1
.\fix-all.ps1
```

## 🔍 Solution Manuelle (Si fix-all.ps1 ne fonctionne pas)

### Étape 1 : Télécharger les dépendances Maven

```powershell
mvn dependency:copy-dependencies
```

### Étape 2 : Copier les DLLs JavaFX

```powershell
.\fix-javafx-dlls.ps1
```

### Étape 3 : Vérifier le diagnostic

```powershell
.\diagnose-exe.ps1
```

### Étape 4 : Si les DLLs sont toujours manquantes

1. Téléchargez JavaFX SDK depuis : https://openjfx.io/
2. Extrayez `javafx-sdk-21\bin\*.dll`
3. Copiez les DLLs dans : `dist\2M-Market\runtime\bin\`

## 🚀 Lancer l'Application

Après avoir exécuté `fix-all.ps1`, lancez l'application :

```powershell
.\dist\2M-Market\2M-Market.exe
```

Ou avec le script de débogage :

```powershell
.\dist\2M-Market\2M-Market-DEBUG.bat
```

## 📋 Vérification Rapide

Vérifiez que les DLLs JavaFX sont présentes :

```powershell
Get-ChildItem "dist\2M-Market\runtime\bin\javafx*.dll"
Get-ChildItem "dist\2M-Market\runtime\bin\prism*.dll"
```

Vous devriez voir plusieurs fichiers DLL.

## 🔄 Si Rien Ne Fonctionne

### Option 1 : Reconstruction Complète (Recommandé)

```powershell
# 1. Nettoyer
.\clean-dist.ps1

# 2. Télécharger les dépendances
mvn dependency:copy-dependencies

# 3. Builder l'application
mvn clean package

# 4. Créer l'installateur/app-image
.\build-exe.ps1

# 5. Corriger les problèmes restants
.\fix-all.ps1
```

### Option 2 : Runtime Personnalisé avec JavaFX (Plus Robuste)

Si le runtime standard ne fonctionne pas, créez un runtime personnalisé :

```powershell
# 1. Créer le runtime avec JavaFX
.\create-runtime-with-javafx.ps1

# 2. Builder avec le runtime personnalisé
.\build-exe.ps1

# 3. Vérifier
.\fix-all.ps1
```

### Option 3 : Télécharger JavaFX SDK Manuellement

Si les DLLs ne sont toujours pas trouvées :

1. Téléchargez JavaFX SDK 21 depuis : https://openjfx.io/
2. Extrayez `javafx-sdk-21\bin\*.dll`
3. Copiez toutes les DLLs dans : `dist\2M-Market\runtime\bin\`

## 📞 Support

Si le problème persiste après avoir exécuté `fix-all.ps1`, vérifiez :
- ✅ Java JDK 17+ est installé
- ✅ Maven est installé et configuré
- ✅ Les dépendances JavaFX sont dans `pom.xml`
- ✅ Le build Maven réussit (`mvn clean package`)

