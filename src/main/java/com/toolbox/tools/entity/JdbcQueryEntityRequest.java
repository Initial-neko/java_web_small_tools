package com.toolbox.tools.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JdbcQueryEntityRequest {

    private String databaseType;
    private String driverClass;
    private List<String> driverJars = new ArrayList<String>();
    private String jdbcUrl;
    private String username;
    private String password;
    private String sql;
    private String className;
    private String packageName;
    private String mode;

    public static JdbcQueryEntityRequest from(Map<String, Object> params) {
        JdbcQueryEntityRequest request = new JdbcQueryEntityRequest();
        request.databaseType = str(params.get("databaseType"), "dm").toLowerCase(Locale.ROOT);
        request.driverClass = str(params.get("driverClass"), "");
        if (request.driverClass.isEmpty()) {
            if ("dm".equals(request.databaseType)) {
                request.driverClass = "dm.jdbc.driver.DmDriver";
            } else if ("oracle".equals(request.databaseType)) {
                request.driverClass = "oracle.jdbc.OracleDriver";
            }
        }

        request.driverJars = list(params.get("driverJars"));
        request.jdbcUrl = str(params.get("jdbcUrl"), "");
        request.username = str(params.get("username"), "");
        request.password = str(params.get("password"), "");
        request.sql = str(params.get("sql"), "");
        request.className = str(params.get("className"), "QueryResult");
        request.packageName = str(params.get("packageName"), "com.example.model");
        request.mode = str(params.get("mode"), "normal");

        request.validate();
        return request;
    }

    private void validate() {
        require(driverClass, "driverClass 不能为空；dm/oracle 可通过 databaseType 自动推断");
        require(jdbcUrl, "jdbcUrl 不能为空");
        require(username, "username 不能为空");
        require(sql, "sql 不能为空");
        require(className, "className 不能为空");

        String normalized = sql.trim().toLowerCase(Locale.ROOT);
        if (!(normalized.startsWith("select") || normalized.startsWith("with"))) {
            throw new IllegalArgumentException("JDBC Query Generator 只允许 SELECT/WITH 查询");
        }

        if (!"normal".equals(mode) && !"lombok".equals(mode)) {
            throw new IllegalArgumentException("mode 仅支持 normal/lombok");
        }
    }

    private static void require(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String str(Object value, String def) {
        if (value == null) return def;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? def : text;
    }

    private static List<String> list(Object value) {
        List<String> result = new ArrayList<String>();
        if (value == null) return result;
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) add(result, item);
            return result;
        }
        String[] parts = String.valueOf(value).split("[,;\\r\\n]+");
        for (String part : parts) add(result, part);
        return result;
    }

    private static void add(List<String> result, Object value) {
        if (value == null) return;
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) result.add(text);
    }

    public String getDatabaseType() { return databaseType; }
    public String getDriverClass() { return driverClass; }
    public List<String> getDriverJars() { return driverJars; }
    public String getJdbcUrl() { return jdbcUrl; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getSql() { return sql; }
    public String getClassName() { return className; }
    public String getPackageName() { return packageName; }
    public String getMode() { return mode; }
}
