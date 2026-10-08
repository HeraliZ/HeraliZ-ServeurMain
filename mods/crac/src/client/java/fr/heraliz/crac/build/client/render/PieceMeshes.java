package fr.heraliz.crac.build.client.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import fr.heraliz.crac.build.geom.EdgeFrame;
import fr.heraliz.crac.build.geom.Footprints;
import fr.heraliz.crac.build.geom.PieceSolids;
import fr.heraliz.crac.build.geom.Placement;
import fr.heraliz.crac.build.geom.Poly;
import fr.heraliz.crac.build.geom.RoofField;
import fr.heraliz.crac.build.logic.Variants;
import fr.heraliz.crac.build.model.Dirs;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import net.minecraft.core.BlockPos;

/**
 * Procedural render meshes of every piece, built from the same contract as the collision ({@link PieceSolids}).
 * Every kind is built in its local frame (u, y, v; see {@link Placement}) and placed once ({@link MeshFace#transform}).
 * Render geometry stays within the collision or the render envelope ({@link PieceSolids#renderEnvelope}) and avoids
 * coplanar faces between pieces that meet (§10.3): wall bodies span y in [-1, H - 1] so stacked walls abut and floors
 * cover them, corner posts span [-0.5, H - 0.5] per owned band and every wall body stops inside the posts, the
 * horizontal faces of a wall are lowered by 0.04 px per edge direction, stairs and ramps sit a hair off the grid.
 */
public final class PieceMeshes {
    public static final double WALL_BOTTOM = -1.0;
    public static final double WALL_TOP = 47.0;
    public static final double HALF_TOP = 23.0;
    public static final double POST_BOTTOM = -0.5;
    public static final double POST_TOP = 47.5;
    public static final double HALF_POST_TOP = 23.5;
    /** Skirt blocks below a foundation slab alternate between two texture rows. */
    public static final int SKIRT_ROWS = 2;
    /** Wall bodies end this far from each vertex, inside the disc of radius 2.45 every post contains (§10.4). */
    public static final double WALL_END = 1.4;
    /** Wall relief starts this far from each wall end, clear of any other wall's relief at 60° or more (§10.3). */
    public static final double RELIEF_START = 5.75;
    /** Horizontal faces of a wall body and its relief are lowered by this much per edge direction (dir6). */
    public static final double BAND_DROP = 0.04;
    private static final double T = PieceSolids.WALL_HALF;
    private static final double CRACK_OFFSET = 0.12;
    private static final double SETTLE = 0.2;
    private static final double SETTLE_DROP = 0.15;
    /** Off the 0.25 px grid of wall relief so joist bottoms never meet a band face. */
    private static final double JOIST_H = 1.85;
    /** cos 15° and sin 15°: the 12-sided logs have their vertices between the lattice directions. */
    private static final double COS15 = (Math.sqrt(6) + Math.sqrt(2)) / 4;
    private static final double SIN15 = (Math.sqrt(6) - Math.sqrt(2)) / 4;

    private PieceMeshes() {
    }

    /**
     * Mesh of a piece in its local frame (u, y, v), crack overlays included: everything except the foundation skirt,
     * which follows the block columns (see {@link #build}). Independent of the piece's position.
     */
    public static List<MeshFace> local(PieceKey key, Tier tier, int state, long variant, int crack) {
        MeshBuilder b = new MeshBuilder();
        PieceKind kind = key.kind();
        switch (kind) {
            case FOUNDATION, TRI_FOUNDATION -> foundation(b, kind.shape);
            case FLOOR, TRI_FLOOR -> floor(b, kind.shape, tier);
            case ROOF, TRI_ROOF -> roof(b, kind.shape, variant);
            case WALL, HALF_WALL, DOORWAY, DOUBLE_DOORWAY, WINDOW -> wall(b, key, tier, state, variant);
            case STAIRS_L -> stairsL(b, key.rot());
            case RAMP -> ramp(b, key.rot());
        }
        List<MeshFace> out = new ArrayList<>(b.faces.size() + 16);
        out.addAll(b.faces);
        if (crack > 0) {
            Mat cm = Mat.crack(crack);
            for (MeshFace f : b.faces) {
                if (f.mat.crackable(tier)) {
                    out.add(f.withMat(cm).offset(CRACK_OFFSET));
                }
            }
        }
        return out;
    }

    /**
     * Mesh of a piece instance in building px: the local mesh placed by the key, plus {@link #SKIRT_ROWS} skirt rows
     * under every foundation column ({@link Footprints#columns}, the same polygon as the slab and the collision).
     */
    public static List<MeshFace> build(PieceKey key, Tier tier, int state, long variant, int crack) {
        Placement p = Placement.of(key);
        List<MeshFace> local = local(key, tier, state, variant, crack);
        List<MeshFace> out = new ArrayList<>(local.size() + 32);
        for (MeshFace f : local) {
            out.add(f.transform(p));
        }
        if (key.kind().isFoundation()) {
            MeshBuilder sk = new MeshBuilder();
            skirt(sk, key);
            out.addAll(sk.faces);
        }
        return out;
    }

    /**
     * Ghost mesh in px relative to the anchor point (building axes: already turned by the orientation), with the
     * skirt rows under the whole footprint instead of per block column, so it depends on the configuration only.
     */
    public static List<MeshFace> ghost(PieceKey key, Tier tier, int state, long variant) {
        Placement p = new Placement(BlockPos.ZERO, 0, 0, 0, key.orient());
        MeshBuilder b = new MeshBuilder();
        List<MeshFace> faces = new ArrayList<>(local(key, tier, state, variant, 0));
        if (key.kind().isFoundation()) {
            double t = PieceSolids.FOUNDATION_T;
            b.prism(Footprints.local(key.kind().shape), -t - 16 * SKIRT_ROWS, -t, null, Mat.SKIRT, Mat.SKIRT, 48, 32, -t, 48, false, true, 0.96f, Double.NaN);
            faces.addAll(b.faces);
        }
        List<MeshFace> out = new ArrayList<>(faces.size());
        for (MeshFace f : faces) {
            out.add(f.transform(p));
        }
        return out;
    }

    /**
     * Blocks {dx, dy, dz} relative to the key's anchor block that receive mesh fragments: the reserved blocks, with
     * the foundation skirt drawn for {@link #SKIRT_ROWS} rows (every deeper skirt block reuses one of them).
     */
    public static List<int[]> renderBlocks(PieceKey key) {
        byte[] skirt = null;
        if (key.kind().isFoundation()) {
            skirt = new byte[Footprints.columns(key).size()];
            Arrays.fill(skirt, (byte) SKIRT_ROWS);
        }
        BlockPos a = key.anchorBlock();
        List<int[]> out = new ArrayList<>();
        for (BlockPos pos : PieceSolids.reservedBlocks(key, skirt)) {
            out.add(new int[]{pos.getX() - a.getX(), pos.getY() - a.getY(), pos.getZ() - a.getZ()});
        }
        return out;
    }

    // ================================================================== foundations, floors

