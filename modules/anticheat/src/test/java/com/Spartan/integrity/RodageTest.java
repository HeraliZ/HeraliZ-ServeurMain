package com.Spartan.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.Spartan.integrity.Rodage.Decision;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Règles en rodage (19/11) : un tricheur seul est toujours expulsé ; une règle qui se trompe (elle touche tout le monde)
 * est suspendue au 3e joueur différent, sans jamais expulser ce 3e.
 */
class RodageTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final long HEURE = 3_600_000L;
    private static final String REGLE = "natif:agent:";

    @Test
    void unTricheurSeulEstToujoursExpulse() {
        Rodage r = new Rodage();
        for (int i = 0; i < 20; i++) {
            assertEquals(Decision.APPLIQUER, r.noter(REGLE, A, i * 60_000L, 3, HEURE));
        }
        assertFalse(r.suspendue(REGLE));
        assertEquals(List.of(), r.suspendues());
    }

    @Test
    void troisJoueursDifferentsSuspendentLaRegle() {
        Rodage r = new Rodage();
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, A, 0, 3, HEURE));
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, B, 1_000, 3, HEURE));
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, A, 2_000, 3, HEURE)); // le même joueur ne compte qu'une fois
        assertEquals(Decision.SUSPENDRE, r.noter(REGLE, C, 3_000, 3, HEURE)); // le 3e n'est pas expulsé
        assertTrue(r.suspendue(REGLE));
        assertEquals(Decision.SUSPENDUE, r.noter(REGLE, A, 4_000, 3, HEURE));
        assertEquals(List.of(REGLE), r.suspendues());
        // les autres règles ne sont pas touchées
        assertEquals(Decision.APPLIQUER, r.noter("inject:redef:", C, 5_000, 3, HEURE));
    }

    @Test
    void horsDeLaFenetreLesJoueursSontOublies() {
        Rodage r = new Rodage();
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, A, 0, 3, HEURE));
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, B, HEURE / 2, 3, HEURE));
        // A est plus vieux qu'une heure : il ne compte plus, C n'est que le 2e
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, C, HEURE + 1, 3, HEURE));
        assertFalse(r.suspendue(REGLE));
    }

    @Test
    void seuilZeroCoupeLeDisjoncteur() {
        Rodage r = new Rodage();
        for (int i = 0; i < 10; i++) {
            assertEquals(Decision.APPLIQUER, r.noter(REGLE, new UUID(1, i), i, 0, HEURE));
        }
        assertFalse(r.suspendue(REGLE));
    }

    @Test
    void motifDeLaListeDeRodage() {
        List<String> rodage = List.of("natif:agent:", " Inject:Redef: ", "");
        assertEquals("natif:agent:", Rodage.motif("natif:agent:instrument.dll", rodage));
        assertEquals("inject:redef:", Rodage.motif("INJECT:REDEF:net.minecraft.client.Minecraft", rodage));
        assertNull(Rodage.motif("tamper:com.Spartan.X", rodage)); // règle établie : jamais suspendue
        assertNull(Rodage.motif("natif:agent:x", null));
        assertNull(Rodage.motif(null, rodage));
    }
}
