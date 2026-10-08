package com.Spartan.esp;

/**
 * Un joueur (l'observateur) peut-il voir un autre joueur (la cible) ? Pur, testable.
 *
 * On cherche UNE ligne de vue libre entre quelques points de l'observateur et
 * quelques points de la cible. Les points sont choisis pour ne jamais cacher
 * injustement :
 * - côté observateur : l'œil, l'œil là où il sera dans un aller-retour réseau
 *   (vitesse × anticipation), l'œil décalé à gauche / à droite (début d'un
 *   « peek » au coin d'un mur) et un peu plus haut (saut). Un point décalé
 *   n'est retenu que si l'observateur peut réellement l'atteindre (rien entre
 *   l'œil et lui) : se coller à un mur ne permet pas de voir au travers ;
 * - côté cible : centre, tête, pieds et bords gauche / droit (vus depuis
 *   l'observateur) de sa hitbox prolongée de son mouvement imminent, avec la
 *   même règle d'accessibilité.
 *
 * Paires lointaines : moins de points (les décalages comptent peu à distance).
 */
public final class Sight {
    /** Au-delà (blocs), jeu de points réduit. */
    public static final double FAR = 48.0;
    /** Anticipation du mouvement de la cible (ticks) : lisse l'apparition. */
    public static final int TARGET_LOOKAHEAD = 2;
    private static final double MAX_PEEK = 2.0;
    private static final double EDGE = 0.1;

    /** Œil de l'observateur et sa vitesse (blocs/tick). */
    public record Eye(double x, double y, double z, double vx, double vy, double vz, boolean grounded) {
    }

    /** Hitbox de la cible (véhicule compris) et sa vitesse (blocs/tick). */
    public record Body(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                       double vx, double vy, double vz) {
        double cx() {
            return (minX + maxX) / 2;
        }

        double cy() {
            return (minY + maxY) / 2;
        }

        double cz() {
            return (minZ + maxZ) / 2;
        }
    }

    private final Raycaster rc;
    // Tampons réutilisés : aucune allocation par paire.
    private final double[] ox = new double[5];
    private final double[] oy = new double[5];
    private final double[] oz = new double[5];
    private final double[] tx = new double[8];
    private final double[] ty = new double[8];
    private final double[] tz = new double[8];

    public Sight(Raycaster rc) {
        this.rc = rc;
    }

    public Raycaster raycaster() {
        return rc;
    }

    /**
     * @param lookahead anticipation côté observateur (ticks) : ping + 1 tick
     * @param peek      décalage latéral minimal de l'œil (blocs)
     * @param jump      hauteur de « saut » testée quand l'observateur est au sol (0 = non)
     */
    public boolean canSee(Eye eye, Body body, int lookahead, double peek, double jump) {
        double cx = body.cx();
        double cy = body.cy();
        double cz = body.cz();
        // Raccourci : œil → centre, puis œil → tête.
        if (!rc.blocked(eye.x(), eye.y(), eye.z(), cx, cy, cz)
                || !rc.blocked(eye.x(), eye.y(), eye.z(), cx, body.maxY() - EDGE, cz)) {
            return true;
        }
        double hx = cx - eye.x();
        double hz = cz - eye.z();
        double hl = Math.sqrt(hx * hx + hz * hz);
        double px = hl < 1e-6 ? 1 : -hz / hl; // perpendiculaire horizontale au regard
        double pz = hl < 1e-6 ? 0 : hx / hl;
        boolean far = Math.sqrt(hl * hl + (cy - eye.y()) * (cy - eye.y())) > FAR;

        int no = observerPoints(eye, lookahead, peek, jump, px, pz, far);
        int nt = targetPoints(body, px, pz, far);
        for (int i = 0; i < no; i++) {
            for (int j = 0; j < nt; j++) {
                if (i == 0 && j <= 1) {
                    continue; // déjà testés (centre et tête depuis l'œil)
                }
                if (!rc.blocked(ox[i], oy[i], oz[i], tx[j], ty[j], tz[j])) {
                    return true;
                }
            }
        }
        return false;
    }

