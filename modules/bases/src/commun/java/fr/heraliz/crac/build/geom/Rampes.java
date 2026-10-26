package fr.heraliz.crac.build.geom;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.model.Dirs;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;

/**
 * Rampes ouvertes façon Rust (HeraliZ, 26/10) : rampe carrée et rampe triangle, une dalle inclinée de {@link #T} px
 * d'épaisseur posée sur deux poteaux au bout haut, vide dessous (on passe sous la partie haute). Tout est calculé dans le
 * repère local de la pièce ({@link Placement}), à partir du côté haut (la sortie vers le plancher suivant) :
 * <ul>
 * <li>rampe carrée : côté haut = côté {@code (2 + quart de tour) mod 4}, côtés latéraux les deux voisins ;</li>
 * <li>rampe triangle : coin bas {@code q = rot mod 3}, côté haut = côté opposé {@code (q + 1) mod 3}, côtés latéraux les
 * deux côtés qui partent du coin bas.</li>
 * </ul>
 * La hauteur en un point ne dépend que de sa distance au côté haut : le long d'un côté latéral, elle monte de 0 à la
 * hauteur de la rampe, de son bout bas à son bout haut, pour les deux formes. Deux rampes qui partagent un côté latéral
 * avec le même bout bas (même niveau, même montée, même palier) se raccordent donc sans marche : ce côté n'a ni retrait,
 * ni rebord, ni poteau ({@code variant} : un bit par côté du contour, {@link fr.heraliz.crac.build.logic.Variants}).
 * Collision et rendu suivent ces mêmes nombres (le rendu ajoute seulement de quoi ne jamais toucher un mur ou un
 * plancher voisin dans le même plan).
 */
public final class Rampes {
    /** Épaisseur de la dalle sous la pente, px. */
    public static final double T = 3.0;
    /** Tranches de collision le long de la montée (2 px de profondeur pour la rampe carrée, comme avant). */
    public static final int TRANCHES = 24;
    /** Côté des poteaux du bout haut, px. */
    public static final double POTEAU = 2.0;
    /** Bits de raccord utiles : un par côté du contour (4 pour le carré, 3 pour le triangle). */
    public static final long MASQUE_CARRE = 0b1111;
    public static final long MASQUE_TRIANGLE = 0b111;

    private Rampes() {
    }

    public static boolean est(PieceKind kind) {
        return kind.isRamp();
    }

    /** Côté du contour (indice canonique) par lequel la rampe sort en haut. */
    public static int coteHaut(PieceKey key) {
        int r = key.rot();
        return key.kind() == PieceKind.TRI_RAMP ? (r % 3 + 1) % 3 : (2 + (r & 3)) & 3;
    }

    /** Les deux côtés latéraux (indices canoniques du contour). */
    public static int[] cotesLateraux(PieceKey key) {
        int r = key.rot();
        if (key.kind() == PieceKind.TRI_RAMP) {
            int q = r % 3;
            return new int[]{q, (q + 2) % 3};
        }
        int q = r & 3;
        return new int[]{(1 + q) & 3, (3 + q) & 3};
    }

    /** Sommet (indice canonique) du bout bas du côté latéral {@code cote}. */
    public static int sommetBas(PieceKey key, int cote) {
        if (key.kind() == PieceKind.TRI_RAMP) {
            return key.rot() % 3;
        }
        int q = key.rot() & 3;
        // côté (1 + q) : de v(1+q) à v(2+q), bas en v(1+q) ; côté (3 + q) : de v(3+q) à v(q), bas en v(q)
        return cote == ((1 + q) & 3) ? (1 + q) & 3 : q & 3;
    }

    /** Le point du réseau au bout bas d'un côté latéral (pour comparer deux rampes voisines). */
    public static LPoint pointBas(PieceKey key, int cote) {
        return key.lattice()[sommetBas(key, cote)];
    }

    /** Hauteur de la pente au bout haut, px : 48, ou 24 pour une demi-montée. */
    public static double hauteur(PieceKey key) {
        return PieceKey.UNIT_H * key.topUnits();
    }

    /** Longueur de la montée (distance du côté haut au point le plus bas), px. */
    public static double longueur(PieceKind.Shape shape) {
        return shape == PieceKind.Shape.TRIANGLE ? Footprints.TRI_H : Footprints.SIDE;
    }

    /** Normale sortante (unitaire, tirée de la table des directions) du côté {@code e} du contour local. */
    public static double[] normale(PieceKind.Shape shape, int e) {
        int k = Footprints.localOut(shape, e) - 3;
        return new double[]{Dirs.ex(k), Dirs.ez(k)};
    }

