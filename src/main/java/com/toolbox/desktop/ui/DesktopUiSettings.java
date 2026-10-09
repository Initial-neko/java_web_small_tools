package com.toolbox.desktop.ui;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;
final class DesktopUiSettings {
 private DesktopUiSettings(){}
 static int loadFontSize(Path root){Properties p=new Properties();try(InputStream in=Files.newInputStream(root.resolve("ui.properties"))){p.load(in);return Math.max(16,Math.min(32,Integer.parseInt(p.getProperty("fontSize","22"))));}catch(Exception ignored){return 22;}}
 static void saveFontSize(Path root,int size)throws IOException{Properties p=new Properties();p.setProperty("fontSize",Integer.toString(size));Path temp=Files.createTempFile(root,"ui-",".tmp");try{try(OutputStream out=Files.newOutputStream(temp)){p.store(out,"Desktop reading preferences");}Files.move(temp,root.resolve("ui.properties"),StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
}
