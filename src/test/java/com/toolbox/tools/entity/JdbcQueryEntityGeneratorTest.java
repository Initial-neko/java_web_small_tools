package com.toolbox.tools.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JdbcQueryEntityGeneratorTest {

    @Test
    void shouldRunFullFakeJdbcMetadataFlowAndCompileEntity() throws Exception {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("databaseType", "custom");
        params.put("driverClass", FakeDriver.class.getName());
        params.put("jdbcUrl", "jdbc:fake:test");
        params.put("username", "tester");
        params.put("password", "secret");
        params.put("sql", "SELECT ID, TOTAL, CREATED_AT, RAW_DATA, USER_NAME FROM T");
        params.put("className", "QueryRow");
        params.put("packageName", "demo.jdbc");
        params.put("mode", "normal");

        JdbcQueryEntityRequest request = JdbcQueryEntityRequest.from(params);
        EntitySchema schema = new JdbcQueryEntityGenerator().generate(request);

        assertEquals("Long", field(schema, "ID").getJavaType());
        assertEquals("BigDecimal", field(schema, "TOTAL").getJavaType());
        assertEquals("LocalDateTime", field(schema, "CREATED_AT").getJavaType());
        assertEquals("byte[]", field(schema, "RAW_DATA").getJavaType());
        assertEquals("String", field(schema, "USER_NAME").getJavaType());

        String source = new NormalJavaRenderer().render(schema);
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("demo.jdbc.QueryRow", source);
        ClassLoader loader = GeneratedSourceCompiler.compile(sources);

        Class<?> type = loader.loadClass("demo.jdbc.QueryRow");
        assertNotNull(type.getMethod("getId"));
        assertNotNull(type.getMethod("getTotal"));
        assertNotNull(type.getMethod("getCreatedAt"));
        assertNotNull(type.getMethod("getRawData"));
    }

    @Test
    void shouldRejectNonSelectQuery() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("databaseType", "custom");
        params.put("driverClass", FakeDriver.class.getName());
        params.put("jdbcUrl", "jdbc:fake:test");
        params.put("username", "tester");
        params.put("sql", "DELETE FROM T");

        try {
            JdbcQueryEntityRequest.from(params);
        } catch (IllegalArgumentException e) {
            assertEquals("JDBC Query Generator 只允许 SELECT/WITH 查询", e.getMessage());
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private FieldSchema field(EntitySchema schema, String sourceName) {
        for (FieldSchema field : schema.getFields()) {
            if (sourceName.equals(field.getSourceName())) return field;
        }
        throw new AssertionError("field not found: " + sourceName);
    }

    public static class FakeDriver implements Driver {

        @Override
        public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) return null;
            return proxy(Connection.class, new JdbcHandler("connection"));
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:fake:");
        }

        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return false; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
    }

    private static class JdbcHandler implements InvocationHandler {
        private final String role;

        JdbcHandler(String role) {
            this.role = role;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            if ("connection".equals(role)) {
                if ("prepareStatement".equals(name)) {
                    return proxy(PreparedStatement.class, new JdbcHandler("statement"));
                }
                if ("setReadOnly".equals(name) || "close".equals(name)) return null;
            }

            if ("statement".equals(role)) {
                if ("executeQuery".equals(name)) {
                    return proxy(ResultSet.class, new JdbcHandler("resultSet"));
                }
                if ("setMaxRows".equals(name) || "setQueryTimeout".equals(name) || "close".equals(name)) return null;
            }

            if ("resultSet".equals(role)) {
                if ("getMetaData".equals(name)) {
                    return proxy(ResultSetMetaData.class, new JdbcHandler("meta"));
                }
                if ("close".equals(name)) return null;
            }

            if ("meta".equals(role)) {
                int column = args != null && args.length > 0 && args[0] instanceof Integer
                        ? (Integer) args[0]
                        : 0;
                if ("getColumnCount".equals(name)) return 5;
                if ("getColumnLabel".equals(name) || "getColumnName".equals(name)) return labels()[column - 1];
                if ("getColumnType".equals(name)) return jdbcTypes()[column - 1];
                if ("getColumnTypeName".equals(name)) return typeNames()[column - 1];
                if ("getPrecision".equals(name)) return precisions()[column - 1];
                if ("getScale".equals(name)) return scales()[column - 1];
            }

            return defaultValue(method.getReturnType());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                JdbcQueryEntityGeneratorTest.class.getClassLoader(),
                new Class<?>[]{type},
                handler
        );
    }

    private static String[] labels() {
        return new String[]{"ID", "TOTAL", "CREATED_AT", "RAW_DATA", "USER_NAME"};
    }

    private static int[] jdbcTypes() {
        return new int[]{Types.NUMERIC, Types.DECIMAL, Types.TIMESTAMP, Types.BLOB, Types.VARCHAR};
    }

    private static String[] typeNames() {
        return new String[]{"NUMBER", "DECIMAL", "TIMESTAMP", "BLOB", "VARCHAR2"};
    }

    private static int[] precisions() {
        return new int[]{10, 18, 0, 0, 100};
    }

    private static int[] scales() {
        return new int[]{0, 2, 0, 0, 0};
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
