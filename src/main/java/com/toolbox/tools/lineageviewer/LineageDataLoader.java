package com.toolbox.tools.lineageviewer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * SQL 记录加载器。
 *
 * <p>不需要数据库：默认从 classpath 下的 {@code lineage-viewer/sqls.json} 读取全量 SQL 记录。
 * 如果存在外部文件（{@code -Dlineage.data-file=/path/to/sqls.json} 或工作目录下的
 * {@code lineage-sqls.json}），则优先使用外部文件，方便替换成真实数据。</p>
 *
 * <p><b>为什么先读成字节再解析：</b>原先的实现直接对整个文件做完整 DOM 解析，
 * 94MB 的 JSON 会瞬时占用约 400MB 堆内存，很容易触发超时甚至 OOM。
     * 当前仍使用完整 DOM 解析，超大文件需要足够堆内存。
     * 字节解析避免中间 UTF-16 字符串，但并非流式读取。</p>
 */
public class LineageDataLoader {

    /** classpath 下的默认样例数据 */
    public static final String DEFAULT_CLASSPATH = "lineage-viewer/sqls.json";

    /** 工作目录下的外部数据文件名（相对 -Dlineage.data-file 更轻量的替换方式） */
    private static final String CWD_FILE = "lineage-sqls.json";

    /** 单文件超过该体积时在日志中给出提示（与工具的 50MB 上传上限保持一致） */
    private static final long LARGE_FILE_BYTES = 20L * 1024 * 1024;

    /**
     * 从外部文件或 classpath 载入全部 SQL 记录。
     *
     * <p>兼容两种 JSON 形态：</p>
     * <ol>
     *   <li>数组：[ {"source":..,"inputTables":[...]} , ... ]</li>
     *   <li>对象：{ "records": [ ... ] }</li>
     * </ol>
     *
     * <p>同时兼容 {@code inputTables} 写成字符串（逗号/分号分隔）的情况，
     * 这样可以无缝对接上游系统常见的「清单字段」格式。</p>
     */
    public List<LineageSqlRecord> load() {
        // 1. 优先外部文件（-Dlineage.data-file）
        String external = System.getProperty("lineage.data-file");
        if (external != null && external.trim().length() > 0) {
            File f = new File(external);
            if (f.isFile() && f.canRead()) {
                try {
                    List<LineageSqlRecord> records = readFile(f);
                    System.out.println("[lineage-viewer] 已从外部文件载入 " + records.size()
                            + " 条 SQL 记录: " + f.getAbsolutePath());
                    return records;
                } catch (IOException e) {
                    throw new IllegalStateException("读取外部数据文件失败: " + f, e);
                }
            }
            System.out.println("[lineage-viewer] 指定的外部数据文件不可读，回退到 classpath: " + external);
        }

        // 2. 工作目录下的 lineage-sqls.json
        File cwd = new File(CWD_FILE);
        if (cwd.isFile() && cwd.canRead()) {
            try {
                List<LineageSqlRecord> records = readFile(cwd);
                System.out.println("[lineage-viewer] 已从工作目录载入 " + records.size() + " 条 SQL 记录");
                return records;
            } catch (IOException e) {
                System.out.println("[lineage-viewer] 读取工作目录 " + CWD_FILE + " 失败，回退到 classpath: " + e);
            }
        }

        // 3. classpath
        InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(DEFAULT_CLASSPATH);
        if (in == null) {
            System.out.println("[lineage-viewer] 未找到任何数据文件（classpath:" + DEFAULT_CLASSPATH
                    + "），将使用空数据集启动");
            return new ArrayList<LineageSqlRecord>();
        }
        try {
            byte[] bytes = readAll(in);
            if (bytes.length > LARGE_FILE_BYTES) {
                System.out.println("[lineage-viewer] 内置数据集较大: " + (bytes.length / 1024) + " KB");
            }
            List<LineageSqlRecord> records = parse(bytes);
            System.out.println("[lineage-viewer] 已从 classpath 载入 " + records.size() + " 条 SQL 记录");
            return records;
        } catch (IOException e) {
            throw new IllegalStateException("读取 classpath:" + DEFAULT_CLASSPATH + " 失败", e);
        } finally {
            closeQuietly(in);
        }
    }

