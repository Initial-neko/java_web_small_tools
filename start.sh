#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$ROOT/toolbox-exec.jar"
[ -f "$JAR" ] || JAR="$ROOT/target/toolbox-exec.jar"
[ -f "$JAR" ] || { printf '%s\n' 'Web jar not found. Run: sh package.sh' >&2; exit 1; }
exec java -Dfile.encoding=UTF-8 -jar "$JAR" "$@"
