package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.registry.Snd;
import fr.heraliz.casino.client.render.scene.Sprite;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Neon bricole : des tubes de verre tordus a la main, fixes par des pattes sur un carre de metal deploye rouille
 * encadre de corniere, alimentes par un vieux transformateur. Six motifs (777, cerises, as de pique, des, BAR, fer a
 * cheval) ; dans chacun un tube a un mauvais contact et clignote.
 */
public final class NeonDesign extends DecorDesign {
    static final float Z_MESH = 0.45f, Z_TUBE = 1.55f, R_TUBE = 0.26f;

    /** Un groupe de tubes d'une meme couleur (allume / eteint ensemble). */
    static final class Group {
        final int color;
        final List<float[][]> paths = new ArrayList<>();
        final List<float[]> beads = new ArrayList<>();
        Mesh mesh = Mesh.EMPTY;
        float[][] halos = new float[0][];

        Group(int color) {
            this.color = color;
        }

        Group path(float[]... pts) {
            paths.add(pts);
            return this;
        }

        Group bead(float x, float y, float r) {
            beads.add(new float[]{x, y, r});
            return this;
        }
    }

    private final int motif;
    private final List<Group> groups = new ArrayList<>();
    /** Groupe au faux contact. */
    private int faulty;
    private Mesh body = Mesh.EMPTY;

    public NeonDesign(int motif) {
        super("neon_mural_" + motif, 0xFF4FD8);
        this.motif = motif;
        itemScale = 1.2f;
        itemCenter = new float[]{8, 8, 1.2f};
    }

