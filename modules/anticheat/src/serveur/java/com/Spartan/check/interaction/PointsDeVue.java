package com.Spartan.check.interaction;

/**
 * Points visés pour savoir si un objet est en vue (19/11), Java pur, testé (PointsDeVueTest) : un rayon des yeux du joueur
 * vers chacun, dans l'ordre, jusqu'au premier qui passe. D'abord le point que le jeu du joueur dit avoir visé (c'est là que
 * son propre rayon est passé : presque toujours le seul essayé), tel quel, ramené au bord de la boîte s'il en sort ; puis le
 * milieu de la boîte, le milieu de chaque face et ses coins, un peu rentrés dans la boîte. Une seule face ou un seul coin
 * visible suffit : le joueur voit l'objet.
 */
final class PointsDeVue {
    /** Points rentrés de cette part de la taille de la boîte (un coin exact touche souvent le bloc voisin). */
    static final double RENTRE = 0.08;
    /** Un rayon s'arrête un peu avant son point (un objet posé contre un décor : le décor n'est pas « devant »). */
    static final double AVANT_LE_POINT = 0.05;

    private PointsDeVue() {
    }

    /**
     * Points d'une boîte (min, max), en commençant par le point visé ramené dans la boîte s'il est donné (sinon NaN).
     *
     * @return points {x, y, z} : 1 + 1 + 6 + 8 au plus
     */
    static double[][] points(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                             double viseX, double viseY, double viseZ) {
        double rx = (maxX - minX) * RENTRE;
        double ry = (maxY - minY) * RENTRE;
        double rz = (maxZ - minZ) * RENTRE;
        double x0 = minX + rx;
        double y0 = minY + ry;
        double z0 = minZ + rz;
        double x1 = maxX - rx;
        double y1 = maxY - ry;
        double z1 = maxZ - rz;
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;
        double cz = (minZ + maxZ) / 2;
        boolean vise = !Double.isNaN(viseX) && !Double.isNaN(viseY) && !Double.isNaN(viseZ);
        double[][] p = new double[vise ? 16 : 15][];
        int n = 0;
        if (vise) {
            p[n++] = new double[] {clamp(viseX, minX, maxX), clamp(viseY, minY, maxY), clamp(viseZ, minZ, maxZ)};
        }
        p[n++] = new double[] {cx, cy, cz};
        p[n++] = new double[] {cx, y1, cz};
        p[n++] = new double[] {cx, y0, cz};
        p[n++] = new double[] {x0, cy, cz};
        p[n++] = new double[] {x1, cy, cz};
        p[n++] = new double[] {cx, cy, z0};
        p[n++] = new double[] {cx, cy, z1};
        for (int i = 0; i < 8; i++) {
            p[n++] = new double[] {(i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1};
        }
        return p;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /**
     * Fin du rayon de (ax, ay, az) vers (bx, by, bz) : {@link #AVANT_LE_POINT} avant le point (le point lui-même si le
     * rayon est plus court que deux fois cette marge).
     */
    static double[] finDuRayon(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l <= 2 * AVANT_LE_POINT) {
            return new double[] {bx, by, bz};
        }
        double k = (l - AVANT_LE_POINT) / l;
        return new double[] {ax + dx * k, ay + dy * k, az + dz * k};
    }

    /** Distance du point (x, y, z) à la boîte (0 dedans). */
    static double distance(double x, double y, double z, double minX, double minY, double minZ, double maxX, double maxY,
                           double maxZ) {
        double dx = Math.max(0, Math.max(minX - x, x - maxX));
        double dy = Math.max(0, Math.max(minY - y, y - maxY));
        double dz = Math.max(0, Math.max(minZ - z, z - maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
