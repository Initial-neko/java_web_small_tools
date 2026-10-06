package com.toolbox.desktop.screenshot;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ScreenshotEditorWindowTest {
    private Component find(Component component,String name){
        if(name.equals(component.getName())||component instanceof AbstractButton&&name.equals(((AbstractButton)component).getText()))return component;
        if(component instanceof Container)for(Component child:((Container)component).getComponents()){Component found=find(child,name);if(found!=null)return found;}return null;
    }
    @Test void scaledCanvasDragCopiesRedFrameAtOriginalImageCoordinates() throws Exception {
        BufferedImage source=new BufferedImage(200,100,BufferedImage.TYPE_INT_RGB);Graphics2D g=source.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,200,100);g.dispose();
        CountDownLatch done=new CountDownLatch(1);AtomicReference<BufferedImage> output=new AtomicReference<BufferedImage>();AtomicReference<ScreenshotEditorWindow> window=new AtomicReference<ScreenshotEditorWindow>();
        try {
            SwingUtilities.invokeAndWait(()->{
                ScreenshotEditorWindow editor=new ScreenshotEditorWindow(source,new ScreenshotEditorWindow.Listener(){public void completed(BufferedImage image,java.util.function.Consumer<String> result){output.set(image);result.accept(null);done.countDown();}public void cancelled(){}});window.set(editor);
                Component canvas=find(editor,"screenshotCanvas");canvas.setSize(400,200);
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,0,0,110,60,1,false,MouseEvent.BUTTON1));
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON1_DOWN_MASK,160,90,0,false,MouseEvent.NOBUTTON));
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,0,0,160,90,1,false,MouseEvent.BUTTON1));
                ((AbstractButton)find(editor,"复制并完成")).doClick();
            });
            assertTrue(done.await(5,TimeUnit.SECONDS));assertNotNull(output.get());assertEquals(200,output.get().getWidth());assertEquals(100,output.get().getHeight());
            assertEquals(Color.RED.getRGB(),output.get().getRGB(10,20));assertEquals(Color.WHITE.getRGB(),source.getRGB(10,20));
        }finally{if(window.get()!=null)SwingUtilities.invokeAndWait(()->window.get().dispose());}
    }
    @Test void failedPublicationKeepsAnnotationsAndAllowsRetry() throws Exception {
        AtomicInteger attempts=new AtomicInteger();CountDownLatch failed=new CountDownLatch(1),saved=new CountDownLatch(1);
        AtomicReference<BufferedImage> first=new AtomicReference<BufferedImage>(),second=new AtomicReference<BufferedImage>();AtomicReference<ScreenshotEditorWindow> window=new AtomicReference<ScreenshotEditorWindow>();
        try {
            SwingUtilities.invokeAndWait(()->{
                ScreenshotEditorWindow editor=new ScreenshotEditorWindow(new BufferedImage(200,100,BufferedImage.TYPE_INT_RGB),new ScreenshotEditorWindow.Listener(){
                    public void completed(BufferedImage image,java.util.function.Consumer<String> result){if(attempts.getAndIncrement()==0){first.set(image);result.accept("busy");failed.countDown();}else{second.set(image);result.accept(null);saved.countDown();}}
                    public void cancelled(){}
                });window.set(editor);Component canvas=find(editor,"screenshotCanvas");canvas.setSize(400,200);
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,0,0,110,60,1,false,MouseEvent.BUTTON1));
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,0,0,160,90,1,false,MouseEvent.BUTTON1));
                ((AbstractButton)find(editor,"复制并完成")).doClick();
            });
            assertTrue(failed.await(5,TimeUnit.SECONDS));SwingUtilities.invokeAndWait(()->((AbstractButton)find(window.get(),"复制并完成")).doClick());
            assertTrue(saved.await(5,TimeUnit.SECONDS));assertEquals(2,attempts.get());
            assertEquals(Color.RED.getRGB(),second.get().getRGB(10,20));
            assertArrayEquals(first.get().getRGB(0,0,200,100,null,0,200),second.get().getRGB(0,0,200,100,null,0,200));
        } finally {if(window.get()!=null)SwingUtilities.invokeAndWait(()->window.get().dispose());}
    }
    @Test void cancellingEditorDoesNotPublishAndIsOnlyDeliveredOnce() throws Exception {
        AtomicInteger saved=new AtomicInteger(),cancelled=new AtomicInteger();
        SwingUtilities.invokeAndWait(()->{
            ScreenshotEditorWindow editor=new ScreenshotEditorWindow(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),new ScreenshotEditorWindow.Listener(){public void completed(BufferedImage image,java.util.function.Consumer<String> result){saved.incrementAndGet();result.accept(null);}public void cancelled(){cancelled.incrementAndGet();}});
            try{((AbstractButton)find(editor,"取消")).doClick();editor.dispatchEvent(new WindowEvent(editor,WindowEvent.WINDOW_CLOSING));}
            finally{editor.dispose();}
        });assertEquals(0,saved.get());assertEquals(1,cancelled.get());
    }
}