    // =================================================================== motifs
    private void motifs() {
        switch (motif) {
            case 0 -> {
                Group frame = new Group(NEON_AMBER).path(closed(roundRect(2.3f, 4.2f, 13.7f, 12.8f, 1.3f)));
                groups.add(frame);
                for (int k = 0; k < 3; k++) {
                    float x = 3.45f + k * 3.15f;
                    Group g = new Group(NEON_RED).path(rounded(new float[][]{{x, 11.1f}, {x + 2.75f, 11.1f}, {x + 1.05f, 5.6f}}, 0.45f));
                    groups.add(g);
                }
                faulty = 2;
            }
            case 1 -> {
                Group red = new Group(NEON_RED);
                red.path(circle(5.5f, 6.1f, 1.85f, 100, 340));
                Group red2 = new Group(NEON_RED);
                red2.path(circle(10.1f, 5.6f, 1.85f, 100, 340));
                Group green = new Group(NEON_GREEN);
                green.path(bezier(new float[]{5.7f, 7.95f}, new float[]{6.4f, 11.2f}, new float[]{9.3f, 12.5f}, 10));
                green.path(bezier(new float[]{10.0f, 7.45f}, new float[]{9.6f, 10.4f}, new float[]{9.35f, 12.3f}, 8));
                green.path(closed(smoothLoop(new float[][]{{9.4f, 12.5f}, {10.9f, 13.7f}, {13.1f, 13.2f}, {11.6f, 11.9f}}, 5)));
                Group shine = new Group(NEON_WHITE);
                shine.path(circle(5.5f, 6.1f, 1.1f, 110, 60));
                shine.path(circle(10.1f, 5.6f, 1.1f, 110, 60));
                groups.add(red);
                groups.add(red2);
                groups.add(green);
                groups.add(shine);
                faulty = 1;
            }
            case 2 -> {
                Group spade = new Group(NEON_WHITE).path(closed(spade(8.0f, 9.4f, 0.215f)));
                Group stem = new Group(NEON_WHITE).path(rounded(new float[][]{{7.45f, 7.7f}, {6.2f, 4.2f}, {9.8f, 4.2f}, {8.55f, 7.7f}}, 0.35f));
                Group ace = new Group(NEON_RED);
                ace.path(rounded(new float[][]{{2.5f, 10.9f}, {3.45f, 13.6f}, {4.4f, 10.9f}}, 0.25f));
                ace.path(new float[][]{{2.9f, 11.9f}, {4.0f, 11.9f}});
                groups.add(spade);
                groups.add(stem);
                groups.add(ace);
                faulty = 1;
            }
            case 3 -> {
                Group d1 = new Group(NEON_PINK).path(closed(rotRect(5.4f, 9.4f, 4.6f, 4.6f, 0.9f, -14)));
                float[][] dots1 = {{-1.2f, 1.2f}, {0, 0}, {1.2f, -1.2f}};
                for (float[] d : dots1) {
                    float[] q = rot(d[0], d[1], -14);
                    d1.bead(5.4f + q[0], 9.4f + q[1], 0.42f);
                }
                Group d2 = new Group(NEON_VIOLET).path(closed(rotRect(10.7f, 6.8f, 4.4f, 4.4f, 0.9f, 18)));
                float[][] dots2 = {{-1.15f, 1.15f}, {1.15f, 1.15f}, {0, 0}, {-1.15f, -1.15f}, {1.15f, -1.15f}};
                for (float[] d : dots2) {
                    float[] q = rot(d[0], d[1], 18);
                    d2.bead(10.7f + q[0], 6.8f + q[1], 0.4f);
                }
                groups.add(d1);
                groups.add(d2);
                faulty = 1;
            }
            case 4 -> {
                Group frame = new Group(NEON_PINK).path(closed(roundRect(2.3f, 5.1f, 13.7f, 11.9f, 1.6f)));
                Group b = new Group(NEON_CYAN);
                List<float[]> bb = new ArrayList<>();
                bb.add(new float[]{3.9f, 6.4f});
                bb.add(new float[]{3.9f, 10.6f});
                bb.add(new float[]{4.9f, 10.6f});
                addArc(bb, 4.9f, 9.575f, 1.025f, 90, -90);
                addArc(bb, 5.05f, 7.475f, 1.075f, 90, -90);
                bb.add(new float[]{3.9f, 6.4f});
                b.path(bb.toArray(new float[0][]));
                b.path(new float[][]{{4.1f, 8.55f}, {4.8f, 8.55f}});
                Group a = new Group(NEON_CYAN);
                a.path(rounded(new float[][]{{6.65f, 6.4f}, {7.95f, 10.6f}, {9.25f, 6.4f}}, 0.3f));
                a.path(new float[][]{{7.2f, 7.95f}, {8.7f, 7.95f}});
                Group r = new Group(NEON_CYAN);
                List<float[]> rr = new ArrayList<>();
                rr.add(new float[]{10.35f, 6.4f});
                rr.add(new float[]{10.35f, 10.6f});
                rr.add(new float[]{11.35f, 10.6f});
                addArc(rr, 11.35f, 9.55f, 1.05f, 90, -90);
                rr.add(new float[]{10.55f, 8.5f});
                r.path(rr.toArray(new float[0][]));
                r.path(new float[][]{{11.1f, 8.5f}, {12.4f, 6.4f}});
                groups.add(frame);
                groups.add(b);
                groups.add(a);
                groups.add(r);
                faulty = 3;
            }
            default -> {
                Group shoe = new Group(NEON_GREEN);
                List<float[]> s = new ArrayList<>();
                addArc(s, 8, 8.6f, 4.4f, 22, -202);
                addArc(s, 8, 8.6f, 2.85f, -198, 18);
                shoe.path(closed(s.toArray(new float[0][])));
                for (float a : new float[]{-25, -65, -115, -155}) {
                    double r = Math.toRadians(a);
                    shoe.bead(8 + 3.62f * (float) Math.cos(r), 8.6f + 3.62f * (float) Math.sin(r), 0.3f);
                }
                Group star1 = new Group(NEON_AMBER).path(closed(star(4.1f, 12.9f, 1.15f)));
                Group star2 = new Group(NEON_AMBER).path(closed(star(12.0f, 13.3f, 0.85f)));
                groups.add(shoe);
                groups.add(star1);
                groups.add(star2);
                faulty = 2;
            }
        }
    }

