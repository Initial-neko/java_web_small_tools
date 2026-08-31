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
| IP 端口检测 | `ip-port-checker` | 检测指定 IP 的端口是否开放，支持多端口和端口范围（如 80,443,8000-9000） |

## 环境要求

- JDK 8+
- Maven 3.6+（仅编译时需要）

## 快速开始

### 方式一：直接运行已打包的 JAR

```bash
java -jar toolbox.jar
```

然后浏览器访问 http://localhost:8088

Windows 用户可双击 `start.bat`，Linux/Mac 用户执行 `./start.sh`。

### 方式二：从源码编译

```bash
mvn clean package
java -jar target/toolbox.jar
```

## 自定义端口

```bash
java -jar toolbox.jar --server.port=9000
```

## 新增工具

1. 在 `com.toolbox.tools` 下新建包，创建类实现 `Tool` 接口
2. 加 `@Component` 注解，启动时自动注册
3. 在 `src/main/resources/static/index.html` 的 `TOOL_CONFIGS` 中添加该工具的表单配置

示例：

```java
@Component
public class MyTool implements Tool {
    @Override public String getName() { return "my-tool"; }
    @Override public String getDisplayName() { return "我的工具"; }
    @Override public String getDescription() { return "工具说明"; }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        // 业务逻辑
        return ToolResult.ok("结果");
    }
}
```

## API

- `GET /api/tools` — 列出所有工具
- `POST /api/tools/{name}/execute` — 执行指定工具，Body 为 JSON 参数
- `GET /api/system/info` — 获取本机系统信息（OS、CPU、内存、磁盘、IP）

## 技术栈

- Java 8
- Spring Boot 2.7.18
- Jackson（JSON 处理）
- java-diff-utils 4.12（文本 Diff）
- 原生 HTML + JavaScript（前端，无构建依赖）
