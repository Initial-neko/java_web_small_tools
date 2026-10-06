package com.toolbox.desktop.hotkey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HotkeySpecTest {
 @Test void defaultKeysAndModifiersHaveCorrectWindowsValues(){HotkeySpec key=HotkeySpec.parse("Ctrl+Alt+V");assertEquals(3,key.modifiers);assertEquals(86,key.key);assertEquals("Ctrl+Alt+V",key.toString());assertEquals(key,HotkeySpec.parse("alt+ctrl+v"));assertEquals(112,HotkeySpec.parse("Ctrl+F1").key);}
 @Test void invalidReservedAndDuplicateKeysAreRejected(){for(String value:new String[]{"V","Shift+V","Ctrl+F12","Win+V","Ctrl+Ctrl+V","Ctrl+V+S","Ctrl+"})assertThrows(IllegalArgumentException.class,()->HotkeySpec.parse(value),value);}
}
