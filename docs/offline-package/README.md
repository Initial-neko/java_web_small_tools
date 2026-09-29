# Java Web Small Tools 离线交付包

这个 ZIP 不是普通源码压缩包，而是用于直接带入内网运行和导入工程的完整交付物。

## 目录

    java-web-small-tools/
    ├── toolbox-exec.jar
    ├── lib/
    │   ├── toolbox.jar
    │   ├── Spring Boot / Fastjson2 / Druid / POI / MBG / diff 等 runtime jars
    │   └── LIBS.txt
    ├── optional-lib/
    │   ├── lombok-*.jar
    │   └── LIBS.txt
    ├── drivers/
    │   └── README.md
    ├── docs/
    ├── source/
    │   ├── pom.xml
    │   └── src/
    ├── start.bat
    ├── start.sh
    ├── start-classpath.bat
    ├── start-classpath.sh
    ├── DEPENDENCIES.txt
    ├── VERSION.txt
    └── CHECKSUMS.sha256

## 直接运行

只需要 JDK 8+。

Windows：

    start.bat

Git Bash / Linux：

    ./start.sh

等价：

    java -jar toolbox-exec.jar

toolbox-exec.jar 是 Spring Boot fat jar，运行依赖已经内嵌。

## 使用 lib/ 导入内部工程

lib/ 包含普通 thin toolbox.jar 以及 Toolbox 的全部 runtime 依赖 jar。

Windows 可直接：

    start-classpath.bat

Linux / Git Bash：

    ./start-classpath.sh

等价：

    java -cp "lib/*" com.toolbox.ToolboxApplication

内部工程不使用 Maven 时，可以直接把 lib/*.jar 加入 IDE / javac classpath。

## Lombok

Toolbox 自身运行不依赖 Lombok，所以 Lombok 单独放在 optional-lib/。

使用工具生成 Lombok @Data Entity 时，把 optional-lib/lombok-*.jar 加入目标工程即可。

## 达梦 / Oracle JDBC Driver

发行包不自动捆绑厂商 JDBC Driver。

原因：

1. DM Driver 应尽量与目标 DM Server 版本匹配。
2. Oracle ojdbc8 也应根据目标 Oracle 版本和单位制品策略确定。
3. 避免在公共仓库固定或重新分发厂商 Jar。

进入内网后放入：

    drivers/

例如：

    drivers/DmJdbcDriver8.jar
    drivers/ojdbc8.jar

页面中的 MyBatis Generator / JDBC Query Generator 填对应路径。

如果你在本地执行 scripts/package-offline.sh 前已经把 Driver 放进仓库 drivers/ 目录，
脚本会把这些 Jar 一并带入最终 ZIP；这些 Jar 仍不会提交到 Git。

## 校验

CHECKSUMS.sha256 包含包内所有文件的 SHA-256。

Linux / Git Bash：

    sha256sum -c CHECKSUMS.sha256

## Maven 说明

ZIP 已包含直接运行和普通 classpath 导入需要的 runtime jar。

它不是完整 Maven 本地仓库镜像：Maven 插件、父 POM 等不会复制进 lib/。
如果内网必须执行 mvn -o clean package，建议使用单位 Nexus/Artifactory，
或者另外准备完整 .m2/repository 镜像。

对直接运行 Toolbox 或把 jar 导入内部 Java 工程，本 ZIP 已包含所需 runtime lib。
