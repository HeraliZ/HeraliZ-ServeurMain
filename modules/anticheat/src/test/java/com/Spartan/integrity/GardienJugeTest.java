package com.Spartan.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Jugement des rapports du Gardien par le serveur (audit du 02/11, point 2 ; remplace l'ancien GardienTest, quand le jeu
 * vérifiait lui-même) : un jeu modifié ne peut plus faire passer un rapport « propre » de son invention, un vieux rapport
 * rejoué n'est pas frais, un jeu honnête lancé par le launcher est toujours accepté.
 */
class GardienJugeTest {
    private static final long PID = 4242;

    /** Ce que fait le launcher (gardien.js) : paire Ed25519, clé publique brute en base64url. */
    private static final class Launcher {
        final PrivateKey privee;
        final String publique;

        Launcher() throws Exception {
            KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            privee = kp.getPrivate();
            byte[] spki = kp.getPublic().getEncoded();
            publique = Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOfRange(spki, spki.length - 32, spki.length));
        }

        String signer(String ligne) throws Exception {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(privee);
            s.update(ligne.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
        }
    }

    private static GardienJuge prouve(Launcher l) {
        return new GardienJuge(GardienJuge.Lien.PROUVE, GardienPreuve.cle(l.publique));
    }

    @Test
    void jeuHonneteAccepteEtFrais() throws Exception {
        Launcher l = new Launcher();
        GardienJuge j = prouve(l);
        String defi = j.defiAEnvoyer(0, 60_000);
        assertNotNull(defi);
        assertNull(j.defiAEnvoyer(30_000, 60_000), "un défi par minute");
        String ligne = "v2|1|1000|" + PID + "|" + defi + "|ok||0";
        GardienJuge.Verdict v = j.juger("ok", ligne, l.signer(ligne), PID);
        assertEquals(GardienJuge.Sorte.RAPPORT, v.sorte());
        assertTrue(v.prouve() && v.frais());
        // le jeu relit le même fichier : rien de neuf, jamais une alerte
        assertEquals(GardienJuge.Sorte.DOUBLON, j.juger("ok", ligne, l.signer(ligne), PID).sorte());
        // défi renouvelé : un rapport écrit avec l'avant-dernier reste frais (le launcher écrit toutes les 10 à 20 s)
        String defi2 = j.defiAEnvoyer(60_000, 60_000);
        String ligne2 = "v2|2|1015|" + PID + "|" + defi + "|ok||0";
        assertTrue(j.juger("ok", ligne2, l.signer(ligne2), PID).frais());
        String ligne3 = "v2|3|1030|" + PID + "|" + defi2 + "|ok|cheatengine|1";
        GardienJuge.Verdict v3 = j.juger("ok", ligne3, l.signer(ligne3), PID);
        assertTrue(v3.frais());
        assertEquals("cheatengine", v3.rapport().outils());
        assertEquals(1, v3.rapport().jars());
    }

    @Test
    void rapportInventeParUnJeuModifieRefuse() throws Exception {
        Launcher l = new Launcher();
        GardienJuge j = prouve(l);
        String defi = j.defiAEnvoyer(0, 60_000);
        // l'ancienne phrase « tout va bien »
        assertEquals("signature", j.juger("ok", "ok;lecture=ok;outils=;jars=0", "", PID).raison());
        // une ligne propre signée par un autre programme (autre clé)
        Launcher faux = new Launcher();
        String propre = "v2|9|1000|" + PID + "|" + defi + "|ok||0";
        assertEquals("signature", j.juger("ok", propre, faux.signer(propre), PID).raison());
        // la vraie ligne, outils retirés
        String vraie = "v2|10|1000|" + PID + "|" + defi + "|ok|cheatengine|0";
        String sig = l.signer(vraie);
        assertEquals("signature", j.juger("ok", vraie.replace("cheatengine", ""), sig, PID).raison());
        // rapport du launcher pour un autre jeu
        assertEquals("pid", j.juger("ok", vraie, sig, PID + 1).raison());
        // le jeu dit avoir été lancé sans Gardien alors que le jeton prouve le contraire
        assertEquals(GardienJuge.Sorte.INVALIDE, j.juger("absent", "", "", PID).sorte());
    }

    @Test
    void vieuxRapportRejoueNiFraisNiAccepte() throws Exception {
        Launcher l = new Launcher();
        GardienJuge j = prouve(l);
        String d1 = j.defiAEnvoyer(0, 60_000);
        String ancien = "v2|1|1000|" + PID + "|" + d1 + "|ok||0";
        String sigAncien = l.signer(ancien);
        assertTrue(j.juger("ok", ancien, sigAncien, PID).frais());
        String recent = "v2|5|2000|" + PID + "|" + d1 + "|ok|cheatengine|0";
        assertTrue(j.juger("ok", recent, l.signer(recent), PID).frais());
        // revenir au rapport n°1 (sans l'outil vu ensuite) : refusé
        assertEquals("rejoue", j.juger("ok", ancien, sigAncien, PID).raison());
        // deux défis plus tard, un rapport resté sur le premier défi n'est plus frais
        j.defiAEnvoyer(60_000, 60_000);
        j.defiAEnvoyer(120_000, 60_000);
        String perime = "v2|6|3000|" + PID + "|" + d1 + "|ok||0";
        GardienJuge.Verdict v = j.juger("ok", perime, l.signer(perime), PID);
        assertEquals(GardienJuge.Sorte.RAPPORT, v.sorte());
        assertFalse(v.frais());
        // sans défi (launcher pas encore passé) : pas frais
        String sans = "v2|7|3000|" + PID + "|-|ok||0";
        assertFalse(j.juger("ok", sans, l.signer(sans), PID).frais());
    }

    @Test
    void ancienLauncherLuSansPreuve() {
        GardienJuge j = new GardienJuge(GardienJuge.Lien.ANCIEN, null);
        assertFalse(j.avecCle());
        assertNull(j.defiAEnvoyer(0, 60_000), "pas de défi sans clé");
        GardienJuge.Verdict v = j.juger("ok", "v1|1|1000|" + PID + "|ok|xenos|0", "ab".repeat(32), PID);
        assertEquals(GardienJuge.Sorte.RAPPORT, v.sorte());
        assertFalse(v.prouve());
        assertFalse(v.frais());
        assertEquals("xenos", v.rapport().outils());
        assertEquals(GardienJuge.Sorte.ABSENT, j.juger("absent", "", "", PID).sorte());
        assertEquals(GardienJuge.Sorte.MUET, j.juger("fichier", "", "", PID).sorte());
        assertEquals("format", j.juger("n'importe quoi", "", "", PID).raison());
    }
}
