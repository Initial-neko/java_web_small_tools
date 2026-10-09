package com.toolbox.desktop.ui;

import com.toolbox.desktop.clipboard.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Desktop-only history window. History and image IO run off the EDT. */
public final class DesktopToolboxWindow extends JFrame {
    private final ClipboardHistoryStore store;
    private final ClipboardWatcher watcher;
    private final DefaultListModel<ClipboardEntry> model = new DefaultListModel<ClipboardEntry>();
    private final JList<ClipboardEntry> list = new JList<ClipboardEntry>(model);
    private final JTextField search = new JTextField();
    private final JComboBox<String> type = new JComboBox<String>(new String[]{"全部类型", "文本", "图片", "文件", "仅固定"});
    private final JLabel status = new JLabel("正在加载历史…"), fontLabel = new JLabel();
    private final JScrollPane scroll = new JScrollPane(list);
    private final ExecutorService imageLoader = Executors.newSingleThreadExecutor(r -> {Thread t=new Thread(r,"clipboard-thumbnails");t.setDaemon(true);return t;});
    private final ClipboardEntryRenderer renderer;
    private final List<JButton> buttons = new ArrayList<JButton>();
    private List<ClipboardEntry> allEntries = Collections.emptyList();
    private int fontSize, filterGeneration;
    private Runnable shortcutSettings;
    public void setShortcutSettings(Runnable action){shortcutSettings=action;}
    private boolean refreshing, refreshAgain, disposed, operationRunning;

