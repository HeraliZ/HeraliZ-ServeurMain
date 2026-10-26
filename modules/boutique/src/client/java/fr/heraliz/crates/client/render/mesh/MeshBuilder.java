package fr.heraliz.crates.client.render.mesh;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Construction procedurale de maillages 3D (coordonnees en pixels = 1/16 de bloc).
 * Primitives : boites biseautees, cylindres, tores, spheres, gemmes, plaques polygonales,
 * profils extrudes (couvercles bombes)... Les normales sont lissees la ou il faut
 * pour un rendu "non-pixel".
 */
public final class MeshBuilder {
    private static final float PI = (float) Math.PI;

    /** Capacite de depart modeste (grandit au besoin) : les maillages refaits a chaque image restent legers. */
    private float[] data;
    private int size;
    private Matrix4f mat = new Matrix4f();
    private final Matrix3f nmat = new Matrix3f();
    private final Deque<Matrix4f> stack = new ArrayDeque<>();

    private static final int S = Mesh.STRIDE;
    /** Couleur (ombrage) courante des sommets. */
    private float cr = 1, cg = 1, cb = 1;

    public MeshBuilder() {
        this(512);
    }

    /** Constructeur pour un maillage d'environ vertices sommets (petits maillages dynamiques). */
    public MeshBuilder(int vertices) {
        data = new float[Mesh.STRIDE * Math.max(4, vertices)];
    }

    /** Maillage statique (mis en cache). */
    public Mesh build() {
        return new Mesh(Arrays.copyOf(data, size), true);
    }

    /** Maillage dynamique (reconstruit a chaque image, jamais mis en cache). */
    public Mesh buildDynamic() {
        return new Mesh(Arrays.copyOf(data, size), false);
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Vide le constructeur (reutilisation pour les maillages dynamiques). */
    public MeshBuilder reset() {
        size = 0;
        mat = new Matrix4f();
        refresh();
        stack.clear();
        cr = cg = cb = 1;
        return this;
    }

    /** Couleur multiplicative des prochains sommets (1,1,1 = texture pure). */
    public MeshBuilder color(float r, float g, float b) {
        cr = r;
        cg = g;
        cb = b;
        return this;
    }

    public MeshBuilder color(float k) {
        return color(k, k, k);
    }

    /** Couleur a partir d'un entier 0xRRGGBB (nom distinct : color(1) doit rester le blanc). */
    public MeshBuilder colorRgb(int rgb) {
        return color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }

    /** Nombre de sommets deja produits (pour appliquer un ombrage a une partie). */
    public int mark() {
        return size / S;
    }

    /** Ombrage personnalise d'un sommet : position (px), normale, couleur a modifier (rgb). */
    @FunctionalInterface
    public interface VertexShader {
        void shade(float x, float y, float z, float nx, float ny, float nz, float[] rgb);
    }

    /** Applique un ombrage aux sommets produits depuis "from" (voir mark()). */
    public MeshBuilder shade(int from, VertexShader f) {
        float[] rgb = new float[3];
        for (int i = from * S; i < size; i += S) {
            rgb[0] = data[i + 8];
            rgb[1] = data[i + 9];
            rgb[2] = data[i + 10];
            f.shade(data[i], data[i + 1], data[i + 2], data[i + 5], data[i + 6], data[i + 7], rgb);
            data[i + 8] = rgb[0];
            data[i + 9] = rgb[1];
            data[i + 10] = rgb[2];
        }
        return this;
    }

    /** Donnees brutes (lecture seule) : utilise par l'occlusion ambiante. */
    float[] rawData() {
        return data;
    }

    int rawSize() {
        return size;
    }

    // =================================================================== transformations
    public MeshBuilder push() {
        stack.push(new Matrix4f(mat));
        return this;
    }

    public MeshBuilder pop() {
        mat = stack.pop();
        refresh();
        return this;
    }

    public MeshBuilder translate(float x, float y, float z) {
        mat.translate(x, y, z);
        return this;
    }

    public MeshBuilder rotateX(float deg) {
        mat.rotateX((float) Math.toRadians(deg));
        refresh();
        return this;
    }

    public MeshBuilder rotateY(float deg) {
        mat.rotateY((float) Math.toRadians(deg));
        refresh();
        return this;
    }

    public MeshBuilder rotateZ(float deg) {
        mat.rotateZ((float) Math.toRadians(deg));
        refresh();
        return this;
    }

    public MeshBuilder scale(float s) {
        return scale(s, s, s);
    }

    /** Multiplie la transformation courante (repere en pixels). */
    public MeshBuilder mul(Matrix4f m) {
        mat.mul(m);
        refresh();
        return this;
    }

    public MeshBuilder scale(float x, float y, float z) {
        mat.scale(x, y, z);
        refresh();
        return this;
    }

    private void refresh() {
        nmat.set(mat).invert().transpose();
    }

    // =================================================================== bas niveau
    private void put(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        if (size + S > data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        Vector3f p = mat.transformPosition(x, y, z, new Vector3f());
        Vector3f n = nmat.transform(nx, ny, nz, new Vector3f());
        float l = n.length();
        if (l > 1e-6f) n.div(l);
        data[size++] = p.x;
        data[size++] = p.y;
        data[size++] = p.z;
        data[size++] = u;
        data[size++] = v;
        data[size++] = n.x;
        data[size++] = n.y;
        data[size++] = n.z;
        data[size++] = cr;
        data[size++] = cg;
        data[size++] = cb;
    }

    /** Polygone de 3 ou 4 sommets. Le sens est corrige pour correspondre a la normale fn. */
    public void poly(float[][] pts, float[][] uv, float[][] normals, float[] fn) {
        int n = pts.length;
        float[] nw = newell(pts);
        boolean flip = dot(nw, fn) < 0;
        int[] order;
        if (n == 4) {
            order = flip ? new int[]{0, 3, 2, 1} : new int[]{0, 1, 2, 3};
        } else {
            order = flip ? new int[]{0, 2, 1, 1} : new int[]{0, 1, 2, 2};
        }
        float r0 = cr, g0 = cg, b0 = cb;
        for (int k : order) {
            float[] nn = normals != null ? normals[k] : fn;
            if (vcol != null) {
                cr = r0 * vcol[k][0];
                cg = g0 * vcol[k][1];
                cb = b0 * vcol[k][2];
            }
            put(pts[k][0], pts[k][1], pts[k][2], uv[k][0], uv[k][1], nn[0], nn[1], nn[2]);
        }
        cr = r0;
        cg = g0;
        cb = b0;
    }

    private float[][] vcol;

    /** Polygone avec une couleur par sommet (degrades : liseres lumineux, halos...). */
    public void polyColored(float[][] pts, float[][] uv, float[][] normals, float[] fn, float[][] colors) {
        vcol = colors;
        try {
            poly(pts, uv, normals, fn);
        } finally {
            vcol = null;
        }
    }

    /** Face plane avec UV projetes. */
    public void face(Mat m, float[] fn, float[]... pts) {
        poly(pts, m.project(pts, fn, null), null, fn);
    }

    /** Face plane avec UV projetes et bornes FIT imposees. */
    public void faceBounded(Mat m, float[] fn, float[] bounds, float[]... pts) {
        poly(pts, m.project(pts, fn, bounds), null, fn);
    }

    /** Face plane dont la normale est calculee et orientee a l'oppose de "center". */
    public void flatOut(Mat m, float[] center, float[]... pts) {
        float[] n = normalize(newell(pts));
        float[] c = centroid(pts);
        if ((c[0] - center[0]) * n[0] + (c[1] - center[1]) * n[1] + (c[2] - center[2]) * n[2] < 0) {
            n[0] = -n[0];
            n[1] = -n[1];
            n[2] = -n[2];
        }
        face(m, n, pts);
    }

    // =================================================================== boites
    public void box(float x0, float y0, float z0, float x1, float y1, float z1, Mat m) {
        box6(x0, y0, z0, x1, y1, z1, m, m, m, m, m, m);
    }

    /** Boite avec un materiau par face : -X, +X, -Y, +Y, -Z, +Z (null = face omise). */
    public void box6(float x0, float y0, float z0, float x1, float y1, float z1,
                     Mat nx, Mat px, Mat ny, Mat py, Mat nz, Mat pz) {
        if (nx != null) face(nx, v(-1, 0, 0), p(x0, y0, z0), p(x0, y0, z1), p(x0, y1, z1), p(x0, y1, z0));
        if (px != null) face(px, v(1, 0, 0), p(x1, y0, z1), p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1));
        if (ny != null) face(ny, v(0, -1, 0), p(x0, y0, z0), p(x1, y0, z0), p(x1, y0, z1), p(x0, y0, z1));
        if (py != null) face(py, v(0, 1, 0), p(x0, y1, z1), p(x1, y1, z1), p(x1, y1, z0), p(x0, y1, z0));
        if (nz != null) face(nz, v(0, 0, -1), p(x1, y0, z0), p(x0, y0, z0), p(x0, y1, z0), p(x1, y1, z0));
        if (pz != null) face(pz, v(0, 0, 1), p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1));
    }