    // =================================================================== construction
    @Override
    protected void build() {
        motifs();
        MeshBuilder b = new MeshBuilder();
        // ---- cadre en corniere rouillee et metal deploye
        float c0 = 1.0f, c1 = 15.0f, w = 0.6f;
        b.bevelBox(c0, c0, 0.15f, c1, c0 + w, 0.8f, 0.08f, RUST);
        b.bevelBox(c0, c1 - w, 0.15f, c1, c1, 0.8f, 0.08f, RUST);
        b.bevelBox(c0, c0 + w, 0.15f, c0 + w, c1 - w, 0.8f, 0.08f, RUST);
        b.bevelBox(c1 - w, c0 + w, 0.15f, c1, c1 - w, 0.8f, 0.08f, RUST);
        b.face(MESH, NZ, p(c0 + 0.3f, c0 + 0.3f, Z_MESH), p(c1 - 0.3f, c0 + 0.3f, Z_MESH), p(c1 - 0.3f, c1 - 0.3f, Z_MESH), p(c0 + 0.3f, c1 - 0.3f, Z_MESH));
        for (float[] q : new float[][]{{c0 + 0.3f, c0 + 0.3f}, {c1 - 0.3f, c0 + 0.3f}, {c0 + 0.3f, c1 - 0.3f}, {c1 - 0.3f, c1 - 0.3f}}) {
            screw(b, q[0], q[1], 0.8f, 0.2f, STEEL);
        }
        // ---- transformateur et son cable
        b.bevelBox(10.6f, 1.5f, 0.6f, 14.3f, 3.3f, 1.7f, 0.15f, STEEL);
        b.push().translate(12.45f, 2.4f, 1.72f);
        b.decalZ(STICKER2, 0, 0, 0, 3.2f, 0.72f);
        b.pop();
        cable(b, new float[][]{p(14.3f, 2.4f, 1.1f), p(15.2f, 2.1f, 1.0f), p(15.6f, 1.2f, 0.6f), p(15.6f, 0.2f, 0.25f)}, 0.18f, CABLE);
        // ---- pattes (supports des tubes) et electrodes aux bouts des tubes
        for (Group g : groups) {
            for (float[][] path : g.paths) {
                float acc = 1.2f;
                for (int i = 1; i < path.length; i++) {
                    float d = dist(path[i - 1], path[i]);
                    acc += d;
                    if (acc >= 3.2f) {
                        acc = 0;
                        b.push().translate(path[i][0], path[i][1], Z_MESH).rotateX(90);
                        b.cylinder(0.09f, 0, Z_TUBE - R_TUBE - Z_MESH + 0.05f, 5, DARK, null, null);
                        b.pop();
                    }
                }
                electrode(b, path[0]);
                electrode(b, path[path.length - 1]);
            }
        }
        Occlusion.bake(b, 0.3f, 1.8f, 0.4f, Float.NaN);
        body = b.build();

        // ---- tubes (un maillage par groupe : allumes ou non ensemble)
        for (Group g : groups) {
            MeshBuilder t = new MeshBuilder();
            List<float[]> halos = new ArrayList<>();
            for (float[][] path : g.paths) {
                float[][] fine = resample(path, 0.3f);
                float[][] p3 = new float[fine.length][];
                float[] radii = new float[fine.length];
                for (int i = 0; i < fine.length; i++) {
                    p3[i] = p(fine[i][0], fine[i][1], Z_TUBE);
                    radii[i] = R_TUBE;
                }
                t.tube(p3, radii, 7, neon(g.color), true, true, 0, false);
                float acc = 0;
                for (int i = 1; i < fine.length; i++) {
                    acc += dist(fine[i - 1], fine[i]);
                    if (acc >= 1.3f) {
                        acc = 0;
                        halos.add(fine[i]);
                    }
                }
            }
            for (float[] bd : g.beads) {
                t.push().translate(bd[0], bd[1], Z_TUBE);
                t.sphere(bd[2], 8, 5, neon(g.color));
                t.pop();
                halos.add(new float[]{bd[0], bd[1]});
            }
            g.mesh = t.build();
            g.halos = halos.toArray(new float[0][]);
        }
    }

