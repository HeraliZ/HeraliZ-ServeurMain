package com.Spartan.check.combat;

/**
 * « Visée forcée » (silent aim, 19/11), Java pur, testé (ViseeForceeTest).
 *
 * <p>Un aimbot discret ne bouge pas l'écran du tricheur : il envoie au serveur, juste avant le tir, une visée tournée vers
 * la cible, puis, au paquet suivant, la vraie visée de l'écran. Le serveur voit les paquets dans l'ordre exact où le jeu
 * les a envoyés : visée A (avant), visée B (juste avant le tir), TIR, visée C (juste après). Signe de la visée forcée :
 * <ul>
 *   <li>B s'écarte de A d'au moins {@code coup} degrés (le saut vers la cible) ;</li>
 *   <li>C s'écarte de B d'au moins {@code coup} degrés et revient à moins de {@code retour} degrés de A (la visée de
 *   l'écran, qui n'avait pas bougé) ;</li>
 *   <li>B arrive juste avant le tir et C juste après (un tick du jeu) ;</li>
 *   <li>et, vérifié à part (TirsTacz), B vise un joueur que ni A ni C ne visent.</li>
 * </ul>
 * Une main humaine peut faire un grand coup de souris vers une cible et tirer ; elle ne revient pas, au tick suivant, au
 * degré près à sa visée d'avant. Le recul de l'arme (quelques degrés vers le haut, après le tir) ne fait jamais un tel
 * aller-retour. Alerte seulement, jamais de tir annulé.
 */
final class ViseeForcee {
    private ViseeForcee() {
    }

    /** Écart (degrés) entre deux visées (lacet, tangage). */
    static double ecart(float lacetA, float tangageA, float lacetB, float tangageB) {
        double[] a = direction(lacetA, tangageA);
        double[] b = direction(lacetB, tangageB);
        double c = Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
        return Math.toDegrees(Math.acos(c));
    }

    /** Direction (vecteur unitaire) d'une visée de Minecraft (lacet 0 = sud, tangage positif = vers le bas). */
    static double[] direction(float lacet, float tangage) {
        double y = Math.toRadians(lacet);
        double p = Math.toRadians(tangage);
        return new double[] {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
    }

    /** Aller-retour de la visée autour du tir : A → B (saut), B → C (saut), C revenue près de A. */
    static boolean allerRetour(float lacetA, float tangageA, float lacetB, float tangageB, float lacetC, float tangageC,
                               double coup, double retour) {
        return ecart(lacetA, tangageA, lacetB, tangageB) >= coup && ecart(lacetB, tangageB, lacetC, tangageC) >= coup
                && ecart(lacetA, tangageA, lacetC, tangageC) <= retour;
    }

    /**
     * Écart (degrés) entre une visée partant de l'œil (ex, ey, ez) et une boîte : angle vers son milieu moins son rayon
     * apparent (0 si l'œil est dedans).
     */
    static double ecartBoite(double ex, double ey, double ez, float lacet, float tangage, double minX, double minY,
                             double minZ, double maxX, double maxY, double maxZ) {
        double cx = (minX + maxX) / 2 - ex;
        double cy = (minY + maxY) / 2 - ey;
        double cz = (minZ + maxZ) / 2 - ez;
        double d = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (d < 1e-6) {
            return 0;
        }
        double[] v = direction(lacet, tangage);
        double c = Math.max(-1, Math.min(1, (v[0] * cx + v[1] * cy + v[2] * cz) / d));
        double rayon = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) / 2;
        double apparent = Math.toDegrees(Math.atan(rayon / d));
        return Math.max(0, Math.toDegrees(Math.acos(c)) - apparent);
    }
}
