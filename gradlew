#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
GRADLE_VERSION="8.10.2"
GRADLE_HOME="$(pwd)/.gradle-dist/gradle-${GRADLE_VERSION}"
GRADLE_ZIP="${TMPDIR:-/tmp}/gradle-${GRADLE_VERSION}-bin.zip"

command -v java >/dev/null 2>&1 || { echo "ERROR: JDK 17 or newer is required." >&2; exit 1; }

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  command -v curl >/dev/null 2>&1 || { echo "ERROR: curl is required to bootstrap Gradle." >&2; exit 1; }
  mkdir -p .gradle-dist
  echo "Gradle ${GRADLE_VERSION} not found. Downloading it once..."
  curl -fL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$GRADLE_ZIP"
  unzip -q -o "$GRADLE_ZIP" -d .gradle-dist
  rm -f "$GRADLE_ZIP"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
