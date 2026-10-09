#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java -Dfile.encoding=UTF-8 -cp "$ROOT/lib/*" com.toolbox.ToolboxApplication "$@"
