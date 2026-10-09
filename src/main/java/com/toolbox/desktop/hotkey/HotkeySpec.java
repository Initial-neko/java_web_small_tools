package com.toolbox.desktop.hotkey;

import java.util.Locale;

public final class HotkeySpec {
    public final int modifiers,key;
    private HotkeySpec(int modifiers,int key){this.modifiers=modifiers;this.key=key;}
    public static HotkeySpec parse(String value){
        if(value==null)throw new IllegalArgumentException("快捷键不能为空");
        String[] parts=value.trim().toUpperCase(Locale.ROOT).split("\\+");int modifiers=0,key=0;
        for(String raw:parts){String token=raw.trim();int modifier=token.equals("CTRL")||token.equals("CONTROL")?2:token.equals("ALT")?1:token.equals("SHIFT")?4:0;
            if(modifier!=0){if((modifiers&modifier)!=0)throw new IllegalArgumentException("修饰键重复");modifiers|=modifier;continue;}
            if(key!=0)throw new IllegalArgumentException("每组快捷键只允许一个主键");
            if(token.matches("[A-Z0-9]"))key=token.charAt(0);
            else if(token.matches("F([1-9]|10|11)"))key=111+Integer.parseInt(token.substring(1));
            else throw new IllegalArgumentException("主键支持 A–Z、0–9、F1–F11");
        }
        if(key==0||(modifiers&3)==0)throw new IllegalArgumentException("快捷键至少需要 Ctrl 或 Alt");
        return new HotkeySpec(modifiers,key);
    }
    public String toString(){return ((modifiers&2)!=0?"Ctrl+":"")+((modifiers&1)!=0?"Alt+":"")+((modifiers&4)!=0?"Shift+":"")+(key>=112?"F"+(key-111):Character.toString((char)key));}
    public boolean equals(Object other){return other instanceof HotkeySpec&&((HotkeySpec)other).modifiers==modifiers&&((HotkeySpec)other).key==key;}
    public int hashCode(){return modifiers*257+key;}
}
