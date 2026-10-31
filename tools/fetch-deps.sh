#!/usr/bin/env bash
# Populates fabric/libs-repo with the jars this branch compiles against.
#
#   tools/fetch-deps.sh [MODS_DIR]
#
# With MODS_DIR (a Minecraft mods folder containing fabric-api and EMI for 1.21.1) the jars are
# copied from disk — fastest, and exactly the builds the game runs. Without it they are
# downloaded from upstream, which stalls on some networks.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="$ROOT/fabric/libs-repo"
MODS_DIR="${1:-}"

FABRIC_API_VERSION="0.116.17+1.21.1"
EMI_VERSION="1.1.24+1.21.1"

fetch() { # url dest
    if [ -f "$2" ]; then
        echo "have  $2"
        return
    fi
    echo "fetch $2"
    mkdir -p "$(dirname "$2")"
    curl -sSL --retry 3 -o "$2" "$1"
}

extract_module() { # bundle.jar module-name dest
    local tmp
    tmp="$(mktemp -d)"
    unzip -o -q "$1" "META-INF/jars/$2-*.jar" -d "$tmp"
    cp "$tmp"/META-INF/jars/"$2"-*.jar "$3"
    rm -rf "$tmp"
}

bundle="$REPO/.cache/fabric-api-$FABRIC_API_VERSION.jar"
emi="$REPO/.cache/emi-$EMI_VERSION.jar"

if [ -n "$MODS_DIR" ]; then
    cp "$MODS_DIR/fabric-api-$FABRIC_API_VERSION.jar" "$bundle"
    cp "$MODS_DIR/emi-$EMI_VERSION+fabric.jar" "$emi"
else
    fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$FABRIC_API_VERSION/fabric-api-$FABRIC_API_VERSION.jar" "$bundle"
    fetch "https://repo.sleeping.town/dev/emi/emi-fabric/$EMI_VERSION/emi-fabric-$EMI_VERSION.jar" "$emi"
fi

for module in fabric-api-base fabric-key-binding-api-v1 fabric-lifecycle-events-v1; do
    dest="$REPO/cfx/local/$module/1.21.1/$module-1.21.1.jar"
    extract_module "$bundle" "$module" "$dest"
    cat > "$REPO/cfx/local/$module/1.21.1/$module-1.21.1.pom" <<POM
<project><modelVersion>4.0.0</modelVersion><groupId>cfx.local</groupId><artifactId>$module</artifactId><version>1.21.1</version></project>
POM
done

cp "$emi" "$REPO/cfx/local/emi-fabric/1.21.1/emi-fabric-1.21.1.jar"
cat > "$REPO/cfx/local/emi-fabric/1.21.1/emi-fabric-1.21.1.pom" <<'POM'
<project><modelVersion>4.0.0</modelVersion><groupId>cfx.local</groupId><artifactId>emi-fabric</artifactId><version>1.21.1</version></project>
POM

rm -f "$bundle" "$emi"
rmdir "$REPO/.cache" 2>/dev/null || true
echo "done"
