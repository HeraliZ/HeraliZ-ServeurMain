package com.Spartan.check.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.Spartan.check.movement.model.Silences;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Cadence réelle des tirs (19/11) : un jeu honnête n'est jamais retardé ni refusé (à-coups du réseau, 4G, coupure, réseau
 * qui se vide d'un coup) ; une rafale envoyée d'un coup par un jeu modifié est étalée à la cadence de l'arme, et une cadence
 * soutenue plus rapide que l'arme finit refusée.
 *
 * <p>Réseau simulé : le jeu répond à un ping toutes les 50 ms et tire toutes les 100 ms (arme à 600 coups/min) ; chaque
 * paquet part à son heure, arrive après le retard du réseau de ce moment, jamais avant le paquet précédent (TCP).
 */
class CadenceTirsTest {
    private static final double TOLERANCE = 150;
    private static final double FENETRE = 600; // comme TirsTacz
    private static final double ATTENTE_MAX = 1000;

    /** Un paquet du jeu : heure d'envoi (ms) ; pour un tir, l'heure annoncée (ms, horloge des tirs du jeu). */
    private record Paquet(double envoi, boolean tir, long heure) {
    }

    /** Résultat d'un tir : arrivée (ms) et étalement rendu par CadenceTirs. */
    private record Jugement(double arrivee, double etaler) {
    }

    /** Jeu honnête : pings toutes les 50 ms, un tir toutes les 100 ms, heure annoncée = heure réelle. */
    private static List<Paquet> honnete(double debut, double fin) {
        List<Paquet> p = new ArrayList<>();
        for (double t = debut; t < fin; t += 50) {
            p.add(new Paquet(t, false, 0));
            if (Math.round(t) % 100 == 0) {
                p.add(new Paquet(t + 1, true, Math.round(t + 1)));
            }
        }
        return p;
    }

    /** Fait passer les paquets par le réseau (retard selon l'heure d'envoi, ordre gardé) et juge chaque tir. */
    private static List<Jugement> jouer(List<Paquet> paquets, DoubleUnaryOperator retard) {
        paquets.sort((a, b) -> Double.compare(a.envoi(), b.envoi()));
        CadenceTirs c = new CadenceTirs();
        Silences s = new Silences();
        List<Jugement> out = new ArrayList<>();
        double precedente = Double.NEGATIVE_INFINITY;
        for (Paquet p : paquets) {
            double arrivee = Math.max(precedente, p.envoi() + retard.applyAsDouble(p.envoi()));
            precedente = arrivee;
            long nanos = Math.round(arrivee * 1_000_000L);
            s.paquet(nanos);
            if (p.tir()) {
                double silence = s.recent(nanos, 1_500_000_000L) / 1e6;
                out.add(new Jugement(arrivee, c.tir(arrivee, p.heure(), silence, TOLERANCE, FENETRE, ATTENTE_MAX)));
            }
        }
        return out;
    }

    private static double plusGrandEtalement(List<Jugement> j) {
        return j.stream().mapToDouble(Jugement::etaler).max().orElse(0);
    }

    @Test
    void jeuHonneteAvecDesACoupsDuReseauNestJamaisRetarde() {
        Random r = new Random(7);
        double[] bruit = new double[20_000];
        for (int i = 0; i < bruit.length; i++) {
            bruit[i] = r.nextDouble() * 140; // Wi-Fi chargé, 4G : de 40 à 180 ms
        }
        List<Jugement> j = jouer(honnete(0, 20_000), t -> 40 + bruit[(int) t]);
        assertEquals(0.0, plusGrandEtalement(j), "un tir honnête a été retardé");
    }

    @Test
    void coupureDuReseauPuisToutArriveDUnCoupNestPasUneRafale() {
        // de 2 000 à 2 380 ms, plus rien ne passe ; tout ce qui a été envoyé pendant la coupure arrive à 2 420 ms
        DoubleUnaryOperator retard = t -> t >= 2000 && t < 2380 ? 2380 - t + 40 : 40;
        List<Jugement> j = jouer(honnete(0, 6000), retard);
        assertEquals(0.0, plusGrandEtalement(j), "tirs d'une coupure pris pour une rafale");
    }

    @Test
    void reseauQuiSeVideDUnCoupRetardeAuPlusUnPeuEtNeRefuseRien() {
        // file d'attente qui grossit 2 s (30 -> 330 ms de retard), puis se vide d'un coup (envoi du reste de la maison fini)
        DoubleUnaryOperator retard = t -> t < 2000 ? 30 : t < 4000 ? 30 + (t - 2000) * 0.15 : 30;
        List<Jugement> j = jouer(honnete(0, 8000), retard);
        assertTrue(j.stream().noneMatch(x -> x.etaler() == CadenceTirs.REFUSE), "un tir honnête a été refusé");
        assertTrue(plusGrandEtalement(j) <= 200, () -> "retard trop grand : " + plusGrandEtalement(j));
    }

