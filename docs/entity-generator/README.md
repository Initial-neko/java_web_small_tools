# Entity Generator

Entity Generator 的目标是把不同来源统一转换成 `EntitySchema`，再由同一套 renderer 输出 Java 代码。

当前已经接入：

- Fastjson2 JSON Sample
- Excel Schema Sheet
- DM / Oracle CREATE TABLE DDL
- JDBC SELECT/WITH ResultSetMetaData

## 统一结构

    JSON / Excel / JDBC / DDL
              ↓
          EntitySchema
              ↓
      Normal Java / Lombok
              ↓
       optional Mapping Code

这样字段命名、类型映射、注释、Java 8 编译规则只维护一份。

## JSON → Java

Toolbox 左侧：

    JSON → Java

支持：

- Normal Java Entity
- Lombok @Data Entity
- Fastjson2 JSONObject -> Entity 显式 Mapping Code

JSON 根节点当前要求是 Object。

### 已覆盖类型

- Integer
- Long
- BigInteger
- BigDecimal
- Boolean
- String
- Object
- List
- 嵌套 Object
- 嵌套 List

### 类型推断原则

JSON 数字不会为了方便统一使用 Long/Double。

例如：

    2147483647                  -> Integer
    2147483648                  -> Long
    922337203685477580812345    -> BigInteger
    12.35                       -> BigDecimal

数组里的数字会进行安全提升：

    [1, 2147483648]             -> List<Long>
    [1, 9223372036854775808123] -> List<BigInteger>
    [1, 2.5]                    -> List<BigDecimal>

### 字段名

支持常见数据库/接口字段风格：

    user_id       -> userId
    USER_ID       -> userId
    user-id       -> userId
    class         -> classValue

发生归一化冲突时会稳定追加序号。

## Excel → Java

入口仍在：

    Excel 浏览

先上传 Excel，然后在当前 Sheet 上直接使用：

    Excel → Java Entity

默认 Schema Sheet：

| 第 1 行 | 第 2 行 | 第 3 行 |
|---|---|---|
| 字段名 | 类型 | 注释 |

三个行号都可以在页面调整：

- 字段名行必须大于 0
- 类型行填 0：全部默认 String
- 注释行填 0：不生成字段 JavaDoc

### 示例

    USER_ID       AMOUNT          CREATED_AT
    NUMBER(10,0)  DECIMAL(18,2)   TIMESTAMP
    用户ID         金额             创建时间

生成：

    private Long userId;
    private BigDecimal amount;
    private LocalDateTime createdAt;

字段注释会生成 JavaDoc。

### 常用类型

字符串：

- String
- CHAR / VARCHAR / VARCHAR2
- NCHAR / NVARCHAR / NVARCHAR2
- TEXT / CLOB / NCLOB

整数：

- INT / INTEGER / SMALLINT / TINYINT
- BIGINT
- NUMBER(p,0)

数值：

- NUMBER
- DECIMAL / NUMERIC
- NUMBER(p,s)
- FLOAT / DOUBLE / REAL

其他：

- BOOLEAN / BIT
- DATE
- TIMESTAMP / DATETIME
- BigInteger / BigDecimal
- LocalDate / LocalDateTime

未知类型不会偷偷映射成 String，而是生成 Object，避免把不认识的数据类型伪装成确定类型。

## API

Excel 上传仍使用：

    POST /api/excel/upload

生成：

    POST /api/excel/{fileId}/entity

Request：

    {
      "sheetIndex": 0,
      "className": "OrderEntity",
      "packageName": "com.example.model",
      "mode": "normal",
      "headerRow": 1,
      "typeRow": 2,
      "commentRow": 3
    }

mode：

    normal
    lombok

返回同时包含：

- 生成源码
- 字段数量
- 每个字段的 sourceName
- fieldName
- sourceType
- javaType
- comment

## 测试门槛

JSON 和 Excel Entity Generator 不接受只做字符串模板测试。

JUnit 必须覆盖：

    source input
      -> EntitySchema
      -> generated Java
      -> Java 8 javac
      -> generated class

JSON Mapping 还必须继续执行：

    Fastjson2 JSONObject
      -> generated fromJson()
      -> generated Entity
      -> assert actual values

详细规则见：

    docs/testing/README.md


## DDL → Java

工具：

    ddl-to-java

当前支持：

    databaseType = dm
    databaseType = oracle

只接受 CREATE TABLE。通过 Druid AST 读取字段，不使用正则解析 DDL。

类名留空时默认由表名转换。

## JDBC Query → Java

工具：

    jdbc-query-to-java

通过外置 Driver 直接连接数据库，并执行只读 SELECT/WITH 查询获取 ResultSetMetaData。

保护措施：

- 非 SELECT/WITH 输入直接拒绝。
- Connection 尝试 setReadOnly(true)。
- PreparedStatement 尝试 maxRows=1。
- PreparedStatement 尝试 queryTimeout=30 秒。
- Driver Jar 使用独立 URLClassLoader，不要求注册到系统 DriverManager。

DM / Oracle Driver Class 会自动推断；其他 JDBC 数据库可选择 custom 并填写 driverClass。

## 统一类型映射

Excel / DDL / JDBC Query 已统一使用：

    EntityTypeMapper

避免三个入口各自维护 NUMBER/VARCHAR/TIMESTAMP 等规则。

额外覆盖：

- byte[] / BLOB
- LocalTime / TIME
- LocalDate / DATE
- LocalDateTime / TIMESTAMP
