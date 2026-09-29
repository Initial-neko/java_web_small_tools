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
