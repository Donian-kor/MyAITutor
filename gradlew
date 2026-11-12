#!/usr/bin/env sh
set -eu
GRADLE_VERSION="9.3.1"
GRADLE_HOME="${HOME}/.gradle/focus-ai-bootstrap/gradle-${GRADLE_VERSION}"
if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
  echo "[MyAITutor] Gradle ${GRADLE_VERSION} is not installed. Downloading it now..."
  mkdir -p "${HOME}/.gradle/focus-ai-bootstrap"
  ZIP_FILE="${TMPDIR:-/tmp}/gradle-${GRADLE_VERSION}-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${ZIP_FILE}"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "${ZIP_FILE}" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  else
    echo "curl or wget is required to download Gradle." >&2
    exit 1
  fi
  unzip -q -o "${ZIP_FILE}" -d "${HOME}/.gradle/focus-ai-bootstrap"
fi
exec "${GRADLE_HOME}/bin/gradle" -p "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" "$@"
