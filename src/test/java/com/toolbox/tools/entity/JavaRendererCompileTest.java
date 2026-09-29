package com.toolbox.tools.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JavaRendererCompileTest {

    private final JsonEntitySchemaInferer inferer = new JsonEntitySchemaInferer();

    @Test
    void normalEntityShouldCompileWithJava8Compiler() throws Exception {
        EntitySchema schema = sampleSchema();
        String source = new NormalJavaRenderer().render(schema);

        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("demo.User", source);

        ClassLoader loader = GeneratedSourceCompiler.compile(sources);
        assertNotNull(loader.loadClass("demo.User"));
        assertNotNull(loader.loadClass("demo.User$OrdersItem"));
    }

    @Test
    void lombokEntityShouldCompileAndActuallyGenerateAccessors() throws Exception {
        EntitySchema schema = sampleSchema();
        String source = new LombokJavaRenderer().render(schema);

        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("demo.User", source);

        ClassLoader loader = GeneratedSourceCompiler.compile(sources);
        Class<?> userClass = loader.loadClass("demo.User");

        assertNotNull(userClass.getMethod("getName"));
        assertNotNull(userClass.getMethod("setName", String.class));
    }

    @Test
    void mappingCodeShouldCompileAndRunAgainstFastjson2() throws Exception {
        EntitySchema schema = sampleSchema();
        String entitySource = new NormalJavaRenderer().render(schema);
        String mappingMethod = new Fastjson2MappingRenderer().render(schema);

        String mapperSource =
                "package demo;\n" +
                "import com.alibaba.fastjson2.JSONObject;\n" +
                "public class UserMapper {\n" +
                indent(mappingMethod, "    ") +
                "}\n";

        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("demo.User", entitySource);
        sources.put("demo.UserMapper", mapperSource);

        ClassLoader loader = GeneratedSourceCompiler.compile(sources);
        Class<?> mapperClass = loader.loadClass("demo.UserMapper");
        Method fromJson = mapperClass.getMethod("fromJson", JSONObject.class);

        JSONObject json = JSON.parseObject("{\"name\":\"Alice\",\"orders\":[{\"id\":7,\"amount\":12.50}]}");
        Object user = fromJson.invoke(null, json);

        Method getName = user.getClass().getMethod("getName");
        assertEquals("Alice", getName.invoke(user));

        Method getOrders = user.getClass().getMethod("getOrders");
        java.util.List<?> orders = (java.util.List<?>) getOrders.invoke(user);
        assertEquals(1, orders.size());
        Method getId = orders.get(0).getClass().getMethod("getId");
        assertEquals(7, getId.invoke(orders.get(0)));
    }

    private EntitySchema sampleSchema() {
        return inferer.infer(
                "{\"name\":\"Alice\",\"orders\":[{\"id\":7,\"amount\":12.50}]}",
                "User", "demo");
    }

    private String indent(String text, String prefix) {
        String[] lines = text.split("\\n", -1);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (!line.isEmpty()) out.append(prefix).append(line);
            out.append("\n");
        }
        return out.toString();
    }
}
