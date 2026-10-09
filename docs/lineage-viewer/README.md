# SQL 血缘展示（lineage-viewer）

从**末端产出表**出发，交互式钻取整条血缘链路的可视化工具。

数据全部来自已有的 SQL 字段（`source` / `name` / `description` / `sql` / `inputTables` / `outputTables`），
表之间的依赖关系由 `inputTables → outputTables` **推导**，不需要任何人工维护的图结构。

---

## 1. 它解决什么问题

只有 300 条 SQL、200 张表时，「谁产生了这张表」「这张表的数从哪来」靠人翻 SQL 还能忍。
规模一上去，就需要一个能**从终点往回看**的视图：

- 从**末端产出表**（只被写入、没有任何下游）进入 —— 它们是业务的最终交付物；
- 点进去看这张表的**完整上游血缘**；
- 沿图继续钻取，或点连线看「这条关系是哪几条 SQL 造成的」。

## 2. 界面结构

```
┌──────────────────────────────────────────────────────────┐
│ 末端产出表列表（入口）                                     │
│  ┌─ 指标条：表总数 / 血缘关系 / 末端产出表 / 源头表 / 血缘组 ┐│
│  ┌─ 搜索框 + 范围切页（末端表 | 源头表 | 全部表）           ┐│
│  ┌─ 表卡片网格（按分层着色的左侧色条）                      ┐│
└──────────────────────────────────────────────────────────┘
                          ↓ 点击卡片
┌──────────────────────────────────────────────────────────┐
│ 单表血缘图                              │ 详情侧栏         │
│  ┌─ 上游层数 1/2/3/6/全部  适应 重置 导出 ┐│  分层 / 度数    │
│  ┌─ Cytoscape 画布（上游在左、焦点居中）   ┐│  直接上游表     │
│  ┌─ 图例 + 操作提示                       ┐│  直接下游表     │
│                                          │  产出该表的 SQL │
└──────────────────────────────────────────────────────────┘
                          ↓ 点击连线
                    侧栏切换为「血缘关系」详情
```

### 交互要点

| 操作 | 效果 |
|---|---|
| 列表点卡片 | 进入该表的血缘图（上游回溯 + 下游一层） |
| 图上点节点 | 以该表为焦点重新钻取 |
| 图上点连线 | 侧栏显示造成这条关系的 SQL 清单 |
| 侧栏点上游/下游 chip | 跳到那张表的血缘图 |
| 滚轮 / 拖拽 | 缩放 / 平移 |
| 上游层数下拉 | 1 / 2 / 3 / 6 / 全部 层，实时重算子图 |
| 导出 | 把当前图存成 2 倍分辨率 PNG |

## 3. 后端计算（`LineageGraph`）

一次构建、常驻内存，之后所有查询都是 Map 查找。

构建分四步：

1. **拆分**：逐条 SQL 取出 `inputTables` / `outputTables`，表名自动去重、去反引号、
   兼容 `,` `;` `，` `；` 多种分隔；
2. **去重 + 计数**：同一对 `(上游, 下游)` 只保留一条边（键用 `\u0000` 拼接，避免表名含分隔符时碰撞），
   命中的 SQL 作业名挂到边上的 `LinkedHashSet`，即边的 `weight`；
3. **算度数**：由边反推每张表的入度/出度 ——
   **出度 = 0 → 末端产出表**（列表主角），**入度 = 0 → 源头表**；
4. **连通分量**：沿入边 + 出边双向 BFS 求弱连通分量，编号供分组。

### 子图语义

`subGraph(table, depth)` 是「以目标表为终点，向上游回溯」：

- 自目标表沿**入边**最多回溯 `depth` 层，得到未标注 → **上游闭包**；
- `depth <= 0` 表示不限层数，一路回溯到源头表；
- 同时带上目标表的**下游一层**，方便看清它在链路中的位置；
- 距离口径：`0` = 焦点，正数 = 上游第 N 跳，`-1` = 焦点的直接下游。

**环形依赖处理**：若图中存在环（A→B→C→A），某个节点可能既是焦点的上游、又是它的直接下游。
实现里以上游方向为准 —— 下游标记会跳过已被上游访问过的节点，不制造自相矛盾的方向。

### 分层推断

按表名前缀推断数仓分层，识别不了就返回 `UNKNOWN`，不强行猜：

| 前缀 | 分层 |
|---|---|
| `ods` | ODS（原始） |
| `dwd` | DWD（明细） |
| `dws` | DWS（汇总） |
| `ads` / `app` / `rpt` | ADS（应用） |
| `dim` | DIM（维度） |

带库名前缀（`db.dwd_order`）也能正确识别。

## 4. 接口

