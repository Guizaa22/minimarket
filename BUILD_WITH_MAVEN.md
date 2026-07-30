# Guide de Build avec Maven - Solution Robuste

## Problème Identifié

Les scripts PowerShell peuvent échouer ou créer des runtimes incomplets. Cette solution utilise uniquement Maven et le plugin JavaFX pour créer un installateur Windows complet avec toutes les DLLs JavaFX incluses.

## Prérequis (Vérification Finale)

### 1. Variable JAVA_HOME

Assurez-vous que `JAVA_HOME` est définie en **Système** et qu'elle pointe vers la racine de votre JDK :
- ✅ **Correct** : `C:\Program Files\Java\jdk-21` (SANS le `\bin`)
- ❌ **Incorrect** : `C:\Program Files\Java\jdk-21\bin`

**Pour vérifier :**
```powershell
echo $env:JAVA_HOME
```

**Pour définir (si nécessaire) :**
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
```

### 2. Nouvelle Fenêtre PowerShell

Fermez toutes les fenêtres PowerShell et ouvrez-en une **nouvelle** pour prendre en compte les variables d'environnement.

### 3. Position

Vous devez être dans le dossier racine de votre projet (`2M-market-main`).

## ÉTAPE 1 : Nettoyage Complet (Mandatoire)

Supprimez tous les fichiers générés par les scripts précédents :

```powershell
mvn clean
```

Cela supprime le dossier `target/` et tous les fichiers de build.

## ÉTAPE 2 : Configuration du pom.xml

Le `pom.xml` a été mis à jour avec la configuration complète pour `jlink` et `jpackage`. La configuration inclut :

- ✅ Classe principale : `app.MainApp`
- ✅ Goals : `jlink` puis `jpackage`
- ✅ Modules JavaFX : `javafx.controls`, `javafx.fxml`, `javafx.graphics`, `javafx.base`
- ✅ Modules Java nécessaires : `java.sql`, `java.desktop`, `java.xml`, `jdk.unsupported`
- ✅ Compression et optimisation du runtime

**Aucune modification manuelle nécessaire** - le fichier est déjà configuré.

## ÉTAPE 3 : Exécuter la Commande de Packaging Complète

Une fois dans le dossier du projet avec `JAVA_HOME` correctement défini, exécutez :

```powershell
mvn clean package javafx:jpackage
```

### Explication de la commande :

1. **`mvn clean`** : Nettoie tous les fichiers de build précédents
2. **`package`** : Compile le code et crée le JAR (`target/2M-market-1.0-SNAPSHOT.jar`)
3. **`javafx:jpackage`** : Lance l'exécution des goals `jlink` (qui inclut les DLLs) et `jpackage` (qui crée l'installeur)

### Ce qui se passe :

1. **jlink** crée un runtime personnalisé avec :
   - Les modules JavaFX (incluant les DLLs natives)
   - Les modules Java nécessaires
   - Optimisation et compression

2. **jpackage** crée l'installateur Windows avec :
   - Le runtime personnalisé (incluant `java.exe` et les DLLs)
   - L'application JAR
   - Un fichier `.exe` d'installation

## Le Résultat Attendu

Si cette commande est lancée avec succès, vous trouverez le fichier final :

- **Fichier** : `2M-Market-App.exe` (ou `2M-Market-App_Setup.exe`)
- **Emplacement** : Dans le dossier `target/installer/` ou `target/jpackage/`
- **Taille** : Le fichier EXE devrait faire entre **80 Mo et 150 Mo**, car il contient :
  - Le JRE minimal (créé par jlink)
  - Les DLLs JavaFX natives
  - L'application JAR

## Déploiement sur le PC de Caisse

1. **Copier** le fichier `.exe` sur le PC de caisse
2. **Double-cliquer** sur le fichier pour lancer l'installation
3. **Installer** l'application (suivre l'assistant d'installation)
4. **Lancer** l'application depuis le menu Démarrer ou le raccourci créé

**Aucune installation de Java nécessaire** sur le PC de caisse - tout est inclus dans l'installateur !

## Dépannage

### Si la commande échoue avec "JAVA_HOME not set"

```powershell
# Vérifier JAVA_HOME
echo $env:JAVA_HOME

# Si vide, définir (remplacez par votre chemin JDK)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"

# Vérifier que jpackage existe
Test-Path "$env:JAVA_HOME\bin\jpackage.exe"
```

### Si jlink échoue avec "Module not found"

Vérifiez que les dépendances JavaFX sont bien téléchargées :
```powershell
mvn dependency:resolve
```

### Si jpackage échoue avec "WiX Toolset not found"

Le plugin JavaFX peut créer un `app-image` au lieu d'un `.exe` si WiX n'est pas installé. C'est acceptable - vous pouvez utiliser le dossier `app-image` directement.

## Avantages de cette Méthode

✅ **Robuste** : Utilise uniquement Maven, pas de scripts PowerShell complexes  
✅ **Standard** : Configuration standard du plugin JavaFX  
✅ **Complet** : Inclut automatiquement toutes les DLLs JavaFX via jlink  
✅ **Portable** : L'installateur contient tout (Java + Application)  
✅ **Maintenable** : Configuration dans `pom.xml`, facile à modifier

## Comparaison avec les Scripts PowerShell

| Aspect | Scripts PowerShell | Maven Plugin |
|--------|-------------------|--------------|
| Complexité | Élevée | Faible |
| Dépendances | Java, Maven, WiX, Scripts | Java, Maven uniquement |
| Fiabilité | Peut échouer silencieusement | Erreurs claires |
| Maintenance | Plusieurs scripts | Un seul `pom.xml` |
| Standard | Non | Oui (plugin officiel) |

## Conclusion

Cette méthode est **recommandée** car elle est plus simple, plus robuste et plus standard. Utilisez-la pour créer vos installateurs Windows.

