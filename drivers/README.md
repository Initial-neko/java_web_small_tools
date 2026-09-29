# JDBC Drivers（内网使用）

本目录只保存说明，**不要提交 JDBC 驱动 Jar**。Toolbox 的 MyBatis Generator 支持运行时加载外置 JDBC Jar。

## 目录约定

建议部署包保持：

    toolbox.jar
    drivers/
      DmJdbcDriver8.jar        # 或旧版 DmJdbcDriver18.jar
      ojdbc8.jar
    docs/

Web 页面中的 `driverJars` 填相对路径或绝对路径，例如：

    drivers/DmJdbcDriver8.jar

多个 Jar 使用分号、逗号或换行分隔。

## 达梦 DM8 / Java 8

驱动类：

    dm.jdbc.driver.DmDriver

常用 URL：

    jdbc:dm://127.0.0.1:5236

JDK 8 驱动命名需要兼容达梦不同发行时期：

- 较老版本：`DmJdbcDriver18.jar`（18 表示 JDK 1.8）
- 2024 年第三季度以后新命名：`DmJdbcDriver8.jar`
- 达梦官方建议 JDBC 驱动版本尽量与数据库服务器版本接近

不要把某个固定 DM 驱动直接编进 toolbox.jar。内网部署时从目标 DM 安装目录的 `drivers/jdbc` 获取对应版本最稳妥。

## Oracle / Java 8

驱动类：

    oracle.jdbc.OracleDriver

常用 Thin URL：

    jdbc:oracle:thin:@//127.0.0.1:1521/ORCL

Java 8 使用 `ojdbc8.jar`。具体 ojdbc8 版本应根据目标 Oracle Database 版本和单位现有支持策略确定，不在 Toolbox 中强绑定。

Oracle JDBC 默认不会返回 `DatabaseMetaData.getColumns()` 的 REMARKS。Toolbox 在勾选“生成数据库字段备注”时会自动向 JDBC 连接传入：

    remarksReporting=true

注意：Oracle 官方说明开启 REMARKS 会让部分元数据查询明显变慢，因此大量表生成时如不需要字段备注，可以关闭该选项。

## 为什么“有 Driver 基本就能用”

MyBatis Generator 的数据库 introspection 主要依赖标准 JDBC `DatabaseMetaData`，尤其是：

- `getColumns`
- `getPrimaryKeys`

所以 Oracle、达梦这类 JDBC 驱动正常实现元数据接口后，生成主流程不需要为每个数据库写一套代码。

但以下内容仍存在数据库差异，遇到时应记录成回归用例，而不是修改公共生成框架：

- schema / catalog 的大小写和默认值
- Oracle public synonym
- Oracle LONG 等特殊类型
- 达梦自定义类型或特定版本 JDBC 元数据差异
- 字段备注返回策略
- 驱动与数据库服务器版本跨度过大

## 安全要求

- 驱动 Jar 不进入 Git。
- 数据库密码只用于本次 Web 请求，不写入配置文件。
- 内网生产部署建议使用只读元数据账号。
- 不要把 SYS/SYSDBA 等高权限账号作为默认生成账号。
