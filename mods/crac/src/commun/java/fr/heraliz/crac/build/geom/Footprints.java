package fr.heraliz.crac.build.geom;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.model.Dirs;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;

/**
 * The two footprints (square, equilateral triangle; every edge 48 px) and the plan regions of pieces: local
 * polygons, building polygons (doubles and exact) and the foundation block columns.
 */
public final class Footprints {
    public static final double SIDE = 48.0;
    /** Height of the triangle, {@code 24 sqrt3}. */
    public static final double TRI_H = 24.0 * Dirs.SQRT3;

    private Footprints() {
    }

    /** Local polygon (u, v) of a footprint shape, vertices in canonical order; a fresh array. */
    public static double[][] local(PieceKind.Shape shape) {
        return switch (shape) {
            case SQUARE -> new double[][]{{0, 0}, {SIDE, 0}, {SIDE, SIDE}, {0, SIDE}};
            case TRIANGLE -> new double[][]{{0, 0}, {SIDE, 0}, {SIDE / 2, TRI_H}};
            case EDGE -> new double[][]{{0, 0}, {SIDE, 0}};
        };
    }

    /** Local direction of the edge leaving vertex i (0 at the anchor). */
    public static int localOut(PieceKind.Shape shape, int i) {
        return shape.turn * i;
    }

    public static double area(PieceKind.Shape shape) {
        return switch (shape) {
            case SQUARE -> SIDE * SIDE;
            case TRIANGLE -> SIDE * TRI_H / 2;
            case EDGE -> 0;
        };
    }

    /** Plan region of the piece in building px: footprint polygon, or the wall's centre segment (two points). */
    public static double[][] polygon(PieceKey key) {
        LPoint[] v = key.lattice();
        double[][] out = new double[v.length][];
        for (int i = 0; i < v.length; i++) {
            out[i] = new double[]{v[i].toX(), v[i].toZ()};
        }
        return out;
    }

    /**
     * The footprint polygon in building px as the solids see it (the local polygon through the piece's
     * {@link Placement}): foundation columns, skirts and slab blocks all clip this same polygon, so they always agree.
     */
    public static double[][] solidPolygon(PieceKey key) {
        return Placement.of(key).apply(local(key.kind().shape));
    }

    /** Exact plan region: the footprint's vertices, or the wall's two ends. */
    public static LPoint[] exact(PieceKey key) {
        return key.lattice();
    }

    /**
     * Block columns {x, z} (absolute world coordinates) of the slab-row collision blocks: columns whose intersection
     * with the footprint exceeds {@link PieceSolids#TOUCH} (the rule of every reserved block, so no slab sliver lacks a
     * skirt), sorted by (z, x). Foundation skirts, ground checks and raid protection follow this list.
     */
    public static List<int[]> columns(PieceKey key) {
        List<int[]> out = new ArrayList<>();
        if (!key.kind().shape.isFootprint()) {
            return out;
        }
        double[][] poly = solidPolygon(key);
        double[] b = Poly.bounds(poly);
        int x0 = (int) Math.floor(b[0] / 16.0);
        int x1 = (int) Math.floor(b[2] / 16.0);
        int z0 = (int) Math.floor(b[1] / 16.0);
        int z1 = (int) Math.floor(b[3] / 16.0);
        int ox = key.origin().getX();
        int oz = key.origin().getZ();
        for (int bz = z0; bz <= z1; bz++) {
            for (int bx = x0; bx <= x1; bx++) {
                double[][] c = Poly.clipRect(poly, bx * 16.0, bz * 16.0, bx * 16.0 + 16.0, bz * 16.0 + 16.0);
                if (c != null && Poly.area(c) > PieceSolids.TOUCH) {
                    out.add(new int[]{ox + bx, oz + bz});
                }
            }
        }
        return out;
    }
}
