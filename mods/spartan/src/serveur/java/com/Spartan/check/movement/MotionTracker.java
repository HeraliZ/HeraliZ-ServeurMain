package com.Spartan.check.movement;

import com.Spartan.check.movement.model.HorizontalEnvelope;
import com.Spartan.check.movement.model.VerticalEnvelope;

/**
 * Enveloppes de mouvement d'un joueur, recalculées à chaque déplacement revendiqué :
 * vitesse horizontale maximale (vanilla, sprint, glace, sauts, reculs connus),
 * déplacement vertical maximal en l'air (gravité depuis le dernier appui), et
 * vitesse maximale en utilisant un objet (NoSlow).
 *
 * Après un mode où vanilla suit d'autres règles (eau, échelle, véhicule, élytre,
 * toile, plateforme…), les enveloppes repartent de ce qui a été observé.
 * Thread serveur.
 */
final class MotionTracker {
    /** Marge horizontale par tick sur un terrain accidenté (arêtes, marches en l'air, glissades). */
    private static final double ROUGH_EXTRA = 0.06;
    /** Marge de rebond au-delà de la dernière vitesse de chute (blocs/tick). */
    private static final double BOUNCE_MARGIN = 0.1;

    /**
     * Données du serveur pour un déplacement.
     *
     * @param groundFromExact appui exact (moins de 0,03 bloc) au départ : physique du sol
     * @param friction        frottement du bloc sous les pieds au départ
     * @param speed           vitesse de déplacement maximale récente (attribut, sprint supposé)
     * @param slowed          le joueur utilise un objet de façon certaine (ralentissement attendu)
     * @param impulseH        vitesse horizontale imposée par le serveur (remplace), 0 si aucune
     * @param pushH           vitesse horizontale ajoutée par le serveur (explosion), 0 si aucune
     * @param impulseUp       vitesse verticale imposée (remplace), 0 si aucune
     * @param pushUp          vitesse verticale ajoutée (explosion), 0 si aucune
     * @param crowd           marge pour la poussée des entités collées au joueur
     * @param bouncy          bloc rebondissant sous les pieds au départ (slime, lit)
     * @param ceiling         plafond collé à la tête au départ : un saut peut n'y laisser aucune trace verticale
     * @param rough           terrain accidenté autour des pieds (dalles, pots, meubles et décors 3D, entités
     *                        solides) : le sol affirmé par le client y est cru, voir MovementEnv.roughNear
     */
    record Inputs(boolean groundFromExact, double friction, double speed, boolean slowed, double impulseH,
                  double pushH, double impulseUp, double pushUp, double crowd, boolean bouncy, boolean ceiling,
                  boolean rough) {
    }

    /** Résultat d'un déplacement (une position ; plusieurs ticks seulement si le client n'a rien envoyé entre-temps). */
    record Result(boolean exempt, int ticks, double allowedH, double excessH, double allowedDy, double excessDy,
                  boolean slowed, double allowedSlow, double excessSlow, boolean usedImpulse, boolean rough) {
        static final Result EXEMPT = new Result(true, 1, 0, 0, 0, 0, false, 0, 0, false, false);
    }

    private final HorizontalEnvelope h = new HorizontalEnvelope();
    private final HorizontalEnvelope slow = new HorizontalEnvelope();
    private final VerticalEnvelope v = new VerticalEnvelope();
    private boolean tracking;
    private boolean slowTracking;
    private double lastAirDy;
    /** Dernière position : immobile au sol (le client n'envoie alors plus rien jusqu'à son premier mouvement). */
    private boolean wasStill;
    /** Le client se disait au sol à la position précédente. */
    private boolean lastClaimedGround;

    /** Repart de zéro (connexion, téléportation lointaine, renvoi). */
    void reset() {
        tracking = false;
        slowTracking = false;
        lastAirDy = 0;
        wasStill = false;
        lastClaimedGround = false;
    }

    /**
     * Déplacement dans un mode non modélisé : les enveloppes repartiront de ce qui a
     * été observé (vitesse actuelle, saut possible ensuite).
     */
    Result exempt(MoveSample s) {
        // Une position = un tick pour un client vanilla (un retard réseau ne change rien au déplacement).
        h.reset(s.horizontal());
        v.reset(Math.max(s.dy(), s.jumpVelocity()));
        tracking = true;
        slowTracking = false;
        lastAirDy = 0;
        wasStill = false;
        lastClaimedGround = s.claimedGround();
        return Result.EXEMPT;
    }

