package com.toolbox.tools.mybatis;

import org.junit.jupiter.api.Test;
import org.mybatis.generator.config.Configuration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyBatisGeneratorServiceTest {

    @Test
    void shouldBuildJava8DmConfigurationWithoutBundledDriver() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("databaseType", "dm");
        params.put("jdbcUrl", "jdbc:dm://127.0.0.1:5236");
        params.put("username", "APP");
        params.put("password", "secret");
        params.put("schema", "APP");
        params.put("tables", "T_ORDER,T_ORDER_ITEM");

        GeneratorRequest request = GeneratorRequest.from(params);

        assertEquals("dm.jdbc.driver.DmDriver", request.getDriverClass());
        assertEquals(2, request.getTables().size());
        assertTrue(request.isAddRemarkComments());

        MyBatisGeneratorService service = new MyBatisGeneratorService();
        Configuration configuration = service.buildConfiguration(
                request,
                new File("target/test-generated/src/main/java"),
                new File("target/test-generated/src/main/resources"));

        configuration.validate();
        assertEquals(1, configuration.getContexts().size());
        assertEquals("MyBatis3", configuration.getContexts().get(0).getTargetRuntime());
    }

    @Test
    void shouldInferOracleDriver() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("databaseType", "oracle");
        params.put("jdbcUrl", "jdbc:oracle:thin:@//127.0.0.1:1521/ORCL");
        params.put("username", "APP");
        params.put("tables", "T_ORDER");

        GeneratorRequest request = GeneratorRequest.from(params);

        assertEquals("oracle.jdbc.OracleDriver", request.getDriverClass());
        assertEquals("oracle", request.getDatabaseType());
    }
}
