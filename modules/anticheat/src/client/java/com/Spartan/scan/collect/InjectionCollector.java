package com.Spartan.scan.collect;

import com.Spartan.scan.ScanReport;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Code natif injecté dans le jeu, même CACHÉ (Windows 64 bits).
 *
 * <p>Les clients de triche « injectés » passent par une DLL ; les plus discrets la chargent à la main en mémoire
 * (« manual map ») pour qu'elle n'apparaisse dans aucune liste de modules. Deux traces restent pourtant visibles :
 * <ol>
 *    <li><b>threads démarrés hors de tout module</b> : chaque thread du processus a une adresse de départ. Celles du
 *    jeu, de Java, de Windows et des pilotes sont toutes dans une DLL chargée normalement. Une adresse qui n'appartient
 *    à AUCUN module est du code posé en mémoire par un injecteur ;</li>
 *    <li><b>crochets sur l'affichage</b> : un ESP / menu de triche natif se branche sur la fonction qui affiche chaque
 *    image (wglSwapBuffers / SwapBuffers) en remplaçant son début par un saut. On lit ce début : un saut vers une
 *    mémoire sans module est un crochet de code injecté ; un saut vers un autre module est relevé avec son nom et son
 *    dossier (anonymisé). Le SERVEUR reconnaît les overlays (Discord, Steam, OBS, RivaTuner…, réglage
 *    {@code scan.overlays}) d'après le nom ET le dossier : depuis l'audit du 02/11 (point 13), le jeu n'a plus de liste
 *    de noms ignorés (nommer sa DLL « steam_… » suffisait à passer).</li>
 * </ol>
 * Entrées produites dans {@link ScanReport#injection} : {@code thread|0x…} et {@code hook|fonction|0x…|module|dossier}.
 * Rien d'autre n'est lu : ni la mémoire du jeu, ni les autres programmes.
 */
public final class InjectionCollector {
    private static final int THREAD_QUERY_INFORMATION = 0x0040;
    private static final int THREAD_QUERY_SET_WIN32_START_ADDRESS = 9;
    private static final int MAX_ENTREES = 32;

    private InjectionCollector() {
    }

    /** Une plage de mémoire occupée par un module (DLL / exe) chargé normalement. */
    record Module(long base, long end, String name, String path) {
        boolean contains(long addr) {
            return addr >= base && addr < end;
        }
    }

    public static void collect(ScanReport r, Path gameDir) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win") || Native.POINTER_SIZE != 8) {
            return;
        }
        List<Long> starts = threadStarts();         // d'abord les threads…
        List<Module> modules = modules();           // … puis les modules (un module chargé entre-temps y est déjà)
        if (modules.isEmpty()) {
            return;
        }
        for (long a : starts) {
            if (a != 0 && module(modules, a) == null && r.injection.size() < MAX_ENTREES) {
                String e = "thread|" + hex(a);
                if (!r.injection.contains(e)) {
                    r.injection.add(e);
                }
            }
        }
        String jeu = gameDir == null ? "" : gameDir.toAbsolutePath().toString();
        hook(r, modules, "opengl32", "wglSwapBuffers", jeu);
        hook(r, modules, "gdi32", "SwapBuffers", jeu);
        hook(r, modules, "gdi32full", "SwapBuffers", jeu);
    }

    // ------------------------------------------------------------------ threads
    @Structure.FieldOrder({"dwSize", "cntUsage", "th32ThreadID", "th32OwnerProcessID", "tpBasePri", "tpDeltaPri", "dwFlags"})
    public static class ThreadEntry extends Structure {
        public int dwSize;
        public int cntUsage;
        public int th32ThreadID;
        public int th32OwnerProcessID;
        public int tpBasePri;
        public int tpDeltaPri;
        public int dwFlags;

        public ThreadEntry() {
            dwSize = size();
        }
    }

    interface K32 extends StdCallLibrary {
        K32 I = Native.load("kernel32", K32.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean Thread32First(WinNT.HANDLE snapshot, ThreadEntry entry);

        boolean Thread32Next(WinNT.HANDLE snapshot, ThreadEntry entry);

        WinNT.HANDLE OpenThread(int access, boolean inherit, int threadId);
    }

    interface Nt extends StdCallLibrary {
        Nt I = Native.load("ntdll", Nt.class);

        int NtQueryInformationThread(WinNT.HANDLE thread, int infoClass, Pointer info, int length, IntByReference returned);
    }

    /** Adresse de départ (Win32StartAddress) de chaque thread du jeu. */
    static List<Long> threadStarts() {
        List<Long> out = new ArrayList<>();
        int pid = Kernel32.INSTANCE.GetCurrentProcessId();
        WinNT.HANDLE snap = Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPTHREAD, new WinDef.DWORD(0));
        if (snap == null || WinNT.INVALID_HANDLE_VALUE.equals(snap)) {
            return out;
        }
        try {
            ThreadEntry e = new ThreadEntry();
            Memory buf = new Memory(8);
            for (boolean ok = K32.I.Thread32First(snap, e); ok; ok = K32.I.Thread32Next(snap, e)) {
                if (e.th32OwnerProcessID != pid) {
                    continue;
                }
                WinNT.HANDLE h = K32.I.OpenThread(THREAD_QUERY_INFORMATION, false, e.th32ThreadID);
                if (h == null) {
                    continue;
                }
                try {
                    buf.setLong(0, 0);
                    if (Nt.I.NtQueryInformationThread(h, THREAD_QUERY_SET_WIN32_START_ADDRESS, buf, 8, null) == 0) {
                        out.add(buf.getLong(0));
                    }
                } finally {
                    Kernel32.INSTANCE.CloseHandle(h);
                }
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(snap);
        }
        return out;
    }

    static List<Module> modules() {
        List<Module> out = new ArrayList<>();
        try {
            for (Tlhelp32.MODULEENTRY32W m : Kernel32Util.getModules(Kernel32.INSTANCE.GetCurrentProcessId())) {
                long base = Pointer.nativeValue(m.modBaseAddr);
                out.add(new Module(base, base + (m.modBaseSize.longValue() & 0xFFFFFFFFL), m.szModule(), m.szExePath()));
            }
        } catch (Throwable t) {
            out.clear();
        }
        return out;
    }

    static Module module(List<Module> modules, long addr) {
        for (Module m : modules) {
            if (m.contains(addr)) {
                return m;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ crochets
    private static void hook(ScanReport r, List<Module> modules, String library, String function, String jeu) {
        Module owner = null;
        for (Module m : modules) {
            if (m.name().equalsIgnoreCase(library + ".dll")) {
                owner = m;
            }
        }
        if (owner == null) {
            return; // bibliothèque non chargée : rien à vérifier (et on ne la charge pas)
        }
        long addr;
        Function f;
        try {
            f = NativeLibrary.getInstance(library).getFunction(function);
            addr = Pointer.nativeValue(f);
        } catch (Throwable t) {
            return;
        }
        if (!owner.contains(addr)) {
            return; // fonction transférée vers une autre DLL système
        }
        long target = jumpTarget(f, addr, owner);
        if (target == 0 || owner.contains(target)) {
            return; // pas de saut, ou saut interne
        }
        Module dest = module(modules, target);
        if (dest != null && isSystem(dest)) {
            return;
        }
        // Relais (08/11, overlay de Discord pris pour un cheat) : les crocheteurs (MinHook, Detours, overlay de Discord…)
        // posent souvent un saut court vers un petit relais alloué près de la fonction (mémoire sans module), qui saute
        // ensuite dans leur DLL. On suit ce relais (3 sauts au plus) pour juger la DLL qui reçoit vraiment l'affichage ;
        // aucun module au bout = code injecté caché, comme avant.
        boolean relais = false;
        if (dest == null) {
            long t = target;
            for (int i = 0; i < 3 && dest == null && t != 0; i++) {
                t = jumpTargetSur(t);
                dest = t == 0 ? null : module(modules, t);
            }
            relais = dest != null;
        }
        if (r.injection.size() < MAX_ENTREES) {
            String dossier = dest == null ? "" : NativeCollector.classify(dest.path(), System::getenv,
                    System.getProperty("java.home", ""), jeu, System.getProperty("user.home", ""), true).shown();
            r.injection.add("hook|" + library + "!" + function + "|" + hex(target) + "|" + (dest == null ? "" : dest.name())
                    + "|" + dossier.replace('|', '/') + (relais ? "|relais" : ""));
        }
    }

    /**
     * Cible du saut placé au début d'une fonction (0 si aucun saut reconnu). On ne lit que les 14 premiers octets de la
     * fonction et, pour un saut indirect, la case d'adresse SI elle est dans la DLL elle-même (sinon une lecture hors
     * mémoire valide ferait planter le jeu).
     */
    static long jumpTarget(Pointer code, long addr, Module owner) {
        if (!owner.contains(addr + 14)) {
            return 0;
        }
        byte[] b = code.getByteArray(0, 14);
        return decoder(b, addr, slot -> owner.contains(slot) && owner.contains(slot + 8) ? new Pointer(slot).getLong(0) : 0);
    }

    /**
     * Saut placé à une adresse hors de tout module (relais d'un crochet) : lu par ReadProcessMemory sur notre propre
     * processus, qui répond « échec » pour une page absente ou illisible au lieu de faire planter le jeu.
     */
    static long jumpTargetSur(long addr) {
        byte[] b = lire(addr, 14);
        if (b == null) {
            return 0;
        }
        return decoder(b, addr, slot -> {
            byte[] c = lire(slot, 8);
            return c == null ? 0 : longAt(c, 0);
        });
    }

    static byte[] lire(long addr, int n) {
        if (addr == 0) {
            return null;
        }
        try {
            Memory buf = new Memory(n);
            IntByReference lus = new IntByReference();
            boolean ok = Kernel32.INSTANCE.ReadProcessMemory(Kernel32.INSTANCE.GetCurrentProcess(), new Pointer(addr), buf,
                    n, lus);
            return ok && lus.getValue() == n ? buf.getByteArray(0, n) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Cible du saut codé dans {@code b} (lu à l'adresse {@code addr}), 0 si aucun ; {@code slot} lit une case d'adresse. */
    static long decoder(byte[] b, long addr, java.util.function.LongUnaryOperator slot) {
        int op = b[0] & 0xFF;
        if (op == 0xE9) {                                           // jmp rel32
            return addr + 5 + intAt(b, 1);
        }
        if (op == 0xEB) {                                           // jmp rel8
            return addr + 2 + b[1];
        }
        if (op == 0xFF && (b[1] & 0xFF) == 0x25) {                  // jmp [rip+rel32]
            long case8 = addr + 6 + intAt(b, 2);
            if (case8 == addr + 6) {
                return longAt(b, 6);                                // jmp [rip+0] : l'adresse suit (relais classique)
            }
            return slot.applyAsLong(case8);
        }
        if (op == 0x48 && (b[1] & 0xFF) == 0xB8 && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xE0) {
            return longAt(b, 2);                                    // mov rax, imm64 ; jmp rax
        }
        if (op == 0x49 && (b[1] & 0xFF) == 0xBB && (b[10] & 0xFF) == 0x41 && (b[11] & 0xFF) == 0xFF) {
            return longAt(b, 2);                                    // mov r11, imm64 ; jmp r11
        }
        if (op == 0x68 && (b[5] & 0xFF) == 0xC3) {                  // push imm32 ; ret
            return intAt(b, 1) & 0xFFFFFFFFL;
        }
        return 0;
    }

    private static int intAt(byte[] b, int i) {
        return (b[i] & 0xFF) | (b[i + 1] & 0xFF) << 8 | (b[i + 2] & 0xFF) << 16 | (b[i + 3] & 0xFF) << 24;
    }

    private static long longAt(byte[] b, int i) {
        return (intAt(b, i) & 0xFFFFFFFFL) | ((long) intAt(b, i + 4) << 32);
    }

    static boolean isSystem(Module m) {
        String p = m.path().toLowerCase(Locale.ROOT).replace('/', '\\');
        String root = System.getenv("SystemRoot");
        return root != null && p.startsWith(root.toLowerCase(Locale.ROOT) + "\\");
    }

    static String hex(long a) {
        return "0x" + Long.toHexString(a).toUpperCase(Locale.ROOT);
    }
}
