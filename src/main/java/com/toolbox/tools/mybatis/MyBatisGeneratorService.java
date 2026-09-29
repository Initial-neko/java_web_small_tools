package com.toolbox.tools.mybatis;

import org.mybatis.generator.api.GeneratedJavaFile;
import org.mybatis.generator.api.GeneratedXmlFile;
import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.config.CommentGeneratorConfiguration;
import org.mybatis.generator.config.Configuration;
import org.mybatis.generator.config.Context;
import org.mybatis.generator.config.JDBCConnectionConfiguration;
import org.mybatis.generator.config.JavaClientGeneratorConfiguration;
import org.mybatis.generator.config.JavaModelGeneratorConfiguration;
import org.mybatis.generator.config.JavaTypeResolverConfiguration;
import org.mybatis.generator.config.ModelType;
import org.mybatis.generator.config.SqlMapGeneratorConfiguration;
import org.mybatis.generator.config.TableConfiguration;
import org.mybatis.generator.internal.DefaultShellCallback;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MyBatis Generator 1.4.2 的程序化封装。
 *
 * 设计原则：
 * 1. JDBC 驱动不打进 toolbox.jar，通过 Configuration classPathEntry 动态加载；
 * 2. 输出固定为 Maven 风格目录，便于内网项目直接复制；
 * 3. Java 8 下使用 MyBatis3 + XMLMAPPER，生成代码不依赖 Dynamic SQL；
 * 4. Oracle/达梦只做 JDBC 元数据适配，不绑定厂商驱动版本。
 */
@Service
public class MyBatisGeneratorService {

    public Map<String, Object> generate(GeneratorRequest request) throws Exception {
        File root = new File(request.getOutputDir()).getCanonicalFile();
        File javaDir = new File(root, "src/main/java");
        File resourceDir = new File(root, "src/main/resources");
        ensureDirectory(javaDir);
        ensureDirectory(resourceDir);

        Configuration configuration = buildConfiguration(request, javaDir, resourceDir);

        List<String> warnings = new ArrayList<String>();
        DefaultShellCallback callback = new DefaultShellCallback(request.isOverwrite());
        MyBatisGenerator generator = new MyBatisGenerator(configuration, callback, warnings);
        generator.generate(null);

        List<String> javaFiles = new ArrayList<String>();
        for (GeneratedJavaFile file : generator.getGeneratedJavaFiles()) {
            javaFiles.add(file.getTargetPackage() + "." + file.getFileName());
        }

        List<String> xmlFiles = new ArrayList<String>();
        for (GeneratedXmlFile file : generator.getGeneratedXmlFiles()) {
            xmlFiles.add(file.getTargetPackage() + "/" + file.getFileName());
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("databaseType", request.getDatabaseType());
        result.put("outputDir", root.getAbsolutePath());
        result.put("tables", request.getTables());
        result.put("javaFileCount", javaFiles.size());
        result.put("xmlFileCount", xmlFiles.size());
        result.put("javaFiles", javaFiles);
        result.put("xmlFiles", xmlFiles);
        result.put("warnings", warnings);
        return result;
    }

    Configuration buildConfiguration(GeneratorRequest request, File javaDir, File resourceDir) throws Exception {
        Configuration configuration = new Configuration();
        for (String driverJar : request.getDriverJars()) {
            File jar = new File(driverJar).getCanonicalFile();
            if (!jar.isFile()) {
                throw new IllegalArgumentException("JDBC 驱动不存在: " + jar.getAbsolutePath());
            }
            configuration.addClasspathEntry(jar.getAbsolutePath());
        }

        Context context = new Context(ModelType.FLAT);
        context.setId("toolbox-mybatis-generator");
        context.setTargetRuntime("MyBatis3");
        context.addProperty("javaFileEncoding", "UTF-8");

        JDBCConnectionConfiguration jdbc = new JDBCConnectionConfiguration();
        jdbc.setDriverClass(request.getDriverClass());
        jdbc.setConnectionURL(request.getJdbcUrl());
        jdbc.setUserId(request.getUsername());
        jdbc.setPassword(request.getPassword());

        // Oracle 默认不返回 REMARKS。开启后 MBG 才能把字段备注写入生成代码。
        if (request.isOracle() && request.isAddRemarkComments()) {
            jdbc.addProperty("remarksReporting", "true");
        }
        context.setJdbcConnectionConfiguration(jdbc);

        CommentGeneratorConfiguration comments = new CommentGeneratorConfiguration();
        comments.addProperty("suppressDate", "true");
        comments.addProperty("addRemarkComments", String.valueOf(request.isAddRemarkComments()));
        // MBG 1.4.2 支持该属性；Java 8 项目避免强制 jakarta.annotation.Generated。
        comments.addProperty("useLegacyGeneratedAnnotation", "true");
        context.setCommentGeneratorConfiguration(comments);

        JavaTypeResolverConfiguration typeResolver = new JavaTypeResolverConfiguration();
        typeResolver.addProperty("forceBigDecimals", String.valueOf(request.isForceBigDecimals()));
        context.setJavaTypeResolverConfiguration(typeResolver);

        JavaModelGeneratorConfiguration model = new JavaModelGeneratorConfiguration();
        model.setTargetProject(javaDir.getAbsolutePath());
        model.setTargetPackage(request.getModelPackage());
        model.addProperty("trimStrings", String.valueOf(request.isTrimStrings()));
        context.setJavaModelGeneratorConfiguration(model);

        SqlMapGeneratorConfiguration sqlMap = new SqlMapGeneratorConfiguration();
        sqlMap.setTargetProject(resourceDir.getAbsolutePath());
        sqlMap.setTargetPackage(request.getXmlPackage());
        context.setSqlMapGeneratorConfiguration(sqlMap);

        JavaClientGeneratorConfiguration client = new JavaClientGeneratorConfiguration();
        client.setConfigurationType("XMLMAPPER");
        client.setTargetProject(javaDir.getAbsolutePath());
        client.setTargetPackage(request.getMapperPackage());
        context.setJavaClientGeneratorConfiguration(client);

        for (String tableName : request.getTables()) {
            TableConfiguration table = new TableConfiguration(context);
            table.setTableName(tableName);
            if (request.getSchema() != null && !request.getSchema().isEmpty()) {
                table.setSchema(request.getSchema());
            }
            table.addProperty("useActualColumnNames", String.valueOf(request.isUseActualColumnNames()));
            context.addTableConfiguration(table);
        }

        configuration.addContext(context);
        return configuration;
    }

    private void ensureDirectory(File directory) {
        if (directory.isDirectory()) return;
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalArgumentException("无法创建输出目录: " + directory.getAbsolutePath());
        }
    }
}
