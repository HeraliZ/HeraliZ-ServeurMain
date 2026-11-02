package com.Spartan.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Sondes du contrôle d'intégrité (audit du 02/11, point 13) : le jeu trouve ce que le serveur cherche sans jamais recevoir
 * les noms ; mêmes résultats qu'avec les listes en clair (morceau de nom de programme, classe d'un client de triche).
 */
class SondesTest {
    private static final List<String> PROGRAMMES = List.of("cheatengine", "x64dbg", "injector", "jd-gui");
    private static final List<String> CLASSES = List.of("net.wurstclient.WurstClient", "meteordevelopment.orbit.EventBus",
            "meteordevelopment.orbit.Listener", "net.minecraft.client.Minecraft", "SansPaquet");

    @Test
    void programmesTrouvesParMorceauDeNom() {
        Sondes s = Sondes.preparer(42L, PROGRAMMES, CLASSES);
        assertTrue(s.programmeCherche("cheatengine-x86_64.exe"));
        assertTrue(s.programmeCherche("CheatEngine.exe"), "sans tenir compte des majuscules");
        assertTrue(s.programmeCherche("Extreme Injector v3.exe"));
        assertTrue(s.programmeCherche("jd-gui.exe"));
        assertFalse(s.programmeCherche("chrome.exe"));
        assertFalse(s.programmeCherche("cheat.exe"), "morceau incomplet");
        assertFalse(s.programmeCherche(""));
    }

    @Test
    void rienEnClairDansLaDemande() {
        Sondes s = Sondes.preparer(42L, PROGRAMMES, CLASSES);
        assertEquals(PROGRAMMES.size(), s.programmes.length);
        // seules les longueurs des morceaux sont connues du jeu
        assertTrue(java.util.Arrays.equals(new int[] {6, 8, 11}, s.longueurs), java.util.Arrays.toString(s.longueurs));
        // paquets du jeu jamais cherchés, deux classes du même paquet : une seule empreinte
        assertEquals(2, s.paquets.length);
        // un autre sel : d'autres empreintes (rien de réutilisable d'un contrôle à l'autre)
        Sondes autre = Sondes.preparer(43L, PROGRAMMES, CLASSES);
        assertFalse(autre.programmes[0] == s.programmes[0]);
        assertFalse(new Sondes(43L, s.programmes, s.longueurs, s.paquets).programmeCherche("cheatengine.exe"));
    }

    @Test
    void classesTrouveesParPaquetEtTraduitesParLeServeur() {
        Sondes s = Sondes.preparer(7L, PROGRAMMES, CLASSES);
        String trouve = s.paquetCherche("net.wurstclient");
        assertNotNull(trouve);
        assertTrue(trouve.startsWith("#"));
        assertNull(s.paquetCherche("net.wurstclient.hacks"), "sous-paquet : autre paquet");
        assertNull(s.paquetCherche("net.minecraft.client"), "paquet du jeu : jamais cherché");
        assertNull(s.paquetCherche("mezz.jei"));
        Map<String, String> noms = Sondes.noms(7L, CLASSES);
        List<String> hits = new ArrayList<>(List.of(trouve, s.paquetCherche("meteordevelopment.orbit"), "#0000000000000000",
                "net.wurstclient.WurstClient"));
        Sondes.traduire(hits, noms);
        assertEquals(List.of("paquet de net.wurstclient.WurstClient",
                "paquet de meteordevelopment.orbit.EventBus, meteordevelopment.orbit.Listener"), hits);
    }

    @Test
    void sondesVidesNeTrouventRien() {
        Sondes s = Sondes.vides();
        assertFalse(s.programmeCherche("cheatengine.exe"));
        assertNull(s.paquetCherche("net.wurstclient"));
    }
}
