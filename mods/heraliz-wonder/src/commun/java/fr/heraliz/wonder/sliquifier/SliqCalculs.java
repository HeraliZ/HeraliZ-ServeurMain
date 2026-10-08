package fr.heraliz.wonder.sliquifier;

/**
 * Calculs purs du Sliquifier (aucune classe de Minecraft : testables hors du jeu, voir
 * {@code tools/wonder/tests_sliquifier.py}) : variantes Pack-a-Punch des flaques, vol de la boule de gelee (le client
 * refait exactement le meme trajet), test d'une flaque sous les pieds, glissade, degats.
 */
public final class SliqCalculs {
    private SliqCalculs() {
    }

    // ------------------------------------------------------------------ boule de gelee
    /** Frottement de l'air par tick (la boule ralentit un peu). */
    public static final double FROTTEMENT = 0.992;

    /** Un pas de vol (un tick) : {@code e = {x, y, z, vx, vy, vz}} mis a jour sur place (meme calcul chez le client). */
    public static void pas(double[] e, double gravite) {
        e[0] += e[3];
        e[1] += e[4];
        e[2] += e[5];
        e[3] *= FROTTEMENT;
        e[4] = e[4] * FROTTEMENT - gravite;
        e[5] *= FROTTEMENT;
    }

    /** Position au tick {@code n} (fractionnaire) d'une boule partie de {@code o} a la vitesse {@code v} : rend {x, y, z} dans {@code out}. */
    public static void positionA(double ox, double oy, double oz, double vx, double vy, double vz, double gravite, float n, double[] out) {
        double x = ox, y = oy, z = oz;
        int k = (int) Math.floor(n);
        for (int i = 0; i < k; i++) {
            x += vx;
            y += vy;
            z += vz;
            vx *= FROTTEMENT;
            vy = vy * FROTTEMENT - gravite;
            vz *= FROTTEMENT;
        }
        float f = n - k;
        out[0] = x + vx * f;
        out[1] = y + vy * f;
        out[2] = z + vz * f;
    }

    /** Vitesse de depart (blocs par tick) : {@code base}, +6 % par niveau de Pack-a-Punch. */
    public static double vitesse(int pap, double base) {
        return base * (1 + 0.06 * Math.max(0, pap));
    }

    // ------------------------------------------------------------------ flaques
    /** Rayon d'une flaque (blocs) : {@code base} sans Pack-a-Punch, {@code basePap} au niveau 1, +0,2 par niveau au-dessus. */
    public static double rayonFlaque(int pap, double base, double basePap) {
        return pap <= 0 ? base : basePap + 0.2 * (pap - 1);
    }

    /** Duree d'une flaque (ticks) : {@code base} s sans Pack-a-Punch, {@code basePap} s au niveau 1, +3 s par niveau au-dessus. */
    public static int dureeFlaque(int pap, double baseS, double basePapS) {
        double s = pap <= 0 ? baseS : basePapS + 3.0 * (pap - 1);
        return (int) Math.round(s * 20);
    }

    /**
     * Les pieds (fx, fy, fz) d'une creature de demi-largeur {@code demiLarg} touchent-ils la flaque de centre (x, y, z)
     * (y = dessus du sol) et de rayon {@code r} ? La creature doit etre au niveau de la flaque (0,75 bloc au plus au-dessus,
     * 0,4 au-dessous : marches, dalles), et son pied dans le disque (on compte un peu de sa largeur).
     */
    public static boolean dansFlaque(double fx, double fy, double fz, double demiLarg, double x, double y, double z, double r) {
        double dy = fy - y;
        if (dy > 0.75 || dy < -0.4) return false;
        double dx = fx - x, dz = fz - z;
        double rr = r + demiLarg * 0.5;
        return dx * dx + dz * dz <= rr * rr;
    }

    /**
     * Deux flaques assez proches pour n'en faire qu'une (rafale au meme endroit) : distance horizontale inferieure a 0,6 x le
     * plus grand rayon, et meme niveau (0,5 bloc).
     */
    public static boolean fusion(double x1, double y1, double z1, double r1, double x2, double y2, double z2, double r2) {
        if (Math.abs(y1 - y2) > 0.5) return false;
        double dx = x1 - x2, dz = z1 - z2;
        double m = 0.6 * Math.max(r1, r2);
        return dx * dx + dz * dz <= m * m;
    }

    /** Rayon apres fusion : la flaque s'etale un peu (+15 %), au plus 1,35 fois le rayon de base du tir. */
    public static double rayonFusion(double actuel, double nouveau) {
        return Math.min(Math.max(actuel, nouveau) * 1.15, nouveau * 1.35);
    }

    // ------------------------------------------------------------------ glissade
    /** Vitesse de glissade (blocs par tick) au tick {@code t} : depart {@code v0}, ralentit (0,93 par tick). */
    public static double vitesseGlissade(double v0, int t) {
        return v0 * Math.pow(0.93, Math.max(0, t));
    }

    /** Distance glissee en {@code n} ticks (somme de la serie) : sert a borner les tests. */
    public static double distanceGlissade(double v0, int n) {
        return v0 * (1 - Math.pow(0.93, n)) / (1 - 0.93);
    }

    // ------------------------------------------------------------------ degats
    /**
     * Degats de la gelee (coup direct ou fin de glissade) : hors partie de zombies, {@code hors} (x1,5 / x2 / x2,5 au
     * Pack-a-Punch) ; en partie, une creature d'au plus {@code seuilPvMax} PV max fond d'un coup (montant largement au-dessus
     * de ses PV, meme apres les facteurs du mode zombies), un boss prend {@code colosse} (memes facteurs).
     */
    public static float degats(boolean zombies, float pvMax, float pv, double seuilPvMax, float hors, float colosse, int pap) {
        float k = 1 + 0.5f * Math.max(0, pap);
        if (!zombies) return hors * k;
        if (pvMax <= seuilPvMax) return Math.max(pvMax, pv) * 4f + 100f;
        return colosse * k;
    }

    // ------------------------------------------------------------------ glissade des joueurs (client)
    /**
     * Vitesse horizontale du joueur apres un tick sur la gelee : la vitesse est multipliee par {@code boost} (le frottement
     * du sol est presque annule : on glisse vite, comme sur une patinoire savonneuse), bornee a {@code max} blocs par tick.
     * Rend le facteur a appliquer a (vx, vz).
     */
    public static double facteurGlisse(double vx, double vz, double boost, double max) {
        double v = Math.sqrt(vx * vx + vz * vz);
        if (v < 1.0E-4) return 1.0;
        // deja plus vite que la borne (chute, souffle...) : on ne pousse plus, on ne freine pas non plus
        double nv = v >= max ? v : Math.min(v * boost, max);
        return nv / v;
    }
}