    private static void foundation(MeshBuilder b, PieceKind.Shape shape) {
        double[][] poly = Footprints.local(shape);
        double t = PieceSolids.FOUNDATION_T;
        b.prism(poly, -t, 0, Mat.FOUNDATION_TOP, Mat.FOUNDATION_SIDE, Mat.FOUNDATION_SIDE, 48, 16, 0, 48, true, true, 1f, Double.NaN);
    }

    /** Skirt rows under every foundation column, in building px (the columns follow the block grid). */
    private static void skirt(MeshBuilder b, PieceKey key) {
        double[][] poly = Footprints.solidPolygon(key);
        double top = key.baseY() - PieceSolids.FOUNDATION_T;
        BlockPos o = key.origin();
        for (int row = 0; row < SKIRT_ROWS; row++) {
            double y1 = top - 16 * row;
            double y0 = y1 - 16;
            for (int[] c : Footprints.columns(key)) {
                double x0 = (c[0] - o.getX()) * 16.0;
                double z0 = (c[1] - o.getZ()) * 16.0;
                double[][] col = Poly.clipRect(poly, x0, z0, x0 + 16, z0 + 16);
                if (col == null || Poly.area(col) <= PieceSolids.TOUCH) continue;
                b.prism(col, y0, y1, null, Mat.SKIRT, Mat.SKIRT, 48, 32, top, 48, false, true, 0.96f, Double.NaN);
            }
        }
    }

    private static void floor(MeshBuilder b, PieceKind.Shape shape, Tier tier) {
        double[][] poly = Footprints.local(shape);
        double t = PieceSolids.FLOOR_T;
        b.prism(poly, -t, 0, Mat.FLOOR, Mat.FLOOR_UNDER, Mat.TRIM, 16, 16, 0, 48, true, true, 1f, Double.NaN);
        if (tier == Tier.STONE) {
            return;
        }
        // joists under the floor, running along u
        MeshBuilder.Style js = new MeshBuilder.Style(Mat.BEAM).scale(16, 16).edges(16, 16).top(-t).tone(0.9f);
        js.capAlongU = false;
        double w = tier.isMetal() ? 2.0 : 2.5;
        for (double zc : new double[]{12, 36}) {
            double[][] foot = Poly.clipRect(poly, 0, zc - w / 2, 48, zc + w / 2);
            if (foot == null || Poly.area(foot) < 1.0) continue;
            double[] bd = Poly.bounds(foot);
            if (Poly.isAxisRect(foot)) {
                b.box(MeshBuilder.Frame.CELL, bd[0], bd[2], bd[1], bd[3], -t - JOIST_H, -t, js, MeshBuilder.SKIP_TOP);
            } else {
                b.prism(foot, -t - JOIST_H, -t, Mat.BEAM, Mat.BEAM, Mat.BEAM, 16, 16, -t, 16, false, true, 0.9f, Double.NaN);
            }
        }
    }

    // ================================================================== roofs

    /**
     * The roof top is the {@link RoofField} of the piece's variant: its planar facets, in the local frame. A fascia
     * (TRIM, y -3..0) closes the open edges (no roof across them), the underside is flat at y = -3.
     */
    private static void roof(MeshBuilder b, PieceKind.Shape shape, long variant) {
        RoofField field = RoofField.of(shape, variant);
        for (RoofField.Facet f : field.facets()) {
            double[][] poly = f.poly();
            double[][] pts = new double[poly.length][];
            for (int i = 0; i < poly.length; i++) {
                pts[i] = new double[]{poly[i][0], f.y()[i], poly[i][1]};
            }
            slope(b, Mat.ROOF, pts, 1f);
        }
        double t = PieceSolids.FLOOR_T;
        double[][] fp = Footprints.local(shape);
        int n = fp.length;
        long v = field.variant();
        for (int i = 0; i < n; i++) {
            int out = Footprints.localOut(shape, i);
            // the sector just outside the edge at its start vertex is covered: a roof shares the edge
            if ((RoofField.mask(v, i) >> Dirs.norm(out - 1) & 1) != 0) continue;
            double[] a = fp[i];
            double[] c = fp[(i + 1) % n];
            double[][] pts = {{a[0], -t, a[1]}, {c[0], -t, c[1]}, {c[0], 0, c[1]}, {a[0], 0, a[1]}};
            b.vert(Mat.TRIM, pts, Dirs.ex(out - 3), Dirs.ez(out - 3), 16, 16, 0, false, 1f, Double.NaN);
        }
        double[][] under = new double[n][];
        for (int i = 0; i < n; i++) {
            under[i] = new double[]{fp[i][0], -t, fp[i][1]};
        }
        b.horiz(Mat.ROOF_UNDER, under, -1, 1, 0, 48, 48, 0.85f);
    }

    /**
     * Sloped (or flat) face over its plane: texture rows follow the contour lines, tiles are 48 px along the slope,
     * so roofs and ramps line up across cells.
     */
    private static void slope(MeshBuilder b, Mat m, double[][] pts, float tone) {
        // Newell normal: robust when the first vertices are collinear (facets split on an edge's midpoint)
        double nx = 0;
        double ny = 0;
        double nz = 0;
        for (int i = 0; i < pts.length; i++) {
            double[] p = pts[i];
            double[] q = pts[(i + 1) % pts.length];
            nx += (p[1] - q[1]) * (p[2] + q[2]);
            ny += (p[2] - q[2]) * (p[0] + q[0]);
            nz += (p[0] - q[0]) * (p[1] + q[1]);
        }
        if (ny < 0) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        double[][] uv = new double[pts.length][];
        if (Math.abs(ny) < 1e-9) {
            return;
        }
        // h = c - (nx x + nz z) / ny  ->  gradient (-nx/ny, -nz/ny)
        double gx = -nx / ny;
        double gz = -nz / ny;
        double g = Math.sqrt(gx * gx + gz * gz);
        for (int i = 0; i < pts.length; i++) {
            double[] p = pts[i];
            if (g < 1e-6) {
                uv[i] = new double[]{p[0] / 48, p[2] / 48};
            } else {
                double ex = gx / g;
                double ez = gz / g;
                double s = (p[0] * -ez + p[2] * ex) / 48;
                double t = -(p[0] * ex + p[2] * ez) * Math.sqrt(1 + g * g) / 48;
                uv[i] = new double[]{s, t};
            }
        }
        b.poly(m, pts, uv, nx, ny, nz, tone, Double.NaN);
    }

    // ================================================================== stairs, ramps

