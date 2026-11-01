#!/usr/bin/env bash
# Build the Fabric (MC 1.18.2) artifact for cfx-compat-emi.
# Mirrors cfx-compat-create/tools/build-fabric.sh.
#
# Usage:
#   ./tools/build-fabric.sh
#   ./tools/build-fabric.sh --release
#   ./tools/build-fabric.sh --clean
#
# Environment:
#   MC_1_18_2_FABRIC_PATH  instance dir; unset -> skip instance deploy (WARNING)
#   RELEASE_DEPLOY_PATH   extra copy dest on --release; unset -> WARNING
#
# This project has no build_id, so debug and release builds share one jar name;
# --release only turns on the extra copy.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

# -- Defaults ----------------------------------------------------------------
RELEASE=false
CLEAN=false
EXTRA_ARGS=()

# -- Parse args --------------------------------------------------------------
for arg in "$@"; do
    case "$arg" in
        --release)
            RELEASE=true
            ;;
        --clean)
            CLEAN=true
            ;;
        *)
            EXTRA_ARGS+=("$arg")
            ;;
    esac
done

# -- Resolve the expected artifact -------------------------------------------
mod_version=$(grep -E '^mod_version=' gradle.properties | cut -d= -f2)
mc_ver=$(grep -E '^minecraft_version=' gradle.properties | cut -d= -f2)
NEW_JAR="${PROJECT_DIR}/fabric/build/libs/cfx-compat-emi-${mod_version}-mc${mc_ver}-fabric.jar"

echo "==> Fabric build: cfx-compat-emi ${mod_version} (MC ${mc_ver})"

if $CLEAN; then
    echo "==> Cleaning first..."
    ./gradlew clean
fi

# -- Build -------------------------------------------------------------------
gradle_cmd=(./gradlew :fabric:build)
if [[ ${#EXTRA_ARGS[@]} -gt 0 ]]; then gradle_cmd+=("${EXTRA_ARGS[@]}"); fi
echo "==> Running: ${gradle_cmd[*]}"
"${gradle_cmd[@]}"

if [[ ! -f "${NEW_JAR}" ]]; then
    echo "JAR not found: ${NEW_JAR}"
    exit 1
fi

echo ""
echo "Build complete: $(basename "${NEW_JAR}")"

# -- Deploy to a local instance ----------------------------------------------
MC_INSTANCE="${MC_1_18_2_FABRIC_PATH:-}"
MODS_DIR="${MC_INSTANCE}/mods"

if [[ -z "${MC_INSTANCE}" ]]; then
    echo "MC_1_18_2_FABRIC_PATH is not set; skipping instance deploy"
    echo "   artifact: ${NEW_JAR}"
else
    LOGS_DIR="${MC_INSTANCE}/logs"
    if [[ -d "${LOGS_DIR}" ]]; then
        rm -rf "${LOGS_DIR:?}"/*
        echo "  cleared logs: ${LOGS_DIR}"
    fi

    CRASH_DIR="${MC_INSTANCE}/crash-reports"
    if [[ -d "${CRASH_DIR}" ]]; then
        rm -rf "${CRASH_DIR:?}"/*
        echo "  cleared crash-reports: ${CRASH_DIR}"
    fi

    if [[ -d "${MODS_DIR}" ]]; then
        rm -f "${MODS_DIR}"/cfx-compat-emi-*-mc*-fabric.jar
        echo "  removed old cfx-compat-emi Fabric jars"
    fi

    mkdir -p "${MODS_DIR}"
    cp "${NEW_JAR}" "${MODS_DIR}/"
    echo "  deployed: ${MODS_DIR}/$(basename "${NEW_JAR}")"
fi

# -- Release copy ------------------------------------------------------------
if $RELEASE; then
    if [[ -n "${RELEASE_DEPLOY_PATH:-}" ]]; then
        mkdir -p "${RELEASE_DEPLOY_PATH}"
        cp "${NEW_JAR}" "${RELEASE_DEPLOY_PATH}/"
        echo "  release copy: ${RELEASE_DEPLOY_PATH}/$(basename "${NEW_JAR}")"
    else
        echo "RELEASE_DEPLOY_PATH is not set; skipping release copy"
    fi
fi
