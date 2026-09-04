package com.toolbox.tools.excel;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExcelSessionManager {

    private static final long EXPIRE_MS = 60 * 60 * 1000L;
    private final Map<String, ExcelFile> sessions = new ConcurrentHashMap<String, ExcelFile>();

    @PostConstruct
    public void init() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try { Thread.sleep(10 * 60 * 1000L); cleanup(); }
                    catch (InterruptedException e) { break; }
                }
            }
        }, "excel-session-cleaner");
        t.setDaemon(true);
        t.start();
    }

    public String put(ExcelFile file) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        file.setFileId(id);
        sessions.put(id, file);
        return id;
    }

    public ExcelFile get(String fileId) {
        ExcelFile f = sessions.get(fileId);
        if (f == null) return null;
        if (System.currentTimeMillis() - f.getUploadTime() > EXPIRE_MS) {
            sessions.remove(fileId);
            return null;
        }
        return f;
    }

    public void remove(String fileId) { sessions.remove(fileId); }

    private void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, ExcelFile>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().getUploadTime() > EXPIRE_MS) it.remove();
        }
    }
}
