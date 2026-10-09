package com.toolbox.desktop.hotkey;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import javax.swing.SwingUtilities;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** All RegisterHotKey, message polling and unregistration run on the owning thread. */
public final class GlobalHotkeys implements AutoCloseable {
    interface Backend { boolean register(int id,HotkeySpec key); void unregister(int id); int next(); }
    private final Path settings;
    private final Runnable open,screenshot;
    private final Consumer<String> notices;
    private final Queue<Runnable> commands=new ConcurrentLinkedQueue<Runnable>();
    private final Set<Integer> registered=new HashSet<Integer>();
    private volatile HotkeySpec openKey=HotkeySpec.parse("Ctrl+Alt+V"),screenshotKey=HotkeySpec.parse("Ctrl+Alt+S");
    private volatile boolean running;
    private Thread thread;
    private Backend backend;

    public GlobalHotkeys(Path root,Runnable open,Runnable screenshot,Consumer<String> notices){
        settings=root.resolve("hotkeys.properties");this.open=open;this.screenshot=screenshot;this.notices=notices;
        if(Files.exists(settings))try(Reader reader=Files.newBufferedReader(settings,StandardCharsets.UTF_8)){
            Properties p=new Properties();p.load(reader);HotkeySpec a=HotkeySpec.parse(p.getProperty("open","Ctrl+Alt+V")),b=HotkeySpec.parse(p.getProperty("screenshot","Ctrl+Alt+S"));
            if(!a.equals(b)){openKey=a;screenshotKey=b;}
        }catch(Exception e){System.err.println("Invalid hotkey settings; using defaults.");}
    }
    GlobalHotkeys(Path root,Backend backend){this(root,()->{},()->{},s->{});this.backend=backend;}
    public HotkeySpec getOpenKey(){return openKey;}
    public HotkeySpec getScreenshotKey(){return screenshotKey;}
    public synchronized void start(){
        if(running)return;
        if(!System.getProperty("os.name","").startsWith("Windows")){notice("全局快捷键仅支持 Windows；仍可使用窗口按钮和托盘。");return;}
        running=true;
        thread=new Thread(()->{
            try{
                if(backend==null)backend=new Backend(){
                    final User32 user=User32.INSTANCE;final WinUser.MSG message=new WinUser.MSG();
                    public boolean register(int id,HotkeySpec key){return user.RegisterHotKey(null,id,key.modifiers|0x4000,key.key);}
                    public void unregister(int id){user.UnregisterHotKey(null,id);}
                    public int next(){while(user.PeekMessage(message,null,0,0,1)){if(message.message==0x0312)return message.wParam.intValue();}return 0;}
                };
                boolean a=register(1,openKey),b=register(2,screenshotKey);
                notice(a&&b?"全局快捷键已启用 · "+openKey+" 打开剪切板 · "+screenshotKey+" 截图":"快捷键被占用，请在「快捷键」中修改 · 打开="+a+"，截图="+b);
                while(running){Runnable command;while((command=commands.poll())!=null)command.run();int id=backend.next();if(id==1)SwingUtilities.invokeLater(open);else if(id==2)SwingUtilities.invokeLater(screenshot);Thread.sleep(25);}
            }catch(InterruptedException ignored){Thread.currentThread().interrupt();}
            catch(Throwable e){notice("全局快捷键不可用："+e.getClass().getSimpleName()+" · 检查 desktop-lib 目录");}
            finally{if(backend!=null)clear();running=false;}
        },"windows-global-hotkeys");thread.setDaemon(true);thread.start();
    }
    public void update(final HotkeySpec open,final HotkeySpec screenshot,final Consumer<String> result){
        if(!running){SwingUtilities.invokeLater(()->result.accept("全局快捷键服务未运行，请重启并检查 desktop-lib。"));return;}
        commands.add(()->{String message;try{apply(open,screenshot);message="快捷键已保存并生效";}catch(Exception e){message=e.getMessage();}final String response=message;SwingUtilities.invokeLater(()->result.accept(response));});
    }
    void apply(HotkeySpec a,HotkeySpec b)throws IOException {
        if(a.equals(b))throw new IllegalArgumentException("打开剪切板和截图不能使用同一组快捷键");
        HotkeySpec previousOpen=openKey,previousScreenshot=screenshotKey;Set<Integer> previous=new HashSet<Integer>(registered);
        clear();
        try{
            if(!register(1,a)||!register(2,b))throw new IOException("快捷键被其他程序占用，请更换组合");
            Properties p=new Properties();p.setProperty("open",a.toString());p.setProperty("screenshot",b.toString());writeSettings(p);
            openKey=a;screenshotKey=b;
        }catch(Exception e){
            clear();boolean restored=true;
            if(previous.contains(1))restored&=register(1,previousOpen);if(previous.contains(2))restored&=register(2,previousScreenshot);
            throw new IOException(e.getMessage()+(restored?"；原快捷键保持不变":"；原快捷键恢复失败，请重新设置"),e);
        }
    }
    private boolean register(int id,HotkeySpec key){if(backend.register(id,key)){registered.add(id);return true;}return false;}
    private void clear(){for(Integer id:new HashSet<Integer>(registered))backend.unregister(id);registered.clear();}
    private void writeSettings(Properties p)throws IOException{
        Path temporary=Files.createTempFile(settings.getParent(),"hotkeys-",".tmp");
        try{try(Writer writer=Files.newBufferedWriter(temporary,StandardCharsets.UTF_8)){p.store(writer,"Windows global shortcuts");}
            try{Files.move(temporary,settings,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,settings,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
    private void notice(String message){System.out.println(message);SwingUtilities.invokeLater(()->notices.accept(message));}
    public synchronized void close(){running=false;if(thread!=null)thread.interrupt();}
}