    public void bevelBox(float x0, float y0, float z0, float x1, float y1, float z1, float b, Mat m) {
        bevelBox(x0, y0, z0, x1, y1, z1, b, m, null, 0);
    }

    public static final int SKIP_NX = 1, SKIP_PX = 2, SKIP_NY = 4, SKIP_PY = 8, SKIP_NZ = 16, SKIP_PZ = 32;

    /**
     * Boite aux aretes chanfreinees (reflets sur les bords = aspect "reel").
     * faceMats (optionnel, 6 entrees -X,+X,-Y,+Y,-Z,+Z) remplace le materiau des grandes faces.
     */
    public void bevelBox(float x0, float y0, float z0, float x1, float y1, float z1, float b, Mat m, Mat[] faceMats, int skip) {
        float maxB = Math.min(x1 - x0, Math.min(y1 - y0, z1 - z0)) * 0.49f;
        b = Math.min(b, maxB);
        if (b <= 0.001f) {
            Mat[] f = new Mat[6];
            for (int i = 0; i < 6; i++) {
                f[i] = (skip & (1 << i)) != 0 ? null : (faceMats != null && faceMats[i] != null ? faceMats[i] : m);
            }
            box6(x0, y0, z0, x1, y1, z1, f[0], f[1], f[2], f[3], f[4], f[5]);
            return;
        }
        float[] lo = {x0, y0, z0};
        float[] hi = {x1, y1, z1};
        int[][] quad = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int k = 0; k < 3; k++) {
            for (int s = -1; s <= 1; s += 2) {
                int idx = k * 2 + (s > 0 ? 1 : 0);
                if ((skip & (1 << idx)) != 0) continue;
                int i = (k + 1) % 3, j = (k + 2) % 3;
                float[] n = new float[3];
                n[k] = s;
                float[][] pts = new float[4][];
                for (int q = 0; q < 4; q++) {
                    int[] c = new int[3];
                    c[k] = s;
                    c[i] = quad[q][0];
                    c[j] = quad[q][1];
                    pts[q] = bv(lo, hi, b, c, k);
                }
                Mat fm = faceMats != null && faceMats[idx] != null ? faceMats[idx] : m;
                face(fm, n, pts);
            }
        }
        for (int i = 0; i < 3; i++) {
            for (int j = i + 1; j < 3; j++) {
                int l = 3 - i - j;
                for (int si = -1; si <= 1; si += 2) {
                    for (int sj = -1; sj <= 1; sj += 2) {
                        int[] ca = new int[3];
                        int[] cb = new int[3];
                        ca[i] = si;
                        ca[j] = sj;
                        ca[l] = -1;
                        cb[i] = si;
                        cb[j] = sj;
                        cb[l] = 1;
                        float[] n = new float[3];
                        n[i] = si * 0.70710678f;
                        n[j] = sj * 0.70710678f;
                        face(m, n, bv(lo, hi, b, ca, i), bv(lo, hi, b, cb, i), bv(lo, hi, b, cb, j), bv(lo, hi, b, ca, j));
                    }
                }
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    int[] c = {sx, sy, sz};
                    float[] n = {sx * 0.57735f, sy * 0.57735f, sz * 0.57735f};
                    face(m, n, bv(lo, hi, b, c, 0), bv(lo, hi, b, c, 1), bv(lo, hi, b, c, 2));
                }
            }
        }
    }

    private static float[] bv(float[] lo, float[] hi, float b, int[] c, int k) {
        float[] p = new float[3];
        for (int a = 0; a < 3; a++) {
            if (a == k) {
                p[a] = c[a] > 0 ? hi[a] : lo[a];
            } else {
                p[a] = c[a] > 0 ? hi[a] - b : lo[a] + b;
            }
        }
        return p;
    }

    // =================================================================== decals
    /** Quad plat face a +Z (centre cx,cy, a la profondeur z). */
    public void decalZ(Mat m, float cx, float cy, float z, float w, float h) {
        face(m.fit ? m : m.asFit(), v(0, 0, 1), p(cx - w / 2, cy - h / 2, z), p(cx + w / 2, cy - h / 2, z),
                p(cx + w / 2, cy + h / 2, z), p(cx - w / 2, cy + h / 2, z));
    }

    /** Quad plat double face (visible des deux cotes, texture non inversee), dans le plan XY local. */
    public void decalDouble(Mat m, float w, float h) {
        Mat f = m.fit ? m : m.asFit();
        face(f, v(0, 0, 1), p(-w / 2, -h / 2, 0.01f), p(w / 2, -h / 2, 0.01f), p(w / 2, h / 2, 0.01f), p(-w / 2, h / 2, 0.01f));
        face(f, v(0, 0, -1), p(w / 2, -h / 2, -0.01f), p(-w / 2, -h / 2, -0.01f), p(-w / 2, h / 2, -0.01f), p(w / 2, h / 2, -0.01f));
    }

    /**
     * Plaque extrudee dont le contour suit un polygone donne en coordonnees de texture (0..1, y vers le bas).
     * La face avant (z = d) affiche exactement la zone correspondante de la tuile "front".
     * Centree en (0,0), largeur w, hauteur h.
     */
    public void badge(float[][] tex01, float w, float h, float d, Mat front, Mat side) {
        int n = tex01.length;
        float[][] pp = new float[n][2];
        float cx = 0, cy = 0, cu = 0, cv = 0;
        for (int i = 0; i < n; i++) {
            pp[i][0] = (tex01[i][0] - 0.5f) * w;
            pp[i][1] = (0.5f - tex01[i][1]) * h;
            cx += pp[i][0];
            cy += pp[i][1];
            cu += tex01[i][0];
            cv += tex01[i][1];
        }
        cx /= n;
        cy /= n;
        cu /= n;
        cv /= n;
        float[] fn = {0, 0, 1};
        for (int i = 0; i < n; i++) {
            int k = (i + 1) % n;
            float[][] pts = {{cx, cy, d}, {pp[i][0], pp[i][1], d}, {pp[k][0], pp[k][1], d}};
            float[][] uv = {{front.u(cu), front.v(cv)}, {front.u(tex01[i][0]), front.v(tex01[i][1])},
                    {front.u(tex01[k][0]), front.v(tex01[k][1])}};
            poly(pts, uv, null, fn);
        }
        if (side != null && d > 0) {
            for (int i = 0; i < n; i++) {
                int k = (i + 1) % n;
                float ex = pp[k][0] - pp[i][0], ey = pp[k][1] - pp[i][1];
                float nx = ey, ny = -ex;
                float mx = (pp[i][0] + pp[k][0]) / 2 - cx, my = (pp[i][1] + pp[k][1]) / 2 - cy;
                if (nx * mx + ny * my < 0) {
                    nx = -nx;
                    ny = -ny;
                }
                float l = (float) Math.sqrt(nx * nx + ny * ny);
                if (l < 1e-5f) continue;
                face(side, v(nx / l, ny / l, 0), p(pp[i][0], pp[i][1], 0), p(pp[k][0], pp[k][1], 0),
                        p(pp[k][0], pp[k][1], d), p(pp[i][0], pp[i][1], d));
            }
        }
    }

    /** Prisme extrude d'un polygone etoile (2D, plan XY), face avant en projection du materiau. */
    public void prism(float[][] pts2, float d, Mat front, Mat side) {
        int n = pts2.length;
        float cx = 0, cy = 0;
        for (float[] q : pts2) {
            cx += q[0];
            cy += q[1];
        }
        cx /= n;
        cy /= n;
        for (int i = 0; i < n; i++) {
            int k = (i + 1) % n;
            face(front, v(0, 0, 1), p(cx, cy, d), p(pts2[i][0], pts2[i][1], d), p(pts2[k][0], pts2[k][1], d));
            face(front, v(0, 0, -1), p(cx, cy, 0), p(pts2[k][0], pts2[k][1], 0), p(pts2[i][0], pts2[i][1], 0));
            float ex = pts2[k][0] - pts2[i][0], ey = pts2[k][1] - pts2[i][1];
            float nx = ey, ny = -ex;
            float mx = (pts2[i][0] + pts2[k][0]) / 2 - cx, my = (pts2[i][1] + pts2[k][1]) / 2 - cy;
            if (nx * mx + ny * my < 0) {
                nx = -nx;
                ny = -ny;
            }
            float l = (float) Math.sqrt(nx * nx + ny * ny);
            if (l < 1e-5f) continue;
            face(side, v(nx / l, ny / l, 0), p(pts2[i][0], pts2[i][1], 0), p(pts2[k][0], pts2[k][1], 0),
                    p(pts2[k][0], pts2[k][1], d), p(pts2[i][0], pts2[i][1], d));
        }
    }

    // =================================================================== solides de revolution
    /** Cylindre d'axe Y local centre en (0,0), de y0 a y1. */
    public void cylinder(float r, float y0, float y1, int segs, Mat side, Mat bottom, Mat top) {
        float w = side.world > 0 ? side.world : 16f;
        float sMax = side.fit ? 1f : Math.min(1f, 2 * PI * r / w);
        float tMax = side.fit ? 1f : Math.min(1f, (y1 - y0) / w);
        for (int i = 0; i < segs; i++) {
            float a0 = 2 * PI * i / segs, a1 = 2 * PI * (i + 1) / segs;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[][] pts = {{r * c0, y0, r * s0}, {r * c1, y0, r * s1}, {r * c1, y1, r * s1}, {r * c0, y1, r * s0}};
            float[][] nr = {{c0, 0, s0}, {c1, 0, s1}, {c1, 0, s1}, {c0, 0, s0}};
            float ua = sMax * i / segs, ub = sMax * (i + 1) / segs;
            float[][] uv = {{side.u(ua), side.v(tMax)}, {side.u(ub), side.v(tMax)}, {side.u(ub), side.v(0)}, {side.u(ua), side.v(0)}};
            poly(pts, uv, nr, new float[]{(c0 + c1) / 2, 0, (s0 + s1) / 2});
        }
        if (top != null) disc(r, y1, segs, top, true);
        if (bottom != null) disc(r, y0, segs, bottom, false);
    }

    /** Cylindre creux : peau exterieure, interieure et anneaux. */
    public void hollowCylinder(float rOut, float rIn, float y0, float y1, int segs, Mat m) {
        cylinder(rOut, y0, y1, segs, m, null, null);
        for (int i = 0; i < segs; i++) {
            float a0 = 2 * PI * i / segs, a1 = 2 * PI * (i + 1) / segs;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[][] pts = {{rIn * c0, y0, rIn * s0}, {rIn * c1, y0, rIn * s1}, {rIn * c1, y1, rIn * s1}, {rIn * c0, y1, rIn * s0}};
            float[][] nr = {{-c0, 0, -s0}, {-c1, 0, -s1}, {-c1, 0, -s1}, {-c0, 0, -s0}};
            float[][] uv = m.project(pts, new float[]{-(c0 + c1) / 2, 0, -(s0 + s1) / 2}, null);
            poly(pts, uv, nr, new float[]{-(c0 + c1) / 2, 0, -(s0 + s1) / 2});
            face(m, v(0, 1, 0), p(rIn * c0, y1, rIn * s0), p(rOut * c0, y1, rOut * s0), p(rOut * c1, y1, rOut * s1), p(rIn * c1, y1, rIn * s1));
            face(m, v(0, -1, 0), p(rIn * c0, y0, rIn * s0), p(rIn * c1, y0, rIn * s1), p(rOut * c1, y0, rOut * s1), p(rOut * c0, y0, rOut * s0));
        }
    }

    public void disc(float r, float y, int segs, Mat m, boolean up) {
        float[] n = {0, up ? 1 : -1, 0};
        float[] bounds = null;
        if (m.fit) {
            float[] a = Mat.planar(p(-r, y, -r), n);
            float[] b = Mat.planar(p(r, y, r), n);
            bounds = new float[]{Math.min(a[0], b[0]), Math.max(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[1], b[1])};
        }
        for (int i = 0; i < segs; i++) {
            float a0 = 2 * PI * i / segs, a1 = 2 * PI * (i + 1) / segs;
            float[] p0 = p(r * (float) Math.cos(a0), y, r * (float) Math.sin(a0));
            float[] p1 = p(r * (float) Math.cos(a1), y, r * (float) Math.sin(a1));
            if (m.fit) {
                faceBounded(m, n, bounds, p(0, y, 0), p0, p1);
            } else {
                // UV coherents pour tout le disque : on projette le carre englobant
                float[][] tri = {p(0, y, 0), p0, p1};
                float[][] uv = new float[3][2];
                float w = m.world > 0 ? m.world : 16f;
                float span = Math.min(1f, 2 * r / w);
                for (int k = 0; k < 3; k++) {
                    uv[k][0] = m.u(0.5f + tri[k][0] / (2 * r) * span);
                    uv[k][1] = m.v(0.5f + tri[k][2] / (2 * r) * span);
                }
                poly(tri, uv, null, n);
            }
        }
    }

    /** Tore autour de l'axe Y local (anneau dans le plan XZ). arc en degres. */
    public void torus(float R, float r, int segU, int segV, float arcStart, float arcEnd, Mat m) {
        float w = m.world > 0 ? m.world : 16f;
        float arc = (arcEnd - arcStart) * PI / 180f;
        float sMax = m.fit ? 1f : Math.min(1f, R * Math.abs(arc) / w);
        float tMax = m.fit ? 1f : Math.min(1f, 2 * PI * r / w);
        for (int i = 0; i < segU; i++) {
            float u0 = (float) Math.toRadians(arcStart + (arcEnd - arcStart) * i / segU);
            float u1 = (float) Math.toRadians(arcStart + (arcEnd - arcStart) * (i + 1) / segU);
            for (int j = 0; j < segV; j++) {
                float v0 = 2 * PI * j / segV, v1 = 2 * PI * (j + 1) / segV;
                float[][] pts = {tp(R, r, u0, v0), tp(R, r, u1, v0), tp(R, r, u1, v1), tp(R, r, u0, v1)};
                float[][] nr = {tn(u0, v0), tn(u1, v0), tn(u1, v1), tn(u0, v1)};
                float sa = sMax * i / segU, sb = sMax * (i + 1) / segU;
                float ta = tMax * j / segV, tb = tMax * (j + 1) / segV;
                float[][] uv = {{m.u(sa), m.v(ta)}, {m.u(sb), m.v(ta)}, {m.u(sb), m.v(tb)}, {m.u(sa), m.v(tb)}};
                float um = (u0 + u1) / 2, vm = (v0 + v1) / 2;
                poly(pts, uv, nr, tn(um, vm));
            }
        }
    }

    private static float[] tp(float R, float r, float u, float v) {
        float cv = (float) Math.cos(v);
        return new float[]{(R + r * cv) * (float) Math.cos(u), r * (float) Math.sin(v), (R + r * cv) * (float) Math.sin(u)};
    }

    private static float[] tn(float u, float v) {
        float cv = (float) Math.cos(v);
        return new float[]{cv * (float) Math.cos(u), (float) Math.sin(v), cv * (float) Math.sin(u)};
    }

    public void sphere(float r, int segU, int segV, Mat m) {
        sphereSection(r, segU, segV, 0, 180, m);
    }

    /** Portion de sphere entre les angles polaires th0 et th1 (0 = pole +Y). */
    public void sphereSection(float r, int segU, int segV, float th0, float th1, Mat m) {
        float w = m.world > 0 ? m.world : 16f;
        float sMax = m.fit ? 1f : Math.min(1f, 2 * PI * r / w);
        float tMax = m.fit ? 1f : Math.min(1f, PI * r / w);
        for (int j = 0; j < segV; j++) {
            float a0 = (float) Math.toRadians(th0 + (th1 - th0) * j / segV);
            float a1 = (float) Math.toRadians(th0 + (th1 - th0) * (j + 1) / segV);
            for (int i = 0; i < segU; i++) {
                float b0 = 2 * PI * i / segU, b1 = 2 * PI * (i + 1) / segU;
                float[][] nr = {sn(a0, b0), sn(a0, b1), sn(a1, b1), sn(a1, b0)};
                float[][] pts = new float[4][3];
                for (int k = 0; k < 4; k++) {
                    pts[k][0] = nr[k][0] * r;
                    pts[k][1] = nr[k][1] * r;
                    pts[k][2] = nr[k][2] * r;
                }
                float[][] uv = {{m.u(sMax * i / segU), m.v(tMax * j / segV)}, {m.u(sMax * (i + 1) / segU), m.v(tMax * j / segV)},
                        {m.u(sMax * (i + 1) / segU), m.v(tMax * (j + 1) / segV)}, {m.u(sMax * i / segU), m.v(tMax * (j + 1) / segV)}};
                if (j == 0 && th0 <= 0.001f) {
                    // pole : triangle
                    poly(new float[][]{pts[0], pts[2], pts[3]}, new float[][]{uv[0], uv[2], uv[3]},
                            new float[][]{nr[0], nr[2], nr[3]}, sn((a0 + a1) / 2, (b0 + b1) / 2));
                } else if (j == segV - 1 && th1 >= 179.999f) {
                    poly(new float[][]{pts[0], pts[1], pts[2]}, new float[][]{uv[0], uv[1], uv[2]},
                            new float[][]{nr[0], nr[1], nr[2]}, sn((a0 + a1) / 2, (b0 + b1) / 2));
                } else {
                    poly(pts, uv, nr, sn((a0 + a1) / 2, (b0 + b1) / 2));
                }
            }
        }
    }

    private static float[] sn(float th, float ph) {
        float s = (float) Math.sin(th);
        return new float[]{s * (float) Math.cos(ph), (float) Math.cos(th), s * (float) Math.sin(ph)};
    }

    /** Gemme taillee (table, couronne, pavillon) d'axe Y, facettes plates. */
    public void gem(float r, float crown, float pavilion, int facets, Mat m) {
        float tr = r * 0.55f;
        float[] center = {0, 0, 0};
        float[][] table = new float[facets][];
        float[][] girdle = new float[facets][];
        for (int i = 0; i < facets; i++) {
            float a = 2 * PI * i / facets;
            table[i] = p(tr * (float) Math.cos(a), crown, tr * (float) Math.sin(a));
            girdle[i] = p(r * (float) Math.cos(a), 0, r * (float) Math.sin(a));
        }
        for (int i = 0; i < facets; i++) {
            int k = (i + 1) % facets;
            flatOut(m, center, p(0, crown, 0), table[i], table[k]);
            flatOut(m, center, table[i], table[k], girdle[k], girdle[i]);
            flatOut(m, center, girdle[i], girdle[k], p(0, -pavilion, 0));
        }
    }

    /** Pyramide / pointe d'axe Y : base rectangulaire (w x d) en y=0, sommet en (0,h,0). */
    public void spike(float w, float d, float h, Mat m) {
        float[] c = {0, h * 0.25f, 0};
        float[] a = p(-w / 2, 0, -d / 2), b = p(w / 2, 0, -d / 2), cc = p(w / 2, 0, d / 2), dd = p(-w / 2, 0, d / 2);
        float[] top = p(0, h, 0);
        flatOut(m, c, a, b, top);
        flatOut(m, c, b, cc, top);
        flatOut(m, c, cc, dd, top);
        flatOut(m, c, dd, a, top);
        flatOut(m, c, a, dd, cc, b);
    }

    /** Petit rivet bombe d'axe Y. */
    public void rivet(float r, float h, Mat m) {
        push();
        scale(1, h / r, 1);
        sphereSection(r, 8, 3, 0, 90, m);
        pop();
    }

    // =================================================================== profils extrudes (couvercles bombes)
    /**
     * Extrude un profil (points (z,y) de l'arriere vers l'avant en passant par le haut) le long de X.
     * Normales lissees le long du profil. UV : s suit X, t suit l'abscisse curviligne.
     */
    public void extrudeProfileX(float[][] zy, float x0, float x1, Mat m) {
        int n = zy.length;
        float[][] nrm = profileNormals(zy);
        float w = m.world > 0 ? m.world : 16f;
        float[] len = new float[n];
        for (int i = 1; i < n; i++) {
            len[i] = len[i - 1] + dist(zy[i], zy[i - 1]);
        }
        float[] s = {(x0 + 8f) / w, (x1 + 8f) / w};
        float[] t = new float[n];
        for (int i = 0; i < n; i++) t[i] = len[i] / w;
        if (m.fit) {
            s = new float[]{0, 1};
            for (int i = 0; i < n; i++) t[i] = len[i] / Math.max(1e-4f, len[n - 1]);
        } else {
            Mat.fitRange(s);
            Mat.fitRange(t);
        }
        for (int i = 0; i < n - 1; i++) {
            float[][] pts = {p(x0, zy[i][1], zy[i][0]), p(x1, zy[i][1], zy[i][0]), p(x1, zy[i + 1][1], zy[i + 1][0]), p(x0, zy[i + 1][1], zy[i + 1][0])};
            float[] na = {0, nrm[i][1], nrm[i][0]};
            float[] nb = {0, nrm[i + 1][1], nrm[i + 1][0]};
            float[][] uv = {{m.u(s[0]), m.v(t[i])}, {m.u(s[1]), m.v(t[i])}, {m.u(s[1]), m.v(t[i + 1])}, {m.u(s[0]), m.v(t[i + 1])}};
            float[] fn = normalize(new float[]{0, na[1] + nb[1], na[2] + nb[2]});
            poly(pts, uv, new float[][]{na, na, nb, nb}, fn);
        }
    }

    /** Bouchon plat (face X) d'un profil ferme par sa base. */
    public void profileCapX(float[][] zy, float x, boolean positive, Mat m) {
        int n = zy.length;
        float cz = 0, cy = 0;
        for (float[] q : zy) {
            cz += q[0];
            cy += q[1];
        }
        cz /= n;
        cy /= n;
        float[] fn = {positive ? 1 : -1, 0, 0};
        float[] bounds = null;
        if (m.fit) {
            float minA = Float.MAX_VALUE, maxA = -Float.MAX_VALUE, minB = Float.MAX_VALUE, maxB = -Float.MAX_VALUE;
            for (float[] q : zy) {
                float[] ab = Mat.planar(p(x, q[1], q[0]), fn);
                minA = Math.min(minA, ab[0]);
                maxA = Math.max(maxA, ab[0]);
                minB = Math.min(minB, ab[1]);
                maxB = Math.max(maxB, ab[1]);
            }
            bounds = new float[]{minA, maxA, minB, maxB};
        }
        for (int i = 0; i < n; i++) {
            int k = (i + 1) % n;
            faceBounded(m, fn, bounds, p(x, cy, cz), p(x, zy[i][1], zy[i][0]), p(x, zy[k][1], zy[k][0]));
        }
    }

    /** Bande (renfort metallique) qui epouse le profil, epaisseur "thick", de xa a xb. */
    public void profileBandX(float[][] zy, float xa, float xb, float thick, Mat m) {
        int n = zy.length;
        float[][] nrm = profileNormals(zy);
        float[][] in = new float[n][2];
        float[][] out = new float[n][2];
        for (int i = 0; i < n; i++) {
            in[i][0] = zy[i][0] + nrm[i][0] * 0.02f;
            in[i][1] = zy[i][1] + nrm[i][1] * 0.02f;
            out[i][0] = zy[i][0] + nrm[i][0] * thick;
            out[i][1] = zy[i][1] + nrm[i][1] * thick;
        }
        extrudeProfileX(out, xa, xb, m);
        for (int i = 0; i < n - 1; i++) {
            face(m, v(-1, 0, 0), p(xa, in[i][1], in[i][0]), p(xa, in[i + 1][1], in[i + 1][0]), p(xa, out[i + 1][1], out[i + 1][0]), p(xa, out[i][1], out[i][0]));
            face(m, v(1, 0, 0), p(xb, in[i][1], in[i][0]), p(xb, out[i][1], out[i][0]), p(xb, out[i + 1][1], out[i + 1][0]), p(xb, in[i + 1][1], in[i + 1][0]));
        }
        // extremites (en bas de la jupe, a l'arriere et a l'avant)
        for (int e = 0; e < 2; e++) {
            int i = e == 0 ? 0 : n - 1;
            face(m, v(0, -1, 0), p(xa, in[i][1], in[i][0]), p(xb, in[i][1], in[i][0]), p(xb, out[i][1], out[i][0]), p(xa, out[i][1], out[i][0]));
        }
    }

    private static float[][] profileNormals(float[][] zy) {
        int n = zy.length;
        float[][] segN = new float[n - 1][2];
        for (int i = 0; i < n - 1; i++) {
            float dz = zy[i + 1][0] - zy[i][0], dy = zy[i + 1][1] - zy[i][1];
            float l = (float) Math.sqrt(dz * dz + dy * dy);
            if (l < 1e-6f) l = 1;
            // normale exterieure (z, y) = (-dy, dz)
            segN[i][0] = -dy / l;
            segN[i][1] = dz / l;
        }
        float[][] out = new float[n][2];
        for (int i = 0; i < n; i++) {
            float z = 0, y = 0;
            if (i > 0) {
                z += segN[i - 1][0];
                y += segN[i - 1][1];
            }
            if (i < n - 1) {
                z += segN[i][0];
                y += segN[i][1];
            }
            float l = (float) Math.sqrt(z * z + y * y);
            if (l < 1e-6f) l = 1;
            out[i][0] = z / l;
            out[i][1] = y / l;
        }
        return out;
    }

    /** Profil de couvercle bombe : jupe verticale puis arc elliptique. */
    public static float[][] archProfile(float zBack, float zFront, float yLow, float yBase, float yTop, int segs) {
        float[][] pts = new float[segs + 3][2];
        pts[0] = new float[]{zBack, yLow};
        float zc = (zBack + zFront) / 2, rz = (zFront - zBack) / 2, ry = yTop - yBase;
        for (int i = 0; i <= segs; i++) {
            float a = PI - PI * i / segs;
            pts[i + 1] = new float[]{zc + rz * (float) Math.cos(a), yBase + ry * (float) Math.sin(a)};
        }
        pts[segs + 2] = new float[]{zFront, yLow};
        return pts;
    }

    // =================================================================== surfaces organiques
    /** Orientation des normales d'une surface. */
    public enum Orient { KEEP, FLIP, OUT, IN }

    /** Repli en miroir d'une coordonnee de texture (repete une tuile sans couture visible). */
    public static float mirror(float s) {
        float t = s % 2f;
        if (t < 0) t += 2f;
        return t > 1f ? 2f - t : t;
    }

    /**
     * Surface a partir d'une grille de points P[i][j] (i = lignes, j = colonnes).
     * closedJ : la derniere colonne est un duplicata de la premiere (surface fermee, normales continues).
     * S, T : coordonnees de texture en unites de tuile (repliees en miroir si wrapUV).
     * N : normales (null = calculees a partir de la grille), orient : orientation finale.
     */
    public void grid(float[][][] P, float[][][] N, float[][] S, float[][] T, boolean closedJ, Mat m, Orient orient, boolean wrapUV) {
        int ni = P.length, nj = P[0].length;
        if (N == null) N = gridNormals(P, closedJ);
        boolean flip = false;
        if (orient == Orient.FLIP) {
            flip = true;
        } else if (orient == Orient.OUT || orient == Orient.IN) {
            float[] c = new float[3];
            int cnt = 0;
            for (float[][] row : P) {
                for (float[] q : row) {
                    c[0] += q[0];
                    c[1] += q[1];
                    c[2] += q[2];
                    cnt++;
                }
            }
            c[0] /= cnt;
            c[1] /= cnt;
            c[2] /= cnt;
            double acc = 0;
            for (int i = 0; i < ni; i++) {
                for (int j = 0; j < nj; j++) {
                    acc += (P[i][j][0] - c[0]) * N[i][j][0] + (P[i][j][1] - c[1]) * N[i][j][1] + (P[i][j][2] - c[2]) * N[i][j][2];
                }
            }
            flip = (acc < 0) == (orient == Orient.OUT);
        }
        for (int i = 0; i < ni - 1; i++) {
            for (int j = 0; j < nj - 1; j++) {
                float[][] pts = {P[i][j], P[i][j + 1], P[i + 1][j + 1], P[i + 1][j]};
                int[][] idx = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
                float[][] nr = new float[4][];
                float[][] uv = new float[4][2];
                float fx = 0, fy = 0, fz = 0;
                for (int k = 0; k < 4; k++) {
                    float[] n0 = N[idx[k][0]][idx[k][1]];
                    nr[k] = flip ? new float[]{-n0[0], -n0[1], -n0[2]} : n0;
                    fx += nr[k][0];
                    fy += nr[k][1];
                    fz += nr[k][2];
                    float su = S[idx[k][0]][idx[k][1]], tv = T[idx[k][0]][idx[k][1]];
                    if (wrapUV) {
                        su = mirror(su);
                        tv = mirror(tv);
                    }
                    uv[k][0] = m.u(su);
                    uv[k][1] = m.v(tv);
                }
                float[] nw = newell(pts);
                if (nw[0] * nw[0] + nw[1] * nw[1] + nw[2] * nw[2] < 1e-12f) continue;
                float[] fn = normalize(new float[]{fx, fy, fz});
                poly(pts, uv, nr, fn);
            }
        }
    }

    private static float[][][] gridNormals(float[][][] P, boolean closedJ) {
        int ni = P.length, nj = P[0].length;
        float[][][] N = new float[ni][nj][];
        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                int ip = Math.min(i + 1, ni - 1), im = Math.max(i - 1, 0);
                int jp, jm;
                if (closedJ) {
                    jp = j >= nj - 1 ? 1 : j + 1;
                    jm = j <= 0 ? nj - 2 : j - 1;
                } else {
                    jp = Math.min(j + 1, nj - 1);
                    jm = Math.max(j - 1, 0);
                }
                float[] di = sub(P[ip][j], P[im][j]);
                float[] dj = sub(P[i][jp], P[i][jm]);
                float[] n = cross(di, dj);
                float l = (float) Math.sqrt(dot(n, n));
                N[i][j] = l < 1e-7f ? null : new float[]{n[0] / l, n[1] / l, n[2] / l};
            }
        }
        // points degeneres (poles) : normale de la ligne voisine
        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                if (N[i][j] != null) continue;
                float[] best = null;
                for (int d = 1; d < ni && best == null; d++) {
                    if (i - d >= 0 && N[i - d][j] != null) best = N[i - d][j];
                    else if (i + d < ni && N[i + d][j] != null) best = N[i + d][j];
                }
                N[i][j] = best != null ? best.clone() : new float[]{0, 1, 0};
            }
        }
        // les poles : moyenne de tous les voisins pour une normale lisse
        for (int i = 0; i < ni; i++) {
            boolean pole = true;
            for (int j = 1; j < nj && pole; j++) {
                if (Math.abs(P[i][j][0] - P[i][0][0]) + Math.abs(P[i][j][1] - P[i][0][1]) + Math.abs(P[i][j][2] - P[i][0][2]) > 1e-5f) {
                    pole = false;
                }
            }
            if (pole && nj > 1) {
                float[] avg = new float[3];
                for (int j = 0; j < nj; j++) {
                    avg[0] += N[i][j][0];
                    avg[1] += N[i][j][1];
                    avg[2] += N[i][j][2];
                }
                avg = normalize(avg);
                for (int j = 0; j < nj; j++) N[i][j] = avg.clone();
            }
        }
        return N;
    }

    /**
     * Surface de revolution autour de l'axe Y (vases, oeufs, domes, coquilles...).
     * prof : points (rayon, y) du bas vers le haut. a0..a1 : secteur angulaire (degres, 90 = face +Z).
     * sx, sz : ecrasement elliptique. noiseAmp/noiseFreq : deformation organique (0 = lisse).
     */
    public void lathe(float[][] prof, int segs, float a0, float a1, Mat m, float sx, float sz,
                      float noiseAmp, float noiseFreq, int seed, boolean inward) {
        int ni = prof.length, nj = segs + 1;
        boolean closed = Math.abs(a1 - a0) >= 359.9f;
        float[][][] P = new float[ni][nj][];
        float[][] S = new float[ni][nj];
        float[][] T = new float[ni][nj];
        float w = m.world > 0 ? m.world : 16f;
        float rAvg = 0;
        for (float[] q : prof) rAvg += q[0];
        rAvg = Math.max(0.5f, rAvg / ni) * (sx + sz) / 2f;
        float len = 0;
        for (int i = 0; i < ni; i++) {
            if (i > 0) len += (float) Math.hypot(prof[i][0] - prof[i - 1][0], prof[i][1] - prof[i - 1][1]);
            for (int j = 0; j < nj; j++) {
                int jj = closed && j == nj - 1 ? 0 : j;
                double a = Math.toRadians(a0 + (a1 - a0) * jj / segs);
                float r = prof[i][0];
                float x = (float) Math.cos(a) * r * sx, z = (float) Math.sin(a) * r * sz, y = prof[i][1];
                if (noiseAmp != 0 && r > 1e-4f) {
                    float n = Noise.fbm(x * noiseFreq + seed * 7.13f, y * noiseFreq + seed * 3.71f, z * noiseFreq - seed * 1.3f, 3);
                    float k = 1 + noiseAmp * n;
                    x *= k;
                    z *= k;
                }
                P[i][j] = new float[]{x, y, z};
                S[i][j] = (float) Math.toRadians((a1 - a0) * j / segs) * rAvg / w;
                T[i][j] = len / w;
            }
        }
        float[][][] N = gridNormals(P, closed);
        // orientation : vers l'exterieur (loin de l'axe), ou l'inverse
        double acc = 0;
        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                acc += P[i][j][0] * N[i][j][0] + P[i][j][2] * N[i][j][2];
            }
        }
        boolean flip = (acc < 0) != inward;
        grid(P, N, S, T, closed, m, flip ? Orient.FLIP : Orient.KEEP, true);
    }

    /**
     * Tube courbe le long d'un chemin (tentacules, cornes, pattes, racines, tuyaux).
     * Reperes par transport parallele (pas de torsion parasite). radii : rayon a chaque point.
     * aroundFull : la texture fait exactement le tour (utile pour les ventouses d'un seul cote).
     */
    public void tube(float[][] path, float[] radii, int radial, Mat m, boolean capStart, boolean capEnd, float twistDeg, boolean aroundFull) {
        tube(path, radii, radial, m, capStart, capEnd, twistDeg, aroundFull, null);
    }

    /**
     * Variante avec normale initiale imposee : la texture (S = 0.5) fait face a -n0 au debut du tube
     * (ventouses d'un tentacule tournees vers l'interieur de la courbe...).
     */
    public void tube(float[][] path, float[] radii, int radial, Mat m, boolean capStart, boolean capEnd, float twistDeg, boolean aroundFull, float[] n0) {
        int n = path.length;
        if (n < 2) return;
        float[][] tan = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] a = path[Math.max(0, i - 1)], b = path[Math.min(n - 1, i + 1)];
            tan[i] = normalize(sub(b, a));
        }
        float[][] nor = new float[n][];
        float[][] bin = new float[n][];
        float[] ref = Math.abs(tan[0][1]) < 0.92f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        nor[0] = normalize(cross(cross(tan[0], ref), tan[0]));
        if (n0 != null) {
            float d0 = dot(n0, tan[0]);
            float[] q0 = {n0[0] - d0 * tan[0][0], n0[1] - d0 * tan[0][1], n0[2] - d0 * tan[0][2]};
            if (dot(q0, q0) > 1e-8f) nor[0] = normalize(q0);
        }
        bin[0] = normalize(cross(tan[0], nor[0]));
        for (int i = 1; i < n; i++) {
            float[] pn = nor[i - 1];
            float d = dot(pn, tan[i]);
            float[] q = {pn[0] - d * tan[i][0], pn[1] - d * tan[i][1], pn[2] - d * tan[i][2]};
            if (dot(q, q) < 1e-10f) q = pn.clone();
            nor[i] = normalize(q);
            bin[i] = normalize(cross(tan[i], nor[i]));
        }
        int nj = radial + 1;
        float[][][] P = new float[n][nj][];
        float[][][] N = new float[n][nj][];
        float[][] S = new float[n][nj];
        float[][] T = new float[n][nj];
        float w = m.world > 0 ? m.world : 16f;
        float rAvg = 0;
        for (float r : radii) rAvg += r;
        rAvg /= radii.length;
        float len = 0;
        for (int i = 0; i < n; i++) {
            if (i > 0) len += (float) Math.sqrt(dot(sub(path[i], path[i - 1]), sub(path[i], path[i - 1])));
            float slope = 0;
            if (n > 1) {
                int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1);
                float dl = (float) Math.sqrt(dot(sub(path[i1], path[i0]), sub(path[i1], path[i0])));
                if (dl > 1e-5f) slope = (radii[i1] - radii[i0]) / dl;
            }
            float tw = (float) Math.toRadians(twistDeg) * i / Math.max(1, n - 1);
            for (int j = 0; j < nj; j++) {
                double ph = 2 * Math.PI * (j % radial) / radial + tw;
                float c = (float) Math.cos(ph), s = (float) Math.sin(ph);
                float[] dir = {c * nor[i][0] + s * bin[i][0], c * nor[i][1] + s * bin[i][1], c * nor[i][2] + s * bin[i][2]};
                float r = radii[i];
                P[i][j] = new float[]{path[i][0] + dir[0] * r, path[i][1] + dir[1] * r, path[i][2] + dir[2] * r};
                N[i][j] = normalize(new float[]{dir[0] - slope * tan[i][0], dir[1] - slope * tan[i][1], dir[2] - slope * tan[i][2]});
                S[i][j] = aroundFull ? (float) j / radial : (float) j / radial * Math.min(1f, (float) (2 * Math.PI * Math.max(rAvg, 0.2f)) / w);
                T[i][j] = len / w;
            }
        }
        grid(P, N, S, T, true, m, Orient.KEEP, true);
        if (capStart && radii[0] > 0.01f) capRing(P[0], path[0], new float[]{-tan[0][0], -tan[0][1], -tan[0][2]}, m);
        if (capEnd && radii[n - 1] > 0.01f) capRing(P[n - 1], path[n - 1], tan[n - 1], m);
    }

    private void capRing(float[][] ring, float[] center, float[] normal, Mat m) {
        for (int j = 0; j < ring.length - 1; j++) {
            face(m, normal, center, ring[j], ring[j + 1]);
        }
    }

    /** Fonction de surface parametrique (u, v dans [0,1]) -> point (pixels). */
    @FunctionalInterface
    public interface SurfaceFn {
        float[] at(float u, float v);
    }

    /** Surface parametrique. sSpan/tSpan : etendue de texture (en tuiles) le long de u et v. */
    public void surface(SurfaceFn f, int nu, int nv, Mat m, float sSpan, float tSpan, boolean closedU, Orient orient) {
        float[][][] P = new float[nv + 1][nu + 1][];
        float[][] S = new float[nv + 1][nu + 1];
        float[][] T = new float[nv + 1][nu + 1];
        for (int i = 0; i <= nv; i++) {
            for (int j = 0; j <= nu; j++) {
                float u = closedU && j == nu ? 0f : (float) j / nu;
                P[i][j] = f.at(u, (float) i / nv);
                S[i][j] = (float) j / nu * sSpan;
                T[i][j] = (float) i / nv * tSpan;
            }
        }
        grid(P, null, S, T, closedU, m, orient, true);
    }

    /** Rocher / masse organique irreguliere. */
    public void rock(float rx, float ry, float rz, float rough, float freq, int seed, int seg, Mat m) {
        surface((u, v) -> {
            double ph = u * 2 * Math.PI, th = v * Math.PI;
            float dx = (float) (Math.sin(th) * Math.cos(ph)), dy = (float) Math.cos(th), dz = (float) (Math.sin(th) * Math.sin(ph));
            float n = Noise.fbm(dx * freq + seed * 5.31f, dy * freq + seed * 1.77f, dz * freq - seed * 2.9f, 4);
            float k = 1 + rough * n;
            return new float[]{dx * rx * k, dy * ry * k, dz * rz * k};
        }, seg, Math.max(4, seg / 2), m, Math.min(1f, (rx + rz) * 3.14f / Math.max(1f, m.world)), Math.min(1f, ry * 3.14f / Math.max(1f, m.world)), true, Orient.OUT);
    }

    /** Masse ellipsoidale (squareness < 1 = plus carree, > 1 = plus pointue) avec deformation. */
    public void blob(float rx, float ry, float rz, float squareness, float noiseAmp, float freq, int seed, int seg, Mat m) {
        surface((u, v) -> {
            double ph = u * 2 * Math.PI, th = v * Math.PI;
            float dx = (float) (Math.sin(th) * Math.cos(ph)), dy = (float) Math.cos(th), dz = (float) (Math.sin(th) * Math.sin(ph));
            dx = Math.signum(dx) * (float) Math.pow(Math.abs(dx), squareness);
            dy = Math.signum(dy) * (float) Math.pow(Math.abs(dy), squareness);
            dz = Math.signum(dz) * (float) Math.pow(Math.abs(dz), squareness);
            float k = 1;
            if (noiseAmp != 0) k += noiseAmp * Noise.fbm(dx * freq + seed * 3.1f, dy * freq - seed * 1.3f, dz * freq + seed * 0.7f, 3);
            return new float[]{dx * rx * k, dy * ry * k, dz * rz * k};
        }, seg, Math.max(4, seg / 2), m, Math.min(1f, (rx + rz) * 3.14f / Math.max(1f, m.world)), Math.min(1f, ry * 3.14f / Math.max(1f, m.world)), true, Orient.OUT);
    }

    /** Cristal hexagonal (ou n cotes) pointu, d'axe Y, facettes plates. */
    public void crystal(float r, float h, float tip, int sides, Mat m) {
        float[] c = {0, h * 0.45f, 0};
        float[][] bot = new float[sides][];
        float[][] top = new float[sides][];
        for (int k = 0; k < sides; k++) {
            double a = 2 * Math.PI * k / sides;
            bot[k] = p((float) Math.cos(a) * r * 0.82f, 0, (float) Math.sin(a) * r * 0.82f);
            top[k] = p((float) Math.cos(a) * r, h, (float) Math.sin(a) * r);
        }
        float[] apex = p(0, h + tip, 0);
        for (int k = 0; k < sides; k++) {
            int q = (k + 1) % sides;
            flatOut(m, c, bot[k], bot[q], top[q], top[k]);
            flatOut(m, c, top[k], top[q], apex);
            flatOut(m, c, p(0, 0, 0), bot[q], bot[k]);
        }
    }

    /**
     * Dalle aux coins et aretes arrondis, centree (face avant en +Z) : corps de cadenas, couvertures de livre...
     * front : materiau de la face avant (FIT = la tuile couvre toute la face).
     */
    public void roundedSlab(float w, float h, float d, float corner, float bevel, int cseg, Mat front, Mat side) {
        corner = Math.min(corner, Math.min(w, h) / 2 - 0.01f);
        bevel = Math.min(bevel, Math.min(corner, d / 2) * 0.95f);
        float[][] outN = roundedOutlineNormals(cseg);
        float[][] outer = roundedOutline(w / 2, h / 2, corner, cseg, 0);
        float[][] inner = roundedOutline(w / 2, h / 2, corner, cseg, bevel);
        int n = outer.length;
        float zf = d / 2, zb = -d / 2;
        float[] fnF = {0, 0, 1}, fnB = {0, 0, -1};
        float[] bF = boundsFor(w, h, fnF, zf);
        float[] bB = boundsFor(w, h, fnB, zb);
        for (int k = 0; k < n; k++) {
            int q = (k + 1) % n;
            faceBounded(front, fnF, bF, p(0, 0, zf), p(inner[k][0], inner[k][1], zf), p(inner[q][0], inner[q][1], zf));
            faceBounded(side, fnB, bB, p(0, 0, zb), p(inner[q][0], inner[q][1], zb), p(inner[k][0], inner[k][1], zb));
            // biseaux avant/arriere et flanc
            float[] na = outN[k], nb = outN[q];
            float[][] chF = {p(inner[k][0], inner[k][1], zf), p(inner[q][0], inner[q][1], zf), p(outer[q][0], outer[q][1], zf - bevel), p(outer[k][0], outer[k][1], zf - bevel)};
            float[][] chFn = {normalize(new float[]{na[0], na[1], 1.2f}), normalize(new float[]{nb[0], nb[1], 1.2f}), normalize(new float[]{nb[0], nb[1], 0.4f}), normalize(new float[]{na[0], na[1], 0.4f})};
            poly(chF, side.project(chF, normalize(new float[]{na[0] + nb[0], na[1] + nb[1], 1}), null), chFn, normalize(new float[]{na[0] + nb[0], na[1] + nb[1], 1}));
            float[][] sd = {p(outer[k][0], outer[k][1], zf - bevel), p(outer[q][0], outer[q][1], zf - bevel), p(outer[q][0], outer[q][1], zb + bevel), p(outer[k][0], outer[k][1], zb + bevel)};
            float[][] sdn = {new float[]{na[0], na[1], 0}, new float[]{nb[0], nb[1], 0}, new float[]{nb[0], nb[1], 0}, new float[]{na[0], na[1], 0}};
            float[] sfn = normalize(new float[]{na[0] + nb[0], na[1] + nb[1], 0});
            poly(sd, side.project(sd, sfn, null), sdn, sfn);
            float[][] chB = {p(outer[k][0], outer[k][1], zb + bevel), p(outer[q][0], outer[q][1], zb + bevel), p(inner[q][0], inner[q][1], zb), p(inner[k][0], inner[k][1], zb)};
            float[][] chBn = {normalize(new float[]{na[0], na[1], -0.4f}), normalize(new float[]{nb[0], nb[1], -0.4f}), normalize(new float[]{nb[0], nb[1], -1.2f}), normalize(new float[]{na[0], na[1], -1.2f})};
            poly(chB, side.project(chB, normalize(new float[]{na[0] + nb[0], na[1] + nb[1], -1}), null), chBn, normalize(new float[]{na[0] + nb[0], na[1] + nb[1], -1}));
        }
    }

    private static float[] boundsFor(float w, float h, float[] n, float z) {
        float[] a = Mat.planar(p(-w / 2, -h / 2, z), n);
        float[] b = Mat.planar(p(w / 2, h / 2, z), n);
        return new float[]{Math.min(a[0], b[0]), Math.max(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[1], b[1])};
    }

    private static float[][] roundedOutline(float hw, float hh, float r, int cseg, float inset) {
        float[][] out = new float[4 * (cseg + 1)][];
        float[][] c = {{hw - r, hh - r, 0}, {-hw + r, hh - r, 90}, {-hw + r, -hh + r, 180}, {hw - r, -hh + r, 270}};
        float rr = Math.max(0.001f, r - inset);
        int k = 0;
        for (float[] cc : c) {
            for (int i = 0; i <= cseg; i++) {
                double a = Math.toRadians(cc[2] + 90.0 * i / cseg);
                out[k++] = new float[]{cc[0] + rr * (float) Math.cos(a), cc[1] + rr * (float) Math.sin(a)};
            }
        }
        return out;
    }

    private static float[][] roundedOutlineNormals(int cseg) {
        float[][] out = new float[4 * (cseg + 1)][];
        int k = 0;
        for (int q = 0; q < 4; q++) {
            for (int i = 0; i <= cseg; i++) {
                double a = Math.toRadians(q * 90 + 90.0 * i / cseg);
                out[k++] = new float[]{(float) Math.cos(a), (float) Math.sin(a)};
            }
        }
        return out;
    }

    /**
     * Plaque extrudee d'un contour convexe (plan XY, sens trigonometrique), face avant en +Z (z = depth/2),
     * aretes biseautees. smooth : normales lissees d'une arete a l'autre (contours arrondis).
     * front : materiau de la face avant (FIT = la tuile couvre le rectangle englobant du contour).
     */
    public void slab(float[][] outline, float depth, float bevel, boolean smooth, Mat front, Mat back, Mat side) {
        int n = outline.length;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, cx = 0, cy = 0;
        for (float[] q : outline) {
            minX = Math.min(minX, q[0]);
            maxX = Math.max(maxX, q[0]);
            minY = Math.min(minY, q[1]);
            maxY = Math.max(maxY, q[1]);
            cx += q[0];
            cy += q[1];
        }
        cx /= n;
        cy /= n;
        bevel = Math.min(bevel, depth * 0.45f);
        // normales des aretes (vers l'exterieur)
        float[][] en = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] a = outline[i], b = outline[(i + 1) % n];
            float ex = b[0] - a[0], ey = b[1] - a[1];
            float l = (float) Math.sqrt(ex * ex + ey * ey);
            float nx = ey / Math.max(l, 1e-6f), ny = -ex / Math.max(l, 1e-6f);
            float mx = (a[0] + b[0]) / 2 - cx, my = (a[1] + b[1]) / 2 - cy;
            if (nx * mx + ny * my < 0) {
                nx = -nx;
                ny = -ny;
            }
            en[i] = new float[]{nx, ny};
        }
        float[][] vn = new float[n][];
        float[][] inner = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] na = en[(i + n - 1) % n], nb = en[i];
            float x = na[0] + nb[0], y = na[1] + nb[1];
            float l = (float) Math.sqrt(x * x + y * y);
            vn[i] = new float[]{x / Math.max(l, 1e-6f), y / Math.max(l, 1e-6f)};
            // decalage vers l'interieur de "bevel" perpendiculairement aux deux aretes
            float d = vn[i][0] * nb[0] + vn[i][1] * nb[1];
            float k = bevel / Math.max(0.35f, d);
            inner[i] = new float[]{outline[i][0] - vn[i][0] * k, outline[i][1] - vn[i][1] * k};
        }
        float zf = depth / 2, zb = -depth / 2;
        float[] fnF = {0, 0, 1}, fnB = {0, 0, -1};
        float[] bF = boundsFor2(minX, maxX, minY, maxY, fnF, zf);
        float[] bB = boundsFor2(minX, maxX, minY, maxY, fnB, zb);
        for (int i = 0; i < n; i++) {
            int q = (i + 1) % n;
            faceBounded(front, fnF, bF, p(cx, cy, zf), p(inner[i][0], inner[i][1], zf), p(inner[q][0], inner[q][1], zf));
            if (back != null) {
                faceBounded(back, fnB, bB, p(cx, cy, zb), p(inner[q][0], inner[q][1], zb), p(inner[i][0], inner[i][1], zb));
            }
            float[] na = smooth ? vn[i] : en[i], nb = smooth ? vn[q] : en[i];
            float[] fn2 = en[i];
            // biseau avant
            float[][] chF = {p(inner[i][0], inner[i][1], zf), p(inner[q][0], inner[q][1], zf), p(outline[q][0], outline[q][1], zf - bevel), p(outline[i][0], outline[i][1], zf - bevel)};
            float[][] chFn = {normalize(v(na[0], na[1], 1.4f)), normalize(v(nb[0], nb[1], 1.4f)), normalize(v(nb[0], nb[1], 0.35f)), normalize(v(na[0], na[1], 0.35f))};
            float[] cfn = normalize(v(fn2[0], fn2[1], 1));
            poly(chF, side.project(chF, cfn, null), chFn, cfn);
            // flanc
            float[][] sd = {p(outline[i][0], outline[i][1], zf - bevel), p(outline[q][0], outline[q][1], zf - bevel), p(outline[q][0], outline[q][1], zb + bevel), p(outline[i][0], outline[i][1], zb + bevel)};
            float[][] sdn = {v(na[0], na[1], 0), v(nb[0], nb[1], 0), v(nb[0], nb[1], 0), v(na[0], na[1], 0)};
            float[] sfn = v(fn2[0], fn2[1], 0);
            poly(sd, side.project(sd, sfn, null), sdn, sfn);
            // biseau arriere
            float[][] chB = {p(outline[i][0], outline[i][1], zb + bevel), p(outline[q][0], outline[q][1], zb + bevel), p(inner[q][0], inner[q][1], zb), p(inner[i][0], inner[i][1], zb)};
            float[][] chBn = {normalize(v(na[0], na[1], -0.35f)), normalize(v(nb[0], nb[1], -0.35f)), normalize(v(nb[0], nb[1], -1.4f)), normalize(v(na[0], na[1], -1.4f))};
            float[] bfn = normalize(v(fn2[0], fn2[1], -1));
            poly(chB, side.project(chB, bfn, null), chBn, bfn);
        }
    }

    private static float[] boundsFor2(float minX, float maxX, float minY, float maxY, float[] n, float z) {
        float[] a = Mat.planar(p(minX, minY, z), n);
        float[] b = Mat.planar(p(maxX, maxY, z), n);
        return new float[]{Math.min(a[0], b[0]), Math.max(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[1], b[1])};
    }

    /** Contour rectangulaire aux coins arrondis (sens trigonometrique). */
    public static float[][] roundRect(float w, float h, float r, int cseg) {
        return roundedOutline(w / 2, h / 2, Math.min(r, Math.min(w, h) / 2 - 0.01f), cseg, 0);
    }

    /** Contour polygonal regulier (cercle si n grand). */
    public static float[][] ngon(float r, int n, float startDeg) {
        float[][] out = new float[n][];
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(startDeg + 360.0 * i / n);
            out[i] = new float[]{(float) Math.cos(a) * r, (float) Math.sin(a) * r};
        }
        return out;
    }

    /** Contour d'ecusson (haut droit, bas en pointe arrondie). */
    public static float[][] shield(float w, float h, int seg) {
        java.util.List<float[]> pts = new java.util.ArrayList<>();
        float hw = w / 2, top = h / 2, bottom = -h / 2;
        float shoulder = top - h * 0.42f;
        // bas : deux arcs qui se rejoignent en pointe
        for (int i = 0; i <= seg; i++) {
            float u = (float) i / seg;
            float x = hw * (1 - u);
            float y = shoulder - (shoulder - bottom) * (float) Math.pow(u, 1.6);
            pts.add(new float[]{x, y});
        }
        for (int i = 1; i <= seg; i++) {
            float u = (float) i / seg;
            float x = -hw * u;
            float y = bottom + (shoulder - bottom) * (float) (1 - Math.pow(1 - u, 1.6));
            pts.add(new float[]{x, y});
        }
        pts.add(new float[]{-hw, top});
        pts.add(new float[]{hw, top});
        // ordre trigonometrique : on part de la droite en descendant -> inverser pour CCW
        float[][] out = pts.toArray(new float[0][]);
        java.util.Collections.reverse(java.util.Arrays.asList(out));
        return out;
    }

    /** Chaine de maillons (tores alternes) le long d'un chemin. */
    public void chain(float[][] path, float linkLen, float linkWidth, float wire, Mat m) {
        chain(path, linkLen, linkWidth, wire, m, 12, 6);
    }

    /** Chaine avec resolution des maillons (segU autour, segV section). */
    public void chain(float[][] path, float linkLen, float linkWidth, float wire, Mat m, int segU, int segV) {
        // echantillonnage a pas constant
        float step = linkLen * 0.74f;
        float total = 0;
        float[] segLen = new float[path.length];
        for (int i = 1; i < path.length; i++) {
            segLen[i] = (float) Math.sqrt(dot(sub(path[i], path[i - 1]), sub(path[i], path[i - 1])));
            total += segLen[i];
        }
        int links = Math.max(1, (int) (total / step));
        float[] up0 = {0, 1, 0};
        for (int k = 0; k < links; k++) {
            float s = (k + 0.5f) * total / links;
            int i = 1;
            float acc = 0;
            while (i < path.length - 1 && acc + segLen[i] < s) {
                acc += segLen[i];
                i++;
            }
            float f = segLen[i] > 1e-6f ? (s - acc) / segLen[i] : 0;
            float[] a = path[i - 1], b = path[i];
            float[] pos = {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
            float[] x = normalize(sub(b, a));
            float[] ref = Math.abs(dot(x, up0)) < 0.9f ? up0 : new float[]{1, 0, 0};
            float[] y = normalize(cross(cross(x, ref), x));
            float[] z = normalize(cross(x, y));
            if (k % 2 == 1) {
                float[] t = y;
                y = z;
                z = new float[]{-t[0], -t[1], -t[2]};
            }
            // tore : anneau dans le plan XZ local autour de Y -> Y local = z (normale de l'anneau)
            Matrix4f basis = new Matrix4f(
                    x[0], x[1], x[2], 0,
                    z[0], z[1], z[2], 0,
                    y[0], y[1], y[2], 0,
                    pos[0], pos[1], pos[2], 1);
            push();
            mul(basis);
            scale(linkLen / linkWidth, 1, 1);
            torus(linkWidth / 2 - wire / 2, wire / 2, segU, segV, 0, 360, m);
            pop();
        }
    }

    /** Ruban plat le long d'un chemin (algues, banniere, fils de toile). side : direction de la largeur. */
    public void ribbon(float[][] path, float[] widths, float[] side, Mat m) {
        int n = path.length;
        float[][][] P = new float[n][2][];
        float[][][] N = new float[n][2][];
        float[][] S = new float[n][2];
        float[][] T = new float[n][2];
        float w = m.world > 0 ? m.world : 16f;
        float len = 0;
        for (int i = 0; i < n; i++) {
            float[] a = path[Math.max(0, i - 1)], b = path[Math.min(n - 1, i + 1)];
            float[] t = normalize(sub(b, a));
            float d = dot(side, t);
            float[] sd = normalize(new float[]{side[0] - d * t[0], side[1] - d * t[1], side[2] - d * t[2]});
            float[] nn = normalize(cross(t, sd));
            if (i > 0) len += (float) Math.sqrt(dot(sub(path[i], path[i - 1]), sub(path[i], path[i - 1])));
            float hw = widths[i] / 2;
            P[i][0] = new float[]{path[i][0] - sd[0] * hw, path[i][1] - sd[1] * hw, path[i][2] - sd[2] * hw};
            P[i][1] = new float[]{path[i][0] + sd[0] * hw, path[i][1] + sd[1] * hw, path[i][2] + sd[2] * hw};
            N[i][0] = nn;
            N[i][1] = nn;
            S[i][0] = 0;
            S[i][1] = 1;
            T[i][0] = m.fit ? (float) i / (n - 1) : len / w;
            T[i][1] = T[i][0];
        }
        grid(P, N, S, T, false, m, Orient.KEEP, !m.fit);
    }

    /** Carte plate (dans le plan XY local, normale +Z), toute la tuile (FIT) : feuilles, pages, bulletins. */
    public void card(float w, float h, Mat m) {
        Mat f = m.fit ? m : m.asFit();
        face(f, v(0, 0, 1), p(-w / 2, -h / 2, 0), p(w / 2, -h / 2, 0), p(w / 2, h / 2, 0), p(-w / 2, h / 2, 0));
    }

    /** Anneau torique incline (chaque point du tore) : raccourci pour un tore dans le plan XY (axe Z). */
    public void torusXY(float R, float r, int segU, int segV, Mat m) {
        push();
        rotateX(90);
        torus(R, r, segU, segV, 0, 360, m);
        pop();
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    // =================================================================== utilitaires
    public static float[] p(float x, float y, float z) {
        return new float[]{x, y, z};
    }

    public static float[] v(float x, float y, float z) {
        return new float[]{x, y, z};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float dist(float[] a, float[] b) {
        float dx = a[0] - b[0], dy = a[1] - b[1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float[] normalize(float[] a) {
        float l = (float) Math.sqrt(dot(a, a));
        if (l < 1e-8f) return new float[]{0, 1, 0};
        return new float[]{a[0] / l, a[1] / l, a[2] / l};
    }

    private static float[] centroid(float[][] pts) {
        float[] c = new float[3];
        for (float[] q : pts) {
            c[0] += q[0];
            c[1] += q[1];
            c[2] += q[2];
        }
        c[0] /= pts.length;
        c[1] /= pts.length;
        c[2] /= pts.length;
        return c;
    }

    /** Normale d'un polygone (methode de Newell, robuste aux quads non plans). */
    private static float[] newell(float[][] pts) {
        float nx = 0, ny = 0, nz = 0;
        int n = pts.length;
        for (int i = 0; i < n; i++) {
            float[] a = pts[i], b = pts[(i + 1) % n];
            nx += (a[1] - b[1]) * (a[2] + b[2]);
            ny += (a[2] - b[2]) * (a[0] + b[0]);
            nz += (a[0] - b[0]) * (a[1] + b[1]);
        }
        return new float[]{nx, ny, nz};
    }
}
