package com.Spartan.check.movement.model;

/**
 * Enveloppe verticale d'un joueur vanilla 1.20.1 en l'air (Java pur, testé).
 *
 * En l'air, vanilla fait à chaque tick : déplacement vertical = vitesse, puis
 * vitesse suivante = (vitesse − gravité) × 0,98. Le déplacement vertical d'un
 * tick ne peut donc JAMAIS dépasser la vitesse maximale prédite depuis le dernier
 * appui (saut maximal au décollage). Planer, voler, re-sauter en l'air, monter un
 * mur, rester au-dessus de l'eau : tout cela dépasse l'enveloppe en quelques ticks.
 *
 * Comme pour l'horizontale, la vitesse suivante part du déplacement BORNÉ (pas
 * d'élan accumulé), et les vitesses imposées par le serveur (recul, explosion)
 * sont intégrées avec leur valeur exacte.
 */
public final class VerticalEnvelope {
    public static final double DRAG = Physics.DRAG;

    /**
     * Ce que l'on sait d'un tick.
     *
     * @param groundStart  appui au début du tick (sol réel, plateforme) : un saut est possible
     * @param groundEnd    appui à la fin du tick : une marche (escalier, rebord) est possible
     * @param gravity      gravité du joueur (attribut forge:entity_gravity)
     * @param jumpVelocity vitesse d'un saut (Saut amélioré compris)
     * @param stepHeight   hauteur de marche du joueur
     * @param impulseUp    vitesse verticale imposée par le serveur (recul, élan d'un mod : remplace la vitesse, même vers le bas ou à 0 ; NaN = aucune)
     * @param pushUp       vitesse verticale ajoutée par le serveur (explosion ; 0 = aucune)
     * @param bounce       rebond possible (bloc de slime, lit) : vitesse de rebond maximale (0 = aucun)
     */
    public record Tick(boolean groundStart, boolean groundEnd, double gravity, double jumpVelocity,
                       double stepHeight, double impulseUp, double pushUp, double bounce) {
    }

    private double vy;

    /** Vitesse verticale maximale possible au début du prochain tick. */
    public double velocity() {
        return vy;
    }

    /** Repart d'un état connu (sortie de l'eau, d'une échelle, téléportation…). */
    public void reset(double velocity) {
        vy = velocity;
    }

    /** Vitesse verticale maximale utilisable par le déplacement de ce tick (hors marche). */
    private double base(Tick t) {
        double base = t.groundStart() ? Math.max(vy, t.jumpVelocity()) : vy;
        if (base > -0.003) {
            base = Math.max(base, 0); // vanilla annule les vitesses de moins de 0,003 (LivingEntity.aiStep)
        }
        if (!Double.isNaN(t.impulseUp())) {
            base = Math.max(base, t.impulseUp());
        }
        base += Math.max(0, t.pushUp());
        return t.bounce() > 0 ? Math.max(base, t.bounce()) : base;
    }

    /** Déplacement vertical maximal de ce tick (marche comprise si le tick finit sur un appui). */
    public double allowed(Tick t) {
        double base = base(t);
        return t.groundEnd() ? Math.max(base, t.stepHeight()) : base;
    }

    /**
     * Fin du tick.
     * @return le déplacement vertical autorisé pour ce tick
     */
    public double advance(Tick t, double claimed) {
        return advance(t, 1, claimed);
    }

    /**
     * Fin d'une position qui couvre peut-être plusieurs ticks (voir HorizontalEnvelope) :
     * autorisé = le plus haut point de la trajectoire maximale sur 1 à {@code ticks} ticks.
     *
     * Vitesse suivante : la plus haute compatible avec le déplacement revendiqué. Sur un
     * tick, c'est ce déplacement (borné). Sur k ticks de chute libre, c'est la vitesse
     * finale du seul chemin de k ticks qui couvre ce déplacement, s'il part d'une vitesse
     * possible. Un paquet qui couvre plusieurs ticks de chute ne fait donc jamais
     * « attendre » au joueur une chute plus rapide que la réalité.
     *
     * @return le déplacement vertical autorisé
     */
    public double advance(Tick t, int ticks, double claimed) {
        int n = Math.max(1, ticks);
        double g = t.gravity();
        double base = base(t);
        double allowed = Double.NEGATIVE_INFINITY;
        double u = base;
        double sum = 0;
        for (int k = 1; k <= n; k++) {
            sum += u;
            allowed = Math.max(allowed, sum);
            u = (u - g) * DRAG;
        }
        if (t.groundEnd()) {
            vy = 0; // posé : le prochain tick part du sol
            return Math.max(allowed, t.stepHeight());
        }
        double best = (Math.min(claimed, base) - g) * DRAG;
        // Chemin de k ticks partant de v0 : déplacement = v0 × a + zSum, vitesse finale = v0 × d^k + z(k).
        double a = 1;
        double pow = 1;
        double z = 0;
        double zSum = 0;
        for (int k = 2; k <= n; k++) {
            z = (z - g) * DRAG;
            pow *= DRAG;
            a += pow;
            zSum += z;
            double v0 = (claimed - zSum) / a;
            if (v0 <= base + 1e-9) {
                best = Math.max(best, v0 * pow * DRAG + (z - g) * DRAG);
            }
        }
        vy = best;
        return allowed;
    }
}
