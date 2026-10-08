# XML 工具规则说明

对应工具：`xml-format`、`xml-to-json`、`xml-to-java`。

原则与项目一致：Java 8、零新增依赖（JDK 内置 DOM/Transformer）、确定性规则、不伪造结果。

## 转换规则（xml-to-json / xml-to-java 共用）

| XML 形态 | JSON 输出 | 说明 |
|---|---|---|
| 属性 `id="1"` | `"@id": 1` | 统一 `@` 前缀 |
| 纯文本元素 `<age>18</age>` | `"age": 18` | 推断开关开启时数字/布尔转类型 |
| 空元素 `<a/>` | `"a": ""` | |
| 同名重复子元素 | 数组 | 出现 1 次保持单值，出现 ≥2 次转数组 |
| 容器元素 `<orders><order/><order/></orders>` | `"orders": {"order": [...]}` | 容器是对象，内部才是数组 |
| 混合内容 `<p>Hello <b>x</b>!</p>` | `"#text": "Hello !"` | 文本挂 `#text` 键 |
| 命名空间 | 前缀保留在键名中 | `ns:tag` -> `"ns:tag"`，`xmlns:ns` -> `"@xmlns:ns"` |
| 注释、处理指令 | 忽略 | |
| CDATA | 按文本处理 | |

### 类型推断（inferTypes 开关，默认开启）

- 整数 -> Integer，超出范围 -> Long，再超出 -> BigInteger
- 小数/科学计数 -> BigDecimal
- `true` / `false` -> Boolean
- 其他（含 `1,5`、`Infinity` 等非法数字）保持字符串
- **前导零数字保持字符串**（如 `007`、`-007`），避免编号类数据丢格式
- 元素文本输出前去除首尾空白（排版空白视为噪音）；混合内容中间的空白保留
- 开关关闭时全部输出字符串

### 容器元素语义（重要）

`<orders>` 包含两个 `<order>` 时，`orders` 的值是**对象**（含一个 `order` 数组字段），
而不是数组本身。生成实体时对应 `Orders` 嵌套类内含 `List<OrderItem> order` 字段。
若希望直接得到 `List`，请把重复元素写在同一父元素下（无中间容器）。

## xml-to-java

- 先按上述规则转为 JSON，再复用 `JsonEntitySchemaInferer` 与现有渲染器
- 类名默认取 XML 根元素名（如 `<user>` -> `User`），可用 `className` 参数覆盖
- 支持 `normal` / `lombok` / `mapping` 三种输出，与 JSON → Java 工具一致
- mapping 模式生成的 `json.getInteger("@id")` 等取值键与 XML 转 JSON 输出的键完全对应，
  可直接对 `xml-to-json` 的输出使用

## xml-format

- `format`：移除元素间排版空白后按 2 空格缩进重排，输出统一 UTF-8 声明
- `compress`：单行输出；有意义空白的混合内容（如 `Hello <b>x</b>!`）原样保留
- `validate`：校验合法性并返回根元素名与元素总数

## 安全边界（XXE 防护）

解析阶段直接禁用：

- `disallow-doctype-decl`：文档中出现 `<!DOCTYPE ...>` 即拒绝解析
- 外部一般实体 / 参数实体：禁用
- XInclude：禁用

因此**不支持 DTD 与实体引用**，这是刻意的安全取舍：本地工具不解析不可信来源的 DOCTYPE，
企业内网场景也不依赖 DTD 校验。带 DOCTYPE 的文档会明确报错，不做降级解析。

## 测试范围

- `XmlJsonConverterTest`：属性/嵌套/重复元素/类型推断/前导零/混合内容/CDATA/命名空间/注释/DOCTYPE 拒绝
- `XmlToJsonToolTest`：端到端、压缩输出、推断开关、非法输入
- `XmlFormatToolTest`：美化缩进、排版规范化、压缩、混合内容空白、校验
- `XmlToJavaToolTest`：normal/lombok/mapping 端到端、容器元素语义、生成源码实际编译验证
