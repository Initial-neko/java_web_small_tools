package com.toolbox.tools.diff;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本 Diff 工具。
 * 两段文本按行对比，输出差异详情和 unified diff 格式。
 * 也可用于文件内容对比（前端把文件读成文本传入）。
 */
@Component
public class TextDiffTool implements Tool {

    @Override
    public String getName() {
        return "text-diff";
    }

    @Override
    public String getDisplayName() {
        return "文本 Diff";
    }

    @Override
    public String getDescription() {
        return "两段文本按行对比，输出增删行与 unified diff";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String textA = str(params.get("textA"), "");
        String textB = str(params.get("textB"), "");
        boolean ignoreWhitespace = bool(params.get("ignoreWhitespace"), false);

        List<String> original = toLines(textA);
        List<String> revised = toLines(textB);

        try {
            Patch<String> patch;
            if (ignoreWhitespace) {
                patch = DiffUtils.diff(original, revised,
                        new java.util.function.BiPredicate<String, String>() {
                            @Override
                            public boolean test(String s1, String s2) {
                                return s1.trim().equals(s2.trim());
                            }
                        });
            } else {
                patch = DiffUtils.diff(original, revised);
            }

            List<Map<String, Object>> deltas = new ArrayList<Map<String, Object>>();
            for (AbstractDelta<String> delta : patch.getDeltas()) {
                Map<String, Object> item = new HashMap<String, Object>();
                item.put("type", delta.getType().name()); // INSERT / DELETE / CHANGE / EQUAL
                item.put("sourcePosition", delta.getSource().getPosition());
                item.put("sourceLines", delta.getSource().getLines());
                item.put("targetPosition", delta.getTarget().getPosition());
                item.put("targetLines", delta.getTarget().getLines());
                deltas.add(item);
            }

            // 生成 unified diff（手动构建，兼容不同版本库 API）
            String unifiedDiff = buildUnifiedDiff(original, revised, patch);

            Map<String, Object> data = new HashMap<String, Object>();
            data.put("deltas", deltas);
            data.put("unifiedDiff", unifiedDiff);
            data.put("changeCount", deltas.size());
            data.put("identical", deltas.isEmpty());

            return ToolResult.ok(data);
        } catch (Exception e) {
            return ToolResult.fail("Diff 失败: " + e.getMessage());
        }
    }

    private List<String> toLines(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(Arrays.asList(text.split("\n", -1)));
    }

    private String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    /**
     * 手动构建 unified diff 格式文本。
     */
    private String buildUnifiedDiff(List<String> original, List<String> revised, Patch<String> patch) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- original\n");
        sb.append("+++ revised\n");

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            int srcPos = delta.getSource().getPosition();
            int srcSize = delta.getSource().getLines().size();
            int tgtPos = delta.getTarget().getPosition();
            int tgtSize = delta.getTarget().getLines().size();

            // hunk 头：行号从 1 开始显示
            int srcStart = srcSize == 0 ? srcPos : srcPos + 1;
            int tgtStart = tgtSize == 0 ? tgtPos : tgtPos + 1;
            sb.append(String.format("@@ -%d,%d +%d,%d @@\n", srcStart, srcSize, tgtStart, tgtSize));

            for (String line : delta.getSource().getLines()) {
                sb.append("-").append(line).append("\n");
            }
            for (String line : delta.getTarget().getLines()) {
                sb.append("+").append(line).append("\n");
            }
        }

        return sb.toString().trim();
    }

    private String str(Object obj, String def) {
        return obj == null ? def : obj.toString();
    }

    private boolean bool(Object obj, boolean def) {
        if (obj == null) return def;
        if (obj instanceof Boolean) return (Boolean) obj;
        return Boolean.parseBoolean(obj.toString());
    }
}
