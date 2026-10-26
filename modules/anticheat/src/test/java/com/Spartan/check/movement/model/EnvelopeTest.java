package com.Spartan.check.movement.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Les enveloppes de mouvement contre la physique vanilla simulée : un client honnête
 * reste TOUJOURS dedans (au millième près), les triches en sortent.
 */
class EnvelopeTest {
    private static final double EPS = 1e-6;
    private static final double SPRINT = 0.13;

    /** Rejoue un client et mesure le plus grand dépassement (horizontal, vertical). */
    private static final class Judge {
        final HorizontalEnvelope h = new HorizontalEnvelope();
        final VerticalEnvelope v = new VerticalEnvelope();
        final HorizontalEnvelope slow = new HorizontalEnvelope();
        double friction = 0.6;
        double jumpVelocity = 0.42;
        double maxExcessH;
        double maxExcessV;
        double maxExcessSlow;
        double sumExcessV;
        double sumExcessH;
        double impulseH;
        double impulseUp;
        boolean additive;
        double bounce;

        void feed(VanillaWalker.Step s, boolean usingItem) {
            feed(s, usingItem, 1);
        }

        /** Une position qui couvre peut-être plusieurs ticks (paquet retardé, ticks sans envoi). */
        void feed(VanillaWalker.Step s, boolean usingItem, int ticks) {
            boolean jump = ticks > 1 ? s.groundStart() && s.dy() > 0.05
                    : s.groundStart() && s.dy() > 0.15 && s.dy() < jumpVelocity + 0.05;
            HorizontalEnvelope.Tick ht = new HorizontalEnvelope.Tick(s.groundStart(), false, friction, SPRINT, jump,
                    false, additive ? 0 : impulseH, additive ? impulseH : 0, 0);
            double allowedH = h.advance(ht, ticks, s.horizontal());
            maxExcessH = Math.max(maxExcessH, s.horizontal() - allowedH);
            sumExcessH += Math.max(0, s.horizontal() - allowedH);
            if (usingItem) {
                HorizontalEnvelope.Tick st = new HorizontalEnvelope.Tick(s.groundStart(), false, friction, SPRINT, jump,
                        true, additive ? 0 : impulseH, additive ? impulseH : 0, 0);
                double allowedSlow = slow.advance(st, ticks, s.horizontal());
                maxExcessSlow = Math.max(maxExcessSlow, s.horizontal() - allowedSlow);
            } else {
                slow.reset(h.velocity());
            }
            VerticalEnvelope.Tick vt = new VerticalEnvelope.Tick(s.groundStart(), s.groundEnd(), 0.08, jumpVelocity,
                    0.6, !additive && (impulseH != 0 || impulseUp != 0) ? impulseUp : Double.NaN, additive ? impulseUp : 0, bounce);
            double allowedV = v.advance(vt, ticks, s.dy());
            maxExcessV = Math.max(maxExcessV, s.dy() - allowedV);
            sumExcessV += Math.max(0, s.dy() - allowedV);
            impulseH = 0;
            impulseUp = 0;
            additive = false;
            bounce = 0;
        }
    }

    @Test
    void sprintingAndSprintJumpingStayInside() {
        VanillaWalker w = new VanillaWalker();
        Judge j = new Judge();
        for (int i = 0; i < 100; i++) {
            j.feed(w.tick(1, 0, true, false, false), false);
        }
        for (int i = 0; i < 400; i++) {
            w.yaw += (i % 50 == 0) ? 0.7 : 0.01; // virages
            j.feed(w.tick(1, i % 30 < 15 ? 1 : 0, true, true, false), false);
        }
        assertTrue(j.maxExcessH < EPS, "horizontal " + j.maxExcessH);
        assertTrue(j.maxExcessV < EPS, "vertical " + j.maxExcessV);
    }

    @Test
    void iceBunnyHopUnderACeilingStaysInside() {
        // La façon légitime la plus rapide d'avancer à pied : glace + plafond bas.
        VanillaWalker w = new VanillaWalker();
        w.friction = 0.98;
        w.ceiling = 2.3;
        Judge j = new Judge();
        j.friction = 0.98;
        double fastest = 0;
        for (int i = 0; i < 400; i++) {
            VanillaWalker.Step s = w.tick(1, 0, true, true, false);
            fastest = Math.max(fastest, s.horizontal());
            j.feed(s, false);
        }
        assertTrue(fastest > 0.5, "la simulation doit vraiment aller vite : " + fastest);
        assertTrue(j.maxExcessH < EPS, "horizontal " + j.maxExcessH);
        assertTrue(j.maxExcessV < EPS, "vertical " + j.maxExcessV);
    }

