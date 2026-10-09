package com.toolbox.desktop;

import com.toolbox.desktop.clipboard.ClipboardHistoryStore;
import com.toolbox.desktop.clipboard.ClipboardWatcher;
import com.toolbox.desktop.screenshot.ScreenshotService;
import com.toolbox.desktop.ui.DesktopToolboxWindow;

import com.toolbox.desktop.hotkey.GlobalHotkeys;
import com.toolbox.desktop.hotkey.HotkeySpec;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
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

        com.toolbox.desktop.ui.DesktopFonts.install();
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
                            window.showWindow();
                            window.showStatus("截图已复制并保存 · " + file.getName());
                        }
                        showMessage("截图已保存", file.getAbsolutePath());
                    }

                    public void onCancelled() {
                        if (window != null) {
                            window.showWindow();
                            window.showStatus("已取消截图");
                        }
                    }

                    public void onError(Exception error) {
                        if (window != null) {
                            window.showWindow();
                        }
                        showError("截图失败", error);
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
        final GlobalHotkeys hotkeys=new GlobalHotkeys(store.getRoot(),window::showWindow,screenshotAction,window::showStatus);
        window.setShortcutSettings(()->showShortcutSettings(window,hotkeys));
        Runtime.getRuntime().addShutdownHook(new Thread(()->{hotkeys.close();watcher.stop();},"desktop-cleanup"));
        watcher.addListener(window::refreshAsync);
        watcher.start();

        if (traySupported) {
            installTray(window, screenshotAction, watcher, store);
        }

        window.showWindow();
        hotkeys.start();
    }

    private static void showShortcutSettings(final DesktopToolboxWindow window,final GlobalHotkeys hotkeys){
        JPanel panel=new JPanel(new java.awt.GridLayout(0,1,8,8));
        JTextField open=new JTextField(hotkeys.getOpenKey().toString(),22),screenshot=new JTextField(hotkeys.getScreenshotKey().toString(),22);
        panel.add(new JLabel("打开剪切板（后台也生效）"));panel.add(open);
        panel.add(new JLabel("截图（后台也生效）"));panel.add(screenshot);
        panel.add(new JLabel("格式：Ctrl+Alt+V；支持 Ctrl、Alt、Shift + 字母、数字、F1–F11"));
        for(java.awt.Component component:panel.getComponents())component.setFont(new java.awt.Font("Microsoft YaHei UI",java.awt.Font.PLAIN,22));
        if(JOptionPane.showConfirmDialog(window,panel,"全局快捷键",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        try{hotkeys.update(HotkeySpec.parse(open.getText()),HotkeySpec.parse(screenshot.getText()),message->{window.showStatus(message);JOptionPane.showMessageDialog(window,message,"全局快捷键",JOptionPane.INFORMATION_MESSAGE);});}
        catch(IllegalArgumentException e){JOptionPane.showMessageDialog(window,e.getMessage(),"快捷键格式错误",JOptionPane.WARNING_MESSAGE);}
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

            MenuItem open = new MenuItem("打开剪切板历史");
            open.addActionListener(e -> SwingUtilities.invokeLater(window::showWindow));
            menu.add(open);

            MenuItem screenshot = new MenuItem("截图");
            screenshot.addActionListener(e -> SwingUtilities.invokeLater(screenshotAction));
            menu.add(screenshot);

            MenuItem dataFolder = new MenuItem("打开数据目录");
            dataFolder.addActionListener(e -> {
                try {
                    java.awt.Desktop.getDesktop().open(store.getRoot().toFile());
                } catch (Exception ex) {
                    showMessage("数据目录", store.getRoot().toString());
                }
            });
            menu.add(dataFolder);

            menu.addSeparator();

            MenuItem exit = new MenuItem("退出程序");
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
            showError("托盘安装失败，关闭窗口将退出", e);
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
