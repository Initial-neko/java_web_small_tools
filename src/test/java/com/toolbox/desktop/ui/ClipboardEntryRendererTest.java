package com.toolbox.desktop.ui;

import com.toolbox.desktop.clipboard.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ClipboardEntryRendererTest {
    @TempDir Path root;
    @Test void measuringLargeImageHistoryDoesNotQueueOffscreenImageReads() throws Exception {
        ClipboardHistoryStore store = new ClipboardHistoryStore(root);
        AtomicInteger queued = new AtomicInteger();
        ExecutorService loader = new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>()) {
            public void execute(Runnable task) { queued.incrementAndGet(); }
        };
        try {
            SwingUtilities.invokeAndWait(() -> {
                ClipboardEntryRenderer renderer = new ClipboardEntryRenderer(store,loader,()->{});
                DefaultListModel<ClipboardEntry> model=new DefaultListModel<ClipboardEntry>();
                JList<ClipboardEntry> list=new JList<ClipboardEntry>(model);list.setCellRenderer(renderer);
                for(int i=0;i<160;i++)model.addElement(new ClipboardEntry(i,i,ClipboardEntry.Type.IMAGE,null,null,"images/"+i+".png","hash"+i));
                assertNotNull(list.getPreferredSize());
                assertNotNull(list.getCellBounds(0,159));
                assertEquals(0,queued.get(),"measurement alone must not trigger image IO or recursive layout");
                renderer.setVisibleRange(0,2);
                for(int pass=0;pass<3;pass++)for(int i=0;i<160;i++) {
                    ClipboardEntry entry = new ClipboardEntry(i,i,ClipboardEntry.Type.IMAGE,null,null,"images/"+i+".png","hash"+i);
                    renderer.getListCellRendererComponent(list,entry,i,false,false).getPreferredSize();
                }
                assertEquals(3,queued.get(),"layout measurement must not load the entire image history");
            });
        } finally {loader.shutdownNow();}
    }
}