    @Test
    void rafaleEnvoyeeDUnCoupEstEtaleeALaCadenceDeLArme() {
        List<Paquet> p = honnete(0, 3000);
        // à 3 000 ms, le jeu modifié envoie 8 tirs d'un coup, datés de 100 en 100 ms (TaCZ les acceptait tous)
        for (int k = 0; k < 8; k++) {
            p.add(new Paquet(3000 + k * 0.01, true, 3000 + k * 100L));
        }
        for (double t = 3000; t < 5000; t += 50) {
            p.add(new Paquet(t, false, 0));
        }
        List<Jugement> j = jouer(p, t -> 40);
        List<Jugement> rafale = j.subList(j.size() - 8, j.size());
        long toutDeSuite = rafale.stream().filter(x -> x.etaler() == 0).count();
        assertTrue(toutDeSuite <= 2, () -> toutDeSuite + " tirs de la rafale partis tout de suite");
        // au plus 150 ms d'avance (la tolérance) : 3 tirs dans les 100 premières ms au lieu de 8 ; ensuite la cadence de
        // l'arme (100 ms) ; aucun tir perdu
        double premier = rafale.get(0).arrivee() + rafale.get(0).etaler();
        long debut = rafale.stream().filter(x -> x.arrivee() + x.etaler() - premier < 100).count();
        assertTrue(debut <= 3, () -> debut + " tirs de la rafale dans les 100 premières ms");
        double depart = 0;
        for (int k = 0; k < rafale.size(); k++) {
            Jugement x = rafale.get(k);
            assertTrue(x.etaler() >= 0, "tir de la rafale refusé au lieu d'être étalé");
            double part = x.arrivee() + x.etaler();
            if (k >= 3) {
                assertTrue(part - depart >= 99, "deux tirs étalés trop près : " + (part - depart));
            }
            depart = part;
        }
    }

    @Test
    void cadenceSoutenueDeuxFoisPlusRapideQueLArmeFinitRefusee() {
        List<Paquet> p = new ArrayList<>();
        for (int k = 0; k < 80; k++) {
            p.add(new Paquet(k * 50.0, true, k * 100L)); // un tir toutes les 50 ms, daté de 100 en 100
            p.add(new Paquet(k * 50.0 + 1, false, 0));
        }
        List<Jugement> j = jouer(p, t -> 40);
        assertTrue(j.stream().anyMatch(x -> x.etaler() == CadenceTirs.REFUSE), "jamais refusé");
        // ce qui part ne dépasse jamais la cadence de l'arme de plus que la tolérance
        List<Double> departs = new ArrayList<>();
        for (Jugement x : j) {
            if (x.etaler() >= 0) {
                departs.add(x.arrivee() + x.etaler());
            }
        }
        departs.sort(Double::compare);
        for (int a = 0; a < departs.size(); a++) {
            int dansUneSeconde = 0;
            for (int b = a; b < departs.size() && departs.get(b) - departs.get(a) < 1000; b++) {
                dansUneSeconde++;
            }
            assertTrue(dansUneSeconde <= 12, "trop de tirs en une seconde : " + dansUneSeconde);
        }
    }

    @Test
    void tirsToujoursDatesEnRetardNeDonnentPasDeRafale() {
        // le jeu modifié date tous ses tirs 300 ms dans le passé (TaCZ l'accepte), puis envoie sa rafale
        List<Paquet> p = new ArrayList<>();
        for (double t = 0; t < 3000; t += 50) {
            p.add(new Paquet(t, false, 0));
            if (Math.round(t) % 100 == 0) {
                p.add(new Paquet(t + 1, true, Math.round(t + 1 - 300)));
            }
        }
        for (int k = 0; k < 8; k++) {
            p.add(new Paquet(3000 + k * 0.01, true, 2700 + k * 100L));
        }
        List<Jugement> j = jouer(p, t -> 40);
        long toutDeSuite = j.subList(j.size() - 8, j.size()).stream().filter(x -> x.etaler() == 0).count();
        assertTrue(toutDeSuite <= 2, () -> toutDeSuite + " tirs de la rafale partis tout de suite");
    }

    @Test
    void horlogeRecaleeRepartDeZero() {
        CadenceTirs c = new CadenceTirs();
        assertEquals(0.0, c.tir(1000, 900, 0, TOLERANCE, FENETRE, ATTENTE_MAX));
        c.oublier();
        // nouvelle base de TaCZ : l'heure annoncée repart de presque zéro
        assertEquals(0.0, c.tir(1010, 5_000_000, 0, TOLERANCE, FENETRE, ATTENTE_MAX));
    }
}
