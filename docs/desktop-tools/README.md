# 桌面工具使用说明（大字体版）

Java 8 桌面入口与 Web 独立。完整解压离线包，保留 desktop-lib 与 toolbox-desktop.jar 相对位置，执行 sh start-desktop.sh。JNA/jna-platform 5.14.0 已随包附带，用于 Windows 全局快捷键，无需运行时联网下载。

- 正文默认 22px，可在主窗口调整并保存；控件和标准弹窗至少 22px，详情正文至少 24px。
- 启动后记录普通文本、图片及文件路径；不保存 HTML/RTF 格式。双击或回车恢复历史，空格查看详情。
- 普通历史默认最多 200 条，可设 1–10000；固定条目不计上限并长期保留，取消固定后参与清理。淘汰最旧普通条目及其自有图片缓存，不删除文件路径对应的原文件。
- 主窗口关闭后隐藏并继续记录；真正退出使用托盘菜单。暂停按钮可暂时停止记录。
- Ctrl+Alt+V 打开历史、Ctrl+Alt+S 截图，可在窗口设置。冲突会提示并回退旧设置；本机若截图键被占用，可改 Ctrl+Alt+Shift+S。
- 截图框选后进入编辑器，可添加红框、中文文字，文字默认 32px，可调 12–96px；Ctrl+Z 撤销，Esc 取消。
- “复制并完成”复制图片并保存一条历史；“保存 PNG”选择外部路径并确认覆盖，成功后也发布到剪切板与历史。取消不新增；发布失败保留标注供重试。
- 截图 PNG 复用历史图片缓存，不再另外积累自动截图副本；主动固定及手工另存文件不受普通历史条数清理限制。该限制不是磁盘字节配额。

默认数据目录：%USERPROFILE%\.java-web-small-tools\desktop。独立验收可在解压目录执行：

```bat
java -Dfile.encoding=UTF-8 -Dtoolbox.desktop.dataDir=./desktop-acceptance-data -jar toolbox-desktop.jar
```

仅启动一个实例，避免实例之间抢占热键。不要在真实历史上用很小的容量做清理测试。

源码构建：source 目录执行 mvn -o -B test，需要 Java 8 JDK、Maven 和已缓存固定构建依赖。sh package.sh 默认离线构建打包。运行包无需完整 Maven 缓存。

当前已在 Windows 11/Java 8 验证；Windows 7/10、多屏/混合 DPI、真实厂商数据库及长期资源使用仍需目标环境验收。不会自动识别密码或 Token，请按需要暂停记录。
