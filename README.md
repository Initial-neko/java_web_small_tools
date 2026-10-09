# Toolbox - 本地工具集 Web 服务

基于 Java 8 + Spring Boot 2.7 的本地小工具集合，开箱即用。

## 功能

### 首页系统信息
打开首页即展示本机硬件参数：操作系统、CPU 型号与核心数、内存使用、磁盘分区、所有网卡 IPv4 地址（点击可复制）。

### 工具列表

| 工具 | 标识 | 说明 |
|---|---|---|
| 时间戳转换 | `timestamp` | 时间戳↔日期互转，自动识别秒/毫秒，支持时区 |
| JSON 格式化 | `json-format` | 美化、压缩、校验、转义/反转义 |
| 文本 Diff | `text-diff` | 两段文本按行对比，输出 unified diff 与差异详情 |
| JSON 对比 | `json-compare` | 两个 JSON 结构化对比，按路径输出差异 |
| XML 格式化 | `xml-format` | XML 美化（2 空格缩进）、压缩、校验；禁用 DTD 防 XXE |
| XML → JSON | `xml-to-json` | 属性→`@键`、文本→`#text`、同名重复元素→数组，数字/布尔类型推断可开关 |
| XML → Java | `xml-to-java` | XML 样本生成 Normal/Lombok Entity 或 Fastjson2 显式 Mapping Code，类名默认取根元素名 |
| JSON → Java | `json-to-java` | Fastjson2 JSON 样本生成 Normal/Lombok Entity 或显式 Mapping Code |
| DDL → Java | `ddl-to-java` | Druid AST 解析 DM/Oracle CREATE TABLE 生成 Entity |
| JDBC Query → Java | `jdbc-query-to-java` | 外置 JDBC Driver + ResultSetMetaData 生成 Entity |
| IP 端口检测 | `ip-port-checker` | 检测指定 IP 的端口是否开放，支持多端口和端口范围 |
| Excel 浏览 | `excel-viewer` | 上传 Excel 网页分页浏览，支持多 sheet、合并单元格、样式保留；当前 Sheet 可直接生成 Normal/Lombok Entity |
| 达梦 SQL 分析 | `dm-sql-analyze` | 单条 DM SQL：Statement、读写表、字段、解析错误 |
| 达梦 SQL 批量分析 | `dm-sql-batch` | JSON/分隔文本批量分析、故障隔离、读写表汇总、表级依赖边 |
| SQL 指标探查 | `sql-metric-probe` | 从 SELECT/WITH 中识别聚合指标、维度、WHERE/HAVING 口径和来源 |
| SQL 批量指标探查 | `sql-metric-batch` | 批量识别指标候选并发现重复计算/不同指标别名 |
| MyBatis Generator | `mybatis-generator` | 数据库表生成 Java Model / Mapper / XML；外置 JDBC Driver，优先验证 Oracle / 达梦 |

## 桌面工具

桌面工具不进入 Web 首页，使用独立 Java 入口运行。

| 工具 | 说明 |
|---|---|
| Clipboard History | 记录启动之后的文本、图片、文件路径剪切板历史；支持搜索、删除、重新复制 |
| Screenshot | 鼠标框选截图，自动复制到系统剪切板、进入 Clipboard History，并保存 PNG |

构建后会同时得到：

```text
target/toolbox-exec.jar       # Web 工具
target/toolbox-desktop.jar    # 桌面工具
```

Windows：

```text
start-desktop.bat   # 启动剪切板历史 + 系统托盘
screenshot.bat      # 一次性区域截图
```

完整说明与验收清单见 `docs/desktop-tools/README.md`。

## 环境要求
- JDK 8+
- Maven 3.6+（仅编译时需要）

## 快速开始

### Web

```bash
java -jar toolbox-exec.jar
```

浏览器访问 http://localhost:8088

Windows 双击 `start.bat`，Linux/Mac 执行 `./start.sh`。

### Desktop

```bash
java -jar target/toolbox-desktop.jar
```

Windows 推荐双击 `start-desktop.bat`。桌面 JAR 与 `desktop-lib/` 必须一起保留；固定版本 JNA 5.14.0 已随离线包提供，运行不需要联网。

