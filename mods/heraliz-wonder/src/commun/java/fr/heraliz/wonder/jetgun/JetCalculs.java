package fr.heraliz.wonder.jetgun;

/**
 * Calculs purs du Jet Gun (aucune dépendance à Minecraft : testés hors jeu par {@code tools/wonder/jetgun.py tests}).
 */
public final class JetCalculs {
    private JetCalculs() {
    }

    /**
     * Le point (dx, dy, dz) relatif à la bouche est-il dans le cône d'axe unitaire (ax, ay, az) ? Rayon {@code r0} à la
     * bouche, ouverture {@code demiAngle} degrés, longueur {@code portee}, {@code marge} = demi-largeur de la cible. Rend la
     * distance le long de l'axe (>= 0), -1 dehors.
     */
    public static double dansCone(double dx, double dy, double dz, double ax, double ay, double az, double portee, double demiAngle, double r0,
                                  double marge) {
        double s = dx * ax + dy * ay + dz * az;
        if (s < -marge - 0.5 || s > portee + marge) return -1;
        double px = dx - ax * s, py = dy - ay * s, pz = dz - az * s;
        double perp = Math.sqrt(px * px + py * py + pz * pz);
        double rayon = r0 + Math.max(0, s) * Math.tan(Math.toRadians(demiAngle));
        return perp <= rayon + marge ? Math.max(0, s) : -1;
    }

    /**
     * Vitesse d'aspiration (blocs par tick) à la distance {@code s} de la bouche : pleine force près de la bouche, 45 % au
     * bout du cône, réduite par la résistance au recul (0..1 ; un boss à 1 garde 15 %).
     */
    public static double vitesse(double s, double portee, double force, double resistance) {
        double u = Math.max(0, Math.min(1, s / Math.max(portee, 1e-6)));
        double r = Math.max(0, Math.min(1, resistance));
        return force * (0.45 + 0.55 * (1 - u) * (1 - u)) * (1 - 0.85 * r);
    }

    /** La créature (distance {@code s} le long de l'axe, {@code perp} de l'axe) est-elle dans la bouche (déchiquetée) ? */
    public static boolean dansBouche(double s, double perp, double distanceBroyage, double r0) {
        return s <= distanceBroyage && perp <= r0 + 0.9;
    }

    /**
     * Dégâts d'un passage dans la bouche : hors partie de zombies {@code hors} ; en partie, une créature d'au plus
     * {@code seuilPvMax} PV max meurt (dégâts largement au-dessus de ses PV, comme le Thundergun), un boss prend
     * {@code colosse} (x1,25 / x1,5 / x1,75 au Pack-a-Punch).
     */
    public static float degats(boolean zombies, float pvMax, float pv, double seuilPvMax, float hors, float colosse, int pap) {
        float k = 1 + 0.25f * Math.max(0, pap);
        if (!zombies) return hors * k;
        if (pvMax <= seuilPvMax) return Math.max(pvMax, pv) * 4f + 100f;
        return colosse * k;
    }

    /** Facteur de chaleur par tir au Pack-a-Punch (même formule que le script Lua) : 1 ; 0,72 ; 0,64 ; 0,56. */
    public static float facteurChaleur(int pap) {
        return pap <= 0 ? 1f : Math.max(0.45f, 0.80f - 0.08f * pap);
    }

    /** Rayon du cône à la distance s. */
    public static double rayon(double s, double r0, double demiAngle) {
        return r0 + Math.max(0, s) * Math.tan(Math.toRadians(demiAngle));
    }
}
