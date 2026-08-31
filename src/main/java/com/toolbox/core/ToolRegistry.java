package com.toolbox.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心。Spring 启动时自动收集所有 Tool 实现。
 */
@Component
public class ToolRegistry {

    @Autowired
    private List<Tool> tools;

    private final Map<String, Tool> toolMap = new LinkedHashMap<String, Tool>();

    @PostConstruct
    public void init() {
        for (Tool tool : tools) {
            toolMap.put(tool.getName(), tool);
        }
    }

    public Tool get(String name) {
        return toolMap.get(name);
    }

    public List<Tool> list() {
        return new ArrayList<Tool>(toolMap.values());
    }

    public boolean exists(String name) {
        return toolMap.containsKey(name);
    }
}
