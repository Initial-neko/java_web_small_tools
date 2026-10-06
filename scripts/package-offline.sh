#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
ONLINE=false
SKIP_TESTS=false
for argument in "$@"; do
  case "$argument" in
    --online) ONLINE=true ;;
    --skip-tests) SKIP_TESTS=true ;;
    --help|-h)
      printf '%s\n' 'Usage: sh package.sh [--online] [--skip-tests]' 'Default: offline Maven build, full tests, complete ZIP.'
      exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$argument" >&2; exit 2 ;;
  esac
done
for tool in java jar mvn sha256sum; do
  command -v "$tool" >/dev/null 2>&1 || { printf 'Required tool missing: %s\n' "$tool" >&2; exit 1; }
done
maven() {
  if [ "$ONLINE" = true ]; then mvn -B "$@"; else mvn -o -B "$@"; fi
}
if [ "$SKIP_TESTS" = true ]; then maven -DskipTests clean package; else maven clean package; fi

# Create release directories only after Maven clean has completed.
RELEASE_ROOT="$ROOT/target/release"
DIST="$RELEASE_ROOT/java-web-small-tools"
ZIP="$RELEASE_ROOT/java-web-small-tools-offline.zip"
PLUGIN=org.apache.maven.plugins:maven-dependency-plugin:3.6.1
mkdir -p "$DIST/lib" "$DIST/optional-lib" "$DIST/drivers" "$DIST/source"
cp target/toolbox-exec.jar target/toolbox-desktop.jar "$DIST/"
cp target/toolbox.jar "$DIST/lib/"
cp -R target/desktop-lib "$DIST/"
maven "$PLUGIN:copy-dependencies" -DincludeScope=runtime "-DoutputDirectory=$DIST/lib"
LOMBOK_VERSION=$(sed -n 's:.*<lombok.version>\(.*\)</lombok.version>.*:\1:p' pom.xml | head -n 1)
[ -n "$LOMBOK_VERSION" ] || { printf '%s\n' 'lombok.version missing from pom.xml' >&2; exit 1; }
maven "$PLUGIN:copy" "-Dartifact=org.projectlombok:lombok:$LOMBOK_VERSION" "-DoutputDirectory=$DIST/optional-lib"
maven "$PLUGIN:tree" -Dscope=runtime "-DoutputFile=$DIST/DEPENDENCIES.txt"
for folder in lib optional-lib; do
  (cd "$DIST/$folder"; for file in *.jar; do printf '%s\n' "$file"; done) | LC_ALL=C sort > "$DIST/$folder/LIBS.txt"
done
cp -R docs "$DIST/"
cp README.md "$DIST/"
cp docs/offline-package/README.md "$DIST/OFFLINE-README.md"
cp start.sh start-classpath.sh start-desktop.sh screenshot.sh "$DIST/"
cp drivers/README.md "$DIST/drivers/"
for driver in drivers/*.jar; do [ ! -f "$driver" ] || cp "$driver" "$DIST/drivers/"; done
cp pom.xml README.md package.sh .gitattributes "$DIST/source/"
cp -R src scripts docs drivers "$DIST/source/"
cp start.sh start-classpath.sh start-desktop.sh screenshot.sh "$DIST/source/"
{
  printf '%s\n' 'artifact=java-web-small-tools' 'java=8+'
  if command -v git >/dev/null 2>&1 && git rev-parse HEAD >/dev/null 2>&1; then
    printf 'git_commit=%s\n' "$(git rev-parse HEAD)"
    if [ -n "$(git status --porcelain)" ]; then printf '%s\n' 'working_tree_dirty=true'; else printf '%s\n' 'working_tree_dirty=false'; fi
  fi
} > "$DIST/VERSION.txt"
chmod +x "$DIST"/*.sh "$DIST/source"/*.sh "$DIST/source/scripts"/*.sh
(cd "$DIST"; find . -type f ! -name CHECKSUMS.sha256 -exec sha256sum {} + | LC_ALL=C sort > CHECKSUMS.sha256)
# jar -M creates a standard ZIP without adding a manifest; no zip executable needed.
jar -cMf "$ZIP" -C "$RELEASE_ROOT" java-web-small-tools
printf 'Offline package created: %s\n' "$ZIP"
sha256sum "$ZIP"
