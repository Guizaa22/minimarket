# Analyse : Pourquoi le Build Réussit mais l'Installation Échoue

## 🔍 Problème Identifié

### Symptômes
1. ✅ **Build Maven réussit** - Le JAR est créé correctement
2. ✅ **Tous les fichiers sont trouvés** - JAR, modules JavaFX, etc.
3. ❌ **jpackage échoue** - "The configured main jar does not exist javafx-base-21-win.jar"
4. ❌ **Cursor se ferme** - Le script fait `exit 1` et ferme la fenêtre

---

## 🐛 Cause Racine

### Problème Principal : Variable Écrasée

**Ligne 249-293** : Le script trouve le JAR de l'application et le stocke dans `$jarFile`
```powershell
$jarFile = $shadedJars | Select-Object -First 1
Write-Host "Using JAR: $($jarFile.Name)"  # ✅ Correct : 2M-market-1.0-SNAPSHOT.jar
```

**Ligne 488-506** : Le script cherche les modules JavaFX et **ÉCRASE** `$jarFile`
```powershell
foreach ($artifact in $javafxArtifacts) {
    $jarFile = Get-ChildItem -Path $artifactPath -Filter "*.jar"  # ❌ ÉCRASE $jarFile !
    # Maintenant $jarFile pointe vers javafx-base-21-win.jar au lieu de 2M-market-1.0-SNAPSHOT.jar
}
```

**Ligne 556/591** : jpackage utilise le mauvais JAR
```powershell
"--main-jar", $jarFile.Name  # ❌ Utilise javafx-base-21-win.jar au lieu de 2M-market-1.0-SNAPSHOT.jar
```

### Résultat
jpackage cherche `javafx-base-21-win.jar` dans `target/` comme JAR principal, mais :
- Ce JAR n'est **pas** dans `target/` (il est dans `.m2/repository/`)
- Ce n'est **pas** le JAR de l'application
- jpackage échoue avec : "The configured main jar does not exist"

---

## 🔧 Solution Appliquée

### Correction 1 : Sauvegarder le JAR de l'Application
```powershell
# Ligne 293 (après avoir trouvé le JAR)
$appJarFile = $jarFile  # ✅ Sauvegarder AVANT la recherche JavaFX
```

### Correction 2 : Utiliser la Variable Sauvegardée
```powershell
# Ligne 559/591
"--main-jar", $appJarFile.Name  # ✅ Utilise toujours le bon JAR
```

### Correction 3 : Messages de Débogage
```powershell
Write-Host "  --main-jar: $($appJarFile.Name)"  # Affiche le JAR utilisé
```

---

## 📊 Flux d'Exécution (Avant Correction)

```
1. [OK] Maven build → 2M-market-1.0-SNAPSHOT.jar créé
2. [OK] Script trouve JAR → $jarFile = "2M-market-1.0-SNAPSHOT.jar"
3. [OK] Script cherche modules JavaFX
4. [❌] Boucle JavaFX ÉCRASE $jarFile → $jarFile = "javafx-base-21-win.jar"
5. [❌] jpackage utilise $jarFile.Name → cherche "javafx-base-21-win.jar" dans target/
6. [❌] jpackage échoue → "main jar does not exist"
7. [❌] Script fait exit 1 → Cursor se ferme
```

---

## 📊 Flux d'Exécution (Après Correction)

```
1. [OK] Maven build → 2M-market-1.0-SNAPSHOT.jar créé
2. [OK] Script trouve JAR → $jarFile = "2M-market-1.0-SNAPSHOT.jar"
3. [OK] Script sauvegarde → $appJarFile = $jarFile
4. [OK] Script cherche modules JavaFX → $jarFile peut être écrasé (pas grave)
5. [OK] jpackage utilise $appJarFile.Name → cherche "2M-market-1.0-SNAPSHOT.jar" dans target/
6. [OK] jpackage trouve le JAR → création réussie
7. [OK] Script continue → affiche le succès
```

---