    /** Distance (px) d'un point local au côté {@code e}, positive vers l'intérieur. */
    public static double distance(PieceKind.Shape shape, int e, double u, double v) {
        double[] n = normale(shape, e);
        double[] a = Footprints.local(shape)[e];
        return -((u - a[0]) * n[0] + (v - a[1]) * n[1]);
    }

    /** Hauteur de la pente (px, repère local) au point (u, v). */
    public static double pente(PieceKey key, double u, double v) {
        PieceKind.Shape shape = key.kind().shape;
        double l = longueur(shape);
        double d = distance(shape, coteHaut(key), u, v);
        return hauteur(key) * Math.max(0, Math.min(1, (l - d) / l));
    }

    /** Le côté {@code e} est-il raccordé à une rampe voisine ? */
    public static boolean raccorde(long variant, int e) {
        return (variant & (1L << e)) != 0;
    }

    /** Retrait d'un côté latéral : rien s'il est raccordé, sinon {@link PieceSolids#INSET} plus {@code plus}. */
    public static double retrait(long variant, int cote, double plus) {
        return raccorde(variant, cote) ? 0 : PieceSolids.INSET + plus;
    }

    /** Polygone local {@code poly} gardé du côté où la distance au côté {@code e} vaut au moins {@code d}. */
    public static double[][] auMoins(double[][] poly, PieceKind.Shape shape, int e, double d) {
        double[] n = normale(shape, e);
        double[] a = Footprints.local(shape)[e];
        return Poly.clipHalfPlane(poly, -n[0], -n[1], n[0] * a[0] + n[1] * a[1] - d);
    }

    /** Polygone local {@code poly} gardé du côté où la distance au côté {@code e} vaut au plus {@code d}. */
    public static double[][] auPlus(double[][] poly, PieceKind.Shape shape, int e, double d) {
        double[] n = normale(shape, e);
        double[] a = Footprints.local(shape)[e];
        return Poly.clipHalfPlane(poly, n[0], n[1], d - n[0] * a[0] - n[1] * a[1]);
    }

    /**
     * Contour de la dalle (repère local) : le contour de la pièce, en retrait des côtés latéraux non raccordés, et en
     * plus de {@code plusHaut} du côté haut (le rendu se tient ainsi hors du plan du plancher suivant).
     */
    public static double[][] contour(PieceKey key, long variant, double plusLateral, double plusHaut) {
        PieceKind.Shape shape = key.kind().shape;
        double[][] poly = Footprints.local(shape);
        for (int c : cotesLateraux(key)) {
            double r = retrait(variant, c, plusLateral);
            if (r > 0) {
                poly = auMoins(poly, shape, c, r);
            }
        }
        if (plusHaut > 0 && poly != null) {
            poly = auMoins(poly, shape, coteHaut(key), plusHaut);
        }
        return poly;
    }

    /** Collision locale : la dalle en tranches parallèles au côté haut, puis les poteaux du bout haut. */
    public static List<Prism> collision(PieceKey key, long variant) {
        List<Prism> out = new ArrayList<>();
        PieceKind.Shape shape = key.kind().shape;
        double l = longueur(shape);
        double h = hauteur(key);
        int haut = coteHaut(key);
        double[][] base = contour(key, variant, 0, 0);
        if (base == null) {
            return out;
        }
        for (int i = 0; i < TRANCHES; i++) {
            double[][] p = auMoins(base, shape, haut, l - l * (i + 1) / TRANCHES);
            p = p == null ? null : auPlus(p, shape, haut, l - l * i / TRANCHES);
            if (p == null || Poly.area(p) <= PieceSolids.TOUCH) continue;
            out.add(new Prism(p, Math.max(0, h * i / TRANCHES - T), h * (i + 1) / TRANCHES));
        }
        double piedHaut = h * (TRANCHES - 1) / TRANCHES - T;
        if (piedHaut > 0.5) {
            for (double[][] p : poteaux(key, variant, base)) {
                out.add(new Prism(p, 0, piedHaut));
            }
        }
        return out;
    }

    /** Les poteaux (polygones locaux) aux bouts hauts des côtés latéraux non raccordés. */
    public static List<double[][]> poteaux(PieceKey key, long variant, double[][] base) {
        List<double[][]> out = new ArrayList<>(2);
        PieceKind.Shape shape = key.kind().shape;
        int haut = coteHaut(key);
        for (int c : cotesLateraux(key)) {
            if (raccorde(variant, c)) continue;
            double[][] p = auPlus(base, shape, haut, POTEAU);
            p = p == null ? null : auPlus(p, shape, c, retrait(variant, c, 0) + POTEAU);
            if (p != null && Poly.area(p) > PieceSolids.TOUCH) {
                out.add(p);
            }
        }
        return out;
    }
}
