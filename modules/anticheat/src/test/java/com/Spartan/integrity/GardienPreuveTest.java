package com.Spartan.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.PublicKey;
import org.junit.jupiter.api.Test;

/**
 * Preuve du Gardien (audit du 02/11, point 2) : rapports signés par le launcher (Ed25519), vérifiés par le serveur. Le
 * vecteur vient du launcher lui-même (launcher/app/assets/js/gardien.js, Node) : mêmes formats des deux côtés.
 */
class GardienPreuveTest {
    /** Clé publique, ligne et signature produites par Gardien.nouvellesCles(2) et Gardien.ligneV2 du launcher. */
    private static final String PUB = "q1-T-GXBLPBn8KxO1cR0p2alOYQJV_46lOzTZm1HVVQ";
    private static final String LIGNE = "v2|5|1000|42|0123456789abcdef0123456789abcdef|ok|cheatengine,xenos|2";
    private static final String SIG = "BmQoXzatda7SijaWmpm2-usEsJFri1j-VSZvDZSW27UkEOZZPKiERMGyC6aRrpeKz-aYbT5gdrajLt1RjUhBDA";
    private static final String VIDE = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void engagementCommeLeLauncher() {
        assertEquals("871a1985939220cb08bb4b70d698c48d204db28fb38d843cfd6f40c3c8f27a77",
                GardienPreuve.engagement(VIDE, "A".repeat(43)));
    }

    @Test
    void rapportDuLauncherAccepte() {
        PublicKey cle = GardienPreuve.cle(PUB);
        assertNotNull(cle);
        GardienPreuve.Lu lu = GardienPreuve.verifier(cle, LIGNE, SIG);
        assertTrue(lu.ok(), String.valueOf(lu.refus()));
        GardienPreuve.Rapport r = lu.rapport();
        assertEquals(2, r.version());
        assertEquals(5, r.n());
        assertEquals(42, r.pid());
        assertEquals("0123456789abcdef0123456789abcdef", r.defi());
        assertTrue(r.lecture());
        assertEquals("cheatengine,xenos", r.outils());
        assertEquals(2, r.jars());
    }

    @Test
    void rapportRetoucheOuInventeRefuse() {
        PublicKey cle = GardienPreuve.cle(PUB);
        // le jeu retire les outils vus : signature fausse
        assertEquals("signature", GardienPreuve.verifier(cle, LIGNE.replace("cheatengine,xenos", ""), SIG).refus());
        // rapport « propre » inventé, signature au hasard
        assertEquals("signature", GardienPreuve.verifier(cle, "v2|6|1000|42|-|ok||0", "A".repeat(86)).refus());
        assertEquals("signature", GardienPreuve.verifier(cle, LIGNE, "pas une signature").refus());
        // ancienne phrase du jeu
        assertEquals("signature", GardienPreuve.verifier(cle, "ok;lecture=ok;outils=;jars=0", SIG).refus());
        // autre clé (autre lancement)
        PublicKey autre = GardienPreuve.cle("B".repeat(43));
        assertFalse(autre != null && GardienPreuve.verifier(autre, LIGNE, SIG).ok());
    }

    @Test
    void clesIllisibles() {
        assertNull(GardienPreuve.cle(null));
        assertNull(GardienPreuve.cle(""));
        assertNull(GardienPreuve.cle("ab".repeat(32)));     // ancienne clé HMAC
        assertNull(GardienPreuve.cle("q1-T-GXBLPBn8KxO1cR0p2alOYQJV_46lOzTZm1HVV")); // trop courte
    }

    @Test
    void lectureSansPreuveDesAnciensLaunchers() {
        GardienPreuve.Lu lu = GardienPreuve.lireSansPreuve("v1|3|1000|42|impossible|cheatengine|1");
        assertTrue(lu.ok());
        assertEquals(1, lu.rapport().version());
        assertEquals("-", lu.rapport().defi());
        assertFalse(lu.rapport().lecture());
        assertEquals("cheatengine", lu.rapport().outils());
        assertEquals(1, lu.rapport().jars());
        assertEquals("format", GardienPreuve.lireSansPreuve("rien").refus());
        assertEquals("format", GardienPreuve.lireSansPreuve("v2|1|1|1|pas-un-defi|ok||0").refus());
        assertEquals("format", GardienPreuve.lireSansPreuve("v2|-1|1|1|-|ok||0").refus());
        // outils hors format : ignorés, pas recopiés
        assertEquals("", GardienPreuve.lireSansPreuve("v2|1|1|1|-|ok|<script>|0").rapport().outils());
    }
}
