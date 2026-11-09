#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1; then
    echo 'Falta Java. Necesitas JDK 17 o 21 (compila bytecode Java 17).' >&2
    exit 1
fi
if ! command -v curl >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then
    echo 'Faltan curl o unzip. Instala con sudo apt install curl unzip' >&2
    exit 1
fi
GVER=8.9
GRADLE=".herramientas/gradle-${GVER}/bin/gradle"
if [[ ! -x "$GRADLE" ]]; then
    mkdir -p .herramientas
    echo "Descargando Gradle ${GVER} desde services.gradle.org..."
    curl --fail --location --retry 3 --output ".herramientas/gradle-${GVER}-bin.zip" "https://services.gradle.org/distributions/gradle-${GVER}-bin.zip"
    unzip -q ".herramientas/gradle-${GVER}-bin.zip" -d .herramientas
fi
"$GRADLE" --no-daemon clean build
printf '\nJAR de PRODUCCION (no el -sources ni -dev):\n'
find build/libs -maxdepth 1 -name 'waybridge-*.jar' ! -name '*sources*' ! -name '*dev*' -print