    Result step(MoveSample s, Inputs in) {
        double h1 = s.horizontal();
        double dy = s.dy();
        if (!tracking) {
            // Premier déplacement suivi : on part de ce qui est observé (aucune conclusion possible).
            h.reset(h1);
            v.reset(Math.max(dy, s.jumpVelocity()));
            tracking = true;
        }
        // Un client vanilla envoie une position à chaque tick où il bouge. Après une
        // immobilité au sol, la position suivante est donc son premier tick de mouvement ;
        // sinon un écart de temps réel est un retard réseau (un seul tick en réalité) ou,
        // rarement, des ticks sans envoi : on accepte le plus permissif.
        int ticks = wasStill ? 1 : Math.max(1, s.ticks());
        if (!in.slowed()) {
            slowTracking = false;
        } else if (!slowTracking) {
            slow.reset(h.velocity()); // l'élan d'avant l'utilisation retombe peu à peu
            slowTracking = true;
        }
        double bounce = 0;
        if (s.groundFrom() && in.bouncy() && lastAirDy < 0) {
            bounce = -lastAirDy + s.gravity() + BOUNCE_MARGIN;
        }
        // Saut possible (impulsion du sprint) : le joueur monte depuis le sol. Une montée d'un
        // seul tick que le client termine AU SOL est une marche (escalier, dalle), pas un saut :
        // vanilla ne pose pas le joueur pendant le tick où il saute.
        // Terrain accidenté : le sol affirmé par le client est cru (au départ comme à l'arrivée). Un tricheur
        // qui en abuse doit coller la géométrie ; en plein air, rien ne change.
        boolean rough = in.rough();
        boolean groundFrom = s.groundFrom() || (rough && lastClaimedGround);
        boolean groundTo = s.groundTo() || (rough && s.claimedGround());
        boolean step = ticks == 1 && groundTo && s.claimedGround();
        // Sur terrain accidenté, un saut en sprint qui finit sur une dalle ou un objet 3D (marche prise
        // pendant le saut) ressemble à une marche : l'impulsion du saut reste permise.
        boolean jump = groundFrom && ((dy > 0.01 && (!step || rough)) || (in.ceiling() && dy > -1e-6));
        double extra = in.crowd() + (rough ? ROUGH_EXTRA : 0);
        HorizontalEnvelope.Tick ht = new HorizontalEnvelope.Tick(in.groundFromExact(), rough, in.friction(),
                in.speed(), jump, false, in.impulseH(), in.pushH(), extra);
        VerticalEnvelope.Tick vt = new VerticalEnvelope.Tick(groundFrom, groundTo, s.gravity(),
                s.jumpVelocity(), s.stepHeight(), in.impulseUp(), in.pushUp(), bounce);
        // Ce déplacement a-t-il eu besoin de la vitesse imposée ? (elle ne sert qu'une fois)
        // Seulement si le déplacement a pris au moins la moitié de ce qu'elle ajoute : un pas à peine
        // au-dessus du normal (sprint, poussée) la « consommait » avant que le client l'applique vraiment
        // (fausses alertes Speed après le plongeon du mod tactique, un recul, une explosion).
        boolean usedImpulse = false;
        if (ticks == 1 && (in.impulseH() > 0 || in.pushH() > 0 || in.impulseUp() > 0 || in.pushUp() > 0)) {
            HorizontalEnvelope.Tick plainH = new HorizontalEnvelope.Tick(in.groundFromExact(), rough, in.friction(),
                    in.speed(), jump, false, 0, 0, extra);
            VerticalEnvelope.Tick plainV = new VerticalEnvelope.Tick(groundFrom, groundTo, s.gravity(),
                    s.jumpVelocity(), s.stepHeight(), 0, 0, bounce);
            double plainHAllowed = h.allowed(plainH);
            double plainVAllowed = v.allowed(plainV);
            double addH = h.allowed(ht) - plainHAllowed;
            double addV = v.allowed(vt) - plainVAllowed;
            usedImpulse = (addH > 1e-6 && h1 > plainHAllowed + addH * 0.5)
                    || (addV > 1e-6 && dy > plainVAllowed + addV * 0.5);
        }
        double allowedH = h.advance(ht, ticks, h1);
        double allowedSlow = 0;
        double excessSlow = 0;
        if (in.slowed()) {
            HorizontalEnvelope.Tick st = new HorizontalEnvelope.Tick(in.groundFromExact(), false, in.friction(),
                    in.speed(), jump, true, in.impulseH(), in.pushH(), in.crowd());
            allowedSlow = slow.advance(st, ticks, h1);
            excessSlow = Math.max(0, h1 - allowedSlow);
        }
        double allowedDy = v.advance(vt, ticks, dy);
        if (!groundTo) {
            lastAirDy = dy / ticks;
        } else if (!groundFrom) {
            // Atterrissage : on garde la dernière vitesse de chute complète pour un éventuel rebond.
        } else {
            lastAirDy = 0;
        }
        wasStill = s.still() && groundTo;
        lastClaimedGround = s.claimedGround();
        return new Result(false, ticks, allowedH, Math.max(0, h1 - allowedH), allowedDy, Math.max(0, dy - allowedDy),
                in.slowed(), allowedSlow, excessSlow, usedImpulse, rough);
    }

    double horizontalVelocity() {
        return h.velocity();
    }

    double verticalVelocity() {
        return v.velocity();
    }
}
