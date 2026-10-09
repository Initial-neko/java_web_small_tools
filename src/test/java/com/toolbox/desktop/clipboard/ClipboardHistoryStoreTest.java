package com.toolbox.desktop.clipboard;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardHistoryStoreTest {

    @Test
    void persistsTextFilesAndImages() throws Exception {
        Path root = Files.createTempDirectory("clipboard-history-test");
        ClipboardHistoryStore store = new ClipboardHistoryStore(root);

        String textHash = ClipboardSupport.hashText("hello");
        store.saveText("hello", textHash);
        store.saveFiles(Arrays.asList(new File("a.txt"), new File("b.txt")),
                ClipboardSupport.hashText("files"));

        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        ClipboardEntry imageEntry = store.saveImage(image, ClipboardSupport.hashImage(image));

        List<ClipboardEntry> entries = store.loadAll();
        assertEquals(3, entries.size());
        assertTrue(store.resolveImage(imageEntry).isFile());

        boolean foundText = false;
        boolean foundFiles = false;
        for (ClipboardEntry entry : entries) {
            if (entry.getType() == ClipboardEntry.Type.TEXT) {
                foundText = "hello".equals(entry.getText());
            }
            if (entry.getType() == ClipboardEntry.Type.FILES) {
                foundFiles = entry.getFilePaths().size() == 2;
            }
        }

        assertTrue(foundText);
        assertTrue(foundFiles);

        store.delete(imageEntry);
        assertEquals(2, store.loadAll().size());
    }
}
