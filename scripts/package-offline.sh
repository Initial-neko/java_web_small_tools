#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SKIP_TESTS=false
if [ "$#" -gt 0 ] && [ "$1" = "--skip-tests" ]; then
  SKIP_TESTS=true
fi

RELEASE_ROOT="$ROOT/target/release"
DIST="$RELEASE_ROOT/java-web-small-tools"
ZIP="$RELEASE_ROOT/java-web-small-tools-offline.zip"
DEPENDENCY_PLUGIN="org.apache.maven.plugins:maven-dependency-plugin:3.6.1"

rm -rf "$DIST" "$ZIP"

if [ "$SKIP_TESTS" = "true" ]; then
  mvn -B -DskipTests clean package
else
  mvn -B clean package
fi

# mvn clean 会删除 target，因此发行目录必须在 build 之后创建。
mkdir -p "$DIST/lib" "$DIST/optional-lib" "$DIST/drivers" "$DIST/source" "$RELEASE_ROOT"

test -f "$ROOT/target/toolbox.jar"
test -f "$ROOT/target/toolbox-exec.jar"

cp "$ROOT/target/toolbox-exec.jar" "$DIST/toolbox-exec.jar"
cp "$ROOT/target/toolbox.jar" "$DIST/lib/toolbox.jar"

mvn -B "$DEPENDENCY_PLUGIN:copy-dependencies" -DincludeScope=runtime -DoutputDirectory="$DIST/lib"

LOMBOK_VERSION="$(sed -n 's:.*<lombok.version>\(.*\)</lombok.version>.*:\1:p' pom.xml | head -n 1)"
mvn -B "$DEPENDENCY_PLUGIN:copy" -Dartifact="org.projectlombok:lombok:$LOMBOK_VERSION" -DoutputDirectory="$DIST/optional-lib"

mvn -B "$DEPENDENCY_PLUGIN:tree" -Dscope=runtime -DoutputFile="$DIST/DEPENDENCIES.txt"

find "$DIST/lib" -maxdepth 1 -type f -name '*.jar' -printf '%f\n' | sort > "$DIST/lib/LIBS.txt"
find "$DIST/optional-lib" -maxdepth 1 -type f -name '*.jar' -printf '%f\n' | sort > "$DIST/optional-lib/LIBS.txt"

cp -R "$ROOT/docs" "$DIST/docs"
cp "$ROOT/README.md" "$DIST/README.md"
cp "$ROOT/docs/offline-package/README.md" "$DIST/OFFLINE-README.md"
cp "$ROOT/start.bat" "$DIST/start.bat"
cp "$ROOT/start.sh" "$DIST/start.sh"
cp "$ROOT/start-classpath.bat" "$DIST/start-classpath.bat"
cp "$ROOT/start-classpath.sh" "$DIST/start-classpath.sh"

cp "$ROOT/drivers/README.md" "$DIST/drivers/README.md"
if compgen -G "$ROOT/drivers/*.jar" > /dev/null; then
  cp "$ROOT"/drivers/*.jar "$DIST/drivers/"
fi

cp "$ROOT/pom.xml" "$DIST/source/pom.xml"
cp "$ROOT/README.md" "$DIST/source/README.md"
cp -R "$ROOT/src" "$DIST/source/src"

{
  echo "artifact=java-web-small-tools"
  echo "java=8+"
  echo "toolbox_version=1.0.0"
  echo "fastjson2=2.0.65"
  echo "mybatis_generator=1.4.2"
  echo "druid=1.2.28"
  if git rev-parse HEAD >/dev/null 2>&1; then
    echo "git_commit=$(git rev-parse HEAD)"
  fi
} > "$DIST/VERSION.txt"

chmod +x "$DIST/start.sh" "$DIST/start-classpath.sh"

(
  cd "$DIST"
  find . -type f ! -name 'CHECKSUMS.sha256' -print0 | sort -z | xargs -0 sha256sum > CHECKSUMS.sha256
)

(
  cd "$RELEASE_ROOT"
  zip -qr "$(basename "$ZIP")" "$(basename "$DIST")"
)

echo "Offline package created: $ZIP"
