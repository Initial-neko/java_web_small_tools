package com.toolbox.desktop.ui;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;

/** Desktop-only defaults, including option panes, file choosers and tooltips. */
public final class DesktopFonts {
    private DesktopFonts() { }

    public static void install() {
        for (Object key : new ArrayList<Object>(Collections.list(UIManager.getDefaults().keys()))) {
            Object value = UIManager.get(key);
            if (value instanceof Font) {
                Font old = (Font) value;
                UIManager.put(key, new FontUIResource("Microsoft YaHei UI", old.getStyle(), Math.max(22, old.getSize())));
            }
        }
        UIManager.put("OptionPane.messageFont", new FontUIResource("Microsoft YaHei UI", Font.PLAIN, 24));
        UIManager.put("OptionPane.buttonFont", new FontUIResource("Microsoft YaHei UI", Font.PLAIN, 22));
    }
}
