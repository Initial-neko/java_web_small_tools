package com.toolbox.tools.entity;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * JDBC SELECT/CTE 查询结果 -> EntitySchema。
 *
 * 为了兼容内网厂商驱动，不要求 Driver 注册进系统 DriverManager，
 * 而是使用独立 URLClassLoader 实例化 java.sql.Driver 并直接 connect。
 */
public class JdbcQueryEntityGenerator {

    private final EntityTypeMapper typeMapper = new EntityTypeMapper();

    public EntitySchema generate(JdbcQueryEntityRequest request) throws Exception {
        URLClassLoader loader = null;
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;

        try {
            ClassLoader driverLoader = Thread.currentThread().getContextClassLoader();
            if (!request.getDriverJars().isEmpty()) {
                URL[] urls = new URL[request.getDriverJars().size()];
                for (int i = 0; i < request.getDriverJars().size(); i++) {
                    File file = new File(request.getDriverJars().get(i)).getCanonicalFile();
                    if (!file.isFile()) {
                        throw new IllegalArgumentException("JDBC 驱动不存在: " + file.getAbsolutePath());
                    }
                    urls[i] = file.toURI().toURL();
                }
                loader = new URLClassLoader(urls, driverLoader);
                driverLoader = loader;
            }

            Class<?> driverType = Class.forName(request.getDriverClass(), true, driverLoader);
            Object instance = driverType.newInstance();
            if (!(instance instanceof Driver)) {
                throw new IllegalArgumentException("driverClass 未实现 java.sql.Driver: " + request.getDriverClass());
            }

            Properties properties = new Properties();
            properties.setProperty("user", request.getUsername());
            properties.setProperty("password", request.getPassword());

            connection = ((Driver) instance).connect(request.getJdbcUrl(), properties);
            if (connection == null) {
                throw new IllegalArgumentException("JDBC Driver 不接受 URL: " + request.getJdbcUrl());
            }

            try {
                connection.setReadOnly(true);
            } catch (Exception ignored) {
                // 某些厂商驱动不支持 setReadOnly，不影响 metadata 生成。
            }

            statement = connection.prepareStatement(request.getSql());
            try {
                statement.setMaxRows(1);
            } catch (Exception ignored) {
            }
            try {
                statement.setQueryTimeout(30);
            } catch (Exception ignored) {
            }

            resultSet = statement.executeQuery();
            ResultSetMetaData meta = resultSet.getMetaData();
            return schemaFromMeta(meta, request.getClassName(), request.getPackageName());
        } finally {
            closeQuietly(resultSet);
            closeQuietly(statement);
            closeQuietly(connection);
            if (loader != null) {
                try {
                    loader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    EntitySchema schemaFromMeta(ResultSetMetaData meta, String className, String packageName) throws Exception {
        List<FieldSchema> fields = new ArrayList<FieldSchema>();
        Map<String, Integer> nameCounts = new HashMap<String, Integer>();

        int count = meta.getColumnCount();
        for (int i = 1; i <= count; i++) {
            String label = meta.getColumnLabel(i);
            if (label == null || label.trim().isEmpty()) {
                label = meta.getColumnName(i);
            }
            String sourceName = label == null ? "column" + i : label.trim();
            String fieldName = uniqueName(JavaNameUtils.toFieldName(sourceName), nameCounts);
            String sourceType = meta.getColumnTypeName(i);
            TypeSchema type = typeMapper.fromJdbc(
                    meta.getColumnType(i),
                    meta.getPrecision(i),
                    meta.getScale(i),
                    sourceType
            );

            fields.add(new FieldSchema(
                    sourceName,
                    fieldName,
                    type,
                    sourceType,
                    ""
            ));
        }

        if (fields.isEmpty()) {
            throw new IllegalArgumentException("查询结果没有字段");
        }

        return new EntitySchema(
                packageName,
                JavaNameUtils.toClassName(className),
                fields
        );
    }

    private String uniqueName(String base, Map<String, Integer> counts) {
        Integer count = counts.get(base);
        if (count == null) {
            counts.put(base, 1);
            return base;
        }
        int next = count + 1;
        counts.put(base, next);
        return base + next;
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
