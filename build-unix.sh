#!/usr/bin/env bash
set -euo pipefail
GRADLE_VERSION="9.2.1"
CACHE_DIR="${HOME}/.gradle/manual-bootstrap"
GRADLE_DIR="${CACHE_DIR}/gradle-${GRADLE_VERSION}"

if ! command -v java >/dev/null 2>&1; then
  echo "Java was not found. Install a 64-bit Java 21 JDK first." >&2
  exit 1
fi

if [[ ! -x "${GRADLE_DIR}/bin/gradle" ]]; then
  mkdir -p "${CACHE_DIR}"
  ZIP_FILE="${CACHE_DIR}/gradle-${GRADLE_VERSION}-bin.zip"
  echo "Downloading the official Gradle ${GRADLE_VERSION} distribution..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${ZIP_FILE}"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "${ZIP_FILE}" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  else
    echo "Install curl or wget and retry." >&2
    exit 1
  fi
  unzip -q -o "${ZIP_FILE}" -d "${CACHE_DIR}"
  rm -f "${ZIP_FILE}"
fi

"${GRADLE_DIR}/bin/gradle" --no-daemon clean build
echo "Build complete. Check build/libs/ for the mod jar."
