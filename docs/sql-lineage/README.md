# 达梦 SQL 批量分析 / 表级血缘

当前需求只做**表级**，不做字段级血缘。

## 技术路线

    DM SQL Batch
        ↓
    Druid 1.2.28 / DbType.dm
        ↓
    per-SQL analyze
        ├─ statementTypes
        ├─ readTables
        ├─ writeTables
        ├─ columns
        ├─ warnings/errors
        └─ parseMillis
        ↓
    Batch aggregation
        ├─ total/success/partial/failed
        ├─ merged readTables
        ├─ merged writeTables
        └─ tableLineageEdges

Parser 不自研。

## 批量输入

Tool：

    dm-sql-batch

支持两种输入。

### JSON array

    [
      {
        "sqlId": "sql-001",
        "source": "job-a",
        "sql": "SELECT ID FROM APP.T_ORDER"
      }
    ]

也可直接使用字符串数组。

### 多段 SQL 文本

多条 SQL 使用单独一行分隔：

    -- @SQL

这样 SQL 本身可以跨多行，不按分号粗暴切割。

## 故障隔离

每条 SQL 独立分析。

例如 100 条中 1 条语法失败：

    total   = 100
    success = 99
    failed  = 1

失败 SQL 不允许终止整个 Batch。

## 表级依赖边

当同一条 SQL 同时存在 readTables 和 writeTables 时，生成：

    sourceTable -> targetTable

同一对表在多条 SQL 中出现时只保留一条 edge，并把相关 sqlId 收集到 edge.sqlIds。

当前不解析字段表达式，因此没有 columnLineage。

## 当前非目标

- 字段级血缘
- SELECT * 字段展开
- 跨 SQL 字段传播
- 专门针对 INSERT ... SELECT 的字段映射
- 存储过程内部动态 SQL 的完整语义解析

Druid 能自然识别的 SQL 会继续返回表统计，但这些能力不作为当前需求的专门验收项。

## Regression corpus

目录：

    src/test/resources/sql/dm/

继续保留真实兼容样本，包括合法 SQL、非法 SQL以及已知 parser gap。

### 已知 gap

Druid 1.2.28：

    LISTAGG(...) WITHIN GROUP (ORDER BY ...)

当前 fixture 明确期望 PARSE_FAILED。

后续升级 Druid 时先跑 corpus；只有实际解析成功后才调整 expected。

## 测试

JUnit 当前覆盖：

- 单条 SQL parse/read/write
- 多 Statement
- Batch fault isolation
- JSON batch input
- -- @SQL 多段输入
- 表级 edge 去重
- 一个 edge 收集多个 sqlId
- parser failure 保留错误信息