    /** Bout de tube peint en noir qui repart dans le grillage (electrode). */
    private static void electrode(MeshBuilder b, float[] q) {
        b.tube(new float[][]{p(q[0], q[1], Z_TUBE), p(q[0], q[1], (Z_TUBE + Z_MESH) / 2), p(q[0], q[1], Z_MESH - 0.1f)},
                new float[]{R_TUBE * 1.05f, R_TUBE * 1.05f, R_TUBE * 1.05f}, 6, DARK, false, true, 0, false);
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        float t = s.time + s.seed * 911;
        float hum = 0.93f + 0.07f * (float) Math.sin(s.time * 2.9f + s.seed * 5);
        int main = groups.isEmpty() ? NEON_PINK : groups.get(0).color;
        float spill = 0;
        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            float lit = v.on ? hum : 0;
            if (v.on && i == faulty && !s.item) lit *= faultLevel(t);
            out.solid(p, g.mesh, lit > 0.02f ? lit : 0);
            if (lit > 0.02f && !s.item) {
                int col = NEON_GLOW[g.color];
                for (float[] h : g.halos) out.billboard(p, Sprite.HALO, h[0], h[1], Z_TUBE + 0.1f, 1.05f, 0, col, 0.13f * lit);
            }
            spill += lit;
        }
        if (v.on && !s.item && spill > 0.02f) {
            // la lumiere des tubes eclaire le grillage et le mur
            p.push().translate(0, 0, Z_MESH + 0.05f);
            out.quad(p, Sprite.HALO, -1.5f, -1.5f, 17.5f, 17.5f, NEON_GLOW[main], 0.22f * Math.min(1f, spill / groups.size()));
            p.pop();
        }
    }

    /** Eclat du tube au faux contact : coupures breves, crises de clignotement, parfois eteint un moment. */
    private static float faultLevel(float t) {
        int tk = (int) t;
        float c = t % 260;
        if (c > 200 && c < 230) return 0.03f;
        if (c >= 230) return Ease.hash(tk * 3 + 1) > 0.5f ? 1f : 0.05f;
        return Ease.hash((tk / 2) * 5 + 7) > 0.95f ? 0.1f : 1f;
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (!v.on) return;
        float t = s.time + s.seed * 911;
        float c = t % 260;
        // le tube qui se rallume claque et gresille
        if (r.nextInt(700) == 0) fx.particle(FxSink.SPARK, 14.3f, 2.4f, 1.4f, rnd(r, 0.04f), 0.05f, 0.05f, 0xFFE0A0, 0.28f, 8);
    }

    // =================================================================== chemins 2D
    static float dist(float[] a, float[] b) {
        return (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
    }

    static boolean isClosedLoop(float[][] path) {
        return path.length > 2 && dist(path[0], path[path.length - 1]) < 0.5f;
    }

    /** Boucle refermee en laissant un petit jour (les electrodes passent dans le grillage). */
    static float[][] closed(float[][] pts) {
        float[][] o = java.util.Arrays.copyOf(pts, pts.length + 1);
        float[] a = pts[pts.length - 1], b = pts[0];
        float d = dist(a, b);
        float k = d > 0.4f ? (d - 0.35f) / d : 0;
        o[pts.length] = new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k};
        return o;
    }

    /** Coins arrondis (le verre se cintre, il ne se plie pas). */
    static float[][] rounded(float[][] pts, float r) {
        List<float[]> o = new ArrayList<>();
        o.add(pts[0]);
        for (int i = 1; i < pts.length - 1; i++) {
            float[] a = pts[i - 1], b = pts[i], c = pts[i + 1];
            float la = dist(a, b), lc = dist(b, c);
            float ra = Math.min(r, la * 0.45f), rc = Math.min(r, lc * 0.45f);
            float[] p0 = {b[0] + (a[0] - b[0]) * ra / la, b[1] + (a[1] - b[1]) * ra / la};
            float[] p1 = {b[0] + (c[0] - b[0]) * rc / lc, b[1] + (c[1] - b[1]) * rc / lc};
            for (int k = 0; k <= 4; k++) {
                float t = k / 4f, u = 1 - t;
                o.add(new float[]{u * u * p0[0] + 2 * u * t * b[0] + t * t * p1[0], u * u * p0[1] + 2 * u * t * b[1] + t * t * p1[1]});
            }
        }
        o.add(pts[pts.length - 1]);
        return o.toArray(new float[0][]);
    }

    /** Points regulierement espaces le long d'une polyligne. */
    static float[][] resample(float[][] pts, float step) {
        List<float[]> o = new ArrayList<>();
        o.add(pts[0]);
        float carry = 0;
        for (int i = 1; i < pts.length; i++) {
            float[] a = pts[i - 1], b = pts[i];
            float d = dist(a, b);
            if (d < 1e-5f) continue;
            float s = step - carry;
            while (s < d) {
                o.add(new float[]{a[0] + (b[0] - a[0]) * s / d, a[1] + (b[1] - a[1]) * s / d});
                s += step;
            }
            carry = d - (s - step);
        }
        float[] last = pts[pts.length - 1];
        if (dist(o.get(o.size() - 1), last) > step * 0.3f) o.add(last);
        else o.set(o.size() - 1, last);
        return o.toArray(new float[0][]);
    }

    static float[][] roundRect(float x0, float y0, float x1, float y1, float r) {
        List<float[]> o = new ArrayList<>();
        addArc(o, x0 + r, y0 + r, r, 180, 270);
        addArc(o, x1 - r, y0 + r, r, 270, 360);
        addArc(o, x1 - r, y1 - r, r, 0, 90);
        addArc(o, x0 + r, y1 - r, r, 90, 180);
        return o.toArray(new float[0][]);
    }

    static float[][] rotRect(float cx, float cy, float w, float h, float r, float deg) {
        float[][] base = roundRect(-w / 2, -h / 2, w / 2, h / 2, r);
        for (int i = 0; i < base.length; i++) {
            float[] q = rot(base[i][0], base[i][1], deg);
            base[i] = new float[]{cx + q[0], cy + q[1]};
        }
        return base;
    }

    static float[] rot(float x, float y, float deg) {
        double a = Math.toRadians(deg);
        return new float[]{(float) (x * Math.cos(a) - y * Math.sin(a)), (float) (x * Math.sin(a) + y * Math.cos(a))};
    }

    /** Arc de cercle (degres, sens trigonometrique si a1 > a0). */
    static void addArc(List<float[]> o, float cx, float cy, float r, float a0, float a1) {
        int n = Math.max(3, (int) Math.ceil(Math.abs(a1 - a0) / 12));
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / n);
            o.add(new float[]{cx + r * (float) Math.cos(a), cy + r * (float) Math.sin(a)});
        }
    }

    static float[][] circle(float cx, float cy, float r, float a0, float span) {
        List<float[]> o = new ArrayList<>();
        addArc(o, cx, cy, r, a0, a0 + span);
        return o.toArray(new float[0][]);
    }

    static float[][] bezier(float[] a, float[] c, float[] b, int n) {
        float[][] o = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            float t = (float) i / n, u = 1 - t;
            o[i] = new float[]{u * u * a[0] + 2 * u * t * c[0] + t * t * b[0], u * u * a[1] + 2 * u * t * c[1] + t * t * b[1]};
        }
        return o;
    }

    /** Boucle lisse (Catmull-Rom) passant par les points. */
    static float[][] smoothLoop(float[][] pts, int per) {
        int n = pts.length;
        List<float[]> o = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float[] p0 = pts[(i + n - 1) % n], p1 = pts[i], p2 = pts[(i + 1) % n], p3 = pts[(i + 2) % n];
            for (int k = 0; k < per; k++) {
                float t = (float) k / per, t2 = t * t, t3 = t2 * t;
                float[] q = new float[2];
                for (int j = 0; j < 2; j++) {
                    q[j] = 0.5f * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2 + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3);
                }
                o.add(q);
            }
        }
        return o.toArray(new float[0][]);
    }

    /** Pique (coeur renverse, courbe classique), pointe en haut. */
    static float[][] spade(float cx, float cy, float s) {
        int n = 48;
        float[][] o = new float[n][];
        for (int i = 0; i < n; i++) {
            double t = Math.PI * 2 * i / n;
            double x = 16 * Math.pow(Math.sin(t), 3);
            double y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            o[i] = new float[]{cx + (float) x * s, cy - (float) y * s};
        }
        return o;
    }

    /** Etoile a quatre branches (scintillement). */
    static float[][] star(float cx, float cy, float r) {
        float[][] o = new float[8][];
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 2 + i * Math.PI / 4;
            float rr = i % 2 == 0 ? r : r * 0.3f;
            o[i] = new float[]{cx + rr * (float) Math.cos(a), cy + rr * (float) Math.sin(a)};
        }
        return o;
    }
}
