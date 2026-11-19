package com.Spartan.scan.collect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.Spartan.scan.ScanReport;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * 19/11 : injection qui contourne l'attache coupée par le launcher. Classes redéfinies en mémoire, bibliothèques d'agent
 * ou de débogueur de Java, contrôle à distance de Java ouvert au lancement (sans Minecraft).
 */
class MemoireTest {
    private static final Function<String, String> SANS_ENV = k -> null;

    @Test
    void uneClasseChargeeNormalementNEstJamaisRedefinie() {
        // Java des tests (HotSpot, aucun agent) : lecture possible, 0 pour toute classe ; illisible ailleurs : -1, jamais > 0
        int attendu = Redefinitions.disponible() ? 0 : -1;
        assertEquals(attendu, Redefinitions.compte(String.class));
        assertEquals(attendu, Redefinitions.compte(MemoireTest.class));
        assertEquals(attendu, Redefinitions.compte(Redefinitions.class));
        assertEquals(-1, Redefinitions.compte(null));
    }

    @Test
    void seulesLesClassesRedefiniesVontAuRapport() {
        ScanReport r = new ScanReport();
        List<Class<?>> vues = Redefinitions.collect(r, List.of("java.lang.String", "pas.une.Classe", MemoireTest.class.getName()),
                MemoireTest.class.getClassLoader());
        assertEquals(List.of(String.class, MemoireTest.class), vues);
        assertTrue(r.injection.isEmpty(), r.injection.toString());
        assertEquals("redef|net.minecraft.client.renderer.LevelRenderer|2",
                Redefinitions.entree("net.minecraft.client.renderer.LevelRenderer", 2));
    }

    @Test
    void laVeilleConnaitLesBibliothequesDAgentEtDeDebogueur() {
        for (String n : List.of("instrument.dll", "JDWP.DLL", "libinstrument.so", "dt_socket.dll", "dt_shmem.dll", "attach.dll",
                "libattach.so", "management_agent.dll", "sawindbg.dll", "libsaproc.so", "libinstrument.dylib")) {
            assertTrue(Veille.javaSensible(n), n);
        }
        for (String n : Arrays.asList("java.dll", "management.dll", "management_ext.dll", "javajpeg.dll", "lwjgl.dll",
                "discordhook64.dll", "instrumentation.dll", "attachments.dll", "libjvm.so", "", null)) {
            assertFalse(Veille.javaSensible(n), String.valueOf(n));
        }
        // la veille prend des classes déjà chargées sans rien signaler (leur état sert de référence)
        Veille.surveiller(List.of(String.class, MemoireTest.class));
        Veille.surveiller(List.of());
    }

    @Test
    void controleADistanceEtFichiersDOptionsSontReleves() {
        ScanReport r = new ScanReport();
        JvmCollector.collect(r, List.of("-Dcom.sun.management.jmxremote.port=9999",
                        "-Dcom.sun.management.jmxremote.password.file=C:\\Users\\Joueur\\pw.txt", "-DCOM.SUN.MANAGEMENT.jmxremote",
                        "-XX:+ManagementServer", "-XX:+AllowRedefinitionToAddDeleteMethods",
                        "-XX:Flags=C:\\Users\\Joueur\\.hotspotrc", "-XX:VMOptionsFile=opts.txt", "-XX:+UseG1GC", "-Xmx4G",
                        "-XX:+DisableAttachMechanism"),
                SANS_ENV, n -> "DisableAttachMechanism".equals(n) ? "true" : null, "");
        assertEquals(List.of("-Dcom.sun.management.jmxremote.port", "-Dcom.sun.management.jmxremote.password.file",
                "-Dcom.sun.management.jmxremote", "-XX:+ManagementServer", "-XX:+AllowRedefinitionToAddDeleteMethods",
                "-XX:Flags", "-XX:VMOptionsFile"), r.jvmFlags);
        String tout = String.join(" ", r.jvmFlags);
        assertFalse(tout.contains("Joueur") || tout.contains("9999") || tout.contains("opts"), tout);
        assertTrue(r.attachDisabled);
    }
}
