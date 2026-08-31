package com.toolbox.tools.portchecker;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IP 端口检测工具。
 * 支持单个端口、逗号分隔多端口、范围端口（如 80-90），混合使用。
 */
@Component
public class IpPortCheckerTool implements Tool {

    @Override
    public String getName() {
        return "ip-port-checker";
    }

    @Override
    public String getDisplayName() {
        return "IP 端口检测";
    }

    @Override
    public String getDescription() {
        return "检测指定 IP 的端口是否开放，支持多端口和端口范围";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String ip = str(params.get("ip"), "").trim();
        String portsStr = str(params.get("ports"), "").trim();
        int timeout = intVal(params.get("timeout"), 3000);

        if (ip.isEmpty()) {
            return ToolResult.fail("IP 地址不能为空");
        }
        if (portsStr.isEmpty()) {
            return ToolResult.fail("端口不能为空，支持格式: 80 或 80,443 或 80-90 或混合");
        }

        List<Integer> ports = parsePorts(portsStr);
        if (ports.isEmpty()) {
            return ToolResult.fail("端口格式错误，支持: 80 / 80,443 / 80-90 / 80,443,8000-9000");
        }
        if (ports.size() > 1000) {
            return ToolResult.fail("一次最多检测 1000 个端口，当前解析到 " + ports.size() + " 个");
        }

        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        int openCount = 0;

        for (int port : ports) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("port", port);

            long start = System.currentTimeMillis();
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), timeout);
                socket.close();
                long cost = System.currentTimeMillis() - start;
                item.put("open", true);
                item.put("message", "开放");
                item.put("costMs", cost);
                openCount++;
            } catch (Exception e) {
                item.put("open", false);
                String msg = e.getMessage();
                if (msg == null || msg.isEmpty()) {
                    msg = e.getClass().getSimpleName();
                }
                item.put("message", "不可达 (" + msg + ")");
                item.put("costMs", System.currentTimeMillis() - start);
            }
            results.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("ip", ip);
        data.put("results", results);
        data.put("openCount", openCount);
        data.put("totalCount", results.size());
        data.put("closedCount", results.size() - openCount);

        return ToolResult.ok(data);
    }

    /**
     * 解析端口字符串，支持 80 / 80,443 / 80-90 / 混合。
     */
    private List<Integer> parsePorts(String str) {
        List<Integer> ports = new ArrayList<Integer>();
        String[] parts = str.split("[,，\\s]+");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        if (start > end) {
                            int tmp = start;
                            start = end;
                            end = tmp;
                        }
                        for (int i = start; i <= end && i <= 65535; i++) {
                            if (i >= 1 && !ports.contains(i)) {
                                ports.add(i);
                            }
                        }
                    } catch (NumberFormatException e) {
                        // skip invalid
                    }
                }
            } else {
                try {
                    int p = Integer.parseInt(part);
                    if (p >= 1 && p <= 65535 && !ports.contains(p)) {
                        ports.add(p);
                    }
                } catch (NumberFormatException e) {
                    // skip invalid
                }
            }
        }
        return ports;
    }

    private String str(Object obj, String def) {
        return obj == null ? def : obj.toString();
    }

    private int intVal(Object obj, int def) {
        if (obj == null) return def;
        try {
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return def;
        }
    }
}
