package com.toolbox.desktop.clipboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClipboardEntry {

    public enum Type {
        TEXT, IMAGE, FILES
    }

    private final long id;
    private final long createdAt;
    private final Type type;
    private final String text;
    private final List<String> filePaths;
    private final String imagePath;
    private final String hash;

    public ClipboardEntry(long id,
                          long createdAt,
                          Type type,
                          String text,
                          List<String> filePaths,
                          String imagePath,
                          String hash) {
        this.id = id;
        this.createdAt = createdAt;
        this.type = type;
        this.text = text;
        this.filePaths = filePaths == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(filePaths));
        this.imagePath = imagePath;
        this.hash = hash;
    }

    public long getId() {
        return id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Type getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public List<String> getFilePaths() {
        return filePaths;
    }

    public String getImagePath() {
        return imagePath;
    }

    public String getHash() {
        return hash;
    }

    public String preview() {
        if (type == Type.TEXT) {
            return compact(text);
        }
        if (type == Type.FILES) {
            if (filePaths.isEmpty()) {
                return "(empty file list)";
            }
            if (filePaths.size() == 1) {
                return filePaths.get(0);
            }
            return filePaths.get(0) + "  (+" + (filePaths.size() - 1) + " files)";
        }
        return imagePath == null ? "(image)" : imagePath;
    }

    private String compact(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        while (normalized.contains("  ")) {
            normalized = normalized.replace("  ", " ");
        }
        return normalized.length() > 160 ? normalized.substring(0, 157) + "..." : normalized;
    }
}
