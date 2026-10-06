package com.toolbox.desktop.hotkey;

import com.sun.jna.platform.win32.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledOnOs(OS.WINDOWS)
class WindowsHotkeyIntegrationTest {
    @Test void nativeRegistrationConflictMessageAndRelease() throws Exception {
        User32 user=User32.INSTANCE;int id=0x51A0,modifiers=7|0x4000,key=122;
        boolean registered=user.RegisterHotKey(null,id,modifiers,key);
        assumeTrue(registered,"Ctrl+Alt+Shift+F11 already occupied on this machine");
        ExecutorService anotherThread=Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> conflict=anotherThread.submit(()->{boolean accepted=user.RegisterHotKey(null,id+1,modifiers,key);if(accepted)user.UnregisterHotKey(null,id+1);return accepted;});
            assertFalse(conflict.get(5,TimeUnit.SECONDS),"another thread cannot claim the registered combination");
            WinUser.MSG message=new WinUser.MSG();user.PeekMessage(message,null,0,0,0);
            assertNotEquals(0,user.PostThreadMessage(Kernel32.INSTANCE.GetCurrentThreadId(),0x0312,new WinDef.WPARAM(id),new WinDef.LPARAM(0)));
            assertTrue(user.PeekMessage(message,null,0x0312,0x0312,1));assertEquals(id,message.wParam.intValue());
        } finally {anotherThread.shutdownNow();user.UnregisterHotKey(null,id);}
        boolean reclaimed=user.RegisterHotKey(null,id,modifiers,key);
        try{assertTrue(reclaimed,"unregistration must release the combination");}finally{if(reclaimed)user.UnregisterHotKey(null,id);}
    }
}
