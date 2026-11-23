#!/bin/sh
# AlpAI Gradle wrapper launcher.
# The binary gradle-wrapper.jar is not committed in the v0.1 scaffold
# (the GitHub push path used for this scaffold is text-only), so this
# script runs the Gradle wrapper jar when present and otherwise falls
# back to a Gradle installation on PATH (which is also what CI uses).
# To get the real wrapper locally, run:  gradle wrapper --gradle-version 8.7
set -e
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WRAPPER_JAR" ]; then
  exec java -jar "$WRAPPER_JAR" "$@"
elif command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
else
  echo "Gradle not found. Install Gradle 8.7 or run: gradle wrapper --gradle-version 8.7" >&2
  exit 1
fi
