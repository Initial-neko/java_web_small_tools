package com.toolbox.desktop.screenshot;

import com.toolbox.desktop.clipboard.ClipboardHistoryStore;
import com.toolbox.desktop.clipboard.ClipboardSupport;
import com.toolbox.desktop.clipboard.ClipboardWatcher;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
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

    public ScreenshotService(ClipboardHistoryStore store, ClipboardWatcher watcher) {
        this.store = store;
        this.watcher = watcher;
    }

    public void captureRegion(final Callback callback) {
        try {
            final VirtualScreen virtualScreen = captureVirtualScreen();
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    SelectionWindow window = new SelectionWindow(virtualScreen, new SelectionListener() {
                        public void selected(Rectangle selection) {
                            try {
                                BufferedImage cropped = crop(virtualScreen.image, selection);
                                String hash = ClipboardSupport.hashImage(cropped);
                                store.saveImage(cropped, hash);
                                File screenshot = store.saveScreenshotCopy(cropped);

                                if (watcher != null) {
                                    watcher.suppressNext(hash);
                                }
                                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                                clipboard.setContents(new ClipboardSupport.ImageTransferable(cropped), null);

                                if (callback != null) {
                                    callback.onSaved(screenshot);
                                }
                            } catch (Exception e) {
                                if (callback != null) {
                                    callback.onError(e);
                                }
                            }
                        }

                        public void cancelled() {
                            if (callback != null) {
                                callback.onCancelled();
                            }
                        }
                    });
                    window.open();
                }
            });
        } catch (Exception e) {
            if (callback != null) {
                callback.onError(e);
            }
        }
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

    private static final class SelectionWindow extends JWindow {
        private final VirtualScreen screen;
        private final SelectionListener listener;
        private final SelectionPanel panel;

        private SelectionWindow(VirtualScreen screen, SelectionListener listener) {
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
                    g.fillRect(selection.x, Math.max(0, selection.y - 22), 88, 20);
                    g.setColor(Color.BLACK);
                    g.drawString(size, selection.x + 6, Math.max(14, selection.y - 7));
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
            return new Rectangle(x, y, width, height);
        }
    }
}
