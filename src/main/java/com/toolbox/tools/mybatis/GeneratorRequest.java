package com.toolbox.tools.mybatis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MyBatis Generator 请求参数。
 * 保持纯 Java 8 POJO，便于后续 CLI/Web/配置文件复用。
 */
public class GeneratorRequest {

    private String databaseType;
    private String driverClass;
    private List<String> driverJars = new ArrayList<String>();
    private String jdbcUrl;
    private String username;
    private String password;
    private String schema;
    private List<String> tables = new ArrayList<String>();

    private String outputDir;
    private String modelPackage;
    private String mapperPackage;
    private String xmlPackage;

    private boolean overwrite;
    private boolean forceBigDecimals;
    private boolean trimStrings;
    private boolean useActualColumnNames;
    private boolean addRemarkComments;

    public static GeneratorRequest from(Map<String, Object> params) {
        GeneratorRequest request = new GeneratorRequest();
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
        request.schema = str(params.get("schema"), "");
        request.tables = list(params.get("tables"));

        request.outputDir = str(params.get("outputDir"), "./generated/mybatis");
        request.modelPackage = str(params.get("modelPackage"), "com.example.model");
        request.mapperPackage = str(params.get("mapperPackage"), "com.example.mapper");
        request.xmlPackage = str(params.get("xmlPackage"), "mapper");

        request.overwrite = bool(params.get("overwrite"), true);
        request.forceBigDecimals = bool(params.get("forceBigDecimals"), false);
        request.trimStrings = bool(params.get("trimStrings"), false);
        request.useActualColumnNames = bool(params.get("useActualColumnNames"), false);
        request.addRemarkComments = bool(params.get("addRemarkComments"), true);

        request.validate();
        return request;
    }

    private void validate() {
        require(driverClass, "driverClass 不能为空；dm/oracle 可通过 databaseType 自动推断");
        require(jdbcUrl, "jdbcUrl 不能为空");
        require(username, "username 不能为空");
        require(outputDir, "outputDir 不能为空");
        require(modelPackage, "modelPackage 不能为空");
        require(mapperPackage, "mapperPackage 不能为空");
        require(xmlPackage, "xmlPackage 不能为空");
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("tables 不能为空，可填写单表、多表或 JDBC 通配符");
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

    private static boolean bool(Object value, boolean def) {
        if (value == null) return def;
        if (value instanceof Boolean) return (Boolean) value;
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return def;
        return Boolean.parseBoolean(text);
    }

    private static List<String> list(Object value) {
        List<String> result = new ArrayList<String>();
        if (value == null) return result;

        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                addIfPresent(result, item == null ? null : String.valueOf(item));
            }
            return result;
        }

        String text = String.valueOf(value);
        String[] parts = text.split("[,;\\r\\n]+");
        for (String part : parts) {
            addIfPresent(result, part);
        }
        return result;
    }

    private static void addIfPresent(List<String> result, String value) {
        if (value == null) return;
        String text = value.trim();
        if (!text.isEmpty()) result.add(text);
    }

    public String getDatabaseType() { return databaseType; }
    public String getDriverClass() { return driverClass; }
    public List<String> getDriverJars() { return driverJars; }
    public String getJdbcUrl() { return jdbcUrl; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getSchema() { return schema; }
    public List<String> getTables() { return tables; }
    public String getOutputDir() { return outputDir; }
    public String getModelPackage() { return modelPackage; }
    public String getMapperPackage() { return mapperPackage; }
    public String getXmlPackage() { return xmlPackage; }
    public boolean isOverwrite() { return overwrite; }
    public boolean isForceBigDecimals() { return forceBigDecimals; }
    public boolean isTrimStrings() { return trimStrings; }
    public boolean isUseActualColumnNames() { return useActualColumnNames; }
    public boolean isAddRemarkComments() { return addRemarkComments; }

    public boolean isOracle() { return "oracle".equals(databaseType); }
    public boolean isDm() { return "dm".equals(databaseType); }
}
