package com.toolbox.desktop.ui;
import com.toolbox.desktop.clipboard.*;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;

final class ClipboardEntryRenderer implements ListCellRenderer<ClipboardEntry> {
 private final ClipboardHistoryStore store;private final ExecutorService loader;private final Runnable repaint;
 private final Map<String,ImageIcon> icons=new LinkedHashMap<String,ImageIcon>(128,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,ImageIcon> e){return size()>128;}};
 private final Set<String> pending=new HashSet<String>(), missing=new HashSet<String>();
 private int fontSize=22,width=900,firstVisible=-1,lastVisible=-1;
 void setVisibleRange(int first,int last){firstVisible=first;lastVisible=last;}
 ClipboardEntryRenderer(ClipboardHistoryStore s,ExecutorService l,Runnable r){store=s;loader=l;repaint=r;}
 void setFontSize(int size){fontSize=size;}void setContentWidth(int width){this.width=width;}void invalidate(ClipboardEntry e){icons.remove(e.getImagePath());missing.remove(e.getImagePath());}
 public Component getListCellRendererComponent(JList<? extends ClipboardEntry> list,ClipboardEntry entry,int index,boolean selected,boolean focus){
  JPanel row=new JPanel(new BorderLayout(14,8));row.setBackground(selected?new Color(238,246,255):Color.WHITE);
  Border outline=selected?BorderFactory.createLineBorder(new Color(57,126,203),2):BorderFactory.createMatteBorder(0,0,1,0,new Color(230,235,240));row.setBorder(BorderFactory.createCompoundBorder(outline,BorderFactory.createEmptyBorder(14,12,12,18)));
  JLabel number=new JLabel(Integer.toString(index+1),SwingConstants.CENTER);number.setPreferredSize(new Dimension(38,25));number.setVerticalAlignment(SwingConstants.TOP);number.setFont(new Font("Microsoft YaHei UI",Font.PLAIN,20));number.setForeground(selected?new Color(57,126,203):new Color(132,146,161));row.add(number,BorderLayout.WEST);
  JPanel body=new JPanel(new BorderLayout(0,8));body.setOpaque(false);
  if(entry.getType()==ClipboardEntry.Type.IMAGE){JLabel image=new JLabel();ImageIcon icon=icons.get(entry.getImagePath());if(icon!=null)image.setIcon(icon);else{image.setText(missing.contains(entry.getImagePath())?"图片已丢失或损坏":"正在加载图片预览…");image.setFont(new Font("Microsoft YaHei UI",Font.PLAIN,18));image.setPreferredSize(new Dimension(280,110));if(index>=firstVisible&&index<=lastVisible)requestImage(entry);}image.setPreferredSize(new Dimension(360,140));body.add(image,BorderLayout.CENTER);}
  else{String content=entry.getType()==ClipboardEntry.Type.TEXT?entry.getText():String.join("\n",entry.getFilePaths());if(content==null)content="";String bounded=content.length()>700?content.substring(0,700):content;String[] lines=bounded.split("\r?\n",5);StringBuilder preview=new StringBuilder();for(int i=0;i<Math.min(lines.length,4);i++){if(i>0)preview.append('\n');preview.append(lines[i]);}
   String value=preview.toString();if(value.length()>700)value=value.substring(0,700);if(lines.length>4||content.length()>700)value+="\n… 按 Space 查看完整内容";
   JTextArea text=new JTextArea(value);text.setEditable(false);text.setOpaque(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setForeground(new Color(38,49,59));text.setFont(new Font("Microsoft YaHei UI",Font.PLAIN,fontSize));text.setSize(Math.max(280,width),Short.MAX_VALUE);Dimension preferred=text.getPreferredSize();text.setPreferredSize(new Dimension(Math.max(280,width),Math.min(220,preferred.height)));body.add(text,BorderLayout.CENTER);}
  String kind=entry.getType()==ClipboardEntry.Type.TEXT?"文本":entry.getType()==ClipboardEntry.Type.IMAGE?"图片":"文件 · "+entry.getFilePaths().size()+" 项";JLabel meta=new JLabel((entry.isPinned()?"★ 固定    ":"")+kind+"    "+new SimpleDateFormat("MM-dd HH:mm:ss").format(new Date(entry.getCreatedAt()))+(selected?"    已选中 · 双击复制":""));meta.setFont(new Font("Microsoft YaHei UI",Font.PLAIN,18));meta.setForeground(new Color(119,135,150));body.add(meta,BorderLayout.SOUTH);row.add(body,BorderLayout.CENTER);return row;
 }
 private void requestImage(ClipboardEntry entry){String path=entry.getImagePath();if(path==null||pending.contains(path)||missing.contains(path)||loader.isShutdown())return;pending.add(path);
  loader.execute(()->{ImageIcon icon=null;try{BufferedImage image=javax.imageio.ImageIO.read(store.resolveImage(entry));if(image!=null){double scale=Math.min(1,Math.min(360d/image.getWidth(),140d/image.getHeight()));int w=Math.max(1,(int)(image.getWidth()*scale)),h=Math.max(1,(int)(image.getHeight()*scale));BufferedImage thumbnail=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=thumbnail.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(image,0,0,w,h,null);}finally{g.dispose();}icon=new ImageIcon(thumbnail);}}catch(Exception ignored){}
   final ImageIcon result=icon;SwingUtilities.invokeLater(()->{pending.remove(path);if(result==null)missing.add(path);else icons.put(path,result);repaint.run();});});
 }
}
