package com.toolbox.desktop.ui;

import com.toolbox.desktop.clipboard.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DesktopToolboxWindowTest {
 @TempDir Path root;
 private <T extends Component> T find(Component c, Class<T> type) {
  if(type.isInstance(c))return type.cast(c);
  if(c instanceof Container)for(Component child:((Container)c).getComponents()){T result=find(child,type);if(result!=null)return result;}
  return null;
 }
 @Test void largeReadableListMustReplaceClippedTablePreview() throws Exception {
  ClipboardHistoryStore store=new ClipboardHistoryStore(root);store.saveText("第一行\n第二行内容必须可以直接阅读",ClipboardSupport.hashText("第一行\n第二行内容必须可以直接阅读"));
  ClipboardWatcher watcher=new ClipboardWatcher(store);AtomicReference<Throwable> failure=new AtomicReference<Throwable>();
  SwingUtilities.invokeAndWait(()->{DesktopToolboxWindow window=new DesktopToolboxWindow(store,watcher,()->{},false);try {
   JList<?> list=find(window,JList.class);
   assertNotNull(list,"readable variable-height content list is required");
   assertNull(find(window,JTable.class),"do not clip clipboard content into a tiny table");
   assertTrue(find(window,JTextField.class).getFont().getSize()>=18,"search text must be readable");
  }catch(Throwable e){failure.set(e);}finally{window.dispose();}});
  watcher.stop();if(failure.get()!=null)throw new AssertionError(failure.get());
 }
 @Test void fontAdjustmentMustPersistAcrossReopening() throws Exception {
  ClipboardHistoryStore store=new ClipboardHistoryStore(root);ClipboardWatcher watcher=new ClipboardWatcher(store);
  AtomicReference<Throwable> failure=new AtomicReference<Throwable>();
  SwingUtilities.invokeAndWait(()->{DesktopToolboxWindow first=new DesktopToolboxWindow(store,watcher,()->{},false);DesktopToolboxWindow second=null;try{
   JButton larger=findButton(first,"字号 +");assertNotNull(larger);larger.doClick();larger.doClick();
   second=new DesktopToolboxWindow(store,watcher,()->{},false);assertEquals(26,DesktopUiSettings.loadFontSize(root));assertEquals(26,find(second,JList.class).getFont().getSize());assertTrue(find(second,JTextField.class).getFont().getSize()>=22);
  }catch(Throwable e){failure.set(e);}finally{first.dispose();if(second!=null)second.dispose();}});
  watcher.stop();if(failure.get()!=null)throw new AssertionError(failure.get());
 }
 @Test void fullTextSearchKeepsLatestQueryAndRefreshKeepsSelection() throws Exception {
  ClipboardHistoryStore store=new ClipboardHistoryStore(root);
  String longText=String.join("",java.util.Collections.nCopies(10000,"长文本 "))+"END_SEARCH_MARKER";
  ClipboardEntry target=store.saveText(longText,ClipboardSupport.hashText(longText));
  store.saveText("other",ClipboardSupport.hashText("other"));
  ClipboardWatcher watcher=new ClipboardWatcher(store);AtomicReference<DesktopToolboxWindow> ref=new AtomicReference<DesktopToolboxWindow>();
  try {
   SwingUtilities.invokeAndWait(()->ref.set(new DesktopToolboxWindow(store,watcher,()->{},false)));
   awaitListSize(ref.get(),2);
   SwingUtilities.invokeAndWait(()->{JTextField search=find(ref.get(),JTextField.class);search.setText("NO_MATCH");search.setText("END_SEARCH_MARKER");});
   awaitListSize(ref.get(),1);
   SwingUtilities.invokeAndWait(()->{JList<?> list=find(ref.get(),JList.class);assertEquals(target.getId(),((ClipboardEntry)list.getSelectedValue()).getId());ref.get().refresh();});
   awaitListSize(ref.get(),1);
   SwingUtilities.invokeAndWait(()->assertEquals(target.getId(),((ClipboardEntry)find(ref.get(),JList.class).getSelectedValue()).getId()));
  } finally {if(ref.get()!=null)SwingUtilities.invokeAndWait(()->ref.get().dispose());watcher.stop();}
 }
 private void awaitListSize(DesktopToolboxWindow window,int expected) throws Exception {
  long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);AtomicReference<Integer> size=new AtomicReference<Integer>();
  do {SwingUtilities.invokeAndWait(()->size.set(find(window,JList.class).getModel().getSize()));if(size.get()==expected)return;Thread.sleep(20);}while(System.nanoTime()<deadline);
  assertEquals(expected,size.get());
 }
 private JButton findButton(Component c,String title){if(c instanceof JButton&&title.equals(((JButton)c).getText()))return (JButton)c;if(c instanceof Container)for(Component child:((Container)c).getComponents()){JButton result=findButton(child,title);if(result!=null)return result;}return null;}
}
