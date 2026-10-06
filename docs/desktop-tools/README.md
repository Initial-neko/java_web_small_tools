# Desktop Tools

这组工具是 `java_web_small_tools` 的 **桌面能力**，不会注册成 Spring MVC Tool，也不会出现在 Web 首页。

当前包含：

- Clipboard History：从启动之后开始记录系统剪切板历史。
- Screenshot：框选截图，自动复制到剪切板并进入历史。

目标仍然是 Java 8 + 本地运行 + 尽量少依赖。当前桌面 JAR 只使用 JDK 自带 AWT/Swing/IO API，不需要启动 Spring Boot，也不需要额外 native DLL。

## 1. 构建

在项目根目录：

```bash
mvn clean package
```

构建后同时得到：

```text
target/toolbox-exec.jar      # 原 Web 工具
target/toolbox-desktop.jar   # 新桌面工具
```

Web 和桌面是两个独立入口，可以单独使用，也可以同时运行。

## 2. Windows 使用

### 启动剪切板历史

双击：

```text
start-desktop.bat
```

或者：

```bat
java -jar target\toolbox-desktop.jar
```

启动后会出现 Clipboard History 窗口，并在系统托盘常驻。

关闭主窗口只会隐藏窗口，不会停止记录。真正退出请使用托盘菜单 **Exit**。

### 截图

桌面工具已经运行时：

1. 右键系统托盘 Java Small Tools。
2. 点击 **Screenshot**。
3. 鼠标拖动框选区域。
4. 松开鼠标完成截图。
5. 截图会自动：
   - 复制到系统剪切板；
   - 进入 Clipboard History；
   - 保存一份 PNG 到 screenshots 目录。

按 `Esc` 取消截图。

也可以直接双击：

```text
screenshot.bat
```

这是一次性截图模式：截图完成或取消后进程退出，不需要提前启动 Clipboard History。

## 3. Clipboard History

当前支持三类常用剪切板内容：

| 类型 | 保存方式 |
|---|---|
| 文本 | 保存完整文本 |
| 图片 | 保存 PNG，并在历史里记录 |
| 文件 | 保存被复制文件/目录的路径列表 |

历史窗口支持：

- 查看时间、类型、内容预览；
- 搜索；
- 双击或点击 **Copy selected** 重新放回系统剪切板；
- 删除单条历史；
- 打开本地数据目录；
- 直接发起 Screenshot。

说明：

- 只能保存 **工具启动之后** 发生的剪切板变化，无法恢复工具启动前已经被覆盖的系统剪切板。
- 当前对连续相同内容做去重，避免轮询造成重复记录。
- 从历史中“重新复制”的内容不会再次产生一条重复历史。
- Office/浏览器等富文本内容如果同时提供普通文本 flavor，当前按普通文本记录；第一版不保存 HTML/RTF 私有格式。
- 第一版不自动删除历史，除非手工删除。
- 第一版也不会识别密码、Token 等敏感内容，因此共享电脑上请谨慎开启长期记录。

## 4. 数据目录

默认：

```text
%USERPROFILE%\.java-web-small-tools\desktop
```

结构：

```text
desktop/
├─ history/       # 每条历史的 metadata
├─ images/        # 剪切板图片
└─ screenshots/   # 主动截图副本
```

可自定义：

```bat
java -Dtoolbox.desktop.dataDir=D:\toolbox-data -jar target\toolbox-desktop.jar
```

数据完全保存在本机，不通过 Web API 上传。

## 5. Web 端为什么不展示

桌面工具依赖系统剪切板、系统托盘、屏幕和鼠标交互，这些能力属于本机 GUI，不适合通过浏览器页面包装。

因此项目约定：

```text
Web 工具
src/main/java/com/toolbox/tools
        ↓
浏览器调用

Desktop 工具
src/main/java/com/toolbox/desktop
        ↓
独立 Java main / 系统托盘
```

README 只负责告诉使用者“有哪些桌面工具以及如何启动”，Web 首页不增加 Clipboard/Screenshot 卡片。

## 6. 快捷方式建议

V1 没有引入 JNA，因此暂时没有进程级全局快捷键。

Windows 上如果想做到接近快捷键体验，可以给 `screenshot.bat` 创建桌面快捷方式，然后在 Windows 的快捷方式属性里设置“快捷键”。

这样无需给 Java 工程引入 native 依赖。

如果后续验收认为必须做到类似 QQ 的全局 `Ctrl + Shift + A`，再单独增加 JNA + `RegisterHotKey`，不要让 native 依赖进入第一版核心。

## 7. Windows 兼容性

目标环境：

- JDK 8
- Windows 7
- Windows 10
- Windows 11

普通单屏截图、剪切板和托盘都使用 Java 8 标准 API。

多屏幕已经按多个 `GraphicsDevice` 合并截图，但以下场景需要在真实 Windows 机器验收：

- 主副屏使用不同 DPI 缩放；
- 副屏位于主屏左侧/上方，导致虚拟桌面坐标为负数；
- 125% / 150% / 200% 缩放下框选坐标是否偏移；
- Remote Desktop/虚拟机下的 `Robot` 截屏行为。

如果发生 DPI 坐标偏移，应作为下一轮 Windows 专项修复，不要改成 Web 截图方案。

## 8. 第一轮验收清单

### Clipboard

- [ ] 启动 `start-desktop.bat` 后复制普通中文/英文文本，历史立即出现。
- [ ] 连续复制完全相同文本不会每 400ms 重复增长。
- [ ] 复制另一段文本后再次复制旧文本，可以正常记录用户真实的再次复制动作。
- [ ] 复制一张图片，历史出现 IMAGE。
- [ ] 在资源管理器复制一个/多个文件，历史出现 FILES。
- [ ] 双击历史文本，可以在记事本粘贴。
- [ ] 双击历史图片，可以在支持图片粘贴的软件中粘贴。
- [ ] 双击历史文件，可以重新得到文件列表剪切板。
- [ ] 搜索可以筛选文本和文件路径。
- [ ] 删除一条记录后重启仍保持删除结果。
- [ ] 关闭窗口后继续复制，重新从托盘打开仍能看到新记录。
- [ ] 托盘 Exit 后不再记录。

### Screenshot

- [ ] 托盘 Screenshot 能出现全屏框选层。
- [ ] 拖动后松开即可完成。
- [ ] Esc 可以取消。
- [ ] 截图后可以直接在聊天软件/画图中 Ctrl+V。
- [ ] 截图进入 Clipboard History。
- [ ] screenshots 目录存在对应 PNG。
- [ ] `screenshot.bat` 可以不启动主程序直接截图。
- [ ] 双屏情况下分别框选主屏和副屏。
- [ ] Windows 125%/150% DPI 下检查框选区域是否准确。

## 9. V1 暂不做

- 图片标注（箭头、矩形、文字、马赛克）。
- OCR。
- 截图钉在桌面。
- 云同步。
- 剪切板跨设备同步。
- 自动敏感信息识别。
- JNA 全局热键。
- Windows 自带 Win+V 历史导入。

这些能力等第一轮真实验收后按使用价值继续加。
