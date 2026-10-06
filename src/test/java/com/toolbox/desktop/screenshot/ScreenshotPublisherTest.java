package com.toolbox.desktop.screenshot;
import com.toolbox.desktop.clipboard.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class ScreenshotPublisherTest {
 @TempDir Path root;
 @Test void busyClipboardKeepsOldHistoryAndRetryWritesExactlyOneImage()throws Exception{
  ClipboardHistoryStore store=new ClipboardHistoryStore(root);store.setMaxEntries(1);ClipboardEntry old=store.saveText("old","old");
  AtomicInteger attempts=new AtomicInteger();ScreenshotPublisher publisher=new ScreenshotPublisher(store,image->{if(attempts.getAndIncrement()==0)throw new IllegalStateException("busy");});
  BufferedImage image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);
  assertThrows(IllegalStateException.class,()->publisher.publish(image));assertEquals(old.getId(),store.loadAll().get(0).getId());
  java.io.File file=publisher.publish(image);publisher.publish(image);assertTrue(file.exists());assertEquals(1,store.loadAll().size());assertEquals(ClipboardEntry.Type.IMAGE,store.loadAll().get(0).getType());
 }
}