    /**
     * L stairs (§5.6, §10.7) in the rot-0 frame, then mirrored (bit 2) and turned (bits 0-1) like the collision: flight
     * 1 along the west side climbing towards -v, a landing in the north-west quarter, flight 2 along the north side
     * climbing towards +u. One box per step and the landing (solid sides, treads), the visible band of each riser
     * (TRIM), nosing strips on the front edge of each tread and an inner stringer along each flight; faces between two
     * boxes are left out. Settled.
     */
    private static void stairsL(MeshBuilder b, int rot) {
        MeshBuilder r = new MeshBuilder();
        MeshBuilder.Style st = new MeshBuilder.Style(Mat.SIDE).edges(48, 48).contact(0);
        st.top = Mat.FLOOR;
        st.capAlongU = false;
        st.capScale = 48;
        double in = PieceSolids.INSET;
        double mid = 24;
        double far = 48 - in;
        double run = PieceSolids.STEP_RUN;
        double rise = PieceSolids.STEP_RISE;
        MeshBuilder.Frame f = MeshBuilder.Frame.CELL;
        int skip = MeshBuilder.SKIP_BOTTOM;
        // flight 1: the front (+v) and back (-v) of every step are risers or lie inside the next box
        for (int s = 0; s < 4; s++) {
            double v1 = far - run * s;
            r.box(f, in, mid, v1 - run, v1, 0, rise * (s + 1), st, skip | MeshBuilder.SKIP_NPOS | MeshBuilder.SKIP_NNEG);
            riser(r, new double[]{in, v1}, new double[]{mid, v1}, rise * s, rise * (s + 1), 0, 1);
        }
        // the landing: its east and south faces lie against flight 2 and the top step of flight 1
        r.box(f, in, mid, in, mid, 0, 4 * rise, st, skip | MeshBuilder.SKIP_UPOS | MeshBuilder.SKIP_NPOS);
        // flight 2: fronts (-u) and backs (+u) likewise, except the back of the top step (the exit side)
        for (int s = 0; s < 4; s++) {
            double u0 = mid + run * s;
            int back = s == 3 ? 0 : MeshBuilder.SKIP_UPOS;
            r.box(f, u0, u0 + run, in, mid, 0, 4 * rise + rise * (s + 1), st, skip | MeshBuilder.SKIP_UNEG | back);
            riser(r, new double[]{u0, mid}, new double[]{u0, in}, 4 * rise + rise * s, 4 * rise + rise * (s + 1), -1, 0);
        }
        // nosing strips on the front edge of each tread (the 4th step of flight 1 is level with the landing)
        MeshBuilder.Style ns = new MeshBuilder.Style(Mat.BEAM).scale(16, 16).edges(16, 16).contact(0);
        ns.capAlongU = false;
        for (int s = 0; s < 3; s++) {
            double y = rise * (s + 1);
            r.box(f, in + 0.5, mid - 0.5, far - run * s - 1.5, far - run * s, y, y + 0.75, ns, skip);
        }
        for (int s = 0; s < 4; s++) {
            double y = 4 * rise + rise * (s + 1);
            r.box(f, mid + run * s, mid + run * s + 1.5, in + 0.5, mid - 0.5, y, y + 0.75, ns, skip);
        }
        // inner stringers: a board standing STRINGER px proud of the inner side of each flight, along its nosing line
        double k = rise / run;
        double lip = 0.75;
        double depth = 5;
        // flight 1, on u = mid, in the (v, y) plane: from the foot (v = far) up to the landing (v = mid)
        double vTop = far - 3 * run;
        stringer(r, true, mid, new double[][][]{
            {{far, rise + lip - depth}, {far, rise + lip}, {vTop, 4 * rise + lip}, {vTop, 4 * rise + lip - depth}},
            {{vTop, 4 * rise + lip - depth}, {vTop, 4 * rise + lip}, {mid, 4 * rise + lip}, {mid, 4 * rise + lip - depth}}});
        // flight 2, on v = mid, in the (u, y) plane: from the landing (u = mid) to the exit (u = far), capped at the top
        double y0 = 5 * rise + lip;
        double uCap = mid + (8 * rise - y0) / k;
        stringer(r, false, mid, new double[][][]{
            {{mid, y0 - depth}, {mid, y0}, {uCap, 8 * rise}, {uCap, 8 * rise - depth}},
            {{uCap, 8 * rise - depth}, {uCap, 8 * rise}, {far, 8 * rise}, {far, 8 * rise - depth}}});
        MeshBuilder m = new MeshBuilder();
        if ((rot & 4) != 0) {
            mirrorInto(m, r);
        } else {
            m.faces.addAll(r.faces);
        }
        rotateSettled(b, m, rot & 3);
    }

    /** How far the inner stringers of L stairs stand proud of the flight's side. */
    private static final double STRINGER = 0.5;

    /** The visible band [y0, y1] of a riser from (x, z) point a to c, facing (ox, oz). */
    private static void riser(MeshBuilder r, double[] a, double[] c, double y0, double y1, double ox, double oz) {
        double[][] pts = {{a[0], y0, a[1]}, {c[0], y0, c[1]}, {c[0], y1, c[1]}, {a[0], y1, a[1]}};
        r.vert(Mat.TRIM, pts, ox, oz, 16, 16, 48, false, 0.95f, 0);
    }

    /**
     * A stringer board of L stairs: convex (along, y) polygons sharing edges, extruded from the flight's inner side at
     * {@code at} (u = at when {@code alongV}, else v = at) to STRINGER px outside it (+u, or +v). The face against the
     * steps and the edges two polygons share are left out.
     */
    private static void stringer(MeshBuilder r, boolean alongV, double at, double[][][] polys) {
        double out = at + STRINGER;
        for (double[][] poly : polys) {
            int n = poly.length;
            double[][] face = new double[n][];
            double[][] uv = new double[n][];
            for (int i = 0; i < n; i++) {
                face[i] = alongV ? new double[]{out, poly[i][1], poly[i][0]} : new double[]{poly[i][0], poly[i][1], out};
                uv[i] = new double[]{poly[i][0] / 16, (48 - poly[i][1]) / 16};
            }
            r.poly(Mat.BEAM, face, uv, alongV ? 1 : 0, 0, alongV ? 0 : 1, 1f, 0);
            for (int i = 0; i < n; i++) {
                double[] p = poly[i];
                double[] q = poly[(i + 1) % n];
                if (shared(polys, poly, p, q)) continue;
                double[][] e = alongV
                    ? new double[][]{{at, p[1], p[0]}, {at, q[1], q[0]}, {out, q[1], q[0]}, {out, p[1], p[0]}}
                    : new double[][]{{p[0], p[1], at}, {q[0], q[1], at}, {q[0], q[1], out}, {p[0], p[1], out}};
                double len = Math.hypot(q[0] - p[0], q[1] - p[1]);
                double[][] euv = {{0, 0}, {len / 16, 0}, {len / 16, STRINGER / 16}, {0, STRINGER / 16}};
                // outward in the board's plane: away from the polygon's centre
                double nx = q[1] - p[1];
                double ny = -(q[0] - p[0]);
                double cx = 0;
                double cy = 0;
                for (double[] v : poly) {
                    cx += v[0] / n;
                    cy += v[1] / n;
                }
                if (nx * ((p[0] + q[0]) / 2 - cx) + ny * ((p[1] + q[1]) / 2 - cy) < 0) {
                    nx = -nx;
                    ny = -ny;
                }
                r.poly(Mat.BEAM, e, euv, alongV ? 0 : nx, ny, alongV ? nx : 0, 0.9f, Double.NaN);
            }
        }
    }

    /** Whether edge p-q of {@code poly} is also an edge of another polygon of the list. */
    private static boolean shared(double[][][] polys, double[][] poly, double[] p, double[] q) {
        for (double[][] o : polys) {
            if (o == poly) continue;
            for (int i = 0; i < o.length; i++) {
                double[] a = o[i];
                double[] c = o[(i + 1) % o.length];
                if ((same(a, p) && same(c, q)) || (same(a, q) && same(c, p))) return true;
            }
        }
        return false;
    }

