package com.toolbox.tools.entity;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
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
        Path root = Files.createTempDirectory("toolbox-generated-source-");
        Path sourceRoot = root.resolve("src");
        Path classRoot = root.resolve("classes");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(classRoot);

        List<File> javaFiles = writeSources(sources, sourceRoot);

        if (containsLombok(sources)) {
            compileWithExternalJavac(javaFiles, classRoot);
        } else {
            compileWithJavaCompiler(javaFiles, classRoot);
        }

        return new URLClassLoader(new URL[]{classRoot.toUri().toURL()},
                Thread.currentThread().getContextClassLoader());
    }

    private static List<File> writeSources(Map<String, String> sources, Path sourceRoot) throws Exception {
        List<File> javaFiles = new ArrayList<File>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            Path sourceFile = sourceRoot.resolve(entry.getKey().replace('.', '/') + ".java");
            Files.createDirectories(sourceFile.getParent());
            Files.write(sourceFile, entry.getValue().getBytes(StandardCharsets.UTF_8));
            javaFiles.add(sourceFile.toFile());
        }
        return javaFiles;
    }

    private static void compileWithJavaCompiler(List<File> javaFiles, Path classRoot) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("必须使用 JDK 运行测试，不能只使用 JRE");
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
    }

    private static void compileWithExternalJavac(List<File> javaFiles, Path classRoot) throws Exception {
        String lombokJar = lombokJarPath();
        List<String> command = new ArrayList<String>();
        command.add(javacExecutable());
        command.add("-cp");
        command.add(lombokJar);
        command.add("-processorpath");
        command.add(lombokJar);
        command.add("-processor");
        command.add("lombok.launch.AnnotationProcessorHider$AnnotationProcessor");
        command.add("-source");
        command.add("8");
        command.add("-target");
        command.add("8");
        command.add("-encoding");
        command.add("UTF-8");
        command.add("-d");
        command.add(classRoot.toString());
        for (File javaFile : javaFiles) {
            command.add(javaFile.getAbsolutePath());
        }

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        StringBuilder output = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        int exit = process.waitFor();
        if (exit != 0) {
            throw new AssertionError("Lombok 生成源码 javac 编译失败:\n" + output);
        }
    }

    private static boolean containsLombok(Map<String, String> sources) {
        for (String source : sources.values()) {
            if (source.contains("import lombok.Data;") || source.contains("@Data")) {
                return true;
            }
        }
        return false;
    }

    private static String javacExecutable() {
        File bin = new File(System.getProperty("java.home"), "bin");
        File javac = new File(bin, isWindows() ? "javac.exe" : "javac");
        if (!javac.isFile()) {
            throw new IllegalStateException("未找到 javac: " + javac.getAbsolutePath());
        }
        return javac.getAbsolutePath();
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    private static String lombokJarPath() {
        try {
            return new File(lombok.Data.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getAbsolutePath();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("无法定位 lombok.jar", e);
        }
    }
}
