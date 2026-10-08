package com.toolbox.tools.xml;

import com.toolbox.core.ToolResult;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlToJavaToolTest {

    private final XmlToJavaTool tool = new XmlToJavaTool();

    private static final String SAMPLE_XML =
            "<user id=\"1\" vip=\"true\"><name>Alice</name>"
                    + "<order id=\"101\"><amount>9.90</amount></order>"
                    + "<order id=\"102\"><amount>19.80</amount></order></user>";

    @Test
    void shouldGenerateNormalEntityFromClassNamedByRootTag() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", SAMPLE_XML);
        params.put("packageName", "demo");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("User", data.get("className"));
        String output = String.valueOf(data.get("output"));
        assertTrue(output.contains("public class User"));
        assertTrue(output.contains("private Integer id;"));
        assertTrue(output.contains("private Boolean vip;"));
        assertTrue(output.contains("private List<OrderItem> order;"));
        assertTrue(output.contains("public static class OrderItem"));
        assertTrue(output.contains("private BigDecimal amount;"));
    }

    @Test
    void shouldMapContainerElementAsNestedObjectWithListInside() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", "<user><orders>"
                + "<order id=\"1\"><amount>1.50</amount></order>"
                + "<order id=\"2\"><amount>2.50</amount></order>"
                + "</orders></user>");
        params.put("packageName", "demo");
        params.put("mode", "normal");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        String output = String.valueOf(((Map<?, ?>) result.getData()).get("output"));
        assertTrue(output.contains("private Orders orders;"));
        assertTrue(output.contains("public static class Orders"));
        assertTrue(output.contains("private List<OrderItem> order;"));
    }

    @Test
    void shouldUseExplicitClassNameWhenProvided() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", "<order><amount>1</amount></order>");
        params.put("className", "OrderEntity");
        params.put("packageName", "demo");
        params.put("mode", "normal");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        assertEquals("OrderEntity", ((Map<?, ?>) result.getData()).get("className"));
        assertTrue(String.valueOf(((Map<?, ?>) result.getData()).get("output"))
                .contains("public class OrderEntity"));
    }

    @Test
    void shouldGenerateLombokEntity() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", SAMPLE_XML);
        params.put("mode", "lombok");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        String output = String.valueOf(((Map<?, ?>) result.getData()).get("output"));
        assertTrue(output.contains("@Data"));
        assertTrue(output.contains("lombok.Data"));
    }

    @Test
    void shouldGenerateMappingCodeWithXmlAttributeKeys() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", SAMPLE_XML);
        params.put("mode", "mapping");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        String output = String.valueOf(((Map<?, ?>) result.getData()).get("output"));
        assertTrue(output.contains("fromJson(JSONObject json)"));
        assertTrue(output.contains("json.getInteger(\"@id\")"));
        assertTrue(output.contains("json.getList(\"order\", User.OrderItem.class)"));
    }

    @Test
    void shouldCompileGeneratedNormalEntity() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", SAMPLE_XML);
        params.put("packageName", "demo");
        params.put("mode", "normal");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        String source = String.valueOf(((Map<?, ?>) result.getData()).get("output"));

        Path root = Files.createTempDirectory("toolbox-xml-generated-");
        Path sourceFile = root.resolve("demo/User.java");
        Files.createDirectories(sourceFile.getParent());
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return; // JRE 环境跳过编译验证
        }
        Path classRoot = root.resolve("classes");
        Files.createDirectories(classRoot); // Java 8 javac 不会自动创建输出目录
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        Iterable<? extends javax.tools.JavaFileObject> units =
                fileManager.getJavaFileObjectsFromFiles(Arrays.asList(sourceFile.toFile()));
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null,
                Arrays.asList("-classpath", System.getProperty("java.class.path"),
                        "-encoding", "UTF-8", "-d", classRoot.toString()),
                null, units);
        Boolean ok = task.call();
        fileManager.close();

        assertTrue(Boolean.TRUE.equals(ok), "生成的 Normal Entity 源码应可编译");
    }

    @Test
    void shouldFailOnInvalidXml() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", "<root><a></root>");

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("XML 解析失败"));
    }

    @Test
    void shouldFailOnUnknownMode() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("input", "<root><a>1</a></root>");
        params.put("mode", "magic");

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
    }
}
