# 📥 Installation de Java JDK 17+ pour créer l'installateur

## ✅ Le script fonctionne maintenant !

Le script `build-exe.ps1` fonctionne correctement, mais vous devez installer **Java JDK 17 ou supérieur** pour créer l'installateur Windows.

---

## 🔽 Télécharger et Installer Java JDK

### Option 1 : Eclipse Adoptium (Recommandé - Open Source)

1. **Télécharger** :
   - Allez sur : https://adoptium.net/
   - Cliquez sur "Latest LTS Release" (JDK 17 ou 21)
   - Choisissez :
     - **Version** : 17 ou 21 (LTS)
     - **Operating System** : Windows
     - **Architecture** : x64
     - **Package Type** : JDK
   - Cliquez sur "Download"

2. **Installer** :
   - Exécutez le fichier `.msi` téléchargé
   - Suivez l'assistant d'installation
   - **Important** : Cochez "Add to PATH" pendant l'installation
   - Installation par défaut : `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot\`

3. **Vérifier** :
   ```powershell
   java -version
   ```
   Vous devriez voir quelque chose comme :
   ```
   openjdk version "17.0.x" ...
   ```

### Option 2 : Oracle JDK

1. **Télécharger** :
   - Allez sur : https://www.oracle.com/java/technologies/downloads/
   - Choisissez Java 17 ou 21
   - Téléchargez le Windows x64 Installer

2. **Installer** :
   - Exécutez l'installateur
   - Installation par défaut : `C:\Program Files\Java\jdk-17\`

---

## 🔧 Configuration après Installation

### Si Java n'est pas dans le PATH :

1. **Trouver le chemin d'installation** :
   - Généralement : `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot\`
   - Ou : `C:\Program Files\Java\jdk-17\`

2. **Ajouter au PATH** (optionnel, le script le trouve automatiquement) :
   - Win + R → `sysdm.cpl` → Onglet "Avancé" → Variables d'environnement
   - Ajouter `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot\bin` au PATH

3. **Ou définir JAVA_HOME** :
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot"
   ```

---

## ✅ Vérification

Après l'installation, testez :

```powershell
java -version
```

Vous devriez voir la version de Java.

Ensuite, exécutez :

```powershell
.\build-exe.ps1
```

Le script devrait maintenant trouver Java automatiquement !

---

## 📝 Note

- **JDK** (Java Development Kit) est requis, pas seulement JRE
- Le script cherche automatiquement Java dans les emplacements communs
- Si Java est installé mais pas trouvé, définissez `JAVA_HOME` manuellement

---

**Une fois Java installé, vous pourrez créer l'installateur Windows avec `.\build-exe.ps1` !**

