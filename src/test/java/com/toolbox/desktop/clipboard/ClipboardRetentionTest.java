package com.toolbox.desktop.clipboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class ClipboardRetentionTest {
    @TempDir Path root;
    @Test void pinnedEntrySurvivesEvictionAndRestart() throws Exception {
        ClipboardHistoryStore store=new ClipboardHistoryStore(root);
        store.setMaxEntries(2);
        ClipboardEntry pinned=store.saveText("keep", "keep");
        store.setPinned(pinned,true);
        store.saveText("old", "old");store.saveText("middle", "middle");store.saveText("new", "new");
        ClipboardHistoryStore reopened=new ClipboardHistoryStore(root);
        assertEquals(2,reopened.getMaxEntries());
        assertEquals(3,reopened.loadAll().size());
        assertTrue(reopened.loadAll().stream().anyMatch(e->e.getId()==pinned.getId()&&e.isPinned()));
        assertFalse(reopened.loadAll().stream().anyMatch(e->"old".equals(e.getText())));
        reopened.setPinned(reopened.loadAll().get(0),false);
        assertFalse(new ClipboardHistoryStore(root).loadAll().stream().anyMatch(ClipboardEntry::isPinned));
    }
    @Test void pinnedEntriesDoNotConsumeOrdinaryHistoryCapacity() throws Exception {
        ClipboardHistoryStore store=new ClipboardHistoryStore(root);store.setMaxEntries(1);
        store.setPinned(store.saveText("keep1","keep1"),true);
        store.setPinned(store.saveText("keep2","keep2"),true);
        store.saveText("old","old");store.saveText("new","new");
        assertEquals(3,store.loadAll().size());
        assertEquals(2,store.loadAll().stream().filter(ClipboardEntry::isPinned).count());
        assertTrue(store.loadAll().stream().anyMatch(e->"new".equals(e.getText())));
    }
    @Test void evictionRemovesOnlyOwnedImageCacheAndNeverReferencedFiles() throws Exception {
        ClipboardHistoryStore store=new ClipboardHistoryStore(root);store.setMaxEntries(1);
        ClipboardEntry image=store.saveImage(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"image");
        Path original=root.resolve("original.txt");Files.write(original,new byte[]{1});
        store.saveFiles(java.util.Collections.singletonList(original.toFile()),"file");
        assertFalse(store.resolveImage(image).exists());assertTrue(Files.exists(original));
        assertEquals(1,store.loadAll().size());
    }
    @Test void loweringLimitTrimsOrdinaryHistoryAndPreservesAllPins() throws Exception {
        ClipboardHistoryStore store=new ClipboardHistoryStore(root);store.setMaxEntries(3);
        store.setPinned(store.saveText("a","a"),true);store.setPinned(store.saveText("b","b"),true);
        store.saveText("old","old");store.saveText("new","new");store.setMaxEntries(1);
        assertEquals(1,store.getMaxEntries());assertEquals(3,store.loadAll().size());
        assertEquals(2,store.loadAll().stream().filter(ClipboardEntry::isPinned).count());
    }
}