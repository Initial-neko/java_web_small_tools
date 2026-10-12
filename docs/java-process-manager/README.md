# Web Java 进程管理（Windows）

访问 http://localhost:8088 ，在「Java 进程管理」任务页查看服务端 Java 进程。

- 仅支持运行 Toolbox Web 服务的 Windows 机器（JDK/JRE 8+）。Linux/macOS 返回 \`supported=false\`，不支持进程结束。
- 仅识别 \`java.exe\` / \`javaw.exe\`；有 JDK \`jps -l\` 时尝试补充主类/Jar，未安装 jps 仍显示 PID、启动时间与可执行文件。
- 前端默认每 3 秒刷新，支持按 PID、主类/Jar、进程路径搜索。对不可访问的进程禁用结束操作。
- 手动结束前需二次确认；结束前以同一 Windows 进程句柄核验可执行文件和创建时间，避免 PID 复用误杀；工具自身进程不可终止。
- 终止使用 \`TerminateProcess\`，不是优雅关闭；Shutdown Hook、缓存刷新、事务回滚、外部子进程清理都不保证执行。

## 安全边界

这个 API 具有 **终止进程** 权限，只允许在服务所在机器访问：

1. 验证 TCP 客户端回环地址及请求主机名为 localhost / 127.0.0.1 / [::1]，拒绝非本机访问和典型 DNS 重绑定。
2. GET 返回启动时生成的临时令牌（响应 \`Cache-Control: no-store\`），Web 页面在 POST 时以 \`X-Process-Token\` 发送。
3. POST 同时检查严格同源 \`Origin\`、令牌、PID 与 \`startTime\`，并在终止前重新打开并验证目标进程。
4. 不支持传入任意进程名称、外部命令、命令行参数或递归终止进程树；不输出 JVM 启动参数，避免暴露密码等敏感信息。

API:

- \`GET /api/system/java-processes\`：返回 \`{success,supported,message,token,processes:[...]}\`
- \`POST /api/system/java-processes/{pid}/kill\`：JSON \`{"startTime":<GET 返回值>}\`，请求头带 \`Origin\` 和 \`X-Process-Token\`，成功返回 \`{success,message}\`。

**注意**：Web 页面不是操作系统访问控制边界。运行 Toolbox 的 Windows 用户必须有权限结束目标进程，原则上不要以管理员权限运行浏览器和服务。没有用户身份认证，因此即便设置了本机访问限制，也不应通过代理、隧道或端口映射对外开放本 API。

## 验收

1. Windows 11 / Java 8：启动两个 Jar、一个 javaw.exe，刷新可以识别且可搜索。
2. 仅 JRE 无 jps：显示 PID/路径；缺失主类是允许的降级，不得中断页面。
3. 假冒 Origin、恶意 Host、远程来源或缺少令牌：HTTP 403，无终止动作。
4. PID/创建时间不匹配：HTTP 409，不能误杀复用 PID。
5. 工具自身 PID：\`canKill=false\`，后端也拒绝；不可读取详细信息的进程不可 Kill。
6. Kill 成功后表格自动刷新；进程终止失败有错误提示，不允许误报成功。
7. 离开页面后轮询停止；再次打开正常刷新，不出现重复计时器。
8. CI Java 8 和 JS 检查通过；Windows 7/10 环境必须手动验收，Linux 只验证不可操作状态。