    @Test
    void blueIceStaysInside() {
        VanillaWalker w = new VanillaWalker();
        w.friction = 0.989;
        Judge j = new Judge();
        j.friction = 0.989;
        double fastest = 0;
        for (int i = 0; i < 600; i++) {
            VanillaWalker.Step s = w.tick(1, 0, true, i % 2 == 0, false);
            fastest = Math.max(fastest, s.horizontal());
            j.feed(s, false);
        }
        assertTrue(fastest > 0.5, "glace bleue : " + fastest);
        assertTrue(j.maxExcessH < EPS, "horizontal " + j.maxExcessH);
        assertTrue(j.maxExcessV < EPS, "vertical " + j.maxExcessV);
    }

    @Test
    void fallsJumpBoostAndSlimeBouncesStayInside() {
        VanillaWalker w = new VanillaWalker();
        w.y = 40;
        w.onGround = false;
        Judge j = new Judge();
        j.v.reset(0);
        for (int i = 0; i < 80; i++) {
            j.feed(w.tick(1, 0, false, false, false), false);
        }
        assertTrue(w.onGround, "posé");
        // Saut amélioré II.
        w.jumpVelocity = 0.62;
        j.jumpVelocity = 0.62;
        for (int i = 0; i < 100; i++) {
            j.feed(w.tick(1, 0, true, true, false), false);
        }
        // Chute sur un bloc de slime : rebonds.
        w.jumpVelocity = 0.42;
        j.jumpVelocity = 0.42;
        w.slimeFloor = true;
        w.y = 10;
        w.onGround = false;
        w.vy = 0;
        j.v.reset(0);
        double lastFall = 0;
        for (int i = 0; i < 200; i++) {
            VanillaWalker.Step s = w.tick(0, 0, false, false, false);
            // Rebond : au plus la vitesse de chute du dernier tick complet avant l'impact.
            j.bounce = s.groundStart() ? Math.abs(lastFall) + 0.08 : 0;
            if (!s.groundEnd()) {
                lastFall = Math.min(0, s.dy());
            }
            j.feed(s, false);
        }
        assertTrue(j.maxExcessH < EPS, "horizontal " + j.maxExcessH);
        assertTrue(j.maxExcessV < EPS, "vertical " + j.maxExcessV);
    }

    @Test
    void knockbackAndExplosionsAreIntegratedNotExempted() {
        VanillaWalker w = new VanillaWalker();
        Judge j = new Judge();
        for (int i = 0; i < 300; i++) {
            if (i % 40 == 10) {
                // Recul : le serveur impose la vitesse (remplace).
                w.vx = 0.4;
                w.vz = -0.2;
                w.vy = 0.4;
                w.onGround = false;
                j.impulseH = Math.hypot(0.4, 0.2);
                j.impulseUp = 0.4;
            } else if (i % 40 == 30) {
                // Explosion : la vitesse s'ajoute.
                w.vx += 0.9;
                w.vy += 0.5;
                j.impulseH = 0.9;
                j.impulseUp = 0.5;
                j.additive = true;
            }
            j.feed(w.tick(1, 0, true, i % 3 == 0, false), false);
        }
        assertTrue(j.maxExcessH < EPS, "horizontal " + j.maxExcessH);
        assertTrue(j.maxExcessV < EPS, "vertical " + j.maxExcessV);
    }

    @Test
    void diveInMidAirResetsFallSpeed() {
        // Plongeon du mod tactique en pleine chute : le serveur remplace la vitesse, verticale à 0.
        VanillaWalker w = new VanillaWalker();
        Judge j = new Judge();
        for (int i = 0; i < 20; i++) {
            j.feed(w.tick(1, 0, true, false, false), false);
        }
        w.y += 7; // bord d'un toit de 7 blocs
        w.onGround = false;
        w.vy = 0;
        for (int i = 0; i < 60; i++) {
            if (i == 8) {
                w.vx = 0.9;
                w.vz = 0;
                w.vy = 0;
                j.impulseH = 0.9;
                j.impulseUp = 0;
            }
            j.feed(w.tick(1, 0, true, false, false), false);
        }
        assertTrue(j.maxExcessH < EPS, "horizontal " + j.maxExcessH);
        assertTrue(j.sumExcessV < EPS, "vertical " + j.sumExcessV);
    }

