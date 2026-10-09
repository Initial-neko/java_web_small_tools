package com.toolbox.desktop.screenshot;

import com.toolbox.desktop.clipboard.ClipboardHistoryStore;
import com.toolbox.desktop.clipboard.ClipboardSupport;
import com.toolbox.desktop.clipboard.ClipboardWatcher;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.AWTException;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class ScreenshotService {

    public interface Callback {
        void onSaved(File file);

        void onCancelled();

        void onError(Exception error);
    }

    private final ClipboardHistoryStore store;
    private final ClipboardWatcher watcher;
    private boolean capturing;
    private synchronized void releaseCapture(){capturing=false;}

    public ScreenshotService(ClipboardHistoryStore store, ClipboardWatcher watcher) {
        this.store = store;
        this.watcher = watcher;
    }

    public synchronized void captureRegion(final Callback callback) {
        if(capturing)return;capturing=true;
        final Callback result=new Callback(){
            public void onSaved(File file){releaseCapture();if(callback!=null)callback.onSaved(file);}
            public void onCancelled(){releaseCapture();if(callback!=null)callback.onCancelled();}
            public void onError(Exception error){releaseCapture();if(callback!=null)callback.onError(error);}
        };
        new SwingWorker<VirtualScreen, Void>() {
            protected VirtualScreen doInBackground() throws Exception { return captureVirtualScreen(); }
            protected void done() {
                try {
                    final VirtualScreen screen = get();
                    SelectionWindow window = new SelectionWindow(screen, new SelectionListener() {
                        public void selected(final Rectangle selection) {
                            try{BufferedImage cropped=crop(screen.image,selection);
                            final ScreenshotPublisher publisher=new ScreenshotPublisher(store,image->{if(watcher!=null)watcher.copyImage(image);else Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ClipboardSupport.ImageTransferable(image),null);});
                            ScreenshotEditorWindow editor=new ScreenshotEditorWindow(cropped,new ScreenshotEditorWindow.Listener(){
                                public void completed(final BufferedImage output,java.util.function.Consumer<String> finished){publishScreenshot(output,publisher,result,finished);}
                                public void cancelled(){if(result!=null)result.onCancelled();}
                            });
                            editor.setVisible(true);
                            }catch(Exception e){result.onError(e);}
                        }
                        public void cancelled() { if(result!=null)result.onCancelled(); }
                    });
                    window.open();
                } catch(Exception e) { if(result!=null)result.onError(e); }
            }
        }.execute();
    }
    private void publishScreenshot(final BufferedImage image,final ScreenshotPublisher publisher,final Callback callback,final java.util.function.Consumer<String> finished){
        new SwingWorker<File,Void>(){
            protected File doInBackground()throws Exception{return publisher.publish(image);}
            protected void done(){
                try{File file=get();finished.accept(null);if(callback!=null)callback.onSaved(file);}
                catch(Exception e){Throwable cause=e.getCause()==null?e:e.getCause();finished.accept(cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage());}
            }
        }.execute();
    }
    private VirtualScreen captureVirtualScreen() throws AWTException {
        GraphicsDevice[] devices = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getScreenDevices();

        Rectangle union = null;
        for (GraphicsDevice device : devices) {
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            union = union == null ? new Rectangle(bounds) : union.union(bounds);
        }

        if (union == null || union.width <= 0 || union.height <= 0) {
            throw new IllegalStateException("No screen device available");
        }

        BufferedImage image = new BufferedImage(union.width, union.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            for (GraphicsDevice device : devices) {
                Rectangle bounds = device.getDefaultConfiguration().getBounds();
                Robot robot = new Robot(device);
                BufferedImage part = robot.createScreenCapture(bounds);
                g.drawImage(part, bounds.x - union.x, bounds.y - union.y, null);
            }
        } finally {
            g.dispose();
        }
        return new VirtualScreen(union, image);
    }

    private BufferedImage crop(BufferedImage source, Rectangle selection) {
        selection = selection.intersection(new Rectangle(0, 0, source.getWidth(), source.getHeight()));
        if (selection.width <= 0 || selection.height <= 0) {
            throw new IllegalArgumentException("Selection is outside the screen");
        }
        BufferedImage result = new BufferedImage(selection.width, selection.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.drawImage(
                    source,
                    0,
                    0,
                    selection.width,
                    selection.height,
                    selection.x,
                    selection.y,
                    selection.x + selection.width,
                    selection.y + selection.height,
                    null
            );
        } finally {
            g.dispose();
        }
        return result;
    }

    private static final class VirtualScreen {
        private final Rectangle bounds;
        private final BufferedImage image;

        private VirtualScreen(Rectangle bounds, BufferedImage image) {
            this.bounds = bounds;
            this.image = image;
        }
    }

    private interface SelectionListener {
        void selected(Rectangle selection);

        void cancelled();
    }

    private static final class SelectionWindow extends JFrame {
        private final VirtualScreen screen;
        private final SelectionListener listener;
        private final SelectionPanel panel;

        private SelectionWindow(VirtualScreen screen, SelectionListener listener) {
            super("区域截图 · Esc 取消");
            setUndecorated(true);
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            this.screen = screen;
            this.listener = listener;
            this.panel = new SelectionPanel(screen.image, new SelectionListener() {
                public void selected(Rectangle selection) {
                    dispose();
                    listener.selected(selection);
                }

                public void cancelled() {
                    dispose();
                    listener.cancelled();
                }
            });

            addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){panel.cancel();}});
            setAlwaysOnTop(true);
            setBounds(screen.bounds);
            setContentPane(panel);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
            getRootPane().getActionMap().put("cancel", new javax.swing.AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    panel.cancel();
                }
            });
        }

        private void open() {
            setVisible(true);
            requestFocus();
        }
    }

    private static final class SelectionPanel extends JComponent {
        private final BufferedImage image;
        private final SelectionListener listener;
        private java.awt.Point start;
        private java.awt.Point current;

        private SelectionPanel(BufferedImage image, SelectionListener listener) {
            this.image = image;
            this.listener = listener;

            MouseAdapter mouse = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    start = e.getPoint();
                    current = e.getPoint();
                    repaint();
                }

                public void mouseDragged(MouseEvent e) {
                    current = e.getPoint();
                    repaint();
                }

                public void mouseReleased(MouseEvent e) {
                    current = e.getPoint();
                    Rectangle selection = selection();
                    if (selection != null && selection.width >= 2 && selection.height >= 2) {
                        listener.selected(selection);
                    } else {
                        start = null;
                        current = null;
                        repaint();
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private void cancel() {
            listener.cancelled();
        }

        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.drawImage(image, 0, 0, null);

                g.setComposite(AlphaComposite.SrcOver.derive(0.48f));
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setComposite(AlphaComposite.SrcOver);

                Rectangle selection = selection();
                if (selection != null) {
                    g.drawImage(
                            image,
                            selection.x,
                            selection.y,
                            selection.x + selection.width,
                            selection.y + selection.height,
                            selection.x,
                            selection.y,
                            selection.x + selection.width,
                            selection.y + selection.height,
                            null
                    );
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawRect(selection.x, selection.y, selection.width - 1, selection.height - 1);

                    String size = selection.width + " x " + selection.height;
                    g.setFont(new java.awt.Font("Microsoft YaHei UI", java.awt.Font.PLAIN, 24));
                    java.awt.FontMetrics metrics = g.getFontMetrics();
                    int badgeHeight = metrics.getHeight() + 12;
                    int badgeY = Math.max(0, selection.y - badgeHeight);
                    g.fillRect(selection.x, badgeY, metrics.stringWidth(size) + 16, badgeHeight);
                    g.setColor(Color.BLACK);
                    g.drawString(size, selection.x + 8, badgeY + 6 + metrics.getAscent());
                }
            } finally {
                g.dispose();
            }
        }

        private Rectangle selection() {
            if (start == null || current == null) {
                return null;
            }
            int x = Math.min(start.x, current.x);
            int y = Math.min(start.y, current.y);
            int width = Math.abs(start.x - current.x);
            int height = Math.abs(start.y - current.y);
            return new Rectangle(x, y, width, height).intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        }
    }
}
