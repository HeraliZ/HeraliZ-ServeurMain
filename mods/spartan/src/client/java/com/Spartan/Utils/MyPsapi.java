package com.Spartan.Utils;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

public interface MyPsapi extends Psapi {
    MyPsapi INSTANCE = Native.load("psapi", MyPsapi.class, W32APIOptions.DEFAULT_OPTIONS);

    boolean EnumProcessModulesEx(Pointer hProcess, Pointer[] lphModule, int cb, IntByReference lpcbNeeded, int dwFilterFlag);

    int GetModuleBaseName(Pointer hProcess, Pointer hModule, byte[] lpBaseName, int nSize);
}
