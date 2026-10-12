package com.toolbox.system;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Read-only discovery plus explicitly requested termination of Windows java.exe/javaw.exe.
 * No shell receives a user-supplied PID or command line. Java 8 compatible.
 */
public class JavaProcessService {
    public boolean supported() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }

    public List<JavaProcessInfo> list() {
        if (!supported()) return Collections.emptyList();
        Map<Integer, String> mains = jpsMainClasses();
        List<JavaProcessInfo> results = new ArrayList<JavaProcessInfo>();
        WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        if (snapshot == null || WinBase.INVALID_HANDLE_VALUE.equals(snapshot)) {
            throw new IllegalStateException("无法枚举 Windows 进程，错误码 "
                    + Kernel32.INSTANCE.GetLastError());
        }
        try {
            Tlhelp32.PROCESSENTRY32 entry = new Tlhelp32.PROCESSENTRY32();
            if (Kernel32.INSTANCE.Process32First(snapshot, entry)) {
                do {
                    String exe = Native.toString(entry.szExeFile);
                    if (!isJavaExecutable(exe)) continue;
                    int pid = entry.th32ProcessID.intValue();
                    JavaProcessInfo info = inspect(pid, exe, mains.get(pid));
                    results.add(info);
                } while (Kernel32.INSTANCE.Process32Next(snapshot, entry));
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        Collections.sort(results, Comparator.comparingInt(p -> p.pid));
        return results;
    }

    private JavaProcessInfo inspect(int pid, String exe, String main) {
        JavaProcessInfo info = new JavaProcessInfo();
        info.pid = pid;
        info.executable = exe;
        info.main = main == null ? "" : main;
        info.name = main == null || main.isEmpty() ? exe : main;
        info.canKill = false;
        WinNT.HANDLE handle = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
        if (handle == null) {
            info.reason = "无法读取进程详情（权限不足或已退出）";
            return info;
        }
        try {
            String path = Kernel32Util.QueryFullProcessImageName(handle, 0);
            if (!isJavaExecutable(path)) {
                info.reason = "进程映像不再是 java.exe/javaw.exe";
                return info;
            }
            info.executable = path;
            info.startTime = creationMillis(handle);
            info.canKill = info.startTime > 0 && pid != Kernel32.INSTANCE.GetCurrentProcessId();
            info.reason = info.canKill ? "" : "不允许结束工具自身进程";
        } catch (Exception ex) {
            info.reason = "进程详情不可用";
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
        return info;
    }

    /**
     * Verify identity and terminate through the SAME handle. The caller must supply
     * the creation time shown by list(); a recycled PID is never silently accepted.
     */
    public void kill(int pid, long expectedStartTime) {
        if (!supported()) throw new UnsupportedOperationException("进程终止目前只支持 Windows");
        if (pid <= 0 || expectedStartTime <= 0) throw new IllegalArgumentException("PID 或启动时间无效");
        if (pid == Kernel32.INSTANCE.GetCurrentProcessId()) {
            throw new SecurityException("不允许终止工具自身进程");
        }
        final int flags = WinNT.PROCESS_TERMINATE
                | WinNT.PROCESS_QUERY_LIMITED_INFORMATION | WinNT.SYNCHRONIZE;
        WinNT.HANDLE handle = Kernel32.INSTANCE.OpenProcess(flags, false, pid);
        if (handle == null) {
            throw new IllegalStateException("无法打开目标进程（已退出或权限不足），错误码 "
                    + Kernel32.INSTANCE.GetLastError());
        }
        try {
            String path;
            try {
                path = Kernel32Util.QueryFullProcessImageName(handle, 0);
            } catch (Exception ex) {
                throw new SecurityException("无法验证进程映像，已拒绝结束");
            }
            if (!isJavaExecutable(path)) {
                throw new SecurityException("目标不是 java.exe/javaw.exe，已拒绝结束");
            }
            long actualStartTime = creationMillis(handle);
            if (actualStartTime == 0 || actualStartTime != expectedStartTime) {
                throw new IllegalStateException("进程已退出或 PID 被重新分配，请刷新列表");
            }
            if (!Kernel32.INSTANCE.TerminateProcess(handle, 1)) {
                throw new IllegalStateException("终止失败，Windows 错误码 " + Kernel32.INSTANCE.GetLastError());
            }
            if (Kernel32.INSTANCE.WaitForSingleObject(handle, 2000) != 0) {
                throw new IllegalStateException("已发送结束请求，但未能在 2 秒内确认退出");
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }

    private long creationMillis(WinNT.HANDLE handle) {
        WinBase.FILETIME created = new WinBase.FILETIME();
        WinBase.FILETIME exited = new WinBase.FILETIME();
        WinBase.FILETIME kernel = new WinBase.FILETIME();
        WinBase.FILETIME user = new WinBase.FILETIME();
        if (!Kernel32.INSTANCE.GetProcessTimes(handle, created, exited, kernel, user)) return 0;
        return created.toTime();
    }

    public static boolean isJavaExecutable(String path) {
        if (path == null) return false;
        String name = path.replace('/', '\\');
        name = name.substring(name.lastIndexOf('\\') + 1).toLowerCase(Locale.ROOT);
        return "java.exe".equals(name) || "javaw.exe".equals(name);
    }

    private Map<Integer, String> jpsMainClasses() {
        Map<Integer, String> mains = new LinkedHashMap<Integer, String>();
        File jps = findJps();
        if (jps == null) return mains;
        Process p = null;
        try {
            p = new ProcessBuilder(jps.getAbsolutePath(), "-l").redirectErrorStream(true).start();
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return mains;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] split = line.trim().split("\\s+", 2);
                    if (split.length != 2) continue;
                    try {
                        int pid = Integer.parseInt(split[0]);
                        String main = split[1].trim();
                        if (!main.isEmpty()) mains.put(pid, main);
                    } catch (NumberFormatException ignored) {
                        // jps may emit diagnostic lines
                    }
                }
            }
        } catch (Exception ignored) {
            // jps is an optional JDK tool; a JRE-only deployment must still list PIDs.
        } finally {
            if (p != null && p.isAlive()) p.destroyForcibly();
        }
        return mains;
    }

    private File findJps() {
        String home = System.getProperty("java.home", "");
        File[] candidates = {
                new File(home, "bin/jps.exe"),
                new File(home, "../bin/jps.exe"),
                new File(System.getenv("JAVA_HOME") == null ? "" : System.getenv("JAVA_HOME"), "bin/jps.exe")
        };
        for (File f : candidates) if (f.isFile()) return f;
        return null;
    }

    public static final class JavaProcessInfo {
        public int pid;
        public String name;
        public String main;
        public String executable;
        public long startTime;
        public boolean canKill;
        public String reason;
    }
}
