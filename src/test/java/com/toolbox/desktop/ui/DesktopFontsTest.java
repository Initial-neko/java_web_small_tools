package com.toolbox.desktop.ui;

import com.toolbox.desktop.screenshot.ScreenshotEditorWindow;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class DesktopFontsTest {
    private void readable(Component component) {
        if (component instanceof JLabel || component instanceof AbstractButton || component instanceof javax.swing.text.JTextComponent) {
            assertTrue(component.getFont().getSize() >= 22, component.getClass().getName());
        }
        if (component instanceof Container) for (Component child : ((Container) component).getComponents()) readable(child);
    }

    @Test void standardChildDialogsAndScreenshotControlsAreReadable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DesktopFonts.install();
            readable(new JOptionPane("删除这条历史？", JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION));
            readable(new JFileChooser());
            assertEquals(24, UIManager.getFont("OptionPane.messageFont").getSize());
            ScreenshotEditorWindow editor = new ScreenshotEditorWindow(new BufferedImage(200,100,BufferedImage.TYPE_INT_RGB), new ScreenshotEditorWindow.Listener() {
                public void completed(BufferedImage image, java.util.function.Consumer<String> result) { result.accept(null); }
                public void cancelled() { }
            });
            try { readable(editor); } finally { editor.dispose(); }
        });
    }
}
