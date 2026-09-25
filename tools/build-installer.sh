#!/usr/bin/env bash
#
# Construit 2M Market en version autonome pour Linux (JRE inclus).
#
# Equivalent de build-installer.ps1, pour les postes sous Linux. Les postes
# de caisse n'ont alors pas besoin d'avoir Java installe.
#
#   ./tools/build-installer.sh              -> dossier executable (aucun prerequis)
#   ./tools/build-installer.sh --type deb   -> paquet .deb installable
#
# ATTENTION : jpackage ne fait pas de compilation croisee. Un paquet construit
# ici ne fonctionne que sous Linux. Pour une caisse sous Windows, il faut
# lancer build-installer.ps1 depuis Windows.

set -euo pipefail

TYPE="app-image"
APP_VERSION="1.0"
SKIP_MAVEN=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --type)        TYPE="$2"; shift 2 ;;
        --app-version) APP_VERSION="$2"; shift 2 ;;
        --skip-maven)  SKIP_MAVEN=1; shift ;;
        -h|--help)     sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Option inconnue : $1" >&2; exit 1 ;;
    esac
done

cd "$(dirname "$0")/.."

# --- Verifier jpackage -----------------------------------------------------
if ! command -v jpackage >/dev/null 2>&1; then
    echo "jpackage introuvable. Il est fourni avec le JDK 17+ :" >&2
    echo "  sudo apt install openjdk-21-jdk" >&2
    exit 1
fi
echo "jpackage : $(command -v jpackage)"

if [[ "$TYPE" == "deb" ]] && ! command -v dpkg-deb >/dev/null 2>&1; then
    echo "Le type 'deb' demande dpkg-deb et fakeroot :" >&2
    echo "  sudo apt install dpkg fakeroot binutils" >&2
    exit 1
fi

# --- Construire le jar -----------------------------------------------------
if [[ "$SKIP_MAVEN" -eq 0 ]]; then
    echo
    echo "Construction du jar (mvn clean package)..."
    mvn clean package -DskipTests -q
fi

JAR=$(find target -maxdepth 1 -name '*.jar' ! -name 'original-*' | head -1)
if [[ -z "$JAR" ]]; then
    echo "Aucun jar trouve dans target/." >&2
    exit 1
fi

SIZE_MB=$(( $(stat -c%s "$JAR") / 1048576 ))
echo "  jar : $(basename "$JAR") (${SIZE_MB} Mo)"

# Le jar doit embarquer ses dependances : en dessous de ~5 Mo, le plugin shade
# n'a pas fonctionne et l'application planterait au demarrage.
if [[ "$SIZE_MB" -lt 5 ]]; then
    echo "Le jar ne fait que ${SIZE_MB} Mo : il ne contient pas ses dependances." >&2
    echo "  Relancez 'mvn clean package' et verifiez qu'il n'y a pas d'erreur." >&2
    exit 1
fi

# --- Preparer un dossier d'entree propre -----------------------------------
# jpackage embarque TOUT le contenu de --input : on n'y met que le jar final.
STAGING="target/jpackage-input"
rm -rf "$STAGING"; mkdir -p "$STAGING"
cp "$JAR" "$STAGING/"

DEST="target/installer"
rm -rf "$DEST"

# --- Lancer jpackage -------------------------------------------------------
echo
echo "Generation de l'application ($TYPE)..."

# app.Launcher, et non app.MainApp : la JVM refuse de lancer une classe qui
# etend Application quand JavaFX vient du classpath (voir app/Launcher.java).
ARGS=(
    --type "$TYPE"
    --name "2M-Market"
    --app-version "$APP_VERSION"
    --vendor "2M Market"
    --description "Gestion de stock et ventes"
    --input "$STAGING"
    --main-jar "$(basename "$JAR")"
    --main-class app.Launcher
    --dest "$DEST"
)

if [[ "$TYPE" != "app-image" ]]; then
    ARGS+=(--linux-shortcut --linux-menu-group "Office")
fi

jpackage "${ARGS[@]}"

# --- Resultat --------------------------------------------------------------
echo
echo "Termine."

if [[ "$TYPE" == "app-image" ]]; then
    APP_DIR="$DEST/2M-Market"
    TOTAL=$(du -sm "$APP_DIR" | cut -f1)
    echo "  Dossier : $APP_DIR (${TOTAL} Mo)"
    echo "  Lancer  : $APP_DIR/bin/2M-Market"
    echo
    echo "Copiez ce dossier sur chaque poste de caisse Linux : aucun Java requis."
else
    find "$DEST" -maxdepth 1 -type f -printf "  %f (%s octets)\n"
fi