所有接口挂在 `/api/lineage-viewer/**` 下，返回体统一是仓库的 `ToolResult` 约定：
`{success, message, data}`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/lineage-viewer/overview` | 总览：表/边/末端表/源头表/血缘组数量、分层分布 |
| GET | `/api/lineage-viewer/tables/leaves?scope=&keyword=` | 入口列表。`scope` = `leaves`（默认）/ `sources` / `all` |
| GET | `/api/lineage-viewer/tables?keyword=&limit=` | 全局搜索（含中间表），默认返回 50 条 |
| GET | `/api/lineage-viewer/tables/{name}` | 表详情：分层、度数、上下游清单、产出该表的 SQL（含正文） |
| GET | `/api/lineage-viewer/tables/{name}/lineage?depth=` | 单表血缘子图。`depth` 缺省 6，`<=0` 表示不限层数 |
| GET | `/api/lineage-viewer/edges/detail?from=&to=` | 边详情：造成该关系的 SQL 清单 |
| GET | `/api/lineage-viewer/sqls/{name}` | 按名称自动判定：命中表名 → 返回产出该表的全部 SQL；命中 SQL 名 → 返回该条 SQL。两种语义都返回数组 |
| POST | `/api/lineage-viewer/rebuild` | 用 `{"json":"[...]"}` 或 `{"records":[...]}` 重建图，无需重启 |

## 5. 数据来源

按优先级依次尝试：

1. `-Dlineage.data-file=/path/to/sqls.json` 指定的外部文件；
2. 工作目录下的 `lineage-sqls.json`；
3. classpath 内置样例 `lineage-viewer/sqls.json`（300 条 SQL / 196 张表 / 720 条边）。

JSON 兼容两种形态：

```jsonc
// 形态 A：直接是数组
[ { "source": "hive", "name": "job1", "sql": "...",
    "inputTables": ["ods_a"], "outputTables": ["dwd_b"] } ]

// 形态 B：对象包一层 records
{ "records": [ { ... } ] }
```

`inputTables` / `outputTables` 既支持 JSON 数组，也支持分隔字符串：

```jsonc
"inputTables": ["ods_a", "ods_b"]     // 数组
"inputTables": "ods_a, ods_b;ods_c"   // 字符串，逗号/分号/中文标点都认
```

字段名大小写不敏感（`inputTables` / `inputtables` 都行），
没有 `name` 的记录会被跳过（无法追溯来源）。

### 换真实数据

内置样例只为演示。接真实数据时**不用改代码**：

```bash
# 方式一：启动参数指定
java -jar toolbox-exec.jar --lineage.data-file=/data/sqls.json

# 方式二：放到运行目录
cp /data/sqls.json ./lineage-sqls.json
java -jar toolbox-exec.jar

# 方式三：页面接口热替换（不重启）
curl -X POST http://localhost:8088/api/lineage-viewer/rebuild \
  -H 'Content-Type: application/json' \
  -d '{"json":"<你的JSON文本>"}'
