package com.Spartan.check.movement.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * La borne d'horloge contre un client vanilla simulé image par image (Minecraft.runTick :
 * réponses aux pings, puis au plus 10 ticks), avec un réseau qui retarde et gèle sans
 * jamais réordonner. Un client honnête reste sous la marge ; un jeu accéléré la dépasse.
 */
class ClockBoundTest {
    private static final long MS = 1_000_000L;

    /** Paramètres d'une simulation. */
    private static final class Net {
        double speed = 1.0;           // cadence de jeu du client (1 = vanilla)
        long frameMs = 16;            // durée d'une image
        long seconds = 60;
        long downMs = 40;             // latence serveur -> client
        long upMs = 40;               // latence client -> serveur
        long jitterMs = 0;            // gigue aléatoire de chaque sens
        long upFreezeEveryMs = 0;     // gel du sens montant (puis rafale)
        long upFreezeMs = 0;
        long clientFreezeEveryMs = 0; // gel du client (GC, chargement)
        long clientFreezeMs = 0;
        long stillUntilMs = 0;        // immobile (aucun paquet de mouvement) jusqu'à cet instant
        long seed = 1;
    }

    /** Événement reçu par le serveur, dans l'ordre d'arrivée. */
    private record Arrival(long nanos, boolean pong, long pingSentNanos) {
    }

    /** @return l'avance maximale observée (ticks) */
    private static double run(Net n) {
        Random rnd = new Random(n.seed);
        long end = n.seconds * 1000 * MS;
        // Pings du serveur : un par tick.
        List<long[]> pings = new ArrayList<>(); // {envoi, réception client}
        long lastDown = 0;
        for (long t = 0; t < end; t += 50 * MS) {
            long recv = t + (n.downMs + (n.jitterMs > 0 ? rnd.nextInt((int) n.jitterMs) : 0)) * MS;
            recv = Math.max(recv, lastDown);
            lastDown = recv;
            pings.add(new long[] {t, recv});
        }
        // Client : images, pongs puis ticks.
        List<Arrival> arrivals = new ArrayList<>();
        long lastUp = 0;
        int nextPing = 0;
        double partial = 0;
        long lastFrame = 0;
        long frame = 0;
        while (frame < end) {
            long dt = n.frameMs * MS;
            if (n.clientFreezeEveryMs > 0 && frame > 0 && (frame / MS) % n.clientFreezeEveryMs < n.frameMs) {
                dt += n.clientFreezeMs * MS;
            }
            frame += dt;
            // Réponses à tous les pings reçus.
            while (nextPing < pings.size() && pings.get(nextPing)[1] <= frame) {
                lastUp = send(n, rnd, frame, lastUp);
                arrivals.add(new Arrival(lastUp, true, pings.get(nextPing)[0]));
                nextPing++;
            }
            // Ticks (Timer vanilla : au plus 10 par image, le reste est perdu).
            partial += (frame - lastFrame) / (50.0 * MS) * n.speed;
            lastFrame = frame;
            int ticks = (int) partial;
            partial -= ticks;
            for (int i = 0; i < Math.min(10, ticks); i++) {
                if (frame >= n.stillUntilMs * MS) {
                    lastUp = send(n, rnd, frame, lastUp);
                    arrivals.add(new Arrival(lastUp, false, 0));
                }
            }
        }
        ClockBound c = new ClockBound();
        double max = Double.NEGATIVE_INFINITY;
        for (Arrival a : arrivals) {
            if (a.pong()) {
                c.onPong(a.pingSentNanos());
            } else {
                max = Math.max(max, c.onPacket(a.nanos()));
            }
        }
        return max;
    }

    /** Envoi client -> serveur : latence, gigue, gels du sens montant ; jamais de réordonnancement. */
    private static long send(Net n, Random rnd, long at, long lastUp) {
        long arrive = at + (n.upMs + (n.jitterMs > 0 ? rnd.nextInt((int) n.jitterMs) : 0)) * MS;
        if (n.upFreezeEveryMs > 0) {
            long period = n.upFreezeEveryMs * MS;
            long phase = at % period;
            if (phase < n.upFreezeMs * MS) {
                arrive = Math.max(arrive, at - phase + n.upFreezeMs * MS);
            }
        }
        return Math.max(arrive, lastUp);
    }

    @Test
    void honestClientsStayUnderTheSlack() {
        Net calm = new Net();
        assertTrue(run(calm) < ClockBound.SLACK_TICKS, "réseau calme");

        Net jitter = new Net();
        jitter.jitterMs = 150;
        jitter.downMs = 120;
        jitter.upMs = 120;
        assertTrue(run(jitter) < ClockBound.SLACK_TICKS, "gigue 150 ms");

        Net upFreeze = new Net();
        upFreeze.upFreezeEveryMs = 2500;
        upFreeze.upFreezeMs = 900;
        assertTrue(run(upFreeze) < ClockBound.SLACK_TICKS, "gels montants de 0,9 s");

        Net clientFreeze = new Net();
        clientFreeze.clientFreezeEveryMs = 3000;
        clientFreeze.clientFreezeMs = 1500;
        assertTrue(run(clientFreeze) < ClockBound.SLACK_TICKS, "client figé 1,5 s puis rattrapage");

        Net slowFrames = new Net();
        slowFrames.frameMs = 180; // 5 images par seconde : plusieurs ticks par image
        slowFrames.jitterMs = 60;
        assertTrue(run(slowFrames) < ClockBound.SLACK_TICKS, "5 images/s");

        Net everything = new Net();
        everything.frameMs = 33;
        everything.jitterMs = 120;
        everything.upFreezeEveryMs = 3100;
        everything.upFreezeMs = 700;
        everything.clientFreezeEveryMs = 4700;
        everything.clientFreezeMs = 800;
        everything.seed = 9;
        assertTrue(run(everything) < ClockBound.SLACK_TICKS, "tout à la fois");
    }

    @Test
    void acceleratedGamesAreCaught() {
        Net x2 = new Net();
        x2.speed = 2.0;
        x2.seconds = 1;
        assertTrue(run(x2) > ClockBound.SLACK_TICKS, "×2 en moins d'une seconde");

        Net x105 = new Net();
        x105.speed = 1.05;
        x105.seconds = 20;
        x105.jitterMs = 80;
        assertTrue(run(x105) > ClockBound.SLACK_TICKS, "×1,05 en 20 s");

        // Rester immobile ne donne aucun crédit à dépenser ensuite.
        Net saved = new Net();
        saved.stillUntilMs = 10_000;
        saved.speed = 1.6;
        saved.seconds = 12;
        assertTrue(run(saved) > ClockBound.SLACK_TICKS, "×1,6 après 10 s d'immobilité, en 2 s");
    }
}
