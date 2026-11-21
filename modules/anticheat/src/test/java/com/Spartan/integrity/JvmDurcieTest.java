package com.Spartan.integrity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@code requireHardenedJvm} (21/11) : un jeu lancé par le launcher (Java 17, attache coupée, chargement d'agents à chaud
 * laissé par défaut) n'est jamais signalé ; un jeu sans l'attache coupée l'est toujours.
 */
class JvmDurcieTest {
    @Test
    void jeuDuLauncherJamaisSignale() {
        // ce que la sonde du jeu envoie pour un jeu du launcher en Java 17 (relevé dans le journal du serveur le 20/11)
        assertFalse(JvmDurcie.attacheOuverte("agents dynamiques autorisés"));
        assertFalse(JvmDurcie.attacheOuverte(""));
        assertFalse(JvmDurcie.attacheOuverte(null));
    }

    @Test
    void attacheOuverteToujoursSignalee() {
        assertTrue(JvmDurcie.attacheOuverte("attach activé"));
        assertTrue(JvmDurcie.attacheOuverte("attach activé, agents dynamiques autorisés"));
        assertTrue(JvmDurcie.attacheOuverte("ATTACH ACTIVÉ"));
    }
}
