# 测试与验收策略

本项目用于内网开箱即用，因此“能生成代码”不等于“可用”。每个工具至少要有自动化测试；涉及 Oracle / 达梦这类厂商数据库时，还要有真实环境集成测试。

## 1. 默认 CI：Java 8 + JUnit

GitHub Actions 固定 Java 8：

    mvn -B test

默认 CI 必须覆盖所有不依赖厂商数据库的能力。

### JSON -> Java

分三层。

#### A. Schema 推断单元测试

验证：

- Integer / Long / BigDecimal / Boolean / String / null
- JSONObject 嵌套对象
- JSONArray 标量数组
- JSONArray 对象数组
- 多个对象样本字段合并
- 混合数组回退 List<Object>
- snake_case / kebab-case
- Java keyword
- 非法首字符
- 字段名归一化冲突

对应：

    JsonEntitySchemaInfererTest

#### B. 生成源码编译测试

不是只判断字符串 contains。

测试直接调用 JDK 自带：

    javax.tools.JavaCompiler

并固定：

    -source 8
    -target 8

验证：

- Normal Java Entity 可以真实编译
- Lombok @Data Entity 可以真实编译
- Lombok annotation processor 实际产生 getter / setter

对应：

    JavaRendererCompileTest

Lombok 仅为测试依赖，不进入 toolbox 运行时。

#### C. Mapping 运行测试

测试会：

1. 生成 Normal Entity 源码
2. 生成 Fastjson2 JSONObject -> Entity Mapping 方法
3. 用 JavaCompiler 动态编译两份源码
4. 构造真实 Fastjson2 JSONObject
5. 反射执行生成的 fromJson
6. 读取生成 Entity getter 验证结果

所以 Mapping 测试覆盖的不是模板，而是：

    Fastjson2 JSONObject
      -> generated mapping code
      -> generated entity
      -> actual field values

## 2. MyBatis Generator 默认测试

默认 CI 不携带 Oracle / DM 厂商驱动。

因此默认测试验证：

- DM 自动 Driver Class
- Oracle 自动 Driver Class
- 多表解析
- MBG 1.4.2 Configuration 能通过 validate
- Java 8 target runtime = MyBatis3
- 外置 driver jar 配置路径

对应：

    MyBatisGeneratorServiceTest

## 3. Oracle / DM 真实数据库测试

真实数据库不能用 H2 替代。

H2 只能说明 JDBC 逻辑能跑，不能验证：

- Oracle remarksReporting
- Oracle schema/synonym
- Oracle 特殊类型
- DM JDBC Metadata
- DM schema 大小写
- DM 特殊数据类型
- 厂商 Driver 与服务器版本兼容

因此仓库内提供：

    MyBatisGeneratorRealDatabaseTest

当环境变量不存在时默认 CI 跳过；进入内网后必须显式跑。

### 达梦

Git Bash 示例：

    export TOOLBOX_DM_DRIVER_JAR=/path/to/DmJdbcDriver8.jar
    export TOOLBOX_DM_JDBC_URL='jdbc:dm://127.0.0.1:5236'
    export TOOLBOX_DM_USERNAME='APP'
    export TOOLBOX_DM_PASSWORD='******'
    export TOOLBOX_DM_SCHEMA='APP'
    export TOOLBOX_DM_TABLE='T_ORDER'

    mvn -Dtest=MyBatisGeneratorRealDatabaseTest#dmShouldGenerateAgainstRealDatabaseWhenEnvironmentIsProvided test

通过条件：

- JDBC 连接成功
- Metadata introspection 成功
- 至少产生一个 Java 文件
- 至少产生一个 Mapper XML
- 无未捕获异常

### Oracle

    export TOOLBOX_ORACLE_DRIVER_JAR=/path/to/ojdbc8.jar
    export TOOLBOX_ORACLE_JDBC_URL='jdbc:oracle:thin:@//127.0.0.1:1521/ORCL'
    export TOOLBOX_ORACLE_USERNAME='APP'
    export TOOLBOX_ORACLE_PASSWORD='******'
    export TOOLBOX_ORACLE_SCHEMA='APP'
    export TOOLBOX_ORACLE_TABLE='T_ORDER'

    mvn -Dtest=MyBatisGeneratorRealDatabaseTest#oracleShouldGenerateAgainstRealDatabaseWhenEnvironmentIsProvided test

## 4. 达梦 SQL 血缘测试（下一阶段）

SQL lineage 不接受“手工看一下结果”。

计划建立：

    src/test/resources/sql/dm/

每个 SQL fixture 有明确期望：

    case-001.sql
    case-001.expected.json

JUnit 参数化扫描整个 corpus。

至少断言：

- parse status
- read tables
- write tables
- table lineage edges
- column lineage edges
- warnings
- unresolved wildcard
- 不支持语法是否正确标为 PARTIAL / UNSUPPORTED

Batch 再独立测试：

- 100 条中 1 条 parse fail，99 条仍成功
- SQL ID/source 不丢
- Local lineage 合并 Global lineage
- 重复 edge 去重
- 循环依赖不导致死循环
- 大批 SQL 不长期保留 AST
- failed SQL 被完整导出

以后修任何 DM SQL 解析 bug：

1. 先加失败 SQL fixture
2. 确认测试红
3. 修复
4. 确认全 corpus 绿

## 5. 可交付门槛

### JSON / Entity Generator

必须：

    mvn test

全部通过。

### MyBatis DM / Oracle

必须同时满足：

    默认 CI green
    +
    对应真实数据库集成测试 green

在没有真实目标数据库验收前，只能称“实现完成 / 待环境验收”，不能称“DM/Oracle 已验证可用”。

### SQL Lineage

必须：

    Java 8 CI green
    DM fixture corpus green
    Batch fault-isolation green

并记录 corpus 的：

    total
    success
    partial
    failed

作为每次升级 Druid 或修改 Resolver 的回归指标。