    private int observerPoints(Eye e, int lookahead, double peek, double jump, double px, double pz, boolean far) {
        int n = 0;
        ox[n] = e.x();
        oy[n] = e.y();
        oz[n] = e.z();
        n++;
        double mx = e.vx() * lookahead;
        double my = e.vy() * lookahead;
        double mz = e.vz() * lookahead;
        if (mx * mx + my * my + mz * mz > 0.0025) {
            n = addReachable(e, e.x() + mx, e.y() + my, e.z() + mz, n);
        }
        if (far) {
            return n;
        }
        double lateral = Math.abs(e.vx() * px + e.vz() * pz) * lookahead;
        double m = Math.min(MAX_PEEK, peek + lateral);
        if (m > 0) {
            n = addReachable(e, e.x() + px * m, e.y(), e.z() + pz * m, n);
            n = addReachable(e, e.x() - px * m, e.y(), e.z() - pz * m, n);
        }
        if (jump > 0 && e.grounded()) {
            n = addReachable(e, e.x(), e.y() + jump, e.z(), n);
        }
        return n;
    }

    private int addReachable(Eye e, double x, double y, double z, int n) {
        if (!rc.blocked(e.x(), e.y(), e.z(), x, y, z)) {
            ox[n] = x;
            oy[n] = y;
            oz[n] = z;
            return n + 1;
        }
        return n;
    }

    private int targetPoints(Body b, double px, double pz, boolean far) {
        // Hitbox prolongée de son mouvement imminent.
        double mx = b.vx() * TARGET_LOOKAHEAD;
        double my = b.vy() * TARGET_LOOKAHEAD;
        double mz = b.vz() * TARGET_LOOKAHEAD;
        double minX = Math.min(b.minX(), b.minX() + mx);
        double minY = Math.min(b.minY(), b.minY() + my);
        double minZ = Math.min(b.minZ(), b.minZ() + mz);
        double maxX = Math.max(b.maxX(), b.maxX() + mx);
        double maxY = Math.max(b.maxY(), b.maxY() + my);
        double maxZ = Math.max(b.maxZ(), b.maxZ() + mz);
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;
        double cz = (minZ + maxZ) / 2;
        double hw = Math.max(0, (maxX - minX) / 2 * Math.abs(px) + (maxZ - minZ) / 2 * Math.abs(pz) - 0.05);
        double top = maxY - EDGE;
        double bottom = minY + EDGE;
        int n = 0;
        // 0 et 1 : centre et tête de la hitbox RÉELLE (testés en premier depuis l'œil).
        n = put(n, b.cx(), b.cy(), b.cz());
        n = put(n, b.cx(), b.maxY() - EDGE, b.cz());
        if (mx * mx + my * my + mz * mz > 0.0025) {
            n = addTarget(b, n, cx, cy, cz);
        }
        n = addTarget(b, n, cx, bottom, cz);
        n = addTarget(b, n, cx - px * hw, cy, cz - pz * hw);
        n = addTarget(b, n, cx + px * hw, cy, cz + pz * hw);
        if (!far) {
            n = addTarget(b, n, cx - px * hw, top, cz - pz * hw);
            n = addTarget(b, n, cx + px * hw, top, cz + pz * hw);
        }
        return n;
    }

    private int put(int n, double x, double y, double z) {
        tx[n] = x;
        ty[n] = y;
        tz[n] = z;
        return n + 1;
    }

    /** Point retenu seulement s'il est atteignable depuis le centre réel de la cible (pas dans un mur). */
    private int addTarget(Body b, int n, double x, double y, double z) {
        if (n >= tx.length) {
            return n;
        }
        if (!rc.blocked(b.cx(), b.cy(), b.cz(), x, y, z)) {
            return put(n, x, y, z);
        }
        return n;
    }
}
