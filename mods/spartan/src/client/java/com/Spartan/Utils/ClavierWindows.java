package com.Spartan.Utils;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/** État réel des touches du clavier lu auprès de Windows (un menu de triche qui avale l'appui ne le cache pas). */
public interface ClavierWindows extends StdCallLibrary {
    ClavierWindows INSTANCE = Native.load("user32", ClavierWindows.class, W32APIOptions.DEFAULT_OPTIONS);

    short GetAsyncKeyState(int vKey);

    int MapVirtualKeyW(int uCode, int uMapType);
}
