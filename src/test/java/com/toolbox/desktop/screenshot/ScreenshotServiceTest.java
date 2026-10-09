package com.toolbox.desktop.screenshot;
import com.toolbox.desktop.clipboard.ClipboardHistoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class ScreenshotServiceTest {
 @TempDir Path root;
 @Test void draggedOutsideScreenMustClampInsteadOfAddingBlackPixels()throws Exception{
  ScreenshotService service=new ScreenshotService(new ClipboardHistoryStore(root),null);
  BufferedImage image=new BufferedImage(10,8,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,0xff123456);
  Method crop=ScreenshotService.class.getDeclaredMethod("crop",BufferedImage.class,Rectangle.class);crop.setAccessible(true);
  BufferedImage actual=(BufferedImage)crop.invoke(service,image,new Rectangle(-3,-2,8,7));
  assertEquals(5,actual.getWidth());assertEquals(5,actual.getHeight());assertEquals(0xff123456,actual.getRGB(0,0));
 }
}
