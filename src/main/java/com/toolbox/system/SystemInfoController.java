package com.toolbox.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统信息接口。首页展示本机硬件参数与 IP。
 */
@RestController
@RequestMapping("/api/system")
public class SystemInfoController {

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();

        // 操作系统
        result.put("os", System.getProperty("os.name") + " "
                + System.getProperty("os.arch") + " "
                + System.getProperty("os.version"));
        result.put("jvm", System.getProperty("java.version") + " ("
                + System.getProperty("java.vendor") + ")");

        // 本机 IP（所有非回环 IPv4）
        List<Map<String, String>> ips = new ArrayList<Map<String, String>>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback() && !ni.isVirtual()) {
                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                            Map<String, String> ipInfo = new LinkedHashMap<String, String>();
                            ipInfo.put("name", ni.getDisplayName());
                            ipInfo.put("ip", addr.getHostAddress());
                            ips.add(ipInfo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        result.put("ips", ips);

        // CPU
        Map<String, Object> cpu = new LinkedHashMap<String, Object>();
        cpu.put("logicalCores", Runtime.getRuntime().availableProcessors());
        cpu.put("name", getCpuName());
        result.put("cpu", cpu);

        // 内存
        result.put("memory", getMemoryInfo());

        // 磁盘
        result.put("disks", getDiskInfo());

        return result;
    }

    /**
     * 获取 CPU 名称，Windows 用 wmic，Linux 读 /proc/cpuinfo。
     */
    private String getCpuName() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String out = execCommand("wmic cpu get name");
                for (String line : out.split("\n")) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.equalsIgnoreCase("Name")) {
                        return line;
                    }
                }
            } else {
                String out = execCommand("cat /proc/cpuinfo");
                for (String line : out.split("\n")) {
                    if (line.startsWith("model name")) {
                        int idx = line.indexOf(':');
                        if (idx > 0) return line.substring(idx + 1).trim();
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return "未知";
    }

    /**
     * 获取内存信息，单位 MB。
     */
    private Map<String, Object> getMemoryInfo() {
        Map<String, Object> mem = new LinkedHashMap<String, Object>();
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String out = execCommand("wmic OS get TotalVisibleMemorySize,FreePhysicalMemory");
                String[] lines = out.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.toLowerCase().startsWith("total")) continue;
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        long totalKB = Long.parseLong(parts[0]);
                        long freeKB = Long.parseLong(parts[1]);
                        long usedKB = totalKB - freeKB;
                        mem.put("totalMB", totalKB / 1024);
                        mem.put("usedMB", usedKB / 1024);
                        mem.put("freeMB", freeKB / 1024);
                        mem.put("usagePercent", String.format("%.1f%%", (double) usedKB / totalKB * 100));
                        return mem;
                    }
                }
            } else {
                String out = execCommand("free -m");
                for (String line : out.split("\n")) {
                    if (line.startsWith("Mem:")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 3) {
                            long total = Long.parseLong(parts[1]);
                            long used = Long.parseLong(parts[2]);
                            long free = Long.parseLong(parts[3]);
                            mem.put("totalMB", total);
                            mem.put("usedMB", used);
                            mem.put("freeMB", free);
                            mem.put("usagePercent", String.format("%.1f%%", (double) used / total * 100));
                            return mem;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        mem.put("totalMB", 0);
        mem.put("usedMB", 0);
        mem.put("freeMB", 0);
        mem.put("usagePercent", "未知");
        return mem;
    }

    /**
     * 获取磁盘信息。
     */
    private List<Map<String, Object>> getDiskInfo() {
        List<Map<String, Object>> disks = new ArrayList<Map<String, Object>>();
        File[] roots = File.listRoots();
        for (File root : roots) {
            try {
                long total = root.getTotalSpace();
                if (total <= 0) continue;
                long free = root.getFreeSpace();
                long used = total - free;
                Map<String, Object> disk = new LinkedHashMap<String, Object>();
                disk.put("path", root.getPath());
                disk.put("totalGB", String.format("%.1f", total / 1073741824.0));
                disk.put("usedGB", String.format("%.1f", used / 1073741824.0));
                disk.put("freeGB", String.format("%.1f", free / 1073741824.0));
                disk.put("usagePercent", String.format("%.1f%%", (double) used / total * 100));
                disks.add(disk);
            } catch (Exception e) {
                // skip
            }
        }
        return disks;
    }

    /**
     * 执行系统命令并返回输出。
     */
    private String execCommand(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "GBK"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
