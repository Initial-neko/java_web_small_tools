package com.toolbox.desktop.clipboard;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ClipboardWatcher {

    private final ClipboardHistoryStore store;
    private final Clipboard clipboard;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<Runnable>();

    private volatile String lastHash;
    private volatile String suppressedHash;
    private volatile boolean started;
    private volatile boolean paused;
    public synchronized void setPaused(boolean paused) { this.paused = paused; }
    public boolean isPaused() { return paused; }

    public ClipboardWatcher(ClipboardHistoryStore store) {
        this(store, Toolkit.getDefaultToolkit().getSystemClipboard());
    }

    ClipboardWatcher(ClipboardHistoryStore store, Clipboard clipboard) {
        this.store = store;
        this.clipboard = clipboard;
    }

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        executor.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                poll();
            }
        }, 0L, 400L, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        executor.shutdownNow();
        started = false;
    }

    public synchronized void suppressNext(String hash) {
        this.suppressedHash = hash;
    }

    /** Publish and update deduplication state under the same lock as polling. */
    public synchronized boolean restore(ClipboardEntry entry) {
        suppressedHash = null;
        if (!ClipboardSupport.restore(entry, store, clipboard)) return false;
        lastHash = key(entry.getType(), entry.getHash());
        return true;
    }

    public synchronized void copyImage(BufferedImage image) {
        String hash = ClipboardSupport.hashImage(image);
        clipboard.setContents(new ClipboardSupport.ImageTransferable(image), null);
        suppressedHash = null;
        lastHash = key(ClipboardEntry.Type.IMAGE, hash);
    }

    @SuppressWarnings("unchecked")
    private synchronized void poll() {
        if (paused) return;
        try {
            Transferable transferable = clipboard.getContents(null);
            if (transferable == null) {
                return;
            }

            String hash;
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                hash = ClipboardSupport.hashFiles(files);
                if (skip(ClipboardEntry.Type.FILES, hash)) {
                    return;
                }
                store.saveFiles(files, hash);
                captured(ClipboardEntry.Type.FILES, hash);
                return;
            }

            if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image image = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
                BufferedImage buffered = ClipboardSupport.toBufferedImage(image);
                hash = ClipboardSupport.hashImage(buffered);
                if (skip(ClipboardEntry.Type.IMAGE, hash)) {
                    return;
                }
                store.saveImage(buffered, hash);
                captured(ClipboardEntry.Type.IMAGE, hash);
                return;
            }

            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
                if (text == null || text.isEmpty()) {
                    return;
                }
                hash = ClipboardSupport.hashText(text);
                if (skip(ClipboardEntry.Type.TEXT, hash)) {
                    return;
                }
                store.saveText(text, hash);
                captured(ClipboardEntry.Type.TEXT, hash);
            }
        } catch (IllegalStateException ignored) {
            // Clipboard is temporarily busy. The next poll will retry.
        } catch (Exception ignored) {
            // Clipboard formats can be provided by arbitrary applications. A bad flavor should not stop the watcher.
        }
    }

    private boolean skip(ClipboardEntry.Type type, String hash) {
        if (hash == null) {
            return true;
        }
        String pending = suppressedHash;
        suppressedHash = null;
        String fingerprint = key(type, hash);
        if (hash.equals(pending)) {
            lastHash = fingerprint;
            return true;
        }
        return fingerprint.equals(lastHash);
    }

    private String key(ClipboardEntry.Type type, String hash) {
        return type.name() + ":" + hash;
    }

    private void captured(ClipboardEntry.Type type, String hash) {
        lastHash = key(type, hash);
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
