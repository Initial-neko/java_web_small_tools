# HTTP 工具帮助

服务默认地址为 `http://localhost:8088`。内网部署时替换为实际服务地址；页面生成的 curl 使用当前访问地址。

## 帮助与能力发现

| 请求 | 返回 |
|---|---|
| `GET /api/tools` | 原有工具名称、展示名和摘要清单，保持兼容 |
| `GET /api/help` | `schemaVersion`、`pages`、`advancedPages`、`environment`、`tools`、`responseContract` |
| `GET /api/tools/{name}/help` | 对应工具的完整说明；未知名称返回 HTTP 404 |

帮助成功直接返回 JSON 对象，没有 `success/data` 包装。`tools[]` 每项含 `name/title/category/summary/notes/endpoints`。每个 endpoint 含 `method/path/contentType/parameters/examples/response`。参数说明使用 `name/in/type/required/default/enum/description`；条件必填项在 description/notes 中说明。`examples[].body` 是实际请求对象，嵌入 JSON 或批量 SQL 的字段仍是字符串，需要由调用方正常 JSON 序列化。

`schemaVersion` 描述帮助结构版本，不是应用发行版本。内部 Java 解析器、渲染器和模型不在 HTTP 能力清单中；桌面剪切板、截图和快捷键无对应 Web API。

## 传参和响应

普通工具使用 `POST /api/tools/{name}/execute`，请求头 `Content-Type: application/json`，参数放在请求体。例如：

```sh
curl -X POST 'http://localhost:8088/api/tools/json-format/execute' \
  -H 'Content-Type: application/json' \
  --data-binary '{"action":"format","input":"{\"id\":1,\"name\":\"张三\"}"}'
```

普通 execute 返回 `{success,message,data}`。业务失败可能仍为 HTTP 200，应检查 `success`。JSON 校验模式需进一步检查 `data.valid`；SQL 分析需检查 `data.status` 或 `data.results[]` 的状态、警告和错误，批量执行成功不表示每条 SQL 都成功解析。

页面展示的 JSON 请求体保留中文，curl 请求体将非 ASCII 字符转换为等价的 JSON `\uXXXX` 转义，避免 Windows 的不同 curl 版本按本地代码页传参时破坏中文。curl 示例是 sh/Bash 语法，在 Windows 上可用 Git Bash 执行。

Excel 使用专门接口，**不要调用 excel-viewer 的通用 execute**：

```sh
# 1. 上传，读取返回的 fileId
curl -X POST 'http://localhost:8088/api/excel/upload' -F 'file=@schema.xlsx'
# 2. 将 FILE_ID 替换为上一步 fileId，工作表索引从 0 开始
curl 'http://localhost:8088/api/excel/FILE_ID/sheet/0?page=1&size=100'
# 3. 配置行号从 1 开始；typeRow/commentRow 为 0 时不使用对应行
curl -X POST 'http://localhost:8088/api/excel/FILE_ID/entity' \
  -H 'Content-Type: application/json' \
  --data-binary '{"sheetIndex":0,"className":"User","mode":"normal","headerRow":1,"typeRow":2,"commentRow":3}'
# 4. 清理
curl -X DELETE 'http://localhost:8088/api/excel/FILE_ID'
```

Excel 响应为扁平对象，例如 `success/fileId/sheets` 或 `success/output/fields`，不放在 `data` 内。文件在上传后 1 小时过期，服务重启也会丢失。

数据库驱动路径、MyBatis 输出目录、SQL 查询和网络探测均发生在服务端；帮助内的数据库账号、地址只是占位示例。JSON/DDL/SQL 静态工具无需连接真实数据库。

## 维护与验收

唯一帮助与任务清单源文件是 `src/main/resources/tool-help.json`，页面和帮助接口使用同一份内容。新增后端 Tool 不自动增加页面；需明确将其配置到某个任务。帮助文档覆盖全部注册 Tool 的行为由 `ToolHelpApiTest` 检查；实体输入源、单条/批量 API 保持原有契约。

Java 8 离线回归：`mvn -o test`（桌面测试需可用显示环境；CI 使用 Xvfb）。可选的前端纯函数检查：`node src/test/js/task-ui.test.cjs`，无需安装 npm 依赖。实际 HTTP/curl 示例验收：先启动服务，再执行 `node scripts/verify-help-api.cjs http://127.0.0.1:8088`；需 Node 18+、curl 和 sh，仅作为开发验收工具，不是运行依赖。
