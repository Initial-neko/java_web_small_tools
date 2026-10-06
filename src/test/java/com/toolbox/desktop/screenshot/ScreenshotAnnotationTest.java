package com.toolbox.desktop.screenshot;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class ScreenshotAnnotationTest {
    @Test void redRectangleRendersAndUndoRestoresOriginalPixels() {
        BufferedImage original=new BufferedImage(120,80,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=original.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,120,80);g.dispose();
        ScreenshotAnnotations model=new ScreenshotAnnotations(original);
        model.addRectangle(new Rectangle(10,10,50,30));
        BufferedImage annotated=model.render();
        assertEquals(Color.RED.getRGB(),annotated.getRGB(10,20));
        assertEquals(Color.WHITE.getRGB(),original.getRGB(10,20),"source screenshot must remain unchanged");
        assertTrue(model.undo());
        assertEquals(Color.WHITE.getRGB(),model.render().getRGB(10,20));
        assertFalse(model.undo());
    }
    @Test void textRendersAndExportPreservesOriginalDimensions() {
        BufferedImage original=new BufferedImage(300,150,BufferedImage.TYPE_INT_RGB);
        ScreenshotAnnotations model=new ScreenshotAnnotations(original);
        model.addText(new Point(20,20),"文字标注\nLabel",24);
        BufferedImage output=model.render();
        assertEquals(300,output.getWidth());assertEquals(150,output.getHeight());
        int changed=0;for(int y=0;y<150;y++)for(int x=0;x<300;x++)if(output.getRGB(x,y)!=original.getRGB(x,y))changed++;
        assertTrue(changed>50,"export must contain visible annotation pixels");
        assertTrue(model.undo());
        assertEquals(original.getRGB(30,30),model.render().getRGB(30,30));
    }
}
