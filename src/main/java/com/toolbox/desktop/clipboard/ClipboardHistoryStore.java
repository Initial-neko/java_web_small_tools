package com.toolbox.desktop.clipboard;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

public final class ClipboardHistoryStore {

    private final Path root;
    private final Path historyDir;
    private final Path imagesDir;
    private final Path screenshotsDir;
    private volatile int maxEntries = 200;
    private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis());

    public ClipboardHistoryStore(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.historyDir = this.root.resolve("history");
        this.imagesDir = this.root.resolve("images");
        this.screenshotsDir = this.root.resolve("screenshots");
        Files.createDirectories(historyDir);
        Files.createDirectories(imagesDir);
        Files.createDirectories(screenshotsDir);
        sequence.set(Math.max(sequence.get(), findMaxId()));
        Path settings=root.resolve("history-settings.properties");
        if(Files.exists(settings)){Properties p=new Properties();try(Reader reader=Files.newBufferedReader(settings,StandardCharsets.UTF_8)){p.load(reader);try{maxEntries=Integer.parseInt(p.getProperty("maxEntries","200"));}catch(NumberFormatException ignored){}if(maxEntries<1||maxEntries>10000)maxEntries=200;}}
        trimHistory();
    }

    public int getMaxEntries(){return maxEntries;}

    public synchronized void setMaxEntries(int limit) throws IOException {
        if(limit<1||limit>10000)throw new IllegalArgumentException("历史上限必须为 1–10000");
        Properties p=new Properties();p.setProperty("maxEntries",Integer.toString(limit));
        writeProperties(root.resolve("history-settings.properties"),p);
        maxEntries=limit;trimHistory();
    }

    public synchronized void setPinned(ClipboardEntry entry,boolean pinned) throws IOException {
        Path file=historyDir.resolve(entry.getId()+".properties");
        if(!Files.exists(file))throw new IOException("这条历史已被清理，请刷新");
        Properties p=new Properties();
        try(Reader reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(reader);}
        p.setProperty("pinned",Boolean.toString(pinned));writeProperties(file,p);trimHistory();
    }

    private void writeProperties(Path file,Properties properties) throws IOException {
        Path temporary=Files.createTempFile(file.getParent(),"settings-",".tmp");
        try {
            try(Writer writer=Files.newBufferedWriter(temporary,StandardCharsets.UTF_8)){properties.store(writer,"desktop history settings");}
            try {Files.move(temporary,file,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(temporary,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
    }

    private void trimHistory() throws IOException {
        List<ClipboardEntry> entries=loadAll();int ordinary=0;
        for(ClipboardEntry entry:entries)if(!entry.isPinned())ordinary++;
        for(int i=entries.size()-1;i>=0&&ordinary>maxEntries;i--){
            ClipboardEntry entry=entries.get(i);if(entry.isPinned())continue;delete(entry);ordinary--;
        }
    }
    public Path getRoot() {
        return root;
    }

    public synchronized ClipboardEntry saveText(String text, String hash) throws IOException {
        long now = System.currentTimeMillis();
        ClipboardEntry entry = new ClipboardEntry(
                nextId(),
                now,
                ClipboardEntry.Type.TEXT,
                text,
                null,
                null,
                hash
        );
        persist(entry);
        trimHistory();
        return entry;
    }

    public synchronized ClipboardEntry saveFiles(List<File> files, String hash) throws IOException {
        List<String> paths = new ArrayList<String>();
        for (File file : files) {
            paths.add(file.getAbsolutePath());
        }
        ClipboardEntry entry = new ClipboardEntry(
                nextId(),
                System.currentTimeMillis(),
                ClipboardEntry.Type.FILES,
                null,
                paths,
                null,
                hash
        );
        persist(entry);
        trimHistory();
        return entry;
    }

    public synchronized ClipboardEntry saveImage(BufferedImage image, String hash) throws IOException {
        long id = nextId();
        String relative = "images/" + id + ".png";
        Path target = root.resolve(relative);
        ImageIO.write(image, "png", target.toFile());

        ClipboardEntry entry = new ClipboardEntry(
                id,
                System.currentTimeMillis(),
                ClipboardEntry.Type.IMAGE,
                null,
                null,
                relative,
                hash
        );
        persist(entry);
        trimHistory();
        return entry;
    }

    public synchronized File saveScreenshotCopy(BufferedImage image) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
        Path target = screenshotsDir.resolve("screenshot-" + timestamp + ".png");
        ImageIO.write(image, "png", target.toFile());
        return target.toFile();
    }

    public synchronized List<ClipboardEntry> loadAll() {
        List<ClipboardEntry> entries = new ArrayList<ClipboardEntry>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(historyDir, "*.properties")) {
            for (Path file : stream) {
                try {
                    ClipboardEntry entry = load(file);
                    if (entry != null) {
                        entries.add(entry);
                    }
                } catch (Exception ignored) {
                    // A damaged history item must not prevent the rest of the history from loading.
                }
            }
        } catch (IOException ignored) {
        }

        Collections.sort(entries, new Comparator<ClipboardEntry>() {
            public int compare(ClipboardEntry left, ClipboardEntry right) {
                if(left.isPinned()!=right.isPinned())return left.isPinned()?-1:1;
                int date=Long.compare(right.getCreatedAt(), left.getCreatedAt());
                return date!=0?date:Long.compare(right.getId(),left.getId());
            }
        });
        return entries;
    }

    public synchronized void delete(ClipboardEntry entry) throws IOException {
        Files.deleteIfExists(historyDir.resolve(entry.getId() + ".properties"));
        if (entry.getType() == ClipboardEntry.Type.IMAGE && entry.getImagePath() != null) {
            Path image=resolveImage(entry).toPath();
            Files.deleteIfExists(image);
        }
    }

    public File resolveImage(ClipboardEntry entry) {
        if (entry.getImagePath() == null) {
            return null;
        }
        Path image=root.resolve(entry.getImagePath()).normalize();
        if(!image.startsWith(imagesDir))throw new IllegalArgumentException("Image path is outside the owned cache");
        return image.toFile();
    }

    private long nextId() {
        while (true) {
            long current = sequence.get();
            long candidate = Math.max(current + 1L, System.currentTimeMillis());
            if (sequence.compareAndSet(current, candidate)) {
                return candidate;
            }
        }
    }

    private long findMaxId() {
        long max = 0L;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(historyDir, "*.properties")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                int dot = name.indexOf('.');
                if (dot <= 0) {
                    continue;
                }
                try {
                    max = Math.max(max, Long.parseLong(name.substring(0, dot)));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return max;
    }

    private void persist(ClipboardEntry entry) throws IOException {
        Properties p = new Properties();
        p.setProperty("pinned",Boolean.toString(entry.isPinned()));
        p.setProperty("id", Long.toString(entry.getId()));
        p.setProperty("createdAt", Long.toString(entry.getCreatedAt()));
        p.setProperty("type", entry.getType().name());
        p.setProperty("hash", value(entry.getHash()));
        p.setProperty("text", value(entry.getText()));
        p.setProperty("imagePath", value(entry.getImagePath()));
        p.setProperty("filePaths", joinLines(entry.getFilePaths()));

        Path file = historyDir.resolve(entry.getId() + ".properties");
        try (Writer writer = new BufferedWriter(Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW
        ))) {
            p.store(writer, "java_web_small_tools desktop clipboard entry");
        }
    }

    private ClipboardEntry load(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader reader = new BufferedReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
            p.load(reader);
        }

        long id = Long.parseLong(p.getProperty("id"));
        long createdAt = Long.parseLong(p.getProperty("createdAt"));
        ClipboardEntry.Type type = ClipboardEntry.Type.valueOf(p.getProperty("type"));
        String text = emptyToNull(p.getProperty("text"));
        String imagePath = emptyToNull(p.getProperty("imagePath"));
        String hash = emptyToNull(p.getProperty("hash"));
        List<String> files = splitLines(p.getProperty("filePaths"));

        return new ClipboardEntry(id, createdAt, type, text, files, imagePath, hash,Boolean.parseBoolean(p.getProperty("pinned","false")));
    }

    private String joinLines(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private List<String> splitLines(String value) {
        List<String> values = new ArrayList<String>();
        if (value == null || value.isEmpty()) {
            return values;
        }
        String[] lines = value.split("\\n", -1);
        for (String line : lines) {
            if (!line.isEmpty()) {
                values.add(line);
            }
        }
        return values;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
