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
| IP 端口检测 | `ip-port-checker` | 检测指定 IP 的端口是否开放，支持多端口和端口范围 |
| Excel 浏览 | `excel-viewer` | 上传 Excel 网页分页浏览，支持多 sheet、合并单元格、样式保留 |

## 环境要求
- JDK 8+
- Maven 3.6+（仅编译时需要）

## 快速开始

```bash
java -jar toolbox.jar
```
浏览器访问 http://localhost:8088

Windows 双击 `start.bat`，Linux/Mac 执行 `./start.sh`。

从源码编译：`mvn clean package && java -jar target/toolbox.jar`

## API

- `GET /api/tools` — 列出所有工具
- `POST /api/tools/{name}/execute` — 执行指定工具
- `GET /api/system/info` — 本机系统信息
- `POST /api/excel/upload` — 上传 Excel
- `GET /api/excel/{fileId}/sheet/{index}?page=1&size=100` — 分页读取 Excel
- `DELETE /api/excel/{fileId}` — 清理

## 技术栈
- Java 8 + Spring Boot 2.7.18
- Jackson（JSON）
- java-diff-utils 4.12（文本 Diff）
- Apache POI 5.2.5（Excel 解析）
- 原生 HTML + JavaScript（前端）
