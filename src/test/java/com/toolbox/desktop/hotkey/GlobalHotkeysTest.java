package com.toolbox.desktop.hotkey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GlobalHotkeysTest {
 @TempDir Path root;
 static class Fake implements GlobalHotkeys.Backend {
  Map<Integer,HotkeySpec> keys=new HashMap<Integer,HotkeySpec>();HotkeySpec occupied;
  public boolean register(int id,HotkeySpec key){if(key.equals(occupied)||keys.containsValue(key))return false;keys.put(id,key);return true;}
  public void unregister(int id){keys.remove(id);}public int next(){return 0;}
 }
 @Test void settingsPersistAndConflictRollsBackBothBindings()throws Exception {
  Fake nativeApi=new Fake();GlobalHotkeys keys=new GlobalHotkeys(root,nativeApi);
  HotkeySpec open=HotkeySpec.parse("Ctrl+Alt+V"),screen=HotkeySpec.parse("Ctrl+Alt+S");keys.apply(open,screen);
  byte[] before=Files.readAllBytes(root.resolve("hotkeys.properties"));nativeApi.occupied=HotkeySpec.parse("Ctrl+Alt+X");
  assertThrows(java.io.IOException.class,()->keys.apply(HotkeySpec.parse("Ctrl+Alt+A"),nativeApi.occupied));
  assertEquals(open,nativeApi.keys.get(1));assertEquals(screen,nativeApi.keys.get(2));assertArrayEquals(before,Files.readAllBytes(root.resolve("hotkeys.properties")));
  GlobalHotkeys reopened=new GlobalHotkeys(root,new Fake());assertEquals(open,reopened.getOpenKey());assertEquals(screen,reopened.getScreenshotKey());
 }
 @Test void identicalBindingsAreRejectedBeforeChangingRegistration()throws Exception {
  Fake nativeApi=new Fake();GlobalHotkeys keys=new GlobalHotkeys(root,nativeApi);HotkeySpec key=HotkeySpec.parse("Ctrl+Alt+V");
  assertThrows(IllegalArgumentException.class,()->keys.apply(key,key));assertTrue(nativeApi.keys.isEmpty());
 }
}
