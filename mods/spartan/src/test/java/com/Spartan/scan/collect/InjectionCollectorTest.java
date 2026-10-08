package com.Spartan.scan.collect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.Spartan.scan.ScanReport;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Sur un vrai Windows : une JVM propre (celle des tests) n'a AUCUN thread démarré hors module ni crochet, sinon tous
 * les joueurs seraient accusés. Et le décodage des sauts reconnaît les formes utilisées par les crochets.
 */
class InjectionCollectorTest {
    @Test
    void aCleanJvmHasNoInjectionTrace() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"), "Windows seulement");
        // quelques threads Java de plus, comme dans le jeu
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
                // fin
            }
        }, "test-spartan");
        t.start();
        List<Long> starts = InjectionCollector.threadStarts();
        List<InjectionCollector.Module> modules = InjectionCollector.modules();
        assertTrue(starts.size() >= 5, "threads lus : " + starts.size());
        assertTrue(modules.size() >= 10, "modules lus : " + modules.size());
        ScanReport r = new ScanReport();
        InjectionCollector.collect(r);
        assertEquals(List.of(), r.injection, "traces sur une JVM propre");
        t.interrupt();
    }

    interface K extends StdCallLibrary {
        K I = Native.load("kernel32", K.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer VirtualAlloc(Pointer address, long size, int type, int protect);

        boolean VirtualFree(Pointer address, long size, int type);

        WinNT.HANDLE CreateThread(Pointer attributes, long stack, Pointer start, Pointer parameter, int flags,
                                  IntByReference threadId);

        boolean TerminateThread(WinNT.HANDLE thread, int exitCode);

        boolean CloseHandle(WinNT.HANDLE handle);
    }

    /**
     * Injection simulée, comme un « manual map » : du code posé dans une mémoire qui n'appartient à aucune DLL, et un
     * thread qui démarre dessus (créé en pause : il n'exécute rien). Il doit être repéré, à sa vraie adresse.
     */
    @Test
    void aThreadStartedInUnbackedMemoryIsCaught() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"), "Windows seulement");
        Pointer code = K.I.VirtualAlloc(null, 4096, 0x3000 /* MEM_COMMIT | MEM_RESERVE */, 0x40 /* PAGE_EXECUTE_READWRITE */);
        assertTrue(code != null);
        code.setByte(0, (byte) 0xC3); // ret
        WinNT.HANDLE h = K.I.CreateThread(null, 0, code, null, 0x4 /* CREATE_SUSPENDED */, new IntByReference());
        assertTrue(h != null);
        try {
            ScanReport r = new ScanReport();
            InjectionCollector.collect(r);
            String attendu = "thread|" + InjectionCollector.hex(Pointer.nativeValue(code));
            assertTrue(r.injection.contains(attendu), "thread injecté non repéré : " + r.injection + " (attendu " + attendu + ")");
        } finally {
            K.I.TerminateThread(h, 0);
            K.I.CloseHandle(h);
            K.I.VirtualFree(code, 0, 0x8000 /* MEM_RELEASE */);
        }
    }

    @Test
    void jumpsUsedByHooksAreDecoded() {
        Memory code = new Memory(32);
        long base = Pointer.nativeValue(code);
        InjectionCollector.Module owner = new InjectionCollector.Module(base, base + 32, "x.dll", "C:\\x.dll");
        // jmp rel32 (+0x100)
        code.clear();
        code.setByte(0, (byte) 0xE9);
        code.setInt(1, 0x100);
        assertEquals(base + 5 + 0x100, InjectionCollector.jumpTarget(code, base, owner));
        // mov rax, imm64 ; jmp rax
        code.clear();
        code.setByte(0, (byte) 0x48);
        code.setByte(1, (byte) 0xB8);
        code.setLong(2, 0x1122334455667788L);
        code.setByte(10, (byte) 0xFF);
        code.setByte(11, (byte) 0xE0);
        assertEquals(0x1122334455667788L, InjectionCollector.jumpTarget(code, base, owner));
        // prologue normal : pas de saut
        code.clear();
        code.setByte(0, (byte) 0x48);
        code.setByte(1, (byte) 0x89);
        code.setByte(2, (byte) 0x5C);
        assertEquals(0, InjectionCollector.jumpTarget(code, base, owner));
        // jmp [rip+x] dont la case est hors du module : jamais lue
        code.clear();
        code.setByte(0, (byte) 0xFF);
        code.setByte(1, (byte) 0x25);
        code.setInt(2, 0x7FFFFFF0);
        assertEquals(0, InjectionCollector.jumpTarget(code, base, owner));
    }

    @Test
    void knownOverlaysAreNotSuspect() {
        assertTrue(InjectionCollector.isOverlay(new InjectionCollector.Module(0, 1, "DiscordHook64.dll", "")));
        assertTrue(InjectionCollector.isOverlay(new InjectionCollector.Module(0, 1, "GameOverlayRenderer64.dll", "")));
        assertTrue(InjectionCollector.isOverlay(new InjectionCollector.Module(0, 1, "graphics-hook64.dll", "")));
        assertFalse(InjectionCollector.isOverlay(new InjectionCollector.Module(0, 1, "cheat.dll", "")));
    }
}