    private static boolean same(double[] a, double[] c) {
        return Math.abs(a[0] - c[0]) < 1e-9 && Math.abs(a[1] - c[1]) < 1e-9;
    }

    /** Low edge on the v = 0 side (rot 0) climbing towards +v: 48 px, or 24 px for a half ramp (bit 2). */
    private static void ramp(MeshBuilder b, int rot) {
        MeshBuilder r = new MeshBuilder();
        double h = (rot & 4) != 0 ? 24 : 48;
        double x0 = PieceSolids.INSET;
        double x1 = 48 - PieceSolids.INSET;
        double rh = 0.75;
        double rw = 1.5;
        slope(r, Mat.RAMP, new double[][]{{x0 + rw, 0, 0}, {x1 - rw, 0, 0}, {x1 - rw, h, 48}, {x0 + rw, h, 48}}, 1f);
        r.vert(Mat.SIDE, new double[][]{{x0, 0, 0}, {x0, 0, 48}, {x0, h + rh, 48}, {x0, rh, 0}}, -1, 0, 48, 48, 48, false, 1f, 0);
        r.vert(Mat.SIDE, new double[][]{{x1, 0, 0}, {x1, 0, 48}, {x1, h + rh, 48}, {x1, rh, 0}}, 1, 0, 48, 48, 48, false, 1f, 0);
        r.vert(Mat.SIDE, new double[][]{{x0, 0, 48}, {x1, 0, 48}, {x1, h, 48}, {x0, h, 48}}, 0, 1, 48, 48, 48, false, 1f, 0);
        // side rails standing proud of the slope
        for (double xa : new double[]{x0, x1 - rw}) {
            double[][] top = {{xa, rh, 0}, {xa + rw, rh, 0}, {xa + rw, h + rh, 48}, {xa, h + rh, 48}};
            slope(r, Mat.BEAM, top, 1f);
            boolean left = xa == x0;
            double side = left ? xa + rw : xa;
            r.vert(Mat.BEAM, new double[][]{{side, 0, 0}, {side, h, 48}, {side, h + rh, 48}, {side, rh, 0}}, left ? 1 : -1, 0, 16, 16, 48, false, 1f, Double.NaN);
            r.vert(Mat.BEAM, new double[][]{{xa, 0, 0}, {xa + rw, 0, 0}, {xa + rw, rh, 0}, {xa, rh, 0}}, 0, -1, 16, 16, 48, false, 1f, Double.NaN);
            r.vert(Mat.BEAM, new double[][]{{xa, h, 48}, {xa + rw, h, 48}, {xa + rw, h + rh, 48}, {xa, h + rh, 48}}, 0, 1, 16, 16, 48, false, 1f, Double.NaN);
        }
        rotateSettled(b, r, rot & 3);
    }

    /** Copies the faces of {@code src} mirrored {@code u -> 48 - u} (winding reversed so the faces stay outward). */
    private static void mirrorInto(MeshBuilder dst, MeshBuilder src) {
        for (MeshFace f : src.faces) {
            int n = f.size();
            double[] p = new double[n * 3];
            double[] t = new double[n * 2];
            for (int i = 0; i < n; i++) {
                int j = n - 1 - i;
                p[i * 3] = 48.0 - f.pos[j * 3];
                p[i * 3 + 1] = f.pos[j * 3 + 1];
                p[i * 3 + 2] = f.pos[j * 3 + 2];
                t[i * 2] = f.uv[j * 2];
                t[i * 2 + 1] = f.uv[j * 2 + 1];
            }
            dst.faces.add(new MeshFace(f.mat, p, t, -f.nx, f.ny, f.nz, f.tone, f.contactY));
        }
    }

