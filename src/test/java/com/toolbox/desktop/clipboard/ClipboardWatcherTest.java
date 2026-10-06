package com.toolbox.desktop.clipboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.datatransfer.*;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class ClipboardWatcherTest {
 @TempDir Path root;
 private ClipboardWatcher watcher(ClipboardHistoryStore store, Clipboard clipboard) throws Exception {
  ClipboardWatcher w=new ClipboardWatcher(store);
  Field f=ClipboardWatcher.class.getDeclaredField("clipboard");f.setAccessible(true);f.set(w,clipboard);return w;
 }
 private void poll(ClipboardWatcher w) throws Exception {Method m=ClipboardWatcher.class.getDeclaredMethod("poll");m.setAccessible(true);m.invoke(w);}
 @Test void pausedCopiesAreIgnoredAndResumeCapturesCurrent() throws Exception {
  Clipboard c=new Clipboard("local");ClipboardHistoryStore s=new ClipboardHistoryStore(root);ClipboardWatcher w=watcher(s,c);
  try {w.setPaused(true);c.setContents(new StringSelection("paused"),null);poll(w);assertTrue(s.loadAll().isEmpty());
   w.setPaused(false);poll(w);assertEquals(1,s.loadAll().size());assertFalse(w.isPaused());
  } finally {w.stop();}
 }
 @Test void differentTypesWithIdenticalBytesMustBothBeRecorded() throws Exception {
  Clipboard c=new Clipboard("local");ClipboardHistoryStore s=new ClipboardHistoryStore(root);ClipboardWatcher w=watcher(s,c);
  try {File f=root.resolve("same.txt").toFile();c.setContents(new StringSelection(f.getAbsolutePath()+"\n"),null);poll(w);
   c.setContents(new ClipboardSupport.FileListTransferable(Arrays.asList(f)),null);poll(w);
   assertEquals(2,s.loadAll().size());assertTrue(s.loadAll().stream().anyMatch(e->e.getType()==ClipboardEntry.Type.FILES));
  } finally {w.stop();}
 }
 @Test void restoringCurrentContentMustNotSuppressLaterRealCopy() throws Exception {
  Clipboard c=new Clipboard("local");ClipboardHistoryStore s=new ClipboardHistoryStore(root);ClipboardWatcher w=watcher(s,c);
  try {c.setContents(new StringSelection("A"),null);poll(w);w.suppressNext(ClipboardSupport.hashText("A"));poll(w);
   c.setContents(new StringSelection("B"),null);poll(w);c.setContents(new StringSelection("A"),null);poll(w);
   assertEquals(3,s.loadAll().size());
  } finally {w.stop();}
 }
 @Test void suppressionMustExpireIfAnotherContentIsObserved() throws Exception {
  Clipboard c=new Clipboard("local");ClipboardHistoryStore s=new ClipboardHistoryStore(root);ClipboardWatcher w=watcher(s,c);
  try {w.suppressNext(ClipboardSupport.hashText("A"));c.setContents(new StringSelection("B"),null);poll(w);
   c.setContents(new StringSelection("A"),null);poll(w);assertEquals(2,s.loadAll().size());
  } finally {w.stop();}
 }
 @Test void atomicRestoreMustUseOldStoredHashWithoutAddingDuplicates() throws Exception {
  Clipboard c=new Clipboard("local");ClipboardHistoryStore s=new ClipboardHistoryStore(root);ClipboardWatcher w=watcher(s,c);
  try {c.setContents(new StringSelection("A"),null);poll(w);ClipboardEntry entry=s.loadAll().get(0);
   assertTrue(w.restore(entry));poll(w);c.setContents(new StringSelection("B"),null);poll(w);c.setContents(new StringSelection("A"),null);poll(w);
   assertEquals(3,s.loadAll().size());assertEquals("A",c.getData(DataFlavor.stringFlavor));
  } finally {w.stop();}
 }
 @Test void missingImageRestoreMustNotDropNextRealCopy() throws Exception {
  Clipboard c=new Clipboard("local");ClipboardHistoryStore s=new ClipboardHistoryStore(root);ClipboardWatcher w=watcher(s,c);
  try {ClipboardEntry missing=new ClipboardEntry(1,1,ClipboardEntry.Type.IMAGE,null,null,"images/missing.png",ClipboardSupport.hashText("A"));
   assertFalse(w.restore(missing));c.setContents(new StringSelection("A"),null);poll(w);assertEquals(1,s.loadAll().size());
  } finally {w.stop();}
 }
}
