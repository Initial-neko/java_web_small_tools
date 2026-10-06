param([switch]$Online, [switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    $mavenArgs = @('-B')
    if (-not $Online) { $mavenArgs += '-o' }
    function Invoke-Maven([string[]]$Arguments) {
        & mvn @mavenArgs @Arguments
        if ($LASTEXITCODE -ne 0) { throw "Maven failed: $LASTEXITCODE" }
    }
    $build = @('clean', 'package')
    if ($SkipTests) { $build += '-DskipTests' }
    Invoke-Maven $build
    $release = Join-Path $root 'target/release'
    $dist = Join-Path $release 'java-web-small-tools'
    $zip = Join-Path $release 'java-web-small-tools-offline.zip'
    foreach ($dir in @($dist, "$dist/lib", "$dist/optional-lib", "$dist/drivers", "$dist/source")) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
    Copy-Item target/toolbox-exec.jar,target/toolbox-desktop.jar -Destination $dist
    Copy-Item target/toolbox.jar -Destination "$dist/lib"
    Copy-Item target/desktop-lib -Destination $dist -Recurse
    $plugin = 'org.apache.maven.plugins:maven-dependency-plugin:3.6.1'
    Invoke-Maven @("${plugin}:copy-dependencies", '-DincludeScope=runtime', "-DoutputDirectory=$dist/lib")
    [xml]$pom = Get-Content pom.xml
    $lombok = $pom.project.properties.'lombok.version'
    Invoke-Maven @("${plugin}:copy", "-Dartifact=org.projectlombok:lombok:$lombok", "-DoutputDirectory=$dist/optional-lib")
    Invoke-Maven @("${plugin}:tree", '-Dscope=runtime', "-DoutputFile=$dist/DEPENDENCIES.txt")
    foreach ($folder in @('lib','optional-lib')) {
        Get-ChildItem "$dist/$folder" -Filter '*.jar' | Sort-Object Name | Select-Object -ExpandProperty Name | Set-Content "$dist/$folder/LIBS.txt" -Encoding UTF8
    }
    Copy-Item docs -Destination $dist -Recurse
    Copy-Item README.md,start.bat,start.sh,start-classpath.bat,start-classpath.sh,start-desktop.bat,start-desktop.sh,screenshot.bat,screenshot.sh -Destination $dist
    Copy-Item docs/offline-package/README.md -Destination "$dist/OFFLINE-README.md"
    Copy-Item drivers/README.md -Destination "$dist/drivers"
    Get-ChildItem drivers -Filter '*.jar' | Copy-Item -Destination "$dist/drivers"
    Copy-Item pom.xml,README.md -Destination "$dist/source"
    Copy-Item src,scripts,docs,drivers -Destination "$dist/source" -Recurse
    Copy-Item start.bat,start.sh,start-classpath.bat,start-classpath.sh,start-desktop.bat,start-desktop.sh,screenshot.bat,screenshot.sh -Destination "$dist/source"
    $commit = & git rev-parse HEAD
    $dirty = @(& git status --porcelain).Count -gt 0
    @('artifact=java-web-small-tools', 'java=8+', "git_commit=$commit", "working_tree_dirty=$dirty") | Set-Content "$dist/VERSION.txt" -Encoding UTF8
    $checksums = Get-ChildItem $dist -Recurse -File | Sort-Object FullName | ForEach-Object {
        $relative = $_.FullName.Substring($dist.Length + 1).Replace('\','/')
        '{0}  ./{1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $relative
    }
    $checksums | Set-Content "$dist/CHECKSUMS.sha256" -Encoding ASCII
    Compress-Archive -LiteralPath $dist -DestinationPath $zip -Force
    Get-FileHash -LiteralPath $zip -Algorithm SHA256
} finally { Pop-Location }
