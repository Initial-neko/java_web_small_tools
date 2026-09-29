# 达梦 SQL 批量血缘设计基线

目标不是只做一个“粘贴 SQL 看结果”的 Demo，而是从第一版核心接口就支持一批 SQL 的稳定分析。

## 技术路线

    DM SQL
      -> Alibaba Druid DM Parser
      -> AST
      -> LocalLineageResolver
      -> LocalLineageResult
      -> GlobalLineageAggregator
      -> LineageGraph

Parser 不自研。

当前候选固定版本：

    com.alibaba:druid:1.2.28

Druid 继续保持 Java 8 编译基线，并包含 DM 方言解析能力。

## 为什么按 Batch-first 设计

实际使用场景更可能来自：

- SQL 文件目录
- 调度系统导出的 SQL
- Excel / CSV SQL 清单
- 数据库配置表中的 SQL
- ETL/报表任务配置
- 一次几百到几万条 SQL

所以核心模型不能只有：

    analyze(String sql)

需要从一开始保留 SQL 身份和来源。

## 输入模型

建议：

    SqlInput
      sqlId
      sql
      source
      fileName
      databaseType
      schema
      metadata

其中：

- sqlId：批次内唯一
- source：来源系统/任务
- fileName：便于失败后定位原文件
- metadata：保留 jobId、owner、module 等扩展信息

## 单条结果

建议状态：

    SUCCESS
    PARTIAL
    PARSE_FAILED
    UNSUPPORTED

每条 SQL 返回：

    sqlId
    status
    readTables
    writeTables
    columns
    columnLineages
    warnings
    errors
    parseMillis

不要因为一条 SQL 失败终止整批。

## 批次结果

    total
    success
    partial
    failed
    unsupported
    results
    mergedGraph

同时输出：

- 单 SQL 血缘
- 批次合并后的全局血缘
- 失败 SQL 列表

## 两层血缘

### Local lineage

例如：

    INSERT INTO DWD_ORDER ...
    SELECT ...
    FROM ODS_ORDER

得到：

    ODS_ORDER -> DWD_ORDER

以及字段级：

    ODS_ORDER.AMOUNT -> DWD_ORDER.AMOUNT

### Global lineage

另一条 SQL：

    INSERT INTO ADS_ORDER ...
    SELECT ...
    FROM DWD_ORDER

批量合并后：

    ODS_ORDER
       -> DWD_ORDER
       -> ADS_ORDER

字段链也需要跨 SQL 合并。

## 第一阶段语法范围

优先：

- SELECT
- INSERT ... SELECT
- CREATE TABLE AS SELECT
- CREATE VIEW AS SELECT
- WITH / CTE
- JOIN
- UNION / UNION ALL
- 子查询
- CASE
- 常见函数表达式
- 表 alias
- 字段 alias

后置：

- MERGE
- CONNECT BY
- 存储过程
- 动态 SQL
- 复杂 PL/SQL/DM 过程块

遇到后置语法不要让 Batch 崩掉，标记为 PARTIAL / UNSUPPORTED。

## SELECT * 与元数据

字段级血缘遇到：

    SELECT a.*

仅靠 SQL AST 无法完整展开。

后续需要可插拔：

    MetadataProvider

第一版可以：

- 保留 table-level lineage
- 字段级标记 unresolved wildcard

后续再接 Oracle/DM metadata。

## 批量性能原则

第一版不要长期保存所有 AST。

推荐流水线：

    read SQL
      -> parse
      -> resolve
      -> compact result
      -> discard AST

全局聚合只保留：

- 节点
- 边
- SQL ID 引用
- warning/error 摘要

大量 SQL 输出优先 JSON Lines / CSV / Excel 分页，不生成一个巨大 HTML。

## 失败语料库

Batch 分析结束至少输出：

    failed-sql.json / failed-sql.xlsx

字段：

    sqlId
    source
    errorType
    errorMessage
    errorPosition
    sql

后续所有 Druid 兼容增强都必须把失败 SQL 去敏后加入：

    src/test/resources/sql/dm/

形成 DM regression corpus。

## 第一版建议 API

单条调试：

    POST /api/tools/sql-analyze/execute

批量：

    POST /api/lineage/batch

后续如文件较大，Batch 应使用 upload/session 模式，而不是把几万条 SQL 一次塞进 JSON Body。

## 导出

第一阶段至少：

    lineage.json
    lineage.xlsx
    failed-sql.xlsx

Excel 建议 sheet：

    Summary
    TableLineage
    ColumnLineage
    FailedSql

## 版本与内网

Druid 依赖版本必须固定，不用 LATEST。

正式升级 Druid 时必须：

1. 跑全部 DM SQL regression corpus
2. 对比 SUCCESS/PARTIAL/FAILED 数量
3. 对比关键 lineage edge
4. 再决定是否升级

内网发布时与 Maven 依赖一起进入 offline-repo / 内部 Nexus。


# 当前实现状态（2026-09-29）

第一阶段 SQL Analyze Core 已开始落地：

    DmSqlAnalyzer
    DmSqlAnalyzeTool
    SqlInput
    SqlAnalysisResult
    BatchSqlAnalysisResult

当前能力：

- 固定使用 `DbType.dm`
- Druid 解析 SQL AST
- Statement 类型识别
- SchemaStatVisitor 收集表/字段
- 区分 readTables / writeTables
- Batch 输入逐条故障隔离
- 保留 sqlId / source / fileName
- 输出 parseMillis / warnings / errors

当前明确未实现：

- 字段表达式到目标字段的 lineage resolver
- SELECT * 元数据展开
- Local lineage edge
- Global lineage aggregator
- 跨 SQL 字段链
- Excel/JSON lineage export

## 当前 DM fixture corpus

目录：

    src/test/resources/sql/dm/

当前包含：

- SELECT + JOIN
- INSERT ... SELECT
- LISTAGG ... WITHIN GROUP
- MERGE
- 非法 SQL

每个 SQL 都有配套：

    *.expected.json

JUnit 会验证：

- parse status
- statement type
- 关键 read tables
- 关键 write tables

同时额外验证：

    3 条 SQL
      2 条合法
      1 条非法

结果必须：

    total = 3
    success = 2
    failed = 1

非法 SQL 不允许中断另外两条分析。


## Druid 1.2.28 已知 DM Parser 缺口

当前 regression corpus 已确认：

    LISTAGG(...) WITHIN GROUP (ORDER BY ...)

在本项目锁定的 Druid 1.2.28 + DbType.dm 下解析失败，因此 fixture：

    003-listagg.sql

当前期望状态明确记录为：

    PARSE_FAILED

这不是把测试放宽，而是把当前依赖版本的真实兼容边界固化下来。

Druid 当前 main 已经存在针对 DM LISTAGG/WITHIN GROUP 的测试；后续升级 Druid 时，
该 fixture 是升级验收点之一：只有实际 parser 能通过后，才把 expected 改回 SUCCESS。

当前不使用正则“修 SQL”，也不偷偷切 Oracle parser fallback，避免血缘分析出现静默误判。