## 🛡️ Gestion d'Erreurs Améliorée

### Problème Secondaire : Cursor se Ferme

Quand jpackage échoue, le script fait :
```powershell
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Failed to create installer!" -ForegroundColor Red
    exit 1  # ❌ Ferme Cursor si exécuté dans un contexte sensible
}
```

### Solution : Meilleure Gestion d'Erreurs

1. **Afficher les détails de l'erreur jpackage**
2. **Ne pas fermer immédiatement** - laisser l'utilisateur voir l'erreur
3. **Suggérer des solutions** basées sur l'erreur spécifique

---

## 🔍 Points de Vérification

### Avant de Builder
- [ ] Le JAR est créé dans `target/` (vérifier avec `ls target/*.jar`)
- [ ] Le JAR shaded existe (2M-market-1.0-SNAPSHOT.jar, ~26 MB)
- [ ] Les modules JavaFX sont téléchargés (vérifier `.m2/repository/org/openjfx/`)

### Pendant le Build
- [ ] Vérifier le message "Using JAR: 2M-market-1.0-SNAPSHOT.jar"
- [ ] Vérifier le message "--main-jar: 2M-market-1.0-SNAPSHOT.jar"
- [ ] Si vous voyez "javafx-base-21-win.jar" comme main-jar → **ERREUR**

### Après le Build
- [ ] Vérifier que `dist/2M-Market/` existe (app-image)
- [ ] Vérifier que `dist/*.exe` existe (installer)
- [ ] Vérifier que `dist/2M-Market/app/2M-market-1.0-SNAPSHOT.jar` existe

---

## 🚨 Erreurs Communes et Solutions

### Erreur 1 : "main jar does not exist javafx-base-21-win.jar"
**Cause** : Variable `$jarFile` écrasée par la boucle JavaFX  
**Solution** : ✅ **CORRIGÉ** - Utiliser `$appJarFile` au lieu de `$jarFile`

### Erreur 2 : "JavaFX runtime components are missing"
**Cause** : DLLs JavaFX natives non incluses  
**Solution** : Exécuter `.\create-runtime-with-javafx.ps1` avant `.\build-exe.ps1`

### Erreur 3 : "Cannot remove item dist: being used by another process"
**Cause** : Dossier `dist` verrouillé (explorateur de fichiers ou application en cours)  
**Solution** : ✅ **CORRIGÉ** - Script ferme automatiquement les processus et réessaie

### Erreur 4 : Cursor se ferme après erreur
**Cause** : `exit 1` ferme la fenêtre  
**Solution** : Améliorer la gestion d'erreurs pour afficher les détails avant de quitter

---

## ✅ Vérification Post-Correction

Après les corrections, le script devrait :
1. ✅ Utiliser le bon JAR (`2M-market-1.0-SNAPSHOT.jar`)
2. ✅ Afficher clairement quel JAR est utilisé
3. ✅ Gérer les erreurs sans fermer Cursor immédiatement
4. ✅ Fournir des messages d'erreur utiles

---

## 📝 Commandes de Test

```powershell
# 1. Nettoyer
.\clean-dist.ps1

# 2. Builder
.\build-exe.ps1

# 3. Vérifier le JAR utilisé (dans la sortie)
# Devrait afficher : "--main-jar: 2M-market-1.0-SNAPSHOT.jar"

# 4. Vérifier le résultat
ls dist\2M-Market\app\*.jar
# Devrait afficher : 2M-market-1.0-SNAPSHOT.jar
```

---

## 🎯 Résumé

**Le problème était** : Une variable PowerShell (`$jarFile`) était écrasée lors de la recherche des modules JavaFX, causant l'utilisation du mauvais JAR par jpackage.

**La solution** : Sauvegarder le JAR de l'application dans une variable séparée (`$appJarFile`) avant la recherche JavaFX, et utiliser cette variable pour jpackage.

**Résultat attendu** : Le build devrait maintenant réussir complètement, avec jpackage utilisant le bon JAR.





