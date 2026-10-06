package com.toolbox.desktop.ui;

import com.toolbox.desktop.clipboard.ClipboardEntry;
import com.toolbox.desktop.clipboard.ClipboardHistoryStore;
import com.toolbox.desktop.clipboard.ClipboardSupport;
import com.toolbox.desktop.clipboard.ClipboardWatcher;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DesktopToolboxWindow extends JFrame {

    private final ClipboardHistoryStore store;
    private final ClipboardWatcher watcher;
    private final Runnable screenshotAction;
    private final ClipboardTableModel model = new ClipboardTableModel();
    private final JTable table = new JTable(model);
    private final JTextField search = new JTextField();
    private final JLabel status = new JLabel(" ");
    private List<ClipboardEntry> allEntries = new ArrayList<ClipboardEntry>();

    public DesktopToolboxWindow(ClipboardHistoryStore store,
                                ClipboardWatcher watcher,
                                Runnable screenshotAction,
                                boolean exitOnClose) {
        super("Java Small Tools - Clipboard");
        this.store = store;
        this.watcher = watcher;
        this.screenshotAction = screenshotAction;

        setDefaultCloseOperation(exitOnClose ? JFrame.EXIT_ON_CLOSE : JFrame.HIDE_ON_CLOSE);
        setSize(880, 560);
        setLocationRelativeTo(null);

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        search.setToolTipText("Search clipboard text, file paths or type");
        top.add(search, BorderLayout.CENTER);

        JButton screenshot = new JButton("Screenshot");
        screenshot.addActionListener(e -> {
            if (screenshotAction != null) {
                screenshotAction.run();
            }
        });
        top.add(screenshot, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(145);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(620);
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    copySelected();
                }
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton copy = new JButton("Copy selected");
        copy.addActionListener(e -> copySelected());
        JButton delete = new JButton("Delete");
        delete.addActionListener(e -> deleteSelected());
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refresh());
        JButton folder = new JButton("Open data folder");
        folder.addActionListener(e -> openDataFolder());
        buttons.add(copy);
        buttons.add(delete);
        buttons.add(refresh);
        buttons.add(folder);

        status.setFont(status.getFont().deriveFont(Font.PLAIN, 12f));
        bottom.add(buttons, BorderLayout.WEST);
        bottom.add(status, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        refresh();
    }

    public void showWindow() {
        refresh();
        setVisible(true);
        toFront();
        requestFocus();
    }

    public void refreshAsync() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                refresh();
            }
        });
    }

    public void refresh() {
        allEntries = store.loadAll();
        applyFilter();
    }

    private void applyFilter() {
        String query = search.getText() == null
                ? ""
                : search.getText().trim().toLowerCase(Locale.ROOT);

        List<ClipboardEntry> filtered = new ArrayList<ClipboardEntry>();
        for (ClipboardEntry entry : allEntries) {
            if (query.isEmpty() || matches(entry, query)) {
                filtered.add(entry);
            }
        }
        model.setEntries(filtered);
        status.setText(filtered.size() + " / " + allEntries.size() + " items");
    }

    private boolean matches(ClipboardEntry entry, String query) {
        if (entry.getType().name().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (entry.getText() != null
                && entry.getText().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (String path : entry.getFilePaths()) {
            if (path.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return entry.preview().toLowerCase(Locale.ROOT).contains(query);
    }

    private ClipboardEntry selectedEntry() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= model.getRowCount()) {
            return null;
        }
        return model.getEntry(row);
    }

    private void copySelected() {
        ClipboardEntry entry = selectedEntry();
        if (entry == null) {
            status.setText("Select one item first");
            return;
        }

        watcher.suppressNext(entry.getHash());
        if (ClipboardSupport.restore(entry, store)) {
            status.setText("Copied: " + entry.preview());
        } else {
            status.setText("Copy failed (the source image may have been removed)");
        }
    }

    private void deleteSelected() {
        ClipboardEntry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(
                this,
                "Delete this clipboard history item?",
                "Delete",
                JOptionPane.YES_NO_OPTION
        );
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            store.delete(entry);
            refresh();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Delete failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openDataFolder() {
        try {
            Desktop.getDesktop().open(store.getRoot().toFile());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this,
                    store.getRoot().toString(),
                    "Data folder",
                    JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    private static final class ClipboardTableModel extends AbstractTableModel {
        private final String[] columns = {"Time", "Type", "Preview"};
        private List<ClipboardEntry> entries = new ArrayList<ClipboardEntry>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        public void setEntries(List<ClipboardEntry> entries) {
            this.entries = new ArrayList<ClipboardEntry>(entries);
            fireTableDataChanged();
        }

        public ClipboardEntry getEntry(int row) {
            return entries.get(row);
        }

        public int getRowCount() {
            return entries.size();
        }

        public int getColumnCount() {
            return columns.length;
        }

        public String getColumnName(int column) {
            return columns[column];
        }

        public Object getValueAt(int rowIndex, int columnIndex) {
            ClipboardEntry entry = entries.get(rowIndex);
            if (columnIndex == 0) {
                return dateFormat.format(new Date(entry.getCreatedAt()));
            }
            if (columnIndex == 1) {
                return entry.getType().name();
            }
            return entry.preview();
        }
    }
}
