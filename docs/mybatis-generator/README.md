# MyBatis Generator 工具

Toolbox 内置 MyBatis Generator 1.4.2 的 Java 8 封装，用于从数据库表结构直接生成：

- Java Model
- Mapper Interface
- Mapper XML

第一批正式保证达梦 DM8 与 Oracle 的使用路径，其他数据库只要 JDBC Metadata 兼容，也可以通过 `databaseType=custom` 使用。

## 为什么锁 MyBatis Generator 1.4.2

当前 Toolbox 运行基线是 Java 8。

- MyBatis Generator 1.4.x 已切到 Java 8
- 当前 2.x 主线要求更高 JDK，不适合本项目
- 因此本项目固定 `mybatis-generator-core:1.4.2`

不要在内网环境中直接把版本号改成 `LATEST`。

## 快速使用

启动：

    java -jar toolbox.jar

打开：

    http://localhost:8088

左侧选择：

    MyBatis Generator

### 达梦示例

数据库类型：

    dm

Driver Jar：

    drivers/DmJdbcDriver8.jar

或旧发行版：

    drivers/DmJdbcDriver18.jar

JDBC URL：

    jdbc:dm://10.0.0.10:5236

Schema：

    APP

Tables：

    T_ORDER
    T_ORDER_ITEM

### Oracle 示例

数据库类型：

    oracle

Driver Jar：

    drivers/ojdbc8.jar

JDBC URL：

    jdbc:oracle:thin:@//10.0.0.20:1521/ORCL

Schema：

    APP

Tables：

    T_ORDER,T_CUSTOMER

## 输出结构

假设 `outputDir=./generated/mybatis`：

    generated/mybatis/
      src/main/java/
        com/example/model/
        com/example/mapper/
      src/main/resources/
        mapper/

输出目录可以直接拷贝进现有 Maven 工程。

## HTTP API

接口：

    POST /api/tools/mybatis-generator/execute

示例：

    {
      "databaseType": "dm",
      "driverJars": "drivers/DmJdbcDriver8.jar",
      "jdbcUrl": "jdbc:dm://127.0.0.1:5236",
      "username": "APP",
      "password": "******",
      "schema": "APP",
      "tables": "T_ORDER,T_ORDER_ITEM",
      "modelPackage": "com.example.model",
      "mapperPackage": "com.example.mapper",
      "xmlPackage": "mapper",
      "outputDir": "./generated/mybatis",
      "overwrite": true,
      "addRemarkComments": true,
      "forceBigDecimals": false,
      "trimStrings": false,
      "useActualColumnNames": false
    }

返回中会包含：

- 实际输出目录
- Java 文件数量
- XML 文件数量
- 生成文件列表
- MyBatis Generator warnings

请求和返回都不会回显密码。

## 参数说明

### databaseType

内置：

- `dm`
- `oracle`
- `custom`

DM 自动使用：

    dm.jdbc.driver.DmDriver

Oracle 自动使用：

    oracle.jdbc.OracleDriver

custom 必须额外提供 `driverClass`。

### driverJars

允许：

- 相对路径
- 绝对路径
- 多个 Jar

多个 Jar 可用分号、逗号或换行分隔。

驱动不会打进 toolbox.jar。

### schema

Oracle/达梦建议显式填写实际 schema。

不要自动把 schema 强制转大写，因为存在 quoted identifier 场景；以实际数据库元数据为准。

### tables

支持：

- 单表
- 多表
- JDBC/MBG 支持的通配符

第一版不做“自动扫描整个数据库再全部生成”，避免误生成系统表或无关 schema。

### addRemarkComments

默认开启。

Oracle 开启时会额外设置：

    remarksReporting=true

这能让字段注释进入 JDBC Metadata，但 Oracle 官方说明这会增加元数据查询成本。

### forceBigDecimals

为 true 时，DECIMAL / NUMERIC 强制映射成 `java.math.BigDecimal`。

为 false 时，MyBatis Generator 会根据 precision/scale 尝试映射到 Short / Integer / Long / BigDecimal。

## Oracle 注意事项

### ojdbc 版本

Java 8 使用 `ojdbc8.jar`。

不要在 Toolbox 中固定一个 Oracle 驱动版本给所有环境。内网包应根据目标 Oracle Database 版本确定驱动。

### 字段备注

Oracle 默认不会在 `DatabaseMetaData.getColumns()` 返回 REMARKS。

Toolbox 已在勾选字段备注时自动打开 `remarksReporting=true`。

### LONG

Oracle JDBC 会把 LONG 报告成 LONGVARCHAR，而 MyBatis 的默认映射可能出现不符合目标项目预期的情况。

碰到 LONG 时建议后续增加 columnOverride，不要在通用类型映射里写死 Oracle 特例。

### Public Synonym

如果通过 public synonym 使用表，优先针对真实表/schema 做生成，再通过 MyBatis Generator 的 runtime table 配置处理运行时名称。

## 达梦注意事项

### JDK 8 驱动命名

达梦不同发行期存在两套名称：

- `DmJdbcDriver18.jar`
- `DmJdbcDriver8.jar`

二者在对应发行版中都表示 Java 8 路线。

Driver Class 保持：

    dm.jdbc.driver.DmDriver

### 驱动与服务端版本

达梦官方建议使用与服务器版本较接近的 JDBC Driver。

因此内网发布物建议保留：

    drivers/README.md

但不要把某个 DM Driver Jar 固化进 Git 仓库。

## 内网离线构建

在有网络的准备机上：

    mvn -Dmaven.repo.local=./offline-repo -DskipTests dependency:go-offline
    mvn -Dmaven.repo.local=./offline-repo clean package

然后把以下内容一并送入内网：

    源码/
    offline-repo/
    drivers/
      对应 DM/Oracle JDBC Jar

内网编译：

    mvn -o -Dmaven.repo.local=./offline-repo clean package

如果单位有 Nexus/Artifactory，优先把固定版本依赖和 JDBC 驱动纳入内部制品库，而不是每台机器单独维护。

## 当前边界

第一批先做好：

- 表 -> Model
- 表 -> Mapper
- 表 -> Mapper XML
- DM/Oracle 外置驱动
- 字段备注
- 多表生成
- 内网离线说明

后续再增加：

- Lombok Model 输出
- columnOverride Web 配置
- GeneratedKey 配置
- 生成配置保存/导入
- 查询结果 -> Entity
- 生成结果 ZIP 下载
