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
    private final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<Runnable>();

    private volatile String lastHash;
    private volatile String suppressedHash;
    private volatile boolean started;

    public ClipboardWatcher(ClipboardHistoryStore store) {
        this.store = store;
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

    public void suppressNext(String hash) {
        this.suppressedHash = hash;
    }

    @SuppressWarnings("unchecked")
    private void poll() {
        try {
            Transferable transferable = clipboard.getContents(null);
            if (transferable == null) {
                return;
            }

            String hash;
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                hash = ClipboardSupport.hashFiles(files);
                if (skip(hash)) {
                    return;
                }
                store.saveFiles(files, hash);
                captured(hash);
                return;
            }

            if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image image = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
                BufferedImage buffered = ClipboardSupport.toBufferedImage(image);
                hash = ClipboardSupport.hashImage(buffered);
                if (skip(hash)) {
                    return;
                }
                store.saveImage(buffered, hash);
                captured(hash);
                return;
            }

            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
                if (text == null || text.isEmpty()) {
                    return;
                }
                hash = ClipboardSupport.hashText(text);
                if (skip(hash)) {
                    return;
                }
                store.saveText(text, hash);
                captured(hash);
            }
        } catch (IllegalStateException ignored) {
            // Clipboard is temporarily busy. The next poll will retry.
        } catch (Exception ignored) {
            // Clipboard formats can be provided by arbitrary applications. A bad flavor should not stop the watcher.
        }
    }

    private boolean skip(String hash) {
        if (hash == null) {
            return true;
        }
        if (hash.equals(lastHash)) {
            return true;
        }
        if (hash.equals(suppressedHash)) {
            suppressedHash = null;
            lastHash = hash;
            return true;
        }
        return false;
    }

    private void captured(String hash) {
        lastHash = hash;
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