    public DesktopToolboxWindow(ClipboardHistoryStore store, ClipboardWatcher watcher,Runnable screenshotAction, boolean exitOnClose) {
        super("剪切板历史 · Java Small Tools");this.store=store;this.watcher=watcher;DesktopFonts.install();
        fontSize=DesktopUiSettings.loadFontSize(store.getRoot());renderer=new ClipboardEntryRenderer(store,imageLoader,()->list.repaint());
        setDefaultCloseOperation(exitOnClose?JFrame.EXIT_ON_CLOSE:JFrame.HIDE_ON_CLOSE);
        setSize(1120,820);setMinimumSize(new Dimension(760,540));setLocationRelativeTo(null);
        JPanel header=new JPanel(new BorderLayout(12,0));header.setBackground(new Color(244,246,248));header.setBorder(BorderFactory.createEmptyBorder(14,18,14,18));
        search.setName("historySearch");search.setToolTipText("搜索完整文字、文件路径或类型（Ctrl+F）");search.setPreferredSize(new Dimension(500,44));
        header.add(search,BorderLayout.CENTER);header.add(type,BorderLayout.EAST);add(header,BorderLayout.NORTH);
        list.setName("historyList");list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setCellRenderer(renderer);list.setBackground(Color.WHITE);
        scroll.setBorder(BorderFactory.createEmptyBorder());scroll.getVerticalScrollBar().setUnitIncrement(30);add(scroll,BorderLayout.CENTER);
        JPanel toolbar=new JPanel();toolbar.setLayout(new BoxLayout(toolbar,BoxLayout.Y_AXIS));toolbar.setBackground(new Color(244,246,248));toolbar.setBorder(BorderFactory.createEmptyBorder(12,10,12,10));
        addButton(toolbar,"复制","复制选中内容（Ctrl+C / Enter / 双击）",this::copySelected);
        addButton(toolbar,"详情","查看完整文字、原图和全部文件（Space）",this::showDetails);
        toolbar.add(Box.createVerticalStrut(16));
        addButton(toolbar,"截图","拖动框选，松开完成；Esc 取消",()->{if(screenshotAction!=null)screenshotAction.run();});
        addButton(toolbar,"刷新","重新加载本地历史（F5）",this::refreshAsync);
        addButton(toolbar,"快捷键","设置全局打开剪切板和截图快捷键",()->{if(shortcutSettings!=null)shortcutSettings.run();});
        addButton(toolbar,"暂停","暂停或继续自动记录",this::togglePaused);
        addButton(toolbar,"目录","打开本地数据目录",this::openDataFolder);
        toolbar.add(Box.createVerticalStrut(16));addButton(toolbar,"固定","固定或取消固定（Ctrl+D），固定条目不参与自动清理",this::togglePinned);addButton(toolbar,"上限","设置普通历史条数上限，固定条目不计入",this::changeHistoryLimit);addButton(toolbar,"删除","删除选中历史（Delete）",this::deleteSelected);
        toolbar.add(Box.createVerticalGlue());addButton(toolbar,"字号 +","放大正文（Ctrl+Plus）",()->changeFontSize(2));addButton(toolbar,"字号 −","缩小正文（Ctrl+Minus）",()->changeFontSize(-2));
        JScrollPane actions=new JScrollPane(toolbar);actions.setBorder(BorderFactory.createEmptyBorder());actions.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);actions.setPreferredSize(new Dimension(130,0));add(actions,BorderLayout.EAST);
        JPanel footer=new JPanel(new BorderLayout(12,0));footer.setBackground(new Color(246,248,250));footer.setBorder(BorderFactory.createEmptyBorder(10,18,10,18));
        footer.add(status,BorderLayout.CENTER);footer.add(fontLabel,BorderLayout.EAST);add(footer,BorderLayout.SOUTH);
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){applyFilter();}public void removeUpdate(DocumentEvent e){applyFilter();}public void changedUpdate(DocumentEvent e){applyFilter();}});
        type.addActionListener(e->applyFilter());
        list.addListSelectionListener(e->{ClipboardEntry entry=list.getSelectedValue();for(JButton button:buttons)if(button.getText().equals("固定")||button.getText().equals("取消固定"))button.setText(entry!=null&&entry.isPinned()?"取消固定":"固定");});
        list.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){int i=list.locationToIndex(e.getPoint());if(i>=0&&list.getCellBounds(i,i).contains(e.getPoint())&&e.getClickCount()==2)copySelected();}});
        scroll.getViewport().addChangeListener(e->SwingUtilities.invokeLater(this::updateVisibleImages));
        scroll.getViewport().addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){updateListLayout();}});
        bind("find",KeyStroke.getKeyStroke(KeyEvent.VK_F,InputEvent.CTRL_DOWN_MASK),()->{search.requestFocusInWindow();search.selectAll();});
        bind("refresh",KeyStroke.getKeyStroke(KeyEvent.VK_F5,0),this::refreshAsync);
        bind("larger",KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS,InputEvent.CTRL_DOWN_MASK),()->changeFontSize(2));
        bind("largerPlus",KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS,InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK),()->changeFontSize(2));
        bind("smaller",KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,InputEvent.CTRL_DOWN_MASK),()->changeFontSize(-2));
        bindList("copy",KeyStroke.getKeyStroke(KeyEvent.VK_C,InputEvent.CTRL_DOWN_MASK),this::copySelected);
        bindList("enter",KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),this::copySelected);
        bindList("details",KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,0),this::showDetails);
        bindList("pin",KeyStroke.getKeyStroke(KeyEvent.VK_D,InputEvent.CTRL_DOWN_MASK),this::togglePinned);
        bindList("delete",KeyStroke.getKeyStroke(KeyEvent.VK_DELETE,0),this::deleteSelected);
        applyFonts();refreshAsync();
    }
    private void togglePinned(){
        final ClipboardEntry entry=list.getSelectedValue();if(entry==null||operationRunning)return;
        operationRunning=true;
        new SwingWorker<Void,Void>(){
            protected Void doInBackground()throws Exception{store.setPinned(entry,!entry.isPinned());return null;}
            protected void done(){operationRunning=false;try{get();refreshAsync();}catch(Exception e){showStatus("固定操作失败："+e.getMessage());}}
        }.execute();
    }
    private void changeHistoryLimit(){
        String input=JOptionPane.showInputDialog(this,"普通历史最多保存多少条？（1–10000）\n固定条目不计入；降低上限会清理最旧的未固定条目。",Integer.toString(store.getMaxEntries()));
        if(input==null)return;final int limit;
        try{limit=Integer.parseInt(input.trim());if(limit<1||limit>10000)throw new NumberFormatException();}
        catch(NumberFormatException e){showStatus("请输入 1–10000 之间的整数");return;}
        if(operationRunning)return;operationRunning=true;
        new SwingWorker<Void,Void>(){
            protected Void doInBackground()throws Exception{store.setMaxEntries(limit);return null;}
            protected void done(){operationRunning=false;try{get();refreshAsync();}catch(Exception e){showStatus("上限设置失败："+e.getMessage());}}
        }.execute();
    }
    private void togglePaused(){
        if(operationRunning)return;
        operationRunning=true;
        new SwingWorker<Void,Void>(){
            protected Void doInBackground(){watcher.setPaused(!watcher.isPaused());return null;}
            protected void done(){operationRunning=false;try{get();for(JButton button:buttons)if(button.getText().equals("暂停")||button.getText().equals("继续"))button.setText(watcher.isPaused()?"继续":"暂停");applyFilter();}catch(Exception e){showStatus("暂停操作失败："+e.getMessage());}}
        }.execute();
    }
    private void addButton(JPanel parent,String text,String tooltip,Runnable action){JButton b=new JButton(text);b.setToolTipText(tooltip);b.setAlignmentX(Component.CENTER_ALIGNMENT);b.setMaximumSize(new Dimension(98,48));b.setPreferredSize(new Dimension(98,48));b.setFocusPainted(false);b.addActionListener(e->action.run());buttons.add(b);parent.add(b);parent.add(Box.createVerticalStrut(9));}
    private void bind(String name,KeyStroke key,Runnable action){getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key,name);getRootPane().getActionMap().put(name,new AbstractAction(){public void actionPerformed(ActionEvent e){action.run();}});}
    private void bindList(String name,KeyStroke key,Runnable action){list.getInputMap().put(key,name);list.getActionMap().put(name,new AbstractAction(){public void actionPerformed(ActionEvent e){action.run();}});}
    private void applyFonts(){Font controls=new Font("Microsoft YaHei UI",Font.PLAIN,Math.max(22,fontSize-2));search.setFont(controls);type.setFont(controls);for(JButton b:buttons)b.setFont(controls);status.setFont(new Font("Microsoft YaHei UI",Font.PLAIN,22));fontLabel.setFont(status.getFont());fontLabel.setText("正文 "+fontSize+"px");renderer.setFontSize(fontSize);list.setFont(new Font("Microsoft YaHei UI",Font.PLAIN,fontSize));updateListLayout();}
    private void updateVisibleImages(){if(disposed)return;renderer.setVisibleRange(list.getFirstVisibleIndex(),list.getLastVisibleIndex());list.repaint();}
    private void updateListLayout(){if(disposed)return;int width=Math.max(300,scroll.getViewport().getExtentSize().width);renderer.setContentWidth(Math.max(210,width-90));list.setFixedCellWidth(-1);list.setFixedCellWidth(width);list.revalidate();list.repaint();}
    private void changeFontSize(int delta){fontSize=Math.max(16,Math.min(32,fontSize+delta));applyFonts();try{DesktopUiSettings.saveFontSize(store.getRoot(),fontSize);}catch(IOException e){showStatus("字号已调整，设置保存失败："+e.getMessage());}}
    public void showWindow(){refreshAsync();setVisible(true);setExtendedState(JFrame.NORMAL);toFront();search.requestFocusInWindow();}
    public void refresh(){refreshAsync();}
    public void refreshAsync(){
        if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(this::refreshAsync);return;}if(disposed)return;
        if(refreshing){refreshAgain=true;return;}refreshing=true;
        new SwingWorker<List<ClipboardEntry>,Void>(){protected List<ClipboardEntry> doInBackground(){return store.loadAll();}
            protected void done(){refreshing=false;if(disposed)return;try{allEntries=get();applyFilter();}catch(Exception e){showStatus("读取历史失败："+e.getMessage());}if(refreshAgain){refreshAgain=false;refreshAsync();}}}.execute();
    }
    private void applyFilter(){
        final int generation=++filterGeneration;
        final String query=search.getText().trim().toLowerCase(Locale.ROOT);
        final int filter=type.getSelectedIndex();
        final List<ClipboardEntry> snapshot=allEntries;
        new SwingWorker<List<ClipboardEntry>,Void>() {
            protected List<ClipboardEntry> doInBackground(){
                List<ClipboardEntry> result=new ArrayList<ClipboardEntry>();
                for(ClipboardEntry entry:snapshot){
                    if(filter==4&&!entry.isPinned())continue;
                    if(filter>0&&filter<4&&entry.getType()!=ClipboardEntry.Type.values()[filter-1])continue;
                    if(matches(entry,query))result.add(entry);
                }
                return result;
            }
            protected void done(){
                if(disposed||generation!=filterGeneration)return;
                try {
                    ClipboardEntry selected=list.getSelectedValue();
                    long id=selected==null?-1:selected.getId();
                    int position=scroll.getVerticalScrollBar().getValue(),index=-1;
                    List<ClipboardEntry> entries=get();
                    model.clear();
                    for(ClipboardEntry entry:entries){if(entry.getId()==id)index=model.size();model.addElement(entry);}
                    if(index>=0)list.setSelectedIndex(index);else if(!model.isEmpty())list.setSelectedIndex(0);
                    status.setText(model.isEmpty()?(snapshot.isEmpty()?"暂无历史 · 复制文字、图片或文件即可开始记录":"没有匹配内容 · 调整搜索或类型筛选"):(watcher.isPaused()?"已暂停记录 · ":"正在记录 · ")+model.size()+" / "+snapshot.size()+" 条历史 · 普通上限 "+store.getMaxEntries()+" · 固定另存");
                    SwingUtilities.invokeLater(()->{if(!disposed&&generation==filterGeneration){scroll.getVerticalScrollBar().setValue(position);updateVisibleImages();}});
                }catch(Exception e){showStatus("筛选失败："+e.getMessage());}
            }
        }.execute();
    }
    private boolean matches(ClipboardEntry e,String query){if(query.isEmpty())return true;if(e.getType().name().toLowerCase(Locale.ROOT).contains(query))return true;if(e.getText()!=null&&e.getText().toLowerCase(Locale.ROOT).contains(query))return true;for(String path:e.getFilePaths())if(path.toLowerCase(Locale.ROOT).contains(query))return true;return e.getImagePath()!=null&&e.getImagePath().toLowerCase(Locale.ROOT).contains(query);}
    public void showStatus(String text){if(SwingUtilities.isEventDispatchThread())status.setText(text);else SwingUtilities.invokeLater(()->status.setText(text));}
    private void copySelected(){ClipboardEntry entry=list.getSelectedValue();if(entry==null){showStatus("请先选择一条历史");return;}if(operationRunning)return;operationRunning=true;showStatus("正在复制…");
        new SwingWorker<Boolean,Void>(){protected Boolean doInBackground(){return watcher.restore(entry);}protected void done(){operationRunning=false;try{showStatus(get()?"已复制 · 可以切换到其他软件粘贴":"复制失败 · 剪切板忙或原图片已丢失，请重试");}catch(Exception e){showStatus("复制失败："+e.getMessage());}}}.execute();}
    private void showDetails(){ClipboardEntry entry=list.getSelectedValue();if(entry==null)return;JDialog dialog=new JDialog(this,"历史详情",false);dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);dialog.setSize(900,650);dialog.setLocationRelativeTo(this);
        if(entry.getType()==ClipboardEntry.Type.IMAGE){JLabel label=new JLabel("正在加载原图…",SwingConstants.CENTER);dialog.add(new JScrollPane(label));new SwingWorker<ImageIcon,Void>(){protected ImageIcon doInBackground()throws Exception{java.awt.image.BufferedImage image=javax.imageio.ImageIO.read(store.resolveImage(entry));if(image==null)throw new IOException("Invalid image");return new ImageIcon(image);}protected void done(){try{label.setText("");label.setIcon(get());}catch(Exception e){label.setText("原图片已丢失或损坏");}}}.execute();}
        else{JTextArea text=new JTextArea(entry.getType()==ClipboardEntry.Type.TEXT?entry.getText():String.join("\n",entry.getFilePaths()));text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(new Font("Microsoft YaHei UI",Font.PLAIN,Math.max(24,fontSize)));text.setMargin(new Insets(16,16,16,16));text.setCaretPosition(0);dialog.add(new JScrollPane(text));}
        dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);dialog.setVisible(true);
    }
    private void deleteSelected(){ClipboardEntry entry=list.getSelectedValue();if(entry==null||operationRunning)return;if(JOptionPane.showConfirmDialog(this,"删除这条历史？原文件不会被删除。","删除历史",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;operationRunning=true;
        new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{store.delete(entry);return null;}protected void done(){operationRunning=false;try{get();renderer.invalidate(entry);refreshAsync();}catch(Exception e){showStatus("删除失败："+e.getMessage());}}}.execute();}
    private void openDataFolder(){new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{Desktop.getDesktop().open(store.getRoot().toFile());return null;}protected void done(){try{get();}catch(Exception e){showStatus("数据目录："+store.getRoot());}}}.execute();}
    @Override public void dispose(){disposed=true;imageLoader.shutdownNow();super.dispose();}
}
