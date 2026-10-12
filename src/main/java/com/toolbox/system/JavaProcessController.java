package com.toolbox.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local-machine-only privileged endpoints. All kill requests require a same-origin
 * browser Origin, an ephemeral token and a process creation-time identity check.
 */
@RestController
@RequestMapping("/api/system/java-processes")
public class JavaProcessController {
    private static final Logger LOG = LoggerFactory.getLogger(JavaProcessController.class);
    private final JavaProcessService processes;
    private final String token;

    public JavaProcessController() {
        this(new JavaProcessService());
    }

    JavaProcessController(JavaProcessService processes) {
        this.processes = processes;
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        StringBuilder value = new StringBuilder();
        for (byte b : secret) value.append(String.format("%02x", b & 0xff));
        token = value.toString();
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest req) {
        if (!localRequest(req)) return failure(HttpStatus.FORBIDDEN, "进程管理仅允许在服务所在机器通过 localhost 访问");
        try {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("success", true);
            data.put("supported", processes.supported());
            data.put("message", processes.supported() ? "" : "当前平台不支持；第一版仅支持 Windows");
            data.put("token", token);
            data.put("processes", processes.list());
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(data);
        } catch (Exception ex) {
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "进程扫描失败：" + ex.getMessage());
        }
    }

    @PostMapping("/{pid}/kill")
    public ResponseEntity<?> kill(@PathVariable int pid,
                                  @RequestBody Map<String, Object> body,
                                  @RequestHeader(value = "X-Process-Token", required = false) String provided,
                                  HttpServletRequest req) {
        if (!localRequest(req) || !sameOrigin(req) || !token.equals(provided)) {
            return failure(HttpStatus.FORBIDDEN, "本机来源或操作令牌验证失败");
        }
        Object value = body == null ? null : body.get("startTime");
        if (!(value instanceof Number)) return failure(HttpStatus.BAD_REQUEST, "缺少合法的启动时间");
        long startTime = ((Number) value).longValue();
        try {
            processes.kill(pid, startTime);
            LOG.info("Local user terminated Java process pid={} startTime={}", pid, startTime);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("success", true);
            data.put("message", "PID " + pid + " 已退出");
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(data);
        } catch (IllegalArgumentException ex) {
            return failure(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (SecurityException ex) {
            return failure(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (UnsupportedOperationException ex) {
            return failure(HttpStatus.NOT_IMPLEMENTED, ex.getMessage());
        } catch (IllegalStateException ex) {
            return failure(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    static boolean localRequest(HttpServletRequest req) {
        if (req == null) return false;
        String host = req.getServerName();
        if (!("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host) || "[::1]".equals(host))) return false;
        try {
            return InetAddress.getByName(req.getRemoteAddr()).isLoopbackAddress();
        } catch (Exception ex) {
            return false;
        }
    }

    static boolean sameOrigin(HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        if (origin == null) return false;
        String host = req.getServerName();
        if ("::1".equals(host)) host = "[::1]";
        String expected = req.getScheme() + "://" + host + ":" + req.getServerPort();
        return expected.equalsIgnoreCase(origin);
    }

    private static ResponseEntity<Map<String, Object>> failure(HttpStatus status, String message) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("success", false);
        data.put("message", message);
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(data);
    }
}
