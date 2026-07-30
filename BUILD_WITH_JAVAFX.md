# Guide de Build avec JavaFX - Solution Complète

## Problème Identifié

Le fichier `.exe` créé par jpackage ne contient pas les DLLs natives JavaFX nécessaires pour afficher l'interface graphique.

## Solution : Utiliser jlink + jpackage avec Modules JavaFX

### Méthode 1 : Build Automatique (Recommandé)

Le script `build-exe.ps1` a été mis à jour pour :
1. Détecter automatiquement les modules JavaFX
2. Utiliser `--module-path` et `--add-modules` avec jpackage
3. Inclure les DLLs JavaFX dans le runtime

**Exécutez simplement :**
```powershell
.\build-exe.ps1
```

Le script va automatiquement :
- Télécharger les dépendances JavaFX si nécessaire
- Trouver les modules JavaFX dans le répertoire Maven
- Inclure les modules avec leurs DLLs natives dans jpackage

### Méthode 2 : Runtime Personnalisé avec jlink (Plus Robuste)

Pour une solution plus robuste qui garantit l'inclusion des DLLs :

**Étape 1 : Créer le runtime personnalisé**
```powershell
.\create-runtime-with-javafx.ps1
```

Ce script va :
- Créer un runtime Java personnalisé avec jlink
- Inclure les modules JavaFX (javafx.controls, javafx.fxml, javafx.graphics, javafx.base)
- Inclure les DLLs natives JavaFX dans le runtime

**Étape 2 : Build avec le runtime personnalisé**
```powershell
.\build-exe.ps1
```

Le script détectera automatiquement le runtime personnalisé et l'utilisera.

## Vérification

Après le build, vérifiez que les DLLs JavaFX sont présentes :

```powershell
Get-ChildItem "dist\2M-Market\runtime\bin\javafx*.dll"
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

## Dépannage

### Si les DLLs ne sont pas trouvées

1. **Vérifier les dépendances Maven :**
   ```powershell
   mvn dependency:copy-dependencies -DoutputDirectory=target/lib
   ```

2. **Vérifier que les JARs JavaFX sont présents :**
   ```powershell
   Get-ChildItem "target\lib\javafx-*.jar"
   ```

3. **Créer manuellement le runtime :**
   ```powershell
   .\create-runtime-with-javafx.ps1
   ```

### Si jpackage échoue avec --module-path

Le problème peut venir de :
- Modules JavaFX non trouvés
- Conflit entre --main-jar et --module-path

**Solution :** Utilisez la méthode 2 (runtime personnalisé avec jlink)

## Structure du Build

```
target/
├── lib/                    # Dépendances (inclut JavaFX JARs)
├── runtime-custom/         # Runtime personnalisé (si créé avec jlink)
└── 2M-market-1.0-SNAPSHOT.jar

dist/
└── 2M-Market/
    ├── 2M-Market.exe
    ├── app/
    │   └── 2M-market-1.0-SNAPSHOT.jar
    └── runtime/
        └── bin/
            ├── java.exe
            └── javafx*.dll  ← DLLs JavaFX (doivent être présentes)
```

## Notes Importantes

1. **JavaFX nécessite des DLLs natives** - Elles ne sont pas dans le JAR, elles doivent être dans le runtime
2. **jpackage avec --module-path** - Inclut les modules mais peut ne pas inclure toutes les DLLs
3. **jlink avec runtime personnalisé** - Solution la plus robuste, garantit l'inclusion de tout

## Test

Après le build, testez avec :
```powershell
.\dist\2M-Market\2M-Market-DEBUG.bat
```

Le script de débogage vérifiera automatiquement la présence des DLLs JavaFX.







