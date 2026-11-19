package com.Spartan.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.Spartan.integrity.GardienBibliotheques.Bibliotheque;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Clés du Gardien réutilisées (launcher imité avec une clé fixe) et bibliothèques de Java vues dans le jeu par le
 * launcher (19/11). Le vrai launcher (une clé neuve par lancement, reconnexions du même jeu) n'est jamais signalé.
 */
class GardienClesTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    @Test
    void laMemeClePourDeuxComptesEstSignalee() {
        GardienCles c = new GardienCles();
        assertNull(c.lier("K", A, "Survivant", 0));
        String r = c.lier("K", B, "Rodeur", 60_000);
        assertNotNull(r);
        assertTrue(r.contains("Survivant"), r);
    }

    @Test
    void uneReconnexionDuMemeJeuNeDitRien() {
        GardienCles c = new GardienCles();
        assertNull(c.lier("K", A, "Survivant", 0));
        assertNull(c.jeu("K", A, 4242, 10_000));
        assertNull(c.jeu("K", A, 4242, 25_000));
        // reconnexion du même jeu (même processus, même clé) : rien
        assertNull(c.lier("K", A, "Survivant", 600_000));
        assertNull(c.jeu("K", A, 4242, 610_000));
    }

    @Test
    void laMemeClePourUnAutreJeuEstSignalee() {
        GardienCles c = new GardienCles();
        assertNull(c.lier("K", A, "Survivant", 0));
        assertNull(c.jeu("K", A, 4242, 10_000));
        assertNull(c.lier("K", A, "Survivant", 3_600_000)); // nouvelle connexion avec la même clé…
        String r = c.jeu("K", A, 5151, 3_610_000);    // … pour un autre jeu
        assertNotNull(r);
        assertTrue(r.contains("4242") && r.contains("5151"), r);
    }

    @Test
    void desClesDifferentesNeSontJamaisSignalees() {
        GardienCles c = new GardienCles();
        assertNull(c.lier("K1", A, "Survivant", 0));
        assertNull(c.lier("K2", B, "Rodeur", 1));
        assertNull(c.lier("K3", A, "Survivant", 2));
        assertNull(c.jeu("K1", A, 1, 3));
        assertNull(c.jeu("K3", A, 2, 4));
        assertNull(c.jeu("K2", A, 9, 5)); // clé d'un autre joueur : jugée à la réception du jeton
        assertNull(c.jeu("K2", B, 0, 6)); // numéro de processus inconnu : rien
        assertNull(c.lier("", A, "Survivant", 7));
        assertNull(c.lier(null, A, "Survivant", 8));
    }

    @Test
    void uneCleEstOublieeApres24h() {
        GardienCles c = new GardienCles();
        assertNull(c.lier("K", A, "Survivant", 0));
        assertNull(c.lier("K", B, "Rodeur", GardienCles.DUREE_MS + 1));
    }

    @Test
    void laMemoireEstBornee() {
        GardienCles c = new GardienCles();
        assertNull(c.lier("premiere", A, "Survivant", 0));
        for (int i = 0; i < GardienCles.MAX + 10; i++) {
            assertNull(c.lier("k" + i, new UUID(1, i), "j" + i, 1 + i));
        }
        // la plus ancienne est oubliée : plus rien à comparer
        assertNull(c.lier("premiere", B, "Rodeur", 10_000));
    }

    @Test
    void bibliothequesVuesParLeLauncher() {
        Bibliotheque i = GardienBibliotheques.lire("dll.instrument");
        assertEquals("instrument", i.nom());
        assertTrue(i.forte());
        assertTrue(GardienBibliotheques.lire("dll.jdwp").forte());
        assertTrue(GardienBibliotheques.lire("dll.dt_socket").forte());
        assertFalse(GardienBibliotheques.lire("dll.attach").forte());
        assertFalse(GardienBibliotheques.lire("dll.management_agent").forte());
        assertFalse(GardienBibliotheques.lire("dll.inconnue").forte());
        // les programmes de la liste du launcher restent des programmes
        assertNull(GardienBibliotheques.lire("cheatengine"));
        assertNull(GardienBibliotheques.lire("cheat engine.titre"));
        assertNull(GardienBibliotheques.lire("dll."));
        assertNull(GardienBibliotheques.lire(null));
    }
}