    private List<LineageSqlRecord> readFile(File f) throws IOException {
        try (InputStream in = Files.newInputStream(f.toPath())) {
            return parse(readAll(in));
        }
    }

    /**
     * 解析 SQL 记录 JSON。独立出来便于测试与压测复用。
     */
    public List<LineageSqlRecord> parse(byte[] json) {
        if (json == null || json.length == 0) {
            throw new IllegalArgumentException("JSON 不能为空；清空数据请提供 []");
        }
        Object root = JSON.parse(trimBom(json));
        return parseRoot(root);
    }

    public List<LineageSqlRecord> parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON 不能为空；清空数据请提供 []");
        }
        return parseRoot(JSON.parse(json));
    }

    private List<LineageSqlRecord> parseRoot(Object root) {
        JSONArray arrayNode = null;
        if (root instanceof JSONArray) {
            arrayNode = (JSONArray) root;
        } else if (root instanceof JSONObject && ((JSONObject) root).get("records") instanceof JSONArray) {
            arrayNode = (JSONArray) ((JSONObject) root).get("records");
        }
        List<LineageSqlRecord> out = new ArrayList<LineageSqlRecord>();
        if (arrayNode == null) {
            throw new IllegalArgumentException("JSON 必须是数组或包含 records 数组的对象");
        }
        Set<String> names = new HashSet<String>();
        for (int i = 0; i < arrayNode.size(); i++) {
            if (!(arrayNode.get(i) instanceof JSONObject)) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 条记录必须是对象");
            }
            JSONObject node = arrayNode.getJSONObject(i);
            if (node == null) {
                continue;
            }
            LineageSqlRecord r = new LineageSqlRecord();
            r.setSource(text(node, "source"));
            r.setName(text(node, "name"));
            r.setDescription(text(node, "description"));
            r.setSql(text(node, "sql"));
            // 字段名大小写两种写法都兼容，避免上游导出格式不一致时整条记录丢失
            r.setInputTables(parseTableList(node.get("inputTables"), node.get("inputtables")));
            r.setOutputTables(parseTableList(node.get("outputTables"), node.get("outputtables")));
            String name = r.getName();
            if (name != null && name.trim().length() > 0) {
                if (!names.add(name)) throw new IllegalArgumentException("SQL name 重复: " + name);
                out.add(r);
            }
        }
        return out;
    }

    private String text(JSONObject node, String field) {
        Object v = node.get(field);
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 把 inputTables / outputTables 解析成表名清单。
     * 支持 JSON 数组、逗号分隔、分号分隔、以及中英文逗号混排。
     */
    private List<String> parseTableList(Object... candidates) {
        for (Object node : candidates) {
            if (node == null) {
                continue;
            }
            List<String> result = new ArrayList<String>();
            if (node instanceof JSONArray) {
                JSONArray arr = (JSONArray) node;
                for (int i = 0; i < arr.size(); i++) {
                    Object item = arr.get(i);
                    addClean(result, item == null ? null : String.valueOf(item));
                }
            } else if (node instanceof Iterable) {
                for (Object item : (Iterable<?>) node) {
                    addClean(result, item == null ? null : String.valueOf(item));
                }
            } else {
                String raw = String.valueOf(node);
                for (String part : raw.split("[,;，；]")) {
                    addClean(result, part);
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return new ArrayList<String>();
    }

    private void addClean(List<String> target, String raw) {
        if (raw == null) {
            return;
        }
        String s = raw.trim().replace("`", "");
        if (s.length() > 0 && !"null".equalsIgnoreCase(s)) {
            target.add(s);
        }
    }

    /** 供测试与批量粘贴场景使用：把分隔字符串拆成表名清单 */
    public static List<String> splitTables(String raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        for (String s : Arrays.asList(raw.split("[,;，；]"))) {
            String t = s.trim();
            if (t.length() > 0) {
                out.add(t);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(256 * 1024);
        byte[] chunk = new byte[64 * 1024];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    /** 去掉 UTF-8 BOM，否则 JSON 解析会直接失败 */
    private static byte[] trimBom(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            byte[] out = new byte[bytes.length - 3];
            System.arraycopy(bytes, 3, out, 0, out.length);
            return out;
        }
        return bytes;
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
                // 关闭失败不影响主流程
            }
        }
    }
}
