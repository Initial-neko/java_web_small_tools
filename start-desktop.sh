#!/bin/sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

JAR="$ROOT/toolbox-desktop.jar"
if [ ! -f "$JAR" ]; then
  JAR="$ROOT/target/toolbox-desktop.jar"
fi

if [ ! -f "$JAR" ]; then
  echo "Desktop jar not found. Run: mvn clean package" >&2
  exit 1
fi

exec java -Dfile.encoding=UTF-8 -jar "$JAR"
