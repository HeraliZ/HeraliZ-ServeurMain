package fr.heraliz.crac.build.geom;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import fr.heraliz.crac.build.model.Dirs;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * Top surface of a roof piece: a 45° hip-roof height field over the union of the roofs at the same h of the same
 * building, capped at {@link #PEAK}. Everything is computed in the piece's local frame from its shape and its
 * variant, so one field serves every roof with the same neighbourhood.
 * <p>
 * <b>Variant</b>: for each vertex i of the footprint (canonical order), the 12-bit mask of the 30° sectors around
 * that vertex covered by roofs sharing that exact vertex, in local directions; bits {@code 12i..12i+11}. Sector s
 * spans the directions [s, s+1). The piece's own sectors are always set.
 * <p>
 * <b>Boundary elements</b>: the maximal run [a, b) of covered sectors at a vertex that contains the piece's own
 * sectors yields two rays from it, direction a (covered side +) and direction b (covered side -), paired, with the
 * interior angle {@code (b - a) * 30°}; other runs at that vertex (pinches) are ignored. A ray
 * along one of the footprint's own edges is that edge (both ends known); other rays are neighbours' edges, 48 px
 * long, far end treated as convex.
 * <p>
 * <b>Height</b>: {@code clamp(min_e D_e(p), 0, PEAK)}, with {@code D_e = d_e} (distance to the element's line,
 * positive on the covered side) when the foot is on the segment and {@code d_e >= 0}; beyond a reflex end W (paired
 * angle above 180°) {@code D_e = max(d_e, d_e', reach_W(p))} with e' the paired element (see {@link #REFLEX_SLACK});
 * otherwise {@code +inf}. This is the straight-skeleton roof for our cases: planar faces, sharp ridges and valleys.
 * <p>
 * <b>Facets</b> (render) and <b>collision</b> come from the same height function, see {@link #facets()} and
 * {@link #collision()}.
 */
public final class RoofField {
    private static final Logger LOGGER = LogManager.getLogger(RoofField.class);
    /** Highest point above the base. Seams between neighbours match only while it is at most half an edge. */
    public static final double PEAK = PieceSolids.ROOF_PEAK;
    /** Collision grid step in the local frame (px). */
    public static final double GRID = 4.0;
    /** Ties and degenerate splits below this are ignored. */
    public static final double TIE = 1e-9;
    public static final int MAX_DEPTH = 32;
    /**
     * Beyond a reflex vertex W the valley height {@code max(d_e, d_e')} is also bounded below by its reach
     * {@code max_k ((p - W) . e(k)) - REFLEX_SLACK} over the lattice directions pointing behind both rays. A
     * piece only knows the vertices it touches, so a valley must reach {@link #PEAK} before any edge of the piece not
     * incident to W (at least {@code 24 sqrt3} px away): {@code 0.966 * 41.57 - 16 >= 24}. The common L-shaped (90°)
     * valley reaches the peak long before this matters; 30° and 60° notches get a short crease.
     */
    public static final double REFLEX_SLACK = 16.0;
    private static final double INF = Double.POSITIVE_INFINITY;
    private static final LoadingCache<Long, RoofField> SQUARES = cache(PieceKind.Shape.SQUARE);
    private static final LoadingCache<Long, RoofField> TRIANGLES = cache(PieceKind.Shape.TRIANGLE);

    static {
        if (PEAK > Footprints.SIDE / 2) {
            throw new IllegalStateException("ROOF_PEAK must not exceed half an edge, roof seams rely on it");
        }
    }

    /**
     * A reflex end (paired angle above 180°) of an element: the vertex W, the paired element's unit normal and the
     * lattice directions {@code k0..k1} (those pointing behind both rays) of its {@link #REFLEX_SLACK reach}.
     */
    public record Reflex(double wx, double wz, double nx, double nz, int k0, int k1) {
        /** {@code max(d_e, d_e', reach)} at (u, v), given {@code d = d_e}. */
        double bound(double u, double v, double d) {
            double dx = u - this.wx;
            double dz = v - this.wz;
            double m = Math.max(d, dx * this.nx + dz * this.nz);
            for (int k = this.k0; k <= this.k1; k++) {
                m = Math.max(m, dx * Dirs.ex(k) + dz * Dirs.ez(k) - REFLEX_SLACK);
            }
            return m;
        }
    }

    /**
     * One boundary element: a segment from (px, pz) along the unit vector (tx, tz), covered side along the unit
     * normal (nx, nz). {@code start} / {@code end} are null at convex or straight ends (and at the far end of a
     * neighbour's edge, treated as convex).
     */
    public record Element(double px, double pz, double tx, double tz, double nx, double nz, double length,
                          @Nullable Reflex start, @Nullable Reflex end) {
        double t(double u, double v) {
            return (u - this.px) * this.tx + (v - this.pz) * this.tz;
        }

        double d(double u, double v) {
            return (u - this.px) * this.nx + (v - this.pz) * this.nz;
        }

        /** {@code D_e(p)}. */
        public double bound(double u, double v) {
            double t = this.t(u, v);
            double d = this.d(u, v);
            if (t >= -TIE && t <= this.length + TIE) {
                // points on the element itself (d = 0 up to rounding) are at the eaves
                return d >= -TIE ? d : INF;
            }
            Reflex r = t < 0 ? this.start : this.end;
            return r == null ? INF : r.bound(u, v, d);
        }
    }

    /**
     * A planar facet of the roof top: local polygon {u, v}[] (positive orientation), the height at each vertex and the
     * plane {@code y = a u + b v + c}.
     */
    public record Facet(double[][] poly, double[] y, double a, double b, double c) {
        public double height(double u, double v) {
            return this.a * u + this.b * v + this.c;
        }
    }

    /** Linear candidate {@code g = a u + b v + c}, valid where every half-plane {@code hp . (u, v, 1) >= 0}. */
    private record Cand(double a, double b, double c, double[][] valid) {
        double g(double u, double v) {
            return this.a * u + this.b * v + this.c;
        }
    }

    private final PieceKind.Shape shape;
    private final long variant;
    private final double[][] poly;
    private final List<Element> elements;
    private volatile List<Facet> facets;
    private volatile List<Prism> collision;
    private volatile List<Prism> occlusion;

    private RoofField(PieceKind.Shape shape, long variant) {
        this.shape = shape;
        this.variant = normalize(shape, variant);
        this.poly = Footprints.local(shape);
        this.elements = List.copyOf(buildElements());
    }

    private static LoadingCache<Long, RoofField> cache(PieceKind.Shape shape) {
        return CacheBuilder.newBuilder().maximumSize(2048).build(new CacheLoader<>() {
            @Override
            public RoofField load(Long variant) {
                RoofField f = new RoofField(shape, variant);
                f.collision();
                return f;
            }
        });
    }

    /** The (cached) field of a square or triangle roof with the given variant. */
    public static RoofField of(PieceKind.Shape shape, long variant) {
        long v = normalize(shape, variant);
        return switch (shape) {
            case SQUARE -> SQUARES.getUnchecked(v);
            case TRIANGLE -> TRIANGLES.getUnchecked(v);
            case EDGE -> throw new IllegalArgumentException("walls have no roof field");
        };
    }

    public static void clearCache() {
        SQUARES.invalidateAll();
        TRIANGLES.invalidateAll();
    }

    public PieceKind.Shape shape() {
        return this.shape;
    }

    public long variant() {
        return this.variant;
    }

    public List<Element> elements() {
        return this.elements;
    }

    // ---------------------------------------------------------------- sector masks

    /** Own sectors of vertex i, in local directions. */
    public static int ownSectors(PieceKind.Shape shape, int i) {
        int out = Footprints.localOut(shape, i);
        int m = 0;
        for (int s = 0; s < shape.sectors; s++) {
            m |= 1 << Dirs.norm(out + s);
        }
        return m;
    }

    /** Variant of a roof with no neighbour (only its own sectors). */
    public static long ownVariant(PieceKind.Shape shape) {
        long v = 0;
        for (int i = 0; i < shape.vertices; i++) {
            v |= (long) ownSectors(shape, i) << (12 * i);
        }
        return v;
    }

    /** Keeps the bits of existing vertices and always sets the own sectors. */
    public static long normalize(PieceKind.Shape shape, long variant) {
        long mask = shape.vertices >= 4 ? 0xFFFF_FFFF_FFFFL : 0xF_FFFF_FFFFL;
        return (variant & mask) | ownVariant(shape);
    }

    /** 12-bit mask of vertex i. */
    public static int mask(long variant, int i) {
        return (int) (variant >>> (12 * i)) & 0xFFF;
    }

    /** Rotates a 12-bit sector mask by {@code by} steps (bit s moves to bit s + by). */
    public static int rotateMask(int m, int by) {
        int r = Dirs.norm(by);
        return ((m << r) | (m >>> (12 - r))) & 0xFFF;
    }

    /**
     * Sectors (12-bit mask, building directions) the footprint of {@code key} covers around {@code p}; 0 when p is
     * not one of its vertices.
     */
    public static int sectorsAt(PieceKey key, LPoint p) {
        LPoint[] v = key.lattice();
        for (int i = 0; i < v.length; i++) {
            if (v[i].equals(p)) {
                int m = 0;
                for (int s = 0; s < key.kind().shape.sectors; s++) {
                    m |= 1 << Dirs.norm(key.edgeDir(i) + s);
                }
                return m;
            }
        }
        return 0;
    }

    /**
     * Variant of {@code roof} given the roof pieces around it. Pieces of another building origin or height, and the
     * walls among {@code roofs}, are ignored; {@code roof} itself may be included.
     */
    public static long variant(PieceKey roof, Iterable<PieceKey> roofs) {
        LPoint[] v = roof.lattice();
        long out = 0;
        for (int i = 0; i < v.length; i++) {
            int m = 0;
            for (PieceKey q : roofs) {
                if (q.kind().isRoof() && q.h() == roof.h() && q.origin().equals(roof.origin())) {
                    m |= sectorsAt(q, v[i]);
                }
            }
            out |= (long) rotateMask(m, -roof.orient()) << (12 * i);
        }
        return normalize(roof.kind().shape, out);
    }

    // ---------------------------------------------------------------- elements

    /** A boundary ray of a run [a, b) of covered sectors; {@code k0..k1} = a+3..b-3 for a reflex run. */
    private record Ray(int dir, boolean plus, int partner, boolean reflex, int k0, int k1) {
    }

    /**
     * The two rays of the run of covered sectors at vertex i that contains the piece's own sectors. Other runs at the
     * same vertex are separated from it by uncovered sectors: they share no edge with this piece, the piece lies in
     * their uncovered notch, and their neighbours across this piece's edges do not know them, so they are ignored
     * (a reflex one would otherwise pull this piece down to its valley, see {@link Reflex#bound}).
     */
    private List<Ray> rays(int i) {
        int own = ownSectors(this.shape, i);
        int m = mask(this.variant, i) | own;
        List<Ray> out = new ArrayList<>(2);
        if (m == 0xFFF) {
            return out;
        }
        // every own sector is in that run: walk from one of them to both ends
        int s0 = Integer.numberOfTrailingZeros(own);
        int a = s0;
        while ((m & (1 << Dirs.norm(a - 1))) != 0) {
            a--;
        }
        int b = s0 + 1;
        while ((m & (1 << Dirs.norm(b))) != 0) {
            b++;
        }
        b = Dirs.norm(a) + (b - a);
        a = Dirs.norm(a);
        boolean reflex = b - a > 6;
        out.add(new Ray(a, true, Dirs.norm(b), reflex, a + 3, b - 3));
        out.add(new Ray(Dirs.norm(b), false, a, reflex, a + 3, b - 3));
        return out;
    }

    private static double[] normal(Ray r) {
        int k = r.plus ? r.dir + 3 : r.dir - 3;
        return new double[]{Dirs.ex(k), Dirs.ez(k)};
    }

    private static double[] partnerNormal(Ray r) {
        int k = r.plus ? r.partner - 3 : r.partner + 3;
        return new double[]{Dirs.ex(k), Dirs.ez(k)};
    }

    @Nullable
    private static Reflex reflex(@Nullable Ray r, double[] w) {
        if (r == null || !r.reflex) {
            return null;
        }
        double[] n = partnerNormal(r);
        return new Reflex(w[0], w[1], n[0], n[1], r.k0, r.k1);
    }

    private List<Element> buildElements() {
        int n = this.shape.vertices;
        List<List<Ray>> rays = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rays.add(this.rays(i));
        }
        List<Element> out = new ArrayList<>();
        // own edges: edge i runs from vertex i (an 'a' ray) to vertex i+1 (a 'b' ray), interior on its + side
        for (int i = 0; i < n; i++) {
            int dir = Footprints.localOut(this.shape, i);
            Ray start = null;
            Ray end = null;
            for (Ray r : rays.get(i)) {
                if (r.plus && r.dir == Dirs.norm(dir)) start = r;
            }
            for (Ray r : rays.get((i + 1) % n)) {
                if (!r.plus && r.dir == Dirs.norm(dir + 6)) end = r;
            }
            if (start == null && end == null) {
                continue;
            }
            double[] p = this.poly[i];
            out.add(new Element(p[0], p[1], Dirs.ex(dir), Dirs.ez(dir), Dirs.ex(dir + 3), Dirs.ez(dir + 3), Footprints.SIDE,
                reflex(start, p), reflex(end, this.poly[(i + 1) % n])));
        }
        // neighbours' edges leaving each vertex
        for (int i = 0; i < n; i++) {
            int out0 = Footprints.localOut(this.shape, i);
            int back = Dirs.norm(out0 + this.shape.sectors);
            for (Ray r : rays.get(i)) {
                if (r.dir == Dirs.norm(out0) || r.dir == back) continue;
                double[] p = this.poly[i];
                double[] nn = normal(r);
                out.add(new Element(p[0], p[1], Dirs.ex(r.dir), Dirs.ez(r.dir), nn[0], nn[1], Footprints.SIDE, reflex(r, p), null));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- height

    /** Height above the base at local (u, v), in px. */
    public double height(double u, double v) {
        double h = PEAK;
        for (Element e : this.elements) {
            h = Math.min(h, e.bound(u, v));
        }
        return Math.max(0, h);
    }

    // ---------------------------------------------------------------- facets

    private List<Cand> candidates() {
        List<Cand> out = new ArrayList<>();
        out.add(new Cand(0, 0, PEAK, new double[0][]));
        for (Element e : this.elements) {
            double dc = -(e.px * e.nx + e.pz * e.nz);
            double tc = -(e.px * e.tx + e.pz * e.tz);
            double[] d = {e.nx, e.nz, dc};
            double[] tPos = {e.tx, e.tz, tc};
            double[] tNeg = {-e.tx, -e.tz, -tc};
            double[] tFar = {-e.tx, -e.tz, e.length - tc};
            double[] tBeyond = {e.tx, e.tz, tc - e.length};
            out.add(new Cand(d[0], d[1], d[2], new double[][]{tPos, tFar, d}));
            if (e.start != null) {
                addMax(out, d, e.start, tNeg);
            }
            if (e.end != null) {
                addMax(out, d, e.end, tBeyond);
            }
        }
        return out;
    }

    /**
     * {@link Reflex#bound} where {@code side >= 0}, as linear candidates: each of its linear functions is valid where
     * it is the largest.
     */
    private static void addMax(List<Cand> out, double[] d1, Reflex r, double[] side) {
        List<double[]> fs = new ArrayList<>(8);
        fs.add(d1);
        fs.add(new double[]{r.nx, r.nz, -(r.wx * r.nx + r.wz * r.nz)});
        for (int k = r.k0; k <= r.k1; k++) {
            double c = Dirs.ex(k);
            double sn = Dirs.ez(k);
            fs.add(new double[]{c, sn, -(r.wx * c + r.wz * sn) - REFLEX_SLACK});
        }
        for (int i = 0; i < fs.size(); i++) {
            double[] f = fs.get(i);
            double[][] valid = new double[fs.size()][];
            valid[0] = side;
            int n = 1;
            for (int j = 0; j < fs.size(); j++) {
                if (j == i) continue;
                double[] g = fs.get(j);
                valid[n++] = new double[]{f[0] - g[0], f[1] - g[1], f[2] - g[2]};
            }
            out.add(new Cand(f[0], f[1], f[2], valid));
        }
    }

    /**
     * Planar facets covering the footprint exactly: the footprint is split recursively by the lines where the minimal
     * linear piece changes until each convex cell has one linear height function.
     */
    public List<Facet> facets() {
        List<Facet> f = this.facets;
        if (f == null) {
            List<Cand> cands = this.candidates();
            List<Facet> out = new ArrayList<>();
            int[] all = new int[cands.size()];
            for (int i = 0; i < all.length; i++) all[i] = i;
            this.split(cands, Poly.positive(this.poly), all, 0, out);
            f = List.copyOf(merge(out));
            this.facets = f;
        }
        return f;
    }

    /** Merges facets of the same plane that share a whole edge while the union stays convex. */
    private static List<Facet> merge(List<Facet> facets) {
        List<Facet> out = new ArrayList<>(facets);
        boolean changed = true;
        while (changed) {
            changed = false;
            search:
            for (int i = 0; i < out.size(); i++) {
                for (int j = i + 1; j < out.size(); j++) {
                    Facet a = out.get(i);
                    Facet b = out.get(j);
                    if (Math.abs(a.a - b.a) > TIE || Math.abs(a.b - b.b) > TIE || Math.abs(a.c - b.c) > 1e-7) continue;
                    double[][] union = union(a.poly, b.poly);
                    if (union == null) continue;
                    double[] y = new double[union.length];
                    for (int k = 0; k < union.length; k++) {
                        y[k] = Math.max(0, Math.min(PEAK, a.height(union[k][0], union[k][1])));
                    }
                    out.set(i, new Facet(union, y, a.a, a.b, a.c));
                    out.remove(j);
                    changed = true;
                    break search;
                }
            }
        }
        return out;
    }

    /** Union of two positive convex polygons sharing a whole edge (opposite directions), or null if not convex. */
    private static double[][] union(double[][] a, double[][] b) {
        int na = a.length;
        int nb = b.length;
        for (int i = 0; i < na; i++) {
            for (int j = 0; j < nb; j++) {
                if (!near(a[i], b[(j + 1) % nb]) || !near(a[(i + 1) % na], b[j])) continue;
                List<double[]> pts = new ArrayList<>(na + nb - 2);
                for (int k = 0; k < na; k++) {
                    pts.add(a[(i + 1 + k) % na]);
                }
                for (int k = 2; k < nb; k++) {
                    pts.add(b[(j + k) % nb]);
                }
                double[][] u = pts.toArray(new double[0][]);
                for (int k = 0; k < u.length; k++) {
                    double[] p = u[k];
                    double[] q = u[(k + 1) % u.length];
                    double[] r = u[(k + 2) % u.length];
                    if ((q[0] - p[0]) * (r[1] - q[1]) - (q[1] - p[1]) * (r[0] - q[0]) < -1e-9) {
                        return null;
                    }
                }
                return u;
            }
        }
        return null;
    }

    private static boolean near(double[] p, double[] q) {
        return Math.abs(p[0] - q[0]) < 1e-9 && Math.abs(p[1] - q[1]) < 1e-9;
    }

    private static double eval(double[] hp, double[] p) {
        return hp[0] * p[0] + hp[1] * p[1] + hp[2];
    }

    private void split(List<Cand> cands, double[][] cell, int[] ids, int depth, List<Facet> out) {
        int n = ids.length;
        boolean[] alive = new boolean[n];
        boolean[] full = new boolean[n];
        double[] minG = new double[n];
        double upper = INF;
        int upperIdx = -1;
        for (int i = 0; i < n; i++) {
            Cand c = cands.get(ids[i]);
            double[][] part = cell;
            boolean isFull = true;
            boolean dead = false;
            for (double[] hp : c.valid) {
                double lo = INF;
                double hi = -INF;
                for (double[] p : cell) {
                    double v = eval(hp, p);
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
                if (lo >= -TIE) continue;
                if (hi <= TIE) {
                    dead = true;
                    break;
                }
                isFull = false;
                part = Poly.clipHalfPlane(part, hp[0], hp[1], hp[2]);
                if (part == null || Poly.area(part) < 1e-12) {
                    dead = true;
                    break;
                }
            }
            if (dead) continue;
            alive[i] = true;
            full[i] = isFull;
            double mn = INF;
            for (double[] p : part) mn = Math.min(mn, c.g(p[0], p[1]));
            minG[i] = mn;
            if (isFull) {
                double mx = -INF;
                for (double[] p : cell) mx = Math.max(mx, c.g(p[0], p[1]));
                if (mx < upper) {
                    upper = mx;
                    upperIdx = i;
                }
            }
        }
        int[] keep = new int[n];
        int kn = 0;
        for (int i = 0; i < n; i++) {
            if (alive[i] && (i == upperIdx || minG[i] < upper - TIE)) {
                keep[kn++] = i;
            }
        }
        if (kn == 1) {
            this.emit(cands.get(ids[upperIdx]), cell, out);
            return;
        }
        if (depth >= MAX_DEPTH) {
            LOGGER.warn("[CRac] roof facets: depth limit reached for {} variant {}, cell triangulated", this.shape, Long.toHexString(this.variant));
            this.triangulate(cell, out);
            return;
        }
        int[] next = new int[kn];
        for (int i = 0; i < kn; i++) next[i] = ids[keep[i]];
        // first make every remaining candidate valid on the whole cell
        for (int i = 0; i < kn; i++) {
            if (full[keep[i]]) continue;
            for (double[] hp : cands.get(ids[keep[i]]).valid) {
                if (this.cuts(cell, hp)) {
                    this.splitBy(cands, cell, hp, next, depth, out);
                    return;
                }
            }
        }
        // then split where the lowest candidate changes
        double[] mid = Poly.centroid(cell);
        int f0 = keep[0];
        for (int i = 1; i < kn; i++) {
            int k = keep[i];
            double gk = cands.get(ids[k]).g(mid[0], mid[1]);
            double gf = cands.get(ids[f0]).g(mid[0], mid[1]);
            if (gk < gf - TIE) f0 = k;
        }
        Cand c0 = cands.get(ids[f0]);
        for (int i = 0; i < kn; i++) {
            int k = keep[i];
            if (k == f0) continue;
            Cand ck = cands.get(ids[k]);
            double[] line = {c0.a - ck.a, c0.b - ck.b, c0.c - ck.c};
            if (this.cuts(cell, line)) {
                this.splitBy(cands, cell, line, next, depth, out);
                return;
            }
        }
        this.emit(c0, cell, out);
    }

    private boolean cuts(double[][] cell, double[] line) {
        double lo = INF;
        double hi = -INF;
        for (double[] p : cell) {
            double v = eval(line, p);
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        return lo < -TIE && hi > TIE;
    }

    private void splitBy(List<Cand> cands, double[][] cell, double[] line, int[] ids, int depth, List<Facet> out) {
        double[][] a = Poly.clipHalfPlane(cell, line[0], line[1], line[2]);
        double[][] b = Poly.clipHalfPlane(cell, -line[0], -line[1], -line[2]);
        if (a != null && Poly.area(a) > 1e-12) {
            this.split(cands, a, ids, depth + 1, out);
        }
        if (b != null && Poly.area(b) > 1e-12) {
            this.split(cands, b, ids, depth + 1, out);
        }
    }

    private void emit(Cand c, double[][] cell, List<Facet> out) {
        double[] y = new double[cell.length];
        for (int i = 0; i < cell.length; i++) {
            y[i] = Math.max(0, Math.min(PEAK, c.g(cell[i][0], cell[i][1])));
        }
        out.add(new Facet(cell, y, c.a, c.b, c.c));
    }

    /** Fallback: a fan of triangles with exact heights at their vertices (always planar). */
    private void triangulate(double[][] cell, List<Facet> out) {
        double[] h = new double[cell.length];
        for (int i = 0; i < cell.length; i++) h[i] = this.height(cell[i][0], cell[i][1]);
        for (int i = 1; i + 1 < cell.length; i++) {
            double[][] tri = {cell[0], cell[i], cell[i + 1]};
            double[] y = {h[0], h[i], h[i + 1]};
            double[] p = planeThrough(tri, y);
            out.add(new Facet(tri, y, p[0], p[1], p[2]));
        }
    }

    private static double[] planeThrough(double[][] t, double[] y) {
        double ux = t[1][0] - t[0][0];
        double uz = t[1][1] - t[0][1];
        double vx = t[2][0] - t[0][0];
        double vz = t[2][1] - t[0][1];
        double det = ux * vz - uz * vx;
        double dy1 = y[1] - y[0];
        double dy2 = y[2] - y[0];
        double a = (dy1 * vz - dy2 * uz) / det;
        double b = (ux * dy2 - vx * dy1) / det;
        return new double[]{a, b, y[0] - a * t[0][0] - b * t[0][1]};
    }

    // ---------------------------------------------------------------- collision

    /**
     * Collision prisms in the local frame: a {@link #GRID} px grid clipped to the footprint, each cell solid from
     * {@code -FLOOR_T} up to the ceiling of the highest roof point over it; runs of equal height along u are merged.
     */
    public List<Prism> collision() {
        List<Prism> c = this.collision;
        if (c == null) {
            c = List.copyOf(this.buildCollision());
            this.collision = c;
        }
        return c;
    }

    /**
     * Occlusion prisms in the local frame (light and face flags, see {@code PieceShapes#occlusion}): the same grid, each
     * cell solid only up to the floor of the lowest roof point over it, so a block they fill completely is entirely
     * below the roof's surface. The collision cells reach the ceiling of the highest point instead: a block they fill
     * can still hold facets, which would be drawn black if that block took the light-blocking flags.
     */
    public List<Prism> occlusion() {
        List<Prism> c = this.occlusion;
        if (c == null) {
            c = List.copyOf(this.buildGrid(false));
            this.occlusion = c;
        }
        return c;
    }

    private List<Prism> buildCollision() {
        return this.buildGrid(true);
    }

    private List<Prism> buildGrid(boolean ceiling) {
        List<Prism> out = new ArrayList<>();
        double[][] fp = Poly.positive(this.poly);
        double maxV = Poly.bounds(fp)[3];
        List<Facet> facets = this.facets();
        for (double v0 = 0; v0 < maxV - 1e-9; v0 += GRID) {
            double v1 = Math.min(v0 + GRID, maxV);
            double runStart = 0;
            double runH = Double.NaN;
            for (double u0 = 0; u0 <= Footprints.SIDE + 1e-9; u0 += GRID) {
                double h = Double.NaN;
                if (u0 < Footprints.SIDE - 1e-9) {
                    double[][] cell = Poly.clipRect(fp, u0, v0, u0 + GRID, v1);
                    if (cell != null && Poly.area(cell) > 1e-6) {
                        h = ceiling ? Math.max(0, Math.ceil(maxHeight(facets, cell) - 1e-6))
                            : Math.max(0, Math.floor(minHeight(facets, cell) + 1e-6));
                    }
                }
                if (!(h == runH) && !(Double.isNaN(h) && Double.isNaN(runH))) {
                    if (!Double.isNaN(runH)) {
                        double[][] run = Poly.clipRect(fp, runStart, v0, u0, v1);
                        if (run != null && Poly.area(run) > 1e-6) {
                            out.add(new Prism(run, -PieceSolids.FLOOR_T, runH));
                        }
                    }
                    runStart = u0;
                    runH = h;
                }
            }
        }
        return out;
    }

    private static double maxHeight(List<Facet> facets, double[][] cell) {
        return extremeHeight(facets, cell, true);
    }

    private static double minHeight(List<Facet> facets, double[][] cell) {
        return extremeHeight(facets, cell, false);
    }

    /** Highest (or lowest) roof point over a convex cell: the facets are planar, so a vertex of a clipped facet. */
    private static double extremeHeight(List<Facet> facets, double[][] cell, boolean highest) {
        double best = highest ? 0 : PEAK;
        for (Facet f : facets) {
            double[][] part = f.poly;
            for (int i = 0; i < cell.length && part != null; i++) {
                double[] p = cell[i];
                double[] q = cell[(i + 1) % cell.length];
                double a = -(q[1] - p[1]);
                double b = q[0] - p[0];
                part = Poly.clipHalfPlane(part, a, b, -(a * p[0] + b * p[1]) + 1e-9);
            }
            if (part == null) continue;
            for (double[] p : part) {
                double h = Math.min(PEAK, f.height(p[0], p[1]));
                best = highest ? Math.max(best, h) : Math.min(best, h);
            }
        }
        return best;
    }

    @Override
    public String toString() {
        return "RoofField[" + this.shape + " " + Long.toHexString(this.variant) + ", " + this.elements.size() + " elements: "
            + Arrays.toString(this.elements.toArray()) + "]";
    }
}
