package fr.heraliz.recolte.client.modeles;

import fr.heraliz.recolte.entites.Arbre;

/**
 * Un arbre à abattre prêt à dessiner : les groupes du modèle cuit ({@code souche} ; dans {@code tronc} : {@code coupe_0}
 * à {@code coupe_3}, {@code fut}, {@code branches}, {@code feuillage}) et ce que la chute en tire, mesuré une fois au
 * chargement :
 * <ul>
 *    <li>le centre de la coupe ({@code pivots.tronc}, à 0,5 m) ;</li>
 *    <li>l'axe du morceau de coupe (du centre de la coupe au centre du bas du fût : le tronc penche un peu), autour
 *    duquel ce morceau tourne par quarts de tour pour mettre l'entaille du côté du joueur ;</li>
 *    <li>la demi-largeur du tronc à la coupe : la charnière de la chute est au bord opposé à l'entaille.</li>
 * </ul>
 */
public final class ModeleArbre {
    public final ModeleCuit modele;
    public final float[] souche, fut, branches, feuillage;
    /** Morceau de tronc au-dessus de la coupe, entaille de plus en plus profonde côté +x (0 : intact). */
    public final float[][] coupes = new float[Arbre.ENTAILLES][];
    /** Centre de la coupe (m). */
    public final float cx, cy, cz;
    /** Axe du morceau de coupe, normé. */
    public final float ax, ay, az;
    /** Demi-largeur du tronc à la coupe (m). */
    public final float rayon;

    ModeleArbre(ModeleCuit m) {
        this.modele = m;
        this.souche = m.groupe("souche");
        this.fut = m.groupe("fut");
        this.branches = m.groupe("branches");
        this.feuillage = m.groupe("feuillage");
        for (int k = 0; k < coupes.length; k++) {
            coupes[k] = m.groupe("coupe_" + k);
        }
        float[] p = m.pivot("tronc");
        float[] c0 = coupes[0];
        // centre de la coupe : pivots.tronc ; sans lui, le centre du bas du morceau intact
        float[] bas = tranche(c0, true);
        float[] haut = tranche(c0, false);
        if (p != null && p.length >= 3) {
            cx = p[0];
            cy = p[1];
            cz = p[2];
        } else if (bas != null) {
            cx = bas[0];
            cy = (float) Arbre.HAUTEUR_COUPE;
            cz = bas[1];
        } else {
            cx = 0.0F;
            cy = (float) Arbre.HAUTEUR_COUPE;
            cz = 0.0F;
        }
        float dx = 0.0F, dy = 1.0F, dz = 0.0F;
        if (haut != null) {
            dx = haut[0] - cx;
            dy = haut[2] - cy;
            dz = haut[1] - cz;
        }
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1.0E-4F || dy <= 0.0F) {
            dx = 0.0F;
            dy = 1.0F;
            dz = 0.0F;
            l = 1.0F;
        }
        ax = dx / l;
        ay = dy / l;
        az = dz / l;
        rayon = bas != null ? Math.max(0.05F, bas[3]) : 0.25F;
    }

    /**
     * Tranche du bas (ou du haut) d'un groupe : sommets à moins de 8 cm du plus bas (ou du plus haut). Rend {centre x,
     * centre z, hauteur moyenne, demi-largeur la plus grande} ou null si le groupe est vide.
     */
    private static float[] tranche(float[] q, boolean bas) {
        if (q.length < ModeleCuit.PAR_SOMMET) {
            return null;
        }
        float ymin = Float.MAX_VALUE, ymax = -Float.MAX_VALUE;
        for (int o = 0; o < q.length; o += ModeleCuit.PAR_SOMMET) {
            ymin = Math.min(ymin, q[o + 1]);
            ymax = Math.max(ymax, q[o + 1]);
        }
        float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, z0 = Float.MAX_VALUE, z1 = -Float.MAX_VALUE, ys = 0.0F;
        int n = 0;
        for (int o = 0; o < q.length; o += ModeleCuit.PAR_SOMMET) {
            float y = q[o + 1];
            if (bas ? y <= ymin + 0.08F : y >= ymax - 0.08F) {
                x0 = Math.min(x0, q[o]);
                x1 = Math.max(x1, q[o]);
                z0 = Math.min(z0, q[o + 2]);
                z1 = Math.max(z1, q[o + 2]);
                ys += y;
                n++;
            }
        }
        if (n == 0) {
            return null;
        }
        return new float[]{(x0 + x1) / 2.0F, (z0 + z1) / 2.0F, ys / n, Math.max(x1 - x0, z1 - z0) / 2.0F};
    }
}
