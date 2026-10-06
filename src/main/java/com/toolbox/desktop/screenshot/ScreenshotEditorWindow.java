package com.toolbox.desktop.screenshot;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Annotation is applied only when the user completes; cancel never publishes a screenshot. */
public final class ScreenshotEditorWindow extends JFrame {
    public interface Listener { void completed(BufferedImage image,java.util.function.Consumer<String> result); void cancelled(); }
    private final ScreenshotAnnotations annotations;
    private final Listener listener;
    private final JTextField text=new JTextField(14);
    private final JSpinner fontSize=new JSpinner(new SpinnerNumberModel(32,12,96,2));
    private final JLabel status=new JLabel("<html>拖动添加红框；文字模式下输入内容，再点击图片放置。<br>Ctrl+Z 撤销；Esc 取消。</html>");
    private final JPanel canvas;
    private boolean textMode,finished,saving;
    private Point start,end;
    private File exportedFile;

    public ScreenshotEditorWindow(BufferedImage image,Listener listener){
        super("截图标注");com.toolbox.desktop.ui.DesktopFonts.install();this.listener=listener;annotations=new ScreenshotAnnotations(image);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){cancel();}});
        canvas=new JPanel(){protected void paintComponent(Graphics graphics){super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();try{double scale=scale();g.translate(offsetX(scale),offsetY(scale));g.scale(scale,scale);annotations.paint(g);if(start!=null&&end!=null){g.setColor(Color.RED);g.setStroke(new BasicStroke(3));Rectangle r=rectangle(start,end);g.drawRect(r.x,r.y,r.width,r.height);}}finally{g.dispose();}}};
        Font controls=new Font("Microsoft YaHei UI",Font.PLAIN,22);
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT,10,10));
        JToggleButton rectangle=new JToggleButton("红框",true),label=new JToggleButton("文字");ButtonGroup group=new ButtonGroup();group.add(rectangle);group.add(label);
        rectangle.addActionListener(e->{textMode=false;start=null;canvas.repaint();});label.addActionListener(e->{textMode=true;start=null;canvas.repaint();text.requestFocusInWindow();});
        text.setToolTipText("输入标注文字，然后在图片上点击放置");
        toolbar.add(rectangle);toolbar.add(label);toolbar.add(text);toolbar.add(new JLabel("字号"));toolbar.add(fontSize);
        JButton undo=new JButton("撤销");undo.addActionListener(e->undo());toolbar.add(undo);JScrollPane tools=new JScrollPane(toolbar,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);tools.setBorder(null);add(tools,BorderLayout.NORTH);

        canvas.setName("screenshotCanvas");canvas.setBackground(new Color(48,53,59));canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        MouseAdapter mouse=new MouseAdapter(){
            public void mousePressed(MouseEvent e){Point point=imagePoint(e.getPoint());if(point==null||saving)return;if(textMode){annotations.addText(point,text.getText(),(Integer)fontSize.getValue());canvas.repaint();}else{start=point;end=point;}}
            public void mouseDragged(MouseEvent e){if(start!=null){end=clampedImagePoint(e.getPoint());canvas.repaint();}}
            public void mouseReleased(MouseEvent e){if(start!=null){end=clampedImagePoint(e.getPoint());annotations.addRectangle(rectangle(start,end));start=null;end=null;canvas.repaint();}}
        };canvas.addMouseListener(mouse);canvas.addMouseMotionListener(mouse);add(canvas,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());status.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));footer.add(status,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));JButton copy=new JButton("复制并完成"),save=new JButton("保存 PNG"),cancel=new JButton("取消");
        copy.addActionListener(e->complete());save.addActionListener(e->save());cancel.addActionListener(e->cancel());actions.add(copy);actions.add(save);actions.add(cancel);footer.add(actions,BorderLayout.SOUTH);add(footer,BorderLayout.SOUTH);
        applyFont(toolbar,controls);applyFont(footer,controls);
        getRootPane().setDefaultButton(copy);
        getRootPane().registerKeyboardAction(e->undo(),KeyStroke.getKeyStroke(KeyEvent.VK_Z,InputEvent.CTRL_DOWN_MASK),JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e->cancel(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();setSize(Math.min(1200,screen.width-80),Math.min(900,screen.height-80));setMinimumSize(new Dimension(700,500));setLocationRelativeTo(null);
    }
    private void applyFont(Component component,Font font){component.setFont(font);if(component instanceof Container)for(Component child:((Container)component).getComponents())applyFont(child,font);}
    private double scale(){return Math.max(.01,Math.min(1,Math.min((double)canvas.getWidth()/annotations.getWidth(),(double)canvas.getHeight()/annotations.getHeight())));}
    private int offsetX(double scale){return (int)((canvas.getWidth()-annotations.getWidth()*scale)/2);}
    private int offsetY(double scale){return (int)((canvas.getHeight()-annotations.getHeight()*scale)/2);}
    private Point clampedImagePoint(Point p){double s=scale();return new Point(Math.max(0,Math.min(annotations.getWidth()-1,(int)((p.x-offsetX(s))/s))),Math.max(0,Math.min(annotations.getHeight()-1,(int)((p.y-offsetY(s))/s))));}
    private Point imagePoint(Point p){double s=scale();if(p.x<offsetX(s)||p.y<offsetY(s)||p.x>=offsetX(s)+annotations.getWidth()*s||p.y>=offsetY(s)+annotations.getHeight()*s)return null;return clampedImagePoint(p);}
    private Rectangle rectangle(Point a,Point b){return new Rectangle(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.abs(a.x-b.x)+1,Math.abs(a.y-b.y)+1);}
    private void undo(){if(!saving){annotations.undo();start=null;end=null;canvas.repaint();}}
    private void complete(){
        if(finished||saving)return;saving=true;status.setText("正在生成标注图片…");
        new SwingWorker<BufferedImage,Void>(){
            protected BufferedImage doInBackground(){return annotations.render();}
            protected void done(){try{BufferedImage image=get();submitResult(image);}catch(Exception e){saving=false;status.setText("生成图片失败，请重试："+e.getMessage());}}
        }.execute();
    }
    private void submitResult(BufferedImage image){
        listener.completed(image,error->SwingUtilities.invokeLater(()->{
            saving=false;
            if(error==null){finished=true;dispose();}
            else{String message=(exportedFile==null?"":"PNG 已保存；")+"发布失败，标注已保留，请重试："+error;status.setText(message);status.setToolTipText(message);}
        }));
    }
    private void cancel(){if(finished||saving)return;finished=true;dispose();listener.cancelled();}
    private void save(){
        if(finished||saving)return;JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("screenshot.png"));
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        File chosen=chooser.getSelectedFile();final File file=chosen.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".png")?chosen:new File(chosen.getParentFile(),chosen.getName()+".png");
        if(file.exists()&&JOptionPane.showConfirmDialog(this,"文件已存在，是否覆盖？","保存 PNG",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        saving=true;status.setText("正在保存 PNG…");
        new SwingWorker<BufferedImage,Void>(){protected BufferedImage doInBackground()throws Exception{BufferedImage output=annotations.render();if(!javax.imageio.ImageIO.write(output,"png",file))throw new java.io.IOException("PNG writer unavailable");return output;}protected void done(){try{BufferedImage output=get();exportedFile=file;submitResult(output);}catch(Exception e){saving=false;status.setText("保存失败，请重试："+e.getMessage());}}}.execute();
    }
}
