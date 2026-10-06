package com.toolbox.desktop.screenshot;

import com.toolbox.desktop.clipboard.*;
import java.awt.image.BufferedImage;
import java.io.*;

/** A retry belongs to the same capture; busy clipboard must not append or evict history. */
final class ScreenshotPublisher {
    interface ClipboardWriter { void copy(BufferedImage image); }
    private final ClipboardHistoryStore store;
    private final ClipboardWriter clipboard;
    private ClipboardEntry saved;
    ScreenshotPublisher(ClipboardHistoryStore store,ClipboardWriter clipboard){this.store=store;this.clipboard=clipboard;}
    synchronized File publish(BufferedImage image)throws IOException{
        clipboard.copy(image);
        if(saved==null)saved=store.saveImage(image,ClipboardSupport.hashImage(image));
        return store.resolveImage(saved);
    }
}
