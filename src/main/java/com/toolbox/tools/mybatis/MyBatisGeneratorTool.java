package com.toolbox.tools.mybatis;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 数据库表 -> Java Model / Mapper / XML。
 */
@Component
public class MyBatisGeneratorTool implements Tool {

    @Autowired
    private MyBatisGeneratorService service;

    @Override
    public String getName() {
        return "mybatis-generator";
    }

    @Override
    public String getDisplayName() {
        return "MyBatis Generator";
    }

    @Override
    public String getDescription() {
        return "数据库表生成 Java Model、Mapper 和 XML，支持外置 Oracle/达梦 JDBC 驱动";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            GeneratorRequest request = GeneratorRequest.from(params);
            return ToolResult.ok("生成完成", service.generate(request));
        } catch (Exception e) {
            return ToolResult.fail("MyBatis 生成失败: " + rootMessage(e));
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
                ? current.getClass().getSimpleName()
                : message;
    }
}