    /**
     * Moves volume pieces a hair off the pixel grid (shrunk towards the cell centre, sunk into the floor) so they never
     * share a plane with the walls, wall relief and floors they touch.
     */
    private static void settle(MeshBuilder dst, MeshBuilder src) {
        double cx = 24;
        double cz = 24;
        double k = 1 - SETTLE / 22.0;
        for (MeshFace f : src.faces) {
            double[] p = f.pos.clone();
            for (int i = 0; i < p.length; i += 3) {
                p[i] = cx + (p[i] - cx) * k;
                p[i + 1] -= SETTLE_DROP;
                p[i + 2] = cz + (p[i + 2] - cz) * k;
            }
            double ax = 0;
            double ay = 0;
            double az = 0;
            int n = p.length / 3;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                ax += (p[i * 3 + 1] - p[j * 3 + 1]) * (p[i * 3 + 2] + p[j * 3 + 2]);
                ay += (p[i * 3 + 2] - p[j * 3 + 2]) * (p[i * 3] + p[j * 3]);
                az += (p[i * 3] - p[j * 3]) * (p[i * 3 + 1] + p[j * 3 + 1]);
            }
            double len = Math.sqrt(ax * ax + ay * ay + az * az);
            if (len < 1e-9) continue;
            dst.faces.add(new MeshFace(f.mat, p, f.uv, ax / len, ay / len, az / len, f.tone, f.contactY));
        }
    }

    private static void rotateSettled(MeshBuilder dst, MeshBuilder src, int rot) {
        MeshBuilder r = new MeshBuilder();
        rotateInto(r, src, rot);
        settle(dst, r);
    }

    /** Copies the faces of {@code src} rotated by quarter turns around the cell centre (see {@link Poly#rotateCell}). */
    private static void rotateInto(MeshBuilder dst, MeshBuilder src, int rot) {
        int r = Math.floorMod(rot, 4);
        for (MeshFace f : src.faces) {
            if (r == 0) {
                dst.faces.add(f);
                continue;
            }
            double[] p = f.pos.clone();
            double nx = f.nx;
            double nz = f.nz;
            for (int k = 0; k < r; k++) {
                for (int i = 0; i < p.length; i += 3) {
                    double x = p[i];
                    p[i] = 48.0 - p[i + 2];
                    p[i + 2] = x;
                }
                double t = nx;
                nx = -nz;
                nz = t;
            }
            dst.faces.add(new MeshFace(f.mat, p, f.uv, nx, f.ny, nz, f.tone, f.contactY));
        }
    }

    // ================================================================== walls

    /**
     * Height range of the caps on a wall's centre plane, clear of foundation, floor (and joist) and roof edges: from
     * 0.25 px up to 5.25 px below the wall's nominal top (a floor slab and its joists resting on the wall, on a full
     * wall as on a half wall).
     */
    private static final double CAP_BOTTOM = 0.25;
    private static final double CAP_BELOW_TOP = 5.25;
    /** How far each centre-plane cap sits inside the half it closes. */
    private static final double CAP_INSET = 0.02;

    /**
     * Post inradius per tier (§10.4): twig and wood 12-sided logs, stone and armored 3.0, metal 2.75 square posts.
     * Every post contains the disc of radius 2.5 around the vertex, where every wall body ends.
     */
    static double postHalf(Tier tier) {
        return switch (tier) {
            case TWIG, WOOD, METAL -> 2.75;
            case STONE, ARMORED -> 3.0;
        };
    }

    /**
     * Post outline around (cx, cz) in the wall's edge frame: a 12-gon whose faces are normal to the 12 lattice
     * directions (logs), or a square aligned with the owner wall.
     */
    private static double[][] postShape(Tier tier, double cx, double cz) {
        double a = postHalf(tier);
        if (tier == Tier.WOOD || tier == Tier.TWIG) {
            double r = a / COS15;
            double[][] p = new double[12][];
            for (int k = 0; k < 12; k++) {
                // vertex at 15° + 30° k
                double x = COS15 * Dirs.ex(k) - SIN15 * Dirs.ez(k);
                double z = SIN15 * Dirs.ex(k) + COS15 * Dirs.ez(k);
                p[k] = new double[]{cx + r * x, cz + r * z};
            }
            return p;
        }
        return new double[][]{{cx - a, cz - a}, {cx + a, cz - a}, {cx + a, cz + a}, {cx - a, cz + a}};
    }

    private static void wall(MeshBuilder b, PieceKey key, Tier tier, int state, long variant) {
        EdgeFrame ef = EdgeFrame.of(key.rot());
        MeshBuilder.Frame f = MeshBuilder.Frame.of(ef);
        PieceKind kind = key.kind();
        double len = EdgeFrame.LENGTH;
        double c = len / 2;
        boolean half = kind == PieceKind.HALF_WALL;
        double y0 = WALL_BOTTOM;
        double y1 = half ? HALF_TOP : WALL_TOP;
        double us = WALL_END;
        double ue = len - us;
        double ur = RELIEF_START;
        // walls of different directions cross outside the posts: their horizontal faces are kept apart (§10.3)
        MeshBuilder w = new MeshBuilder();
        double ow = kind == PieceKind.DOUBLE_DOORWAY ? PieceSolids.DOOR_W : PieceSolids.DOOR_W / 2;
        double sill = PieceSolids.WINDOW_SILL;
        double head = PieceSolids.WINDOW_HEAD;
        double doorH = PieceSolids.DOOR_H;

        List<double[]> prof = new ArrayList<>();
        switch (kind) {
            case DOORWAY, DOUBLE_DOORWAY -> {
                prof.add(new double[]{us, y0, c - ow, y1});
                prof.add(new double[]{c + ow, y0, ue, y1});
                prof.add(new double[]{c - ow, doorH, c + ow, y1});
            }
            case WINDOW -> {
                prof.add(new double[]{us, y0, c - ow, y1});
                prof.add(new double[]{c + ow, y0, ue, y1});
                prof.add(new double[]{c - ow, y0, c + ow, sill});
                prof.add(new double[]{c - ow, head, c + ow, y1});
            }
            default -> prof.add(new double[]{us, y0, ue, y1});
        }
        if (tier == Tier.TWIG) {
            TwigMeshes.wall(w, f, kind, prof, us, ue, y1, c, ow, key.orient() * 3 + key.rot() + kind.ordinal() * 7);
            centreCaps(w, f, prof, half ? 24 : 48);
        } else {
            wallBody(w, f, prof, us, ue, half ? 24 : 48, true);

            // relief on both faces
            List<double[]> relief = wallRelief(kind, tier, prof, ur, len - ur, c, ow, y1);
            MeshBuilder.Style hs = new MeshBuilder.Style(Mat.BEAM).scale(16, 16).edges(16, 16).contact(0);
            hs.capAlongU = false;
            MeshBuilder.Style vs = new MeshBuilder.Style(Mat.BEAM).scale(16, 16).edges(16, 16).contact(0).vertical();
            for (double[] r : relief) {
                MeshBuilder.Style s = r[5] > 0 ? vs : hs;
                w.box(f, r[0], r[2], T, T + r[4], r[1], r[3], s, MeshBuilder.SKIP_NNEG);
                w.box(f, r[0], r[2], -T - r[4], -T, r[1], r[3], s, MeshBuilder.SKIP_NPOS);
            }
        }

        if (kind.hasDoor()) {
            // the stops belong to the frame: they drop with the body
            doorStops(w, f, c, ow, tier);
        } else if (kind == PieceKind.WINDOW) {
            // the insert sits on the sill and reaches into the head: it drops with them, or a slit opens above the sill
            int insert = (state & Piece.INSERT_MASK) >> Piece.INSERT_SHIFT;
            if (insert == Piece.INSERT_BARS) {
                bars(w, f, c, ow, sill, head);
            } else if (insert == Piece.INSERT_GLASS) {
                glass(w, f, c, ow, sill, head);
            }
        }

        double drop = BAND_DROP * key.orient();
        for (MeshFace face : w.faces) {
            b.faces.add(drop == 0 ? face : face.translate(0, -drop, 0));
        }

        boolean open = (state & Piece.STATE_OPEN) != 0;
        if (kind == PieceKind.DOORWAY) {
            doorLeaf(b, f, c - ow, false, open, 2 * ow - LEAF_GAP, tier, false);
        } else if (kind == PieceKind.DOUBLE_DOORWAY) {
            doorLeaf(b, f, c - ow, false, open, ow - LEAF_MEET, tier, false);
            doorLeaf(b, f, c + ow, true, open, ow - LEAF_MEET, tier, true);
        }

        // corner posts per owned 24 px band (§6.5): end A is the edge's start (local u = 0), end B its end (u = 48)
        int bands = key.topUnits();
        for (int end = 0; end < 2; end++) {
            double pu = end == 0 ? 0 : len;
            int from = -1;
            for (int band = 0; band <= bands; band++) {
                boolean owned = band < bands && (variant & (1L << (2 * end + band))) != 0;
                if (owned && from < 0) {
                    from = band;
                } else if (!owned && from >= 0) {
                    post(b, pu, 0, 24 * from + POST_BOTTOM, 24 * band + POST_BOTTOM, tier);
                    from = -1;
                }
            }
        }
    }

    /**
     * Wall slab: the union of non-overlapping (u, y) rectangles extruded over n in [-T, T]. The two ends (u = us and
     * u = ue) are always inside a corner post and get no face. A straight wall is also closed on its edge plane (n = 0,
     * the border between its two block columns): the server leaves out a half buried in terrain, and those faces are
     * culled whenever the other half is there.
     */
    private static void wallBody(MeshBuilder b, MeshBuilder.Frame f, List<double[]> prof, double us, double ue, double top, boolean capEdge) {
        double ax = f.ax();
        double az = f.az();
        double nx = f.nx();
        double nz = f.nz();
        for (double[] r : prof) {
            b.vert(Mat.WALL, quadN(f, r, T), nx, nz, 48, 48, 48, false, 1f, 0);
            b.vert(Mat.WALL, quadN(f, r, -T), -nx, -nz, 48, 48, 48, false, 1f, 0);
        }
        if (capEdge) {
            centreCaps(b, f, prof, top);
        }
        for (double[] r : prof) {
            // vertical sides at u0 (outward -u) and u1 (+u)
            for (double[] seg : r[0] > us ? free(prof, r, 0) : List.<double[]>of()) {
                b.vert(Mat.TRIM, quadU(f, r[0], seg[0], seg[1]), -ax, -az, 16, 16, 48, false, 1f, 0);
            }
            for (double[] seg : r[2] < ue ? free(prof, r, 2) : List.<double[]>of()) {
                b.vert(Mat.TRIM, quadU(f, r[2], seg[0], seg[1]), ax, az, 16, 16, 48, false, 1f, 0);
            }
            for (double[] seg : free(prof, r, 1)) {
                b.horiz(Mat.TRIM, quadY(f, r[1], seg[0], seg[1]), -1, nx, nz, 16, 16, 0.9f);
            }
            for (double[] seg : free(prof, r, 3)) {
                b.horiz(Mat.TRIM, quadY(f, r[3], seg[0], seg[1]), 1, nx, nz, 16, 16, 1f);
            }
        }
    }

    /**
     * TRIM caps on the wall's centre plane (n = 0) so that a half left out by the server (buried in terrain) still
     * shows a closed face (§6.6). Kept off the slab, floor and joist ends that also lie on the edge plane (foundation
     * top 0, joists from 43.15); each cap closes the half behind it, a hair inside that half: never in the plane of a
     * floor's edge face (a floor on a half wall abuts the full wall beside it along that plane).
     */
    static void centreCaps(MeshBuilder b, MeshBuilder.Frame f, List<double[]> prof, double top) {
        for (double[] r : prof) {
            double[] cap = {r[0], Math.max(r[1], CAP_BOTTOM), r[2], Math.min(r[3], top - CAP_BELOW_TOP)};
            if (cap[3] - cap[1] > 0.5) {
                b.vert(Mat.TRIM, quadN(f, cap, -CAP_INSET), f.nx(), f.nz(), 16, 16, 48, false, 0.9f, 0);
                b.vert(Mat.TRIM, quadN(f, cap, CAP_INSET), -f.nx(), -f.nz(), 16, 16, 48, false, 0.9f, 0);
            }
        }
    }

    private static double[][] quadN(MeshBuilder.Frame f, double[] r, double n) {
        return new double[][]{f.p(r[0], n, r[1]), f.p(r[2], n, r[1]), f.p(r[2], n, r[3]), f.p(r[0], n, r[3])};
    }

    private static double[][] quadU(MeshBuilder.Frame f, double u, double ya, double yb) {
        return new double[][]{f.p(u, -T, ya), f.p(u, T, ya), f.p(u, T, yb), f.p(u, -T, yb)};
    }

    private static double[][] quadY(MeshBuilder.Frame f, double y, double ua, double ub) {
        return new double[][]{f.p(ua, -T, y), f.p(ub, -T, y), f.p(ub, T, y), f.p(ua, T, y)};
    }

    /**
     * Parts of one side of rectangle {@code r} (side index into {u0, y0, u1, y1}) not shared with another rectangle
     * of the profile; returned as intervals along the side.
     */
    static List<double[]> free(List<double[]> prof, double[] r, int side) {
        boolean vertical = side == 0 || side == 2;
        double at = r[side];
        int opposite = (side + 2) % 4;
        List<double[]> segs = new ArrayList<>();
        segs.add(vertical ? new double[]{r[1], r[3]} : new double[]{r[0], r[2]});
        for (double[] q : prof) {
            if (q == r || Math.abs(q[opposite] - at) > 1e-9) continue;
            double a = vertical ? q[1] : q[0];
            double bb = vertical ? q[3] : q[2];
            List<double[]> next = new ArrayList<>();
            for (double[] s : segs) {
                if (bb <= s[0] || a >= s[1]) {
                    next.add(s);
                    continue;
                }
                if (a > s[0]) next.add(new double[]{s[0], a});
                if (bb < s[1]) next.add(new double[]{bb, s[1]});
            }
            segs = next;
        }
        segs.removeIf(s -> s[1] - s[0] < 1e-6);
        return segs;
    }

    /**
     * Relief rectangles {u0, y0, u1, y1, depth, vertical} drawn on both faces: cross beams, plinths and ribs cut by
     * the openings, plus frames, lintels and sills around the openings. None of them overlap.
     */
    private static List<double[]> wallRelief(PieceKind kind, Tier tier, List<double[]> prof, double us, double ue, double c, double ow, double y1) {
        boolean full = y1 > 40;
        List<double[]> bands = new ArrayList<>();
        List<double[]> frames = new ArrayList<>();
        boolean door = kind.hasDoor();
        boolean window = kind == PieceKind.WINDOW;
        double sill = PieceSolids.WINDOW_SILL;
        double head = door ? PieceSolids.DOOR_H : PieceSolids.WINDOW_HEAD;
        switch (tier) {
            case TWIG -> {
            }
            case WOOD -> {
                double d = 1.0;
                bands.add(new double[]{4, 8, d});
                bands.add(full ? new double[]{38, 42, d} : new double[]{15, 19, d});
                if (door || window) {
                    double bottom = door ? 0 : sill - 2;
                    frames.add(new double[]{c - ow - 2, bottom, c - ow, head, 0.75, 1});
                    frames.add(new double[]{c + ow, bottom, c + ow + 2, head, 0.75, 1});
                    frames.add(new double[]{c - ow - 2.5, head, c + ow + 2.5, head + 3, 1.0, 0});
                    if (window) {
                        frames.add(new double[]{c - ow - 3, sill - 3.5, c + ow + 3, sill - 2, 1.25, 0});
                    }
                }
            }
            case STONE -> {
                bands.add(new double[]{WALL_BOTTOM, 3, 0.75});
                if (door || window) {
                    frames.add(new double[]{c - ow - 4, head, c + ow + 4, head + 6, 0.75, 0});
                    if (window) {
                        frames.add(new double[]{c - ow - 3, sill - 3, c + ow + 3, sill, 0.75, 0});
                    }
                }
            }
            case METAL -> {
                double d = 0.75;
                bands.add(new double[]{0.5, 3.5, d});
                if (full) {
                    bands.add(new double[]{43, 46, d});
                    bands.add(new double[]{22, 25, 0.5});
                } else {
                    bands.add(new double[]{19, 22, d});
                }
                if (door || window) {
                    double bottom = door ? 0 : sill;
                    frames.add(new double[]{c - ow - 2, bottom, c - ow, head, d, 1});
                    frames.add(new double[]{c + ow, bottom, c + ow + 2, head, d, 1});
                    frames.add(new double[]{c - ow - 2, head, c + ow + 2, head + 2, d, 0});
                    if (window) {
                        frames.add(new double[]{c - ow - 2, sill - 2, c + ow + 2, sill, d, 0});
                    }
                }
            }
            case ARMORED -> {
                double d = 1.25;
                bands.add(new double[]{7, 11, d});
                if (full) {
                    bands.add(new double[]{36, 40, d});
                    if (kind == PieceKind.WALL) {
                        frames.add(new double[]{c - 1.5, 11, c + 1.5, 36, 1.0, 1});
                    }
                } else {
                    bands.add(new double[]{18, 21, d});
                }
                if (door || window) {
                    double bottom = door ? 0 : sill;
                    frames.add(new double[]{c - ow - 2.5, bottom, c - ow, head, d, 1});
                    frames.add(new double[]{c + ow, bottom, c + ow + 2.5, head, d, 1});
                    frames.add(new double[]{c - ow - 2.5, head, c + ow + 2.5, head + 2.5, d, 0});
                    if (window) {
                        frames.add(new double[]{c - ow - 2.5, sill - 2.5, c + ow + 2.5, sill, d, 0});
                    }
                }
            }
        }
        List<double[]> out = new ArrayList<>();
        for (double[] band : bands) {
            List<double[]> pieces = new ArrayList<>();
            for (double[] r : prof) {
                double a0 = Math.max(us, r[0]);
                double a1 = Math.min(ue, r[2]);
                double b0 = Math.max(band[0], r[1]);
                double b1 = Math.min(band[1], r[3]);
                if (a1 - a0 > 1e-6 && b1 - b0 > 1e-6) {
                    pieces.add(new double[]{a0, b0, a1, b1, band[2], 0});
                }
            }
            for (double[] fr : frames) {
                pieces = subtract(pieces, fr);
            }
            out.addAll(merge(pieces));
        }
        for (double[] fr : frames) {
            double a0 = Math.max(us, fr[0]);
            double a1 = Math.min(ue, fr[2]);
            double b0 = Math.max(WALL_BOTTOM, fr[1]);
            double b1 = Math.min(y1, fr[3]);
            if (a1 - a0 > 1e-6 && b1 - b0 > 1e-6) {
                out.add(new double[]{a0, b0, a1, b1, fr[4], fr[5]});
            }
        }
        return out;
    }

    private static List<double[]> subtract(List<double[]> list, double[] b) {
        List<double[]> out = new ArrayList<>();
        for (double[] a : list) {
            if (b[2] <= a[0] || b[0] >= a[2] || b[3] <= a[1] || b[1] >= a[3]) {
                out.add(a);
                continue;
            }
            if (b[0] > a[0]) out.add(new double[]{a[0], a[1], b[0], a[3], a[4], a[5]});
            if (b[2] < a[2]) out.add(new double[]{b[2], a[1], a[2], a[3], a[4], a[5]});
            double l = Math.max(a[0], b[0]);
            double r = Math.min(a[2], b[2]);
            if (b[1] > a[1]) out.add(new double[]{l, a[1], r, b[1], a[4], a[5]});
            if (b[3] < a[3]) out.add(new double[]{l, b[3], r, a[3], a[4], a[5]});
        }
        out.removeIf(r -> r[2] - r[0] < 0.25 || r[3] - r[1] < 0.25);
        return out;
    }

    private static List<double[]> merge(List<double[]> pieces) {
        pieces.sort((p, q) -> Double.compare(p[0], q[0]));
        List<double[]> out = new ArrayList<>();
        for (double[] p : pieces) {
            if (!out.isEmpty()) {
                double[] last = out.get(out.size() - 1);
                if (Math.abs(last[2] - p[0]) < 1e-9 && Math.abs(last[1] - p[1]) < 1e-9 && Math.abs(last[3] - p[3]) < 1e-9) {
                    last[2] = p[2];
                    continue;
                }
            }
            out.add(p.clone());
        }
        return out;
    }

    private static void post(MeshBuilder b, double cx, double cz, double y0, double y1, Tier tier) {
        double[][] shape = postShape(tier, cx, cz);
        double a = postHalf(tier);
        int n = shape.length;
        double arc = 0;
        double perimeter = 0;
        for (int i = 0; i < n; i++) {
            double[] p = shape[i];
            double[] q = shape[(i + 1) % n];
            perimeter += Math.hypot(q[0] - p[0], q[1] - p[1]);
        }
        for (int i = 0; i < n; i++) {
            double[] p = shape[i];
            double[] q = shape[(i + 1) % n];
            double ex = q[0] - p[0];
            double ez = q[1] - p[1];
            double l = Math.hypot(ex, ez);
            double ox = ez / l;
            double oz = -ex / l;
            double[][] pts = {{p[0], y0, p[1]}, {q[0], y0, q[1]}, {q[0], y1, q[1]}, {p[0], y1, p[1]}};
            if (tier == Tier.WOOD || tier == Tier.TWIG) {
                // bark wraps once around the log; walking p -> q goes leftwards seen from outside
                double sp = 1 - arc / perimeter;
                double sq = 1 - (arc + l) / perimeter;
                double[][] uv = {{sp, (48 - y0) / 16}, {sq, (48 - y0) / 16}, {sq, (48 - y1) / 16}, {sp, (48 - y1) / 16}};
                b.poly(Mat.POST, pts, uv, ox, 0, oz, 1f, 0);
            } else {
                b.vert(Mat.POST, pts, ox, oz, 16, 16, 48, true, 1f, 0);
            }
            arc += l;
        }
        double[][] top = new double[n][];
        double[][] bot = new double[n][];
        double[][] uv = new double[n][];
        for (int i = 0; i < n; i++) {
            top[i] = new double[]{shape[i][0], y1, shape[i][1]};
            bot[i] = new double[]{shape[i][0], y0, shape[i][1]};
            uv[i] = new double[]{(shape[i][0] - cx + a) / (2 * a), (shape[i][1] - cz + a) / (2 * a)};
        }
        b.poly(Mat.POST_TOP, top, uv, 0, 1, 0, 1f, Double.NaN);
        b.poly(Mat.POST_TOP, bot, uv, 0, -1, 0, 0.85f, Double.NaN);
    }

    /** Gap between a closed leaf and the jamb or head of its opening (§10.5), px. */
    public static final double LEAF_GAP = 0.02;
    /** Half the gap where the two leaves of a double door meet. */
    public static final double LEAF_MEET = 0.005;
    /** Closed leaves reach this far down into the slab below the doorway, inside every floor and foundation. */
    public static final double LEAF_FOOT = PieceSolids.RENDER_FOOT;
    /** Door stops: how far they reach into the opening from each jamb and down from the head, px. */
    public static final double STOP = 1.25;
    /** Stops sit on the side opposite the swing, between the wall face and the leaf: n in [-WALL_HALF, -LEAF_HALF]. */
    public static final double STOP_N0 = -PieceSolids.WALL_HALF;
    public static final double STOP_N1 = -PieceSolids.LEAF_HALF;
    /** The astragal of a double door: 1.5 px wide on the stop side of the second leaf, over the meeting line. */
    public static final double ASTRAGAL_W = 1.5;
    public static final double ASTRAGAL_N0 = -1.75;
    /** The astragal reaches a hair into its own leaf so that its inner face is never in the plane of the head stop. */
    public static final double ASTRAGAL_N1 = -0.9;

    /**
     * Door stops (§10.5) on the side opposite the swing (n in [-2, -1]): a strip on each jamb reaching {@link #STOP} px
     * into the opening, and one under the head, so any line of sight through the crack between a closed leaf and its
     * frame ends on a stop.
     */
    private static void doorStops(MeshBuilder w, MeshBuilder.Frame f, double c, double ow, Tier tier) {
        Mat m = tier == Tier.TWIG ? Mat.BEAM : Mat.TRIM;
        MeshBuilder.Style vs = new MeshBuilder.Style(m).scale(16, 16).edges(16, 16).contact(0).vertical();
        MeshBuilder.Style hs = new MeshBuilder.Style(m).scale(16, 16).edges(16, 16).contact(0);
        hs.capAlongU = false;
        double head = PieceSolids.DOOR_H - STOP;
        // every face is drawn (the ones against the frame face the other way): a groove of a straw jamb never shows
        // the inside of a stop
        w.box(f, c - ow, c - ow + STOP, STOP_N0, STOP_N1, WALL_BOTTOM, head, vs, MeshBuilder.SKIP_BOTTOM | MeshBuilder.SKIP_TOP);
        w.box(f, c + ow - STOP, c + ow, STOP_N0, STOP_N1, WALL_BOTTOM, head, vs, MeshBuilder.SKIP_BOTTOM | MeshBuilder.SKIP_TOP);
        w.box(f, c - ow, c + ow, STOP_N0, STOP_N1, head, PieceSolids.DOOR_H, hs, 0);
    }

    /**
     * One door leaf hinged at {@code hinge} (wall frame u), extending towards +u, or towards -u when {@code right}, over
     * leaf u in [{@link #LEAF_GAP}, {@code width}], y in [{@link #LEAF_FOOT}, DOOR_H - LEAF_GAP], n in [-1, 1] (§10.5).
     * Open leaves fold beside the opening on the +n side, flat against the jamb (§5.4; collision u in
     * [hinge - 2, hinge] (or [hinge, hinge + 2]), n in [2, 16]): hinged at the jamb's outer corner, the leaf starts
     * 0.45 px off the wall plane, inside the jamb, so it ends within the collision's 16 px plus the envelope's slack.
     * {@code astragal}: the second leaf of a double door carries the strip covering the meeting line.
     */
    private static void doorLeaf(MeshBuilder b, MeshBuilder.Frame f, double hinge, boolean right, boolean open, double width, Tier tier,
                                 boolean astragal) {
        double dir = right ? -1 : 1;
        MeshBuilder.Frame lf;
        if (!open) {
            lf = f.sub(hinge, 0, dir, 0, 0, 1);
        } else {
            // leaf u axis -> wall +n, leaf n axis -> wall -dir*u
            lf = f.sub(hinge - dir * PieceSolids.LEAF_HALF, 0.45, 0, 1, -dir, 0);
        }
        double w0 = LEAF_GAP;
        double yb = LEAF_FOOT;
        double yt = PieceSolids.DOOR_H - LEAF_GAP;
        double l = PieceSolids.LEAF_HALF;
        MeshBuilder.Style ds = new MeshBuilder.Style(Mat.DOOR).scale(16, 32).top(PieceSolids.DOOR_H).local().contact(0);
        ds.end = Mat.TRIM;
        ds.top = Mat.TRIM;
        ds.bottom = Mat.TRIM;
        b.box(lf, w0, width, -l, l, yb, yt, ds, 0);
        if (astragal) {
            MeshBuilder.Style as = new MeshBuilder.Style(Mat.TRIM).scale(16, 16).edges(16, 16).contact(0).vertical();
            double a = width + LEAF_MEET;
            b.box(lf, a - ASTRAGAL_W / 2, a + ASTRAGAL_W / 2, ASTRAGAL_N0, ASTRAGAL_N1, yb, yt, as, MeshBuilder.SKIP_NPOS | MeshBuilder.SKIP_BOTTOM);
        }
        if (tier == Tier.TWIG) {
            TwigMeshes.doorFrame(b, lf, w0, width, yb, yt, astragal);
            return;
        }
        MeshBuilder.Style is = new MeshBuilder.Style(Mat.IRON).scale(16, 16).edges(16, 16);
        // hinge straps on the swing face, handles on both faces (clear of the stops on the other face)
        for (double hy : new double[]{5, 24}) {
            b.box(lf, w0 + 0.25, w0 + 6, l, l + 0.35, hy, hy + 2.5, is, MeshBuilder.SKIP_NNEG);
        }
        double hu = width - 3;
        b.box(lf, hu, hu + 1, l, l + 1.25, 14.5, 17.5, is, MeshBuilder.SKIP_NNEG);
        b.box(lf, hu, hu + 1, -l - 1.25, -l, 14.5, 17.5, is, MeshBuilder.SKIP_NPOS);
    }

    private static void bars(MeshBuilder b, MeshBuilder.Frame f, double c, double ow, double sill, double head) {
        MeshBuilder.Style is = new MeshBuilder.Style(Mat.IRON).scale(16, 16).edges(16, 16);
        int count = 4;
        double r = 0.75;
        for (int i = 0; i < count; i++) {
            double u = c - ow + 2 * ow * (i + 1) / (count + 1);
            b.box(f, u - r, u + r, -r, r, sill, head, is, MeshBuilder.SKIP_TOP | MeshBuilder.SKIP_BOTTOM);
        }
        double ym = (sill + head) / 2;
        b.box(f, c - ow, c + ow, -1, 1, ym - 0.75, ym + 0.75, is, MeshBuilder.SKIP_UPOS | MeshBuilder.SKIP_UNEG);
    }

    private static void glass(MeshBuilder b, MeshBuilder.Frame f, double c, double ow, double sill, double head) {
        MeshBuilder.Style fs = new MeshBuilder.Style(Mat.TRIM).scale(16, 16).edges(16, 16);
        double fn = 0.75;
        double w = 1.0;
        int skipEnds = MeshBuilder.SKIP_TOP | MeshBuilder.SKIP_BOTTOM;
        b.box(f, c - ow, c - ow + w, -fn, fn, sill, head, fs, skipEnds | MeshBuilder.SKIP_UNEG);
        b.box(f, c + ow - w, c + ow, -fn, fn, sill, head, fs, skipEnds | MeshBuilder.SKIP_UPOS);
        int skipSides = MeshBuilder.SKIP_UPOS | MeshBuilder.SKIP_UNEG;
        b.box(f, c - ow + w, c + ow - w, -fn, fn, sill, sill + w, fs, skipSides | MeshBuilder.SKIP_BOTTOM);
        b.box(f, c - ow + w, c + ow - w, -fn, fn, head - w, head, fs, skipSides | MeshBuilder.SKIP_TOP);
        double ym = (sill + head) / 2;
        b.box(f, c - ow + w, c + ow - w, -0.5, 0.5, ym - 0.5, ym + 0.5, fs, skipSides);
        double g = 0.25;
        double[][] lo = {{c - ow + w, sill + w}, {c + ow - w, ym - 0.5}};
        double[][] hi = {{c - ow + w, ym + 0.5}, {c + ow - w, head - w}};
        for (double[][] pane : new double[][][]{lo, hi}) {
            double[] r = {pane[0][0], pane[0][1], pane[1][0], pane[1][1]};
            b.vert(Mat.GLASS, quadN(f, r, g), f.nx(), f.nz(), 16, 16, head, true, 1f, Double.NaN);
            b.vert(Mat.GLASS, quadN(f, r, -g), -f.nx(), -f.nz(), 16, 16, head, true, 1f, Double.NaN);
        }
    }
}
