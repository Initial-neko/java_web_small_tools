package com.toolbox.api;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolRegistry;
import com.toolbox.core.ToolResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具统一入口。
 * GET  /api/tools          列出所有工具
 * POST /api/tools/{name}/execute  执行指定工具
 */
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    @Autowired
    private ToolRegistry registry;

    @GetMapping
    public List<Map<String, String>> list() {
        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        for (Tool tool : registry.list()) {
            Map<String, String> item = new HashMap<String, String>();
            item.put("name", tool.getName());
            item.put("displayName", tool.getDisplayName());
            item.put("description", tool.getDescription());
            result.add(item);
        }
        return result;
    }

    @PostMapping("/{name}/execute")
    public ToolResult execute(@PathVariable String name, @RequestBody Map<String, Object> params) {
        Tool tool = registry.get(name);
        if (tool == null) {
            return ToolResult.fail("工具不存在: " + name);
        }
        if (params == null) {
            params = new HashMap<String, Object>();
        }
        return tool.execute(params);
    }
}
