package com.toolbox.system;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JavaProcessControllerTest {
    @Test
    void detectsOnlyJavaAndJavaw() {
        assertTrue(JavaProcessService.isJavaExecutable("C:\\Program Files\\Java\\bin\\java.exe"));
        assertTrue(JavaProcessService.isJavaExecutable("C:/jdk/bin/JAVAW.EXE"));
        assertFalse(JavaProcessService.isJavaExecutable("java.exe.bak"));
        assertFalse(JavaProcessService.isJavaExecutable("powershell.exe"));
        assertFalse(JavaProcessService.isJavaExecutable(null));
    }

    @Test
    void rejectsRemoteClientsAndDnsRebindingHosts() {
        FakeService service = new FakeService();
        JavaProcessController controller = new JavaProcessController(service);
        MockHttpServletRequest remote = request("127.0.0.1", "evil.example");
        assertEquals(HttpStatus.FORBIDDEN, controller.list(remote).getStatusCode());
        remote = request("192.168.1.88", "localhost");
        assertEquals(HttpStatus.FORBIDDEN, controller.list(remote).getStatusCode());
        assertFalse(service.killed);
    }

    @Test
    @SuppressWarnings("unchecked")
    void killRequiresTokenOriginAndStartTime() {
        FakeService service = new FakeService();
        JavaProcessController controller = new JavaProcessController(service);
        MockHttpServletRequest req = request("127.0.0.1", "localhost");
        ResponseEntity<?> list = controller.list(req);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        String token = (String) ((Map<String, Object>) list.getBody()).get("token");
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("startTime", 123456L);
        assertEquals(HttpStatus.FORBIDDEN, controller.kill(123, body, token, req).getStatusCode());
        req.addHeader("Origin", "http://malicious.example");
        assertEquals(HttpStatus.FORBIDDEN, controller.kill(123, body, token, req).getStatusCode());
        req.removeHeader("Origin");
        req.addHeader("Origin", "http://localhost:8088");
        assertEquals(HttpStatus.FORBIDDEN, controller.kill(123, body, "incorrect", req).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.kill(123, Collections.emptyMap(), token, req).getStatusCode());
        assertFalse(service.killed);
        assertEquals(HttpStatus.OK, controller.kill(123, body, token, req).getStatusCode());
        assertTrue(service.killed);
        assertEquals(123, service.lastPid);
        assertEquals(123456L, service.lastStartTime);
    }

    private MockHttpServletRequest request(String remoteAddress, String host) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.setServerName(host);
        request.setServerPort(8088);
        request.setScheme("http");
        return request;
    }

    private static final class FakeService extends JavaProcessService {
        boolean killed;
        int lastPid;
        long lastStartTime;

        @Override public boolean supported() { return true; }
        @Override public java.util.List<JavaProcessInfo> list() { return Collections.emptyList(); }
        @Override public void kill(int pid, long startTime) {
            killed = true;
            lastPid = pid;
            lastStartTime = startTime;
        }
    }
}