    @Test
    void eatingWhileWalkingOrBunnyHoppingStaysInsideTheSlowEnvelope() {
        VanillaWalker w = new VanillaWalker();
        Judge j = new Judge();
        for (int i = 0; i < 40; i++) {
            j.feed(w.tick(1, 0, true, false, false), false);
        }
        for (int i = 0; i < 64; i++) { // manger en sprint (la vitesse acquise retombe)
            j.feed(w.tick(1, 0, true, false, true), true);
        }
        for (int i = 0; i < 64; i++) { // manger en sautant
            j.feed(w.tick(1, 0, true, true, true), true);
        }
        assertTrue(j.maxExcessSlow < EPS, "lent " + j.maxExcessSlow);
    }

    /** Plusieurs ticks vanilla envoyés en une seule position (client qui n'a rien envoyé entre-temps). */
    private static VanillaWalker.Step merge(java.util.List<VanillaWalker.Step> steps) {
        double dx = 0;
        double dy = 0;
        double dz = 0;
        for (VanillaWalker.Step s : steps) {
            dx += s.dx();
            dy += s.dy();
            dz += s.dz();
        }
        return new VanillaWalker.Step(dx, dy, dz, steps.get(0).groundStart(), steps.get(steps.size() - 1).groundEnd());
    }

    @Test
    void delayedPacketsKeepTheirMomentum() {
        // Gigue réseau : chaque position ne couvre qu'un tick, mais arrive parfois en retard
        // (le serveur y voit jusqu'à 5 ticks de temps réel). Aucun élan ne doit être perdu.
        java.util.Random rnd = new java.util.Random(42);
        VanillaWalker w = new VanillaWalker();
        Judge j = new Judge();
        for (int i = 0; i < 800; i++) {
            w.yaw += (i % 60 == 0) ? 0.8 : 0.01;
            boolean jump = i >= 100 && i < 500;
            VanillaWalker.Step s = w.tick(1, 0, true, jump, false);
            j.feed(s, false, rnd.nextInt(10) < 3 ? 2 + rnd.nextInt(4) : 1);
            if (i == 600) { // chute de 30 blocs, toujours avec des retards
                w.floor = -30;
            }
        }
        assertTrue(w.y < -29, "la chute a eu lieu : " + w.y);
        assertTrue(j.maxExcessH < EPS, "horizontal " + j.maxExcessH);
        assertTrue(j.maxExcessV < EPS, "vertical " + j.maxExcessV);
    }

    @Test
    void ticksSentAsOnePositionAreAccepted() {
        // Plusieurs vrais ticks dans une seule position (caméra détachée par un mod…) :
        // sprint, sauts espacés, longue chute.
        VanillaWalker w = new VanillaWalker();
        Judge j = new Judge();
        java.util.List<VanillaWalker.Step> group = new java.util.ArrayList<>();
        int size = 2;
        for (int i = 0; i < 900; i++) {
            if (i == 500) {
                w.floor = -40;
            }
            group.add(w.tick(1, 0, true, i < 450 && i % 30 == 0, false));
            if (group.size() == size) {
                j.feed(merge(group), false, size);
                group.clear();
                size = 2 + (i % 4);
            }
        }
        assertTrue(w.y < -39, "la chute a eu lieu : " + w.y);
        assertTrue(j.maxExcessH < EPS, "horizontal " + j.maxExcessH);
        assertTrue(j.maxExcessV < EPS, "vertical " + j.maxExcessV);
    }

    @Test
    void fallingAfterStandingStillIsNotJudgedAsAFastFall() {
        // Immobile au sol (une position par seconde), puis le bloc disparaît : la première
        // position de chute arrive longtemps après la précédente.
        VanillaWalker w = new VanillaWalker();
        Judge j = new Judge();
        for (int i = 0; i < 5; i++) {
            j.feed(new VanillaWalker.Step(0, 0, 0, true, true), false, 5);
        }
        w.floor = -12;
        j.feed(w.tick(0, 0, false, false, false), false, 5);
        for (int i = 0; i < 40; i++) {
            j.feed(w.tick(0, 0, false, false, false), false);
        }
        assertTrue(w.onGround && w.y == -12, "posé en bas : " + w.y);
        assertTrue(j.sumExcessV < EPS, "vertical " + j.sumExcessV);
    }

    // ------------------------------------------------------------------ triches

    @Test
    void speedHackLeavesTheEnvelope() {
        VanillaWalker w = new VanillaWalker();
        Judge j = new Judge();
        for (int i = 0; i < 60; i++) {
            VanillaWalker.Step s = w.tick(1, 0, true, false, false);
            j.feed(new VanillaWalker.Step(s.dx() * 1.3, s.dy(), s.dz() * 1.3, s.groundStart(), s.groundEnd()), false);
        }
        assertTrue(j.sumExcessH > 2.0, "speed x1.3 : " + j.sumExcessH);
    }

