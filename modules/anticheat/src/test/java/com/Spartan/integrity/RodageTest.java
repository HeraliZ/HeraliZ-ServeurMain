package com.Spartan.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.Spartan.integrity.Rodage.Decision;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Règles en rodage (19/11, comptées en PC depuis le 21/11) : un tricheur seul est toujours expulsé, même avec plusieurs
 * comptes ou derrière la même connexion ; une règle qui se trompe (elle touche tout le monde) est suspendue au 3e PC
 * différent, sans jamais expulser ce 3e, puis remise en service au bout de l'heure.
 */
class RodageTest {
    private static final long HEURE = 3_600_000L;
    private static final String REGLE = "natif:agent:";

    /** Un PC : son compte, son bloc d'adresses et son identifiant matériel. */
    private static Set<String> pc(String compte, String ip, String materiel) {
        return Set.of("compte:" + compte, "ip4:" + ip, "pc:" + materiel);
    }

    private static final Set<String> A = pc("a", "1.1.1", "m1");
    private static final Set<String> B = pc("b", "2.2.2", "m2");
    private static final Set<String> C = pc("c", "3.3.3", "m3");
    private static final Set<String> D = pc("d", "4.4.4", "m4");
    private static final Set<String> E = pc("e", "5.5.5", "m5");
    private static final Set<String> F = pc("f", "6.6.6", "m6");

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
    void troisPcDifferentsSuspendentLaRegle() {
        Rodage r = new Rodage();
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, A, 0, 3, HEURE));
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, B, 1_000, 3, HEURE));
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, A, 2_000, 3, HEURE)); // le même PC ne compte qu'une fois
        assertEquals(Decision.SUSPENDRE, r.noter(REGLE, C, 3_000, 3, HEURE)); // le 3e n'est pas expulsé
        assertTrue(r.suspendue(REGLE));
        assertTrue(r.desSuspendues());
        assertEquals(3_000L, r.depuis(REGLE));
        assertEquals(Decision.SUSPENDUE, r.noter(REGLE, A, 4_000, 3, HEURE));
        assertEquals(List.of(REGLE), r.suspendues());
        // les autres règles ne sont pas touchées
        assertEquals(Decision.APPLIQUER, r.noter("inject:redef:", C, 5_000, 3, HEURE));
    }

    @Test
    void plusieursComptesSurUnMemePcNeComptentQuUneFois() {
        Rodage r = new Rodage();
        // un tricheur : 10 comptes, même PC (identifiant matériel), adresses différentes
        for (int i = 0; i < 10; i++) {
            Set<String> alt = Set.of("compte:alt" + i, "ip4:9.9." + i, "pc:triche");
            assertEquals(Decision.APPLIQUER, r.noter(REGLE, alt, i * 1_000L, 3, HEURE));
        }
        assertFalse(r.suspendue(REGLE));
    }

    @Test
    void plusieursPcDerriereLaMemeConnexionNeComptentQuUneFois() {
        Rodage r = new Rodage();
        // identifiants matériels inventés à chaque connexion (jeu modifié), mais même bloc d'adresses
        for (int i = 0; i < 10; i++) {
            Set<String> alt = Set.of("compte:alt" + i, "ip4:9.9.9", "pc:faux" + i);
            assertEquals(Decision.APPLIQUER, r.noter(REGLE, alt, i * 1_000L, 3, HEURE));
        }
        assertFalse(r.suspendue(REGLE));
    }

    @Test
    void unDeclenchementQuiReliDeuxPcLesFusionne() {
        Rodage r = new Rodage();
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, A, 0, 3, HEURE));
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, B, 1_000, 3, HEURE));
        // le compte de A depuis l'adresse de B : A et B étaient le même ; C n'est donc que le 2e PC
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, Set.of("compte:a", "ip4:2.2.2"), 2_000, 3, HEURE));
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, C, 3_000, 3, HEURE));
        assertFalse(r.suspendue(REGLE));
        assertEquals(Decision.SUSPENDRE, r.noter(REGLE, D, 4_000, 3, HEURE));
    }

    @Test
    void horsDeLaFenetreLesPcSontOublies() {
        Rodage r = new Rodage();
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, A, 0, 3, HEURE));
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, B, HEURE / 2, 3, HEURE));
        // A est plus vieux qu'une heure : il ne compte plus, C n'est que le 2e
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, C, HEURE + 1, 3, HEURE));
        assertFalse(r.suspendue(REGLE));
    }

    @Test
    void laRegleSeRemetEnServiceAuBoutDeLHeure() {
        Rodage r = new Rodage();
        r.noter(REGLE, A, 0, 3, HEURE);
        r.noter(REGLE, B, 1_000, 3, HEURE);
        assertEquals(Decision.SUSPENDRE, r.noter(REGLE, C, 2_000, 3, HEURE));
        assertEquals(List.of(), r.reprendre(2_000 + HEURE - 1, HEURE)); // pas encore
        assertTrue(r.suspendue(REGLE));
        assertEquals(List.of(REGLE), r.reprendre(2_000 + HEURE, HEURE));
        assertFalse(r.suspendue(REGLE));
        assertFalse(r.desSuspendues());
        assertEquals(-1L, r.depuis(REGLE));
        // de nouveau en service : elle expulse
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, D, 3_000 + HEURE, 3, HEURE));
    }

    @Test
    void lesPcQuiOntCoupeLaRegleNePeuventPasLaCouperUneDeuxiemeFois() {
        Rodage r = new Rodage();
        r.noter(REGLE, A, 0, 3, HEURE);
        r.noter(REGLE, B, 1_000, 3, HEURE);
        assertEquals(Decision.SUSPENDRE, r.noter(REGLE, C, 2_000, 3, HEURE));
        r.reprendre(2_000 + HEURE, HEURE);
        long t = 3_000 + HEURE;
        // les mêmes trois PC (même un autre compte sur l'un d'eux) : toujours expulsés, jamais comptés
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, A, t, 3, HEURE));
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, B, t + 1, 3, HEURE));
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, Set.of("compte:autre", "pc:m3"), t + 2, 3, HEURE));
        assertFalse(r.suspendue(REGLE));
        // une vraie fausse alerte touche de nouveaux PC : suspendue à nouveau au 3e
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, D, t + 3, 3, HEURE));
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, E, t + 4, 3, HEURE));
        assertEquals(Decision.SUSPENDRE, r.noter(REGLE, F, t + 5, 3, HEURE));
    }

    @Test
    void repriseZeroGardeLaRegleSuspendueJusquAuRedemarrage() {
        Rodage r = new Rodage();
        r.noter(REGLE, A, 0, 3, HEURE);
        r.noter(REGLE, B, 1, 3, HEURE);
        r.noter(REGLE, C, 2, 3, HEURE);
        assertEquals(List.of(), r.reprendre(100 * HEURE, 0));
        assertTrue(r.suspendue(REGLE));
    }

    @Test
    void sansAucuneCleTousLesPcSontEnsemble() {
        Rodage r = new Rodage();
        for (int i = 0; i < 5; i++) {
            assertEquals(Decision.APPLIQUER, r.noter(REGLE, Set.of(), i, 3, HEURE));
        }
        assertEquals(Decision.APPLIQUER, r.noter(REGLE, null, 10, 3, HEURE));
        assertFalse(r.suspendue(REGLE));
    }

    @Test
    void seuilZeroCoupeLeDisjoncteur() {
        Rodage r = new Rodage();
        for (int i = 0; i < 10; i++) {
            assertEquals(Decision.APPLIQUER, r.noter(REGLE, pc("x" + i, "7.7." + i, "z" + i), i, 0, HEURE));
        }
        assertFalse(r.suspendue(REGLE));
    }

    @Test
    void blocDAdressesIp() {
        assertEquals("ip4:192.168.1", Rodage.blocIp(new byte[] {(byte) 192, (byte) 168, 1, (byte) 200}));
        // IPv4 écrite en IPv6 : comme l'IPv4
        assertEquals("ip4:10.0.0", Rodage.blocIp(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff,
                10, 0, 0, 7}));
        // IPv6 : les 48 premiers bits
        assertEquals("ip6:2a01:e0a:1f", Rodage.blocIp(new byte[] {0x2a, 0x01, 0x0e, 0x0a, 0x00, 0x1f, 0x12, 0x34,
                0, 0, 0, 0, 0, 0, 0, 1}));
        assertNull(Rodage.blocIp(null));
        assertNull(Rodage.blocIp(new byte[] {1, 2}));
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
