package com.toolbox.tools.entity;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class GeneratedSourceCompiler {

    private GeneratedSourceCompiler() {
    }

    static ClassLoader compile(Map<String, String> sources) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("必须使用 JDK 运行测试，不能只使用 JRE");
        }

        Path root = Files.createTempDirectory("toolbox-generated-source-");
        Path sourceRoot = root.resolve("src");
        Path classRoot = root.resolve("classes");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(classRoot);

        List<File> javaFiles = new ArrayList<File>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            Path sourceFile = sourceRoot.resolve(entry.getKey().replace('.', '/') + ".java");
            Files.createDirectories(sourceFile.getParent());
            Files.write(sourceFile, entry.getValue().getBytes(StandardCharsets.UTF_8));
            javaFiles.add(sourceFile.toFile());
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(javaFiles);

        List<String> options = Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-source", "8",
                "-target", "8",
                "-encoding", "UTF-8",
                "-d", classRoot.toString()
        );

        Boolean ok = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
        fileManager.close();

        if (!Boolean.TRUE.equals(ok)) {
            StringBuilder message = new StringBuilder("生成源码编译失败:\n");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                message.append(diagnostic.getKind()).append(" ")
                        .append(diagnostic.getSource() == null ? "" : diagnostic.getSource().getName())
                        .append(":").append(diagnostic.getLineNumber())
                        .append(" ").append(diagnostic.getMessage(null)).append("\n");
            }
            throw new AssertionError(message.toString());
        }

        return new URLClassLoader(new URL[]{classRoot.toUri().toURL()},
                Thread.currentThread().getContextClassLoader());
    }
}
