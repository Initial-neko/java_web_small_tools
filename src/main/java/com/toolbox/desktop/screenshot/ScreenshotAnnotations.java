package com.toolbox.desktop.screenshot;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Coordinates and font sizes use original image pixels, independent of display zoom. */
public final class ScreenshotAnnotations {
    private interface Mark { void paint(Graphics2D graphics); }
    private final BufferedImage original;
    private final List<Mark> marks=new ArrayList<Mark>();

    public ScreenshotAnnotations(BufferedImage image) {
        original=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=original.createGraphics();try{g.drawImage(image,0,0,null);}finally{g.dispose();}
    }
    public void addRectangle(Rectangle rectangle) {
        final Rectangle r=rectangle.intersection(new Rectangle(0,0,original.getWidth(),original.getHeight()));
        if(r.width<2||r.height<2)return;
        marks.add(g->{g.setColor(Color.RED);g.setStroke(new BasicStroke(3));g.drawRect(r.x,r.y,r.width-1,r.height-1);});
    }
    public void addText(Point point,String text,int size) {
        if(text==null||text.trim().isEmpty())return;
        final Point p=new Point(point);final String[] lines=text.split("\\r?\\n",-1);
        final Font font=new Font("Microsoft YaHei UI",Font.PLAIN,Math.max(12,Math.min(96,size)));
        marks.add(g->{g.setColor(Color.RED);g.setFont(font);int y=p.y+g.getFontMetrics().getAscent();for(String line:lines){g.drawString(line,p.x,y);y+=g.getFontMetrics().getHeight();}});
    }
    public boolean undo(){if(marks.isEmpty())return false;marks.remove(marks.size()-1);return true;}
    public int getWidth(){return original.getWidth();}
    public int getHeight(){return original.getHeight();}
    public void paint(Graphics2D g){g.drawImage(original,0,0,null);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);for(Mark mark:marks)mark.paint(g);}
    public BufferedImage render(){
        BufferedImage output=new BufferedImage(original.getWidth(),original.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=output.createGraphics();
        try{paint(g);}finally{g.dispose();}
        return output;
    }
}