    @Test
    void networkDelaysDoNotHideCheats() {
        java.util.Random rnd = new java.util.Random(7);
        VanillaWalker w = new VanillaWalker();
        Judge speed = new Judge();
        for (int i = 0; i < 60; i++) {
            VanillaWalker.Step s = w.tick(1, 0, true, false, false);
            speed.feed(new VanillaWalker.Step(s.dx() * 1.3, s.dy(), s.dz() * 1.3, s.groundStart(), s.groundEnd()),
                    false, rnd.nextInt(10) < 3 ? 2 + rnd.nextInt(4) : 1);
        }
        assertTrue(speed.sumExcessH > 1.2, "speed x1.3 avec retards : " + speed.sumExcessH);
        Judge glide = new Judge();
        glide.feed(new VanillaWalker.Step(0.2, -0.0784, 0, true, false), false);
        for (int i = 0; i < 20; i++) {
            glide.feed(new VanillaWalker.Step(0.2, -0.05, 0, false, false), false, 1 + rnd.nextInt(5));
        }
        assertTrue(glide.sumExcessV > 1.0, "planer avec retards : " + glide.sumExcessV);
        Judge hover = new Judge();
        hover.feed(new VanillaWalker.Step(0, 0.42, 0, true, false), false);
        for (int i = 0; i < 20; i++) {
            hover.feed(new VanillaWalker.Step(0, 0, 0, false, false), false, 1 + rnd.nextInt(5));
        }
        assertTrue(hover.sumExcessV > 1.0, "vol stationnaire avec retards : " + hover.sumExcessV);
    }

    @Test
    void progressiveBunnyHopCannotBankMomentum() {
        // Le tricheur dépasse l'autorisé de 0,03 bloc à chaque tick : sans enveloppe
        // bornée, l'élan s'accumulerait jusqu'à ~20 blocs/s.
        VanillaWalker w = new VanillaWalker();
        Judge j = new Judge();
        double sum = 0;
        for (int i = 0; i < 200; i++) {
            VanillaWalker.Step s = w.tick(1, 0, true, true, false);
            boolean jump = s.groundStart() && s.dy() > 0.15;
            double allowed = j.h.allowed(new HorizontalEnvelope.Tick(s.groundStart(), false, 0.6, SPRINT, jump,
                    false, 0, 0, 0));
            double cheat = allowed + 0.03;
            sum += cheat;
            j.feed(new VanillaWalker.Step(cheat, s.dy(), 0, s.groundStart(), s.groundEnd()), false);
        }
        assertTrue(sum / 200 * 20 < 9.0, "vitesse moyenne obtenue : " + sum / 200 * 20 + " blocs/s");
        assertTrue(j.sumExcessH >= 200 * 0.03 - 1e-6);
    }

    @Test
    void glideHoverAirJumpAndNoSlowLeaveTheirEnvelopes() {
        // Planer à -0,05 bloc/tick après avoir quitté un rebord.
        Judge glide = new Judge();
        glide.feed(new VanillaWalker.Step(0.2, -0.0784, 0, true, false), false);
        for (int i = 0; i < 20; i++) {
            glide.feed(new VanillaWalker.Step(0.2, -0.05, 0, false, false), false);
        }
        assertTrue(glide.sumExcessV > 1.0, "planer : " + glide.sumExcessV);
        // Vol stationnaire.
        Judge hover = new Judge();
        hover.feed(new VanillaWalker.Step(0, 0.42, 0, true, false), false);
        for (int i = 0; i < 20; i++) {
            hover.feed(new VanillaWalker.Step(0, 0, 0, false, false), false);
        }
        assertTrue(hover.sumExcessV > 1.0, "vol stationnaire : " + hover.sumExcessV);
        // Re-saut en plein air.
        VanillaWalker w = new VanillaWalker();
        Judge air = new Judge();
        for (int i = 0; i < 8; i++) {
            air.feed(w.tick(1, 0, true, i == 0, false), false);
        }
        air.feed(new VanillaWalker.Step(0.3, 0.42, 0, false, false), false);
        assertTrue(air.maxExcessV > 0.3, "saut en l'air : " + air.maxExcessV);
        // Manger à pleine vitesse (NoSlow).
        VanillaWalker run = new VanillaWalker();
        Judge noslow = new Judge();
        for (int i = 0; i < 40; i++) {
            noslow.feed(run.tick(1, 0, true, false, false), false);
        }
        for (int i = 0; i < 32; i++) {
            noslow.feed(run.tick(1, 0, true, false, false), true); // le client ne se ralentit pas
        }
        assertTrue(noslow.maxExcessSlow > 0.15, "NoSlow : " + noslow.maxExcessSlow);
    }
}
