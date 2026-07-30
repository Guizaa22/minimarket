# Guide de Débogage - 2M Market

## Fichiers de Lancement

L'application inclut deux fichiers batch pour le lancement :

### 1. `2M-Market.bat` (Mode Normal avec Détails)
- Affiche les informations de démarrage
- Vérifie les fichiers nécessaires
- Affiche les erreurs si l'application échoue
- **Utilisez ce fichier pour voir les erreurs de base**

### 2. `2M-Market-DEBUG.bat` (Mode Débogage Complet)
- Vérifie tous les fichiers et composants
- Crée un fichier log détaillé (`app-debug.log`)
- Affiche toutes les erreurs
- Ouvre automatiquement le log en cas d'erreur
- **Utilisez ce fichier pour un débogage approfondi**

## Utilisation

### Sur le PC de développement :
```batch
cd dist\2M-Market
2M-Market-DEBUG.bat
```

### Sur le PC de caisse :
1. Copiez le dossier `2M-Market` complet
2. Double-cliquez sur `2M-Market-DEBUG.bat`
3. Si l'application échoue, le fichier `app-debug.log` sera créé
4. Ouvrez `app-debug.log` pour voir les détails de l'erreur

## Erreurs Communes

### "JavaFX runtime components are missing"
**Cause** : Les DLLs JavaFX ne sont pas présentes dans `runtime\bin\`

**Solution** :
1. Exécutez `fix-and-rebuild.ps1` sur le PC de développement
2. Ou téléchargez JavaFX SDK et copiez les DLLs manuellement

### "Application JAR not found"
**Cause** : Le fichier JAR est manquant ou corrompu

**Solution** : Reconstruisez l'application avec `build-exe.ps1`

### "Exit code: 1"
**Cause** : Erreur générale - consultez le log pour les détails

**Solution** : Ouvrez `app-debug.log` pour voir l'erreur exacte

## Vérifications Automatiques

Les scripts .bat vérifient automatiquement :
- ✅ Présence de `2M-Market.exe`
- ✅ Présence du JAR application
- ✅ Présence du runtime Java
- ✅ Présence des DLLs JavaFX
- ✅ Fichier de configuration

## Logs

Le fichier `app-debug.log` contient :
- Date et heure du lancement
- Informations système
- Vérifications des fichiers
- Sortie complète de l'application
- Code de sortie

## Support

Si l'application ne fonctionne pas :
1. Exécutez `2M-Market-DEBUG.bat`
2. Copiez le contenu de `app-debug.log`
3. Vérifiez les erreurs spécifiques
4. Consultez ce guide pour les solutions

