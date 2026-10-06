package com.toolbox.desktop;

import com.toolbox.desktop.clipboard.ClipboardHistoryStore;
import com.toolbox.desktop.clipboard.ClipboardWatcher;
import com.toolbox.desktop.screenshot.ScreenshotService;
import com.toolbox.desktop.ui.DesktopToolboxWindow;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

public final class DesktopToolboxApplication {

    private DesktopToolboxApplication() {
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Desktop toolbox requires a graphical desktop environment.");
            return;
        }

        final ClipboardHistoryStore store = new ClipboardHistoryStore(resolveDataDir());

        if (hasArg(args, "--screenshot")) {
            runScreenshotOnly(store);
            return;
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                startDesktop(store);
            }
        });
    }

    private static void startDesktop(final ClipboardHistoryStore store) {
        final ClipboardWatcher watcher = new ClipboardWatcher(store);
        final ScreenshotService screenshots = new ScreenshotService(store, watcher);
        final AtomicReference<DesktopToolboxWindow> windowRef = new AtomicReference<DesktopToolboxWindow>();

        final boolean traySupported = SystemTray.isSupported();

        final Runnable screenshotAction = new Runnable() {
            public void run() {
                final DesktopToolboxWindow window = windowRef.get();
                if (window != null) {
                    window.setVisible(false);
                }

                Timer timer = new Timer(120, e -> screenshots.captureRegion(new ScreenshotService.Callback() {
                    public void onSaved(File file) {
                        if (window != null) {
                            window.refreshAsync();
                            if (!traySupported) {
                                window.setVisible(true);
                            }
                        }
                        showMessage("Screenshot saved", file.getAbsolutePath());
                    }

                    public void onCancelled() {
                        if (window != null && !traySupported) {
                            window.setVisible(true);
                        }
                    }

                    public void onError(Exception error) {
                        if (window != null && !traySupported) {
                            window.setVisible(true);
                        }
                        showError("Screenshot failed", error);
                    }
                }));
                timer.setRepeats(false);
                timer.start();
            }
        };

        final DesktopToolboxWindow window = new DesktopToolboxWindow(
                store,
                watcher,
                screenshotAction,
                !traySupported
        );
        windowRef.set(window);
        watcher.addListener(window::refreshAsync);
        watcher.start();

        if (traySupported) {
            installTray(window, screenshotAction, watcher, store);
        }

        window.showWindow();
    }

    private static void runScreenshotOnly(final ClipboardHistoryStore store) {
        final ScreenshotService screenshots = new ScreenshotService(store, null);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                screenshots.captureRegion(new ScreenshotService.Callback() {
                    public void onSaved(File file) {
                        System.exit(0);
                    }

                    public void onCancelled() {
                        System.exit(0);
                    }

                    public void onError(Exception error) {
                        error.printStackTrace();
                        System.exit(1);
                    }
                });
            }
        });
    }

    private static void installTray(final DesktopToolboxWindow window,
                                    final Runnable screenshotAction,
                                    final ClipboardWatcher watcher,
                                    final ClipboardHistoryStore store) {
        try {
            PopupMenu menu = new PopupMenu();

            MenuItem open = new MenuItem("Clipboard history");
            open.addActionListener(e -> SwingUtilities.invokeLater(window::showWindow));
            menu.add(open);

            MenuItem screenshot = new MenuItem("Screenshot");
            screenshot.addActionListener(e -> SwingUtilities.invokeLater(screenshotAction));
            menu.add(screenshot);

            MenuItem dataFolder = new MenuItem("Data folder");
            dataFolder.addActionListener(e -> {
                try {
                    java.awt.Desktop.getDesktop().open(store.getRoot().toFile());
                } catch (Exception ex) {
                    showMessage("Data folder", store.getRoot().toString());
                }
            });
            menu.add(dataFolder);

            menu.addSeparator();

            MenuItem exit = new MenuItem("Exit");
            exit.addActionListener(e -> {
                watcher.stop();
                TrayIcon icon = findTrayIcon();
                if (icon != null) {
                    SystemTray.getSystemTray().remove(icon);
                }
                System.exit(0);
            });
            menu.add(exit);

            TrayIcon icon = new TrayIcon(createTrayImage(), "Java Small Tools", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> SwingUtilities.invokeLater(window::showWindow));
            trayIcon = icon;
            SystemTray.getSystemTray().add(icon);
        } catch (AWTException e) {
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            showError("Cannot install tray icon", e);
        }
    }

    private static volatile TrayIcon trayIcon;

    private static TrayIcon findTrayIcon() {
        return trayIcon;
    }

    private static Image createTrayImage() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(45, 110, 210));
            g.fillRoundRect(1, 1, 14, 14, 5, 5);
            g.setColor(Color.WHITE);
            g.drawLine(5, 4, 11, 4);
            g.drawLine(5, 7, 11, 7);
            g.drawLine(5, 10, 9, 10);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static Path resolveDataDir() {
        String configured = System.getProperty("toolbox.desktop.dataDir");
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured.trim());
        }
        return Paths.get(
                System.getProperty("user.home"),
                ".java-web-small-tools",
                "desktop"
        );
    }

    private static boolean hasArg(String[] args, String expected) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (expected.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void showMessage(String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        } else {
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static void showError(String title, Exception error) {
        JOptionPane.showMessageDialog(
                null,
                error == null ? "Unknown error" : error.getMessage(),
                title,
                JOptionPane.ERROR_MESSAGE
        );
    }
}