- 正文默认 22px，可调整并保存；支持全文搜索、类型筛选、图片预览和详情。
- 普通历史默认最多 200 条，可在「上限」设置为 1–10000；超限删除最旧未固定条目及其图片缓存。固定条目置顶、重启保留，不计入普通上限。取消固定后重新参与清理。
- 默认全局快捷键：`Ctrl+Alt+V` 打开剪切板、`Ctrl+Alt+S` 截图；在「快捷键」修改，重复或被占用的组合会提示，修改失败恢复原注册。全局快捷键目前支持 Windows。
- 截图框选后进入标注窗口：拖动添加红框；切到文字模式，输入内容后点击图片放置；`Ctrl+Z` 撤销，`Esc` 取消。复制并完成或保存 PNG 后发布标注结果，保留原始尺寸。
- 历史只管理自有缓存；文件条目指向的原文件、手动另存的 PNG 不参与自动清理。条数限制不等于磁盘容量限制，固定条目由用户主动长期保留。

一键构建发行包：在 Git Bash / Linux 执行 `sh package.sh`。默认使用 Maven 离线缓存并执行完整测试；需要补齐固定依赖时显式加 `--online`，仅需快速打包可加 `--skip-tests`。使用 JDK 自带 jar 生成 ZIP，无需额外 zip 程序。

从源码完整编译：

```bash
mvn clean package
```

## API

- `GET /api/tools` — 列出所有 Web 工具
- `POST /api/tools/{name}/execute` — 执行指定 Web 工具
- `GET /api/system/info` — 本机系统信息
- `POST /api/excel/upload` — 上传 Excel
- `GET /api/excel/{fileId}/sheet/{index}?page=1&size=100` — 分页读取 Excel
- `POST /api/excel/{fileId}/entity` — 按字段/类型/注释行生成 Java Entity
- `DELETE /api/excel/{fileId}` — 清理

## 技术栈
- Java 8 + Spring Boot 2.7.18
- Fastjson2 2.0.65（工具侧 JSON 处理）
- JDK 内置 DOM/Transformer（XML 解析与格式化，禁用 DTD/外部实体防 XXE）
- MyBatis Generator 1.4.2（Java 8 数据库代码生成）
- Alibaba Druid 1.2.28（DM SQL Parser / SchemaStat）
- java-diff-utils 4.12（文本 Diff）
- Apache POI 5.2.5（Excel 解析）
- AWT + Swing（桌面剪切板/截图工具）
- 原生 HTML + JavaScript（Web 前端）

## 内网与数据工具文档

- `docs/desktop-tools/README.md` — Clipboard History / Screenshot、启动、数据目录、Windows 验收
- `docs/entity-generator/README.md` — JSON/Excel/DDL/JDBC Query → Entity、统一类型映射
- `docs/xml-tools/README.md` — XML 格式化/转 JSON/转 Java 的确定性规则与安全边界
- `docs/offline-package/README.md` — 完整离线 ZIP、lib/、启动与校验
- `docs/mybatis-generator/README.md` — MyBatis Generator、Oracle/达梦、外置 Driver、离线构建
- `drivers/README.md` — DM/Oracle JDBC Driver 放置和版本说明
- `docs/sql-lineage/README.md` — 达梦批量 SQL 表级分析
- `docs/metric-probe/README.md` — SQL 指标探查规则、输出与测试范围
- `docs/notebook/2026-09-29-tools.md` — 当前工具需求 Notebook

> JDBC Driver Jar 不提交 Git，也不打进 toolbox.jar；内网部署时放在 `drivers/` 并由工具运行时加载。

## 离线交付

执行：

    sh package.sh

生成：

    target/release/java-web-small-tools-offline.zip

ZIP 同时包含：

- `toolbox-exec.jar`：Web 工具；
- `toolbox-desktop.jar`：Clipboard/Screenshot 桌面工具；
- `lib/`：thin toolbox.jar + 全部 Web runtime 依赖；
- 桌面/Web 启动脚本和完整 docs。