```

启动日志会打印规模与耗时，便于确认数据量：

```
[lineage-viewer] 血缘图构建完成：300 条 SQL → 196 张表 / 720 条边 / 1 个连通分量（载入 76ms，计算 4ms）
[lineage-viewer] 末端产出表 48 张，源头表 50 张
```

## 6. 性能

一次构建、常驻内存，请求侧全部是 O(1) 的 Map 查找。

300 SQL / 196 表下的实测：**载入 76ms + 计算 4ms**，单表全量回溯（55 节点 / 63 边）毫秒级返回。

### 大文件注意事项

早期实现直接对整个 JSON 做完整 DOM 解析，几十 MB 的文件会瞬时占用数百 MB 堆内存，
既慢又容易把接口拖到超时。当前实现的关键约束：

- 解析前先整体读成字节数组，避免流式解析与字符集处理交错带来的不确定性；
- 超过 20MB 的数据集会在启动日志里提示体积，便于提前发现异常数据；
- 自动跳过 UTF-8 BOM，防止 JSON 解析直接失败。

如果单文件到百 MB 级别，建议按业务域拆分后分别加载，或改用 `/rebuild` 分域替换。

## 7. 代码位置

```
src/main/java/com/toolbox/tools/lineageviewer/
  LineageViewerTool.java        工具注册入口（实现 Tool 接口）
  LineageViewerController.java  REST 接口，/api/lineage-viewer/**
  LineageViewerService.java     启动时构建图 + 重建能力
  LineageGraph.java             血缘图引擎（构建 / 查询 / 子图回溯）
  LineageEdge.java              有向边（含命中 SQL 集合与权重）
  LineageTableNode.java         表节点（分层、度数、叶子/源头标记）
  LineageSqlRecord.java         一条 SQL 作业记录（输入模型）
  LineageDataLoader.java        JSON 载入（文件 / classpath / 字符串）

src/main/resources/lineage-viewer/sqls.json          内置样例数据
src/main/resources/static/lineage-viewer/
  app.js                        前端逻辑（mount / unmount）
  app.css                       样式，全部限定在 .lv-root 下
  vendor/cytoscape.min.js       图形库（本地内置，无 CDN 依赖）

src/test/java/com/toolbox/tools/lineageviewer/LineageGraphTest.java
```

### 前端集成方式

血缘展示器的 UI 比较重（独立顶栏 + 双视图 + 画布），没有走主页面那套
`TOOL_CONFIGS` + `renderForm` 表单模式，而是单独做成一个挂了 `mount/unmount` 的模块。

仓库当前的前端结构是「`index.html` 内联脚本 + 外部 `task-ui.js` + `tool-help.json` 目录」，
接入需要三处改动（都是加法，不动既有逻辑）：

1. **`static/index.html`**
   - `<head>` 末尾加 `<link rel="stylesheet" href="/lineage-viewer/app.css">`；
   - `</body>` 前加两行 `<script>` 引入 cytoscape 与 `app.js`。
2. **`static/task-ui.js`**
   - `renderMain()` 里加一个分支：`if (currentTool.name==='lineage-viewer') { renderLineageViewer(main); return; }`；
   - `renderLineageViewer()` 先渲染 `taskHeader()`（保证页签/面包屑与其它工具一致），
     再塞一个 `<div id="lv-root" class="lv-root">`，然后 `LineageViewer.mount()` 到这个节点；
   - `selectTool()` 里插入 `unmountHeavyTools()`：切走时 `LineageViewer.unmount()` 销毁
     cytoscape 实例，避免切走再切回时画布渲染到已移除的 DOM 上。
3. **`resources/tool-help.json`**
   - `pages` 增加 `lineage` 页（`title=SQL 血缘展示`，`tools=["lineage-viewer"]`）；
   - `tools` 增加 `lineage-viewer` 条目，写清 8 个接口的参数、默认值和可复制 curl 示例。
     `GET /api/help` 会直接把它喂给「API 调用与参数说明」折叠面板，无需改 Java。

> **注意**：`index.html` 的内联 `<script>` 里不能出现字面量闭合标签（如 `</div>` 会截断脚本块）。
> 需要用 `\x3C/div>` 这类转义写法。`task-ui.js` 是外部文件，不受这条限制。

> **注意**：`tool-help.json` 里的 curl 示例会被 `src/test/js/task-ui.test.cjs` 逐条执行校验，
> 示例的 `query` 值必须只含 ASCII 与 URL 安全字符（`from` / `to` 之类的示例别用中文表名）。

## 8. 测试

```bash
mvn -B test -Dtest=LineageGraphTest
```

覆盖 20 个用例：

| 类别 | 用例 |
|---|---|
| 基础结构 | 规模与度数、末端/源头列表、边去重、权重累加、自环丢弃、连通分量分组 |
| 子图回溯 | 全量回溯到源头、上游可达性、深度限制、中间表不跨越下游分支 |
| 边界 | 环形依赖方向不自相矛盾、不存在的表返回空子图 |
| 演示数据 | 规模校验（300 SQL / 196 表）、每张末端表都能回溯、搜索忽略大小写、分层推断 |
| 输入兼容 | 对象包 `records`、分隔字符串、无 `name` 记录跳过 |

接口层另有一条用例 `ToolHelpApiTest#shouldResolveLineageSqlsByTableNameNotOnlyBySqlName`
（MockMvc，端到端跑真实接口），校验：

- `/tables/leaves` 的每张末端表都能通过 `/sqls/{表名}` 回溯到至少一条产出 SQL，且 SQL 带正文；
- 反过来拿 SQL 名去查 `/sqls/{SQL名}` 能得到同名的那一条；
- `/api/lineage-viewer` 已进入 `/api/help` 目录（否则前端不会出现入口）。

## 9. 已知限制

- **血缘来源是输入输出清单，不是 SQL 解析。** 默认信任 `inputTables` / `outputTables` 字段。
  如果上游系统这两个字段不准，血缘也会跟着不准 —— 需要 SQL 级解析请用 `dm-sql-analyze` / `dm-sql-batch`。
- **末端表定义是「出度 = 0」。** 跨系统拼接的场景下，一张本地末端表可能在别处还有下游，
  此时它会被误判为末端。可以通过「全部表」标签页核对。
- **单表血缘图在链路很宽时会比较大**（演示数据最多 55 节点 / 63 边）。
  上游层数默认给 6 层、缩放过小时自动隐藏标签，都是为这个场景准备的；
  再大建议先用搜索定位到具体表，而不是从末端表全量展开。
