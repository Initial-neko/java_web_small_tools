package com.toolbox.tools.mybatis;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("real-db")
class MyBatisGeneratorRealDatabaseTest {

    @Test
    void dmShouldGenerateAgainstRealDatabaseWhenEnvironmentIsProvided() throws Exception {
        runIfConfigured("DM", "dm");
    }

    @Test
    void oracleShouldGenerateAgainstRealDatabaseWhenEnvironmentIsProvided() throws Exception {
        runIfConfigured("ORACLE", "oracle");
    }

    private void runIfConfigured(String prefix, String databaseType) throws Exception {
        String driverJar = env("TOOLBOX_" + prefix + "_DRIVER_JAR");
        String jdbcUrl = env("TOOLBOX_" + prefix + "_JDBC_URL");
        String username = env("TOOLBOX_" + prefix + "_USERNAME");
        String password = env("TOOLBOX_" + prefix + "_PASSWORD");
        String schema = env("TOOLBOX_" + prefix + "_SCHEMA");
        String table = env("TOOLBOX_" + prefix + "_TABLE");

        Assumptions.assumeTrue(notBlank(driverJar) && notBlank(jdbcUrl)
                        && notBlank(username) && notBlank(table),
                prefix + " 真实库环境变量未配置，跳过真实 JDBC 集成测试");

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("databaseType", databaseType);
        params.put("driverJars", driverJar);
        params.put("jdbcUrl", jdbcUrl);
        params.put("username", username);
        params.put("password", password == null ? "" : password);
        params.put("schema", schema == null ? "" : schema);
        params.put("tables", table);
        params.put("modelPackage", "com.toolbox.verify.model");
        params.put("mapperPackage", "com.toolbox.verify.mapper");
        params.put("xmlPackage", "mapper");
        params.put("outputDir", Files.createTempDirectory("toolbox-" + databaseType + "-mbg-").toString());
        params.put("overwrite", true);
        params.put("addRemarkComments", true);

        GeneratorRequest request = GeneratorRequest.from(params);
        Map<String, Object> result = new MyBatisGeneratorService().generate(request);

        assertTrue(((Number) result.get("javaFileCount")).intValue() > 0,
                "真实数据库应至少生成一个 Java 文件");
        assertTrue(((Number) result.get("xmlFileCount")).intValue() > 0,
                "真实数据库应至少生成一个 XML 文件");
    }

    private String env(String name) {
        return System.getenv(name);
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
