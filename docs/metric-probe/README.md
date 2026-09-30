# SQL 指标探查 V1

目标：从现有达梦 SELECT/WITH SQL 自动提取“指标候选”，用于指标盘点和治理，不把它做成完整字段血缘。

## 核心原则

第一版采用：

    Druid AST
      + 确定性规则
      + JUnit fixture corpus

不让 LLM 判断“87% 像指标”。

## 当前识别

聚合：

- SUM
- COUNT
- COUNT DISTINCT
- AVG
- MIN
- MAX

派生：

- 条件聚合：聚合表达式中包含 CASE WHEN
- RATIO：聚合结果参与除法
- DERIVED：聚合结果参与其他算术表达式
- WINDOW：聚合函数带 OVER

普通字段：

    SELECT order_id, dept_id ...

默认不会被认成指标。

## 输出

每个 MetricCandidate 包含：

    sqlId
    name
    expression
    normalizedExpression
    type
    aggregation
    distinct
    conditional
    window
    sourceTables
    sourceColumns
    dimensions
    whereCondition
    havingCondition
    reasons

reasons 是结构证据，例如：

    SELECT 表达式包含聚合函数: SUM
    聚合使用 DISTINCT
    聚合表达式包含 CASE WHEN 条件口径
    聚合结果参与除法，识别为比率候选
    存在显式别名: total_sales

## 维度/粒度

V1 将 GROUP BY expression 作为确定性维度来源。

如果 GROUP BY 表达式在 SELECT 中有 alias，优先输出 alias；否则输出表达式本身。

## 口径

保留：

- WHERE
- HAVING

V1 不尝试把任意布尔表达式翻译成业务自然语言，避免误解释。

## 来源字段

从指标 SELECT expression 的 AST 中提取 property / identifier。

例如：

    SUM(CASE WHEN pay_status='PAID' THEN amount ELSE 0 END)

会同时保留：

    pay_status
    amount

## 批量

工具：

    sql-metric-batch

输入格式复用现有 BatchSqlInputParser：

- JSON array
- 单独一行 -- @SQL 分隔多段 SQL

输出：

    total
    success
    partial
    failed
    unsupported
    metricCount
    results
    duplicateGroups

## 重复计算

normalizedExpression 相同且出现两次以上时生成 duplicate group。

例如：

    SUM(amount) AS total_amount
    SUM(amount) AS sale_amount

会发现：

    expression: SUM(amount)
    occurrenceCount: 2
    metricNames:
      total_amount
      sale_amount
    sqlIds:
      sql-a
      sql-b

V1 的 normalization 是保守的 AST SQL 字符串标准化，不强行删除表 alias。
这样可能少合并一些重复表达式，但避免把不同语义的表达式错合并。

## 当前边界

第一版不做：

- LLM 自动命名
- 指标标准库匹配
- 字段级血缘
- SELECT * 字段展开
- 基于历史频次自动判定“核心指标”
- 跨 SQL 语义等价证明

这些都可以建立在当前 MetricCandidate 结果上继续做，但不阻塞第一版。
