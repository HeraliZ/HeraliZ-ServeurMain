package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.decor.DecorNames;
import fr.heraliz.casino.client.render.mesh.Mat;
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
 * Fleche de fete foraine recuperee : tole peinte en rouge sur un cadre jaune, bordee d'ampoules qui courent vers la
 * pointe (chenillard). Plusieurs ampoules manquent ou sont grillees, la tole est trouee de balles. Au centre, un
 * panneau noir : CAISSE, JEUX, SORTIE, BAR, VIP ou ENTREE. Deux modeles : vers la droite et vers la gauche.
 */
public final class FlecheDesign extends DecorDesign {
    static final float ZP = 1.3f, DEPTH = 0.9f;
    static final float LX0 = 3.1f, LX1 = 20.3f, LY0 = 5.9f, LY1 = 10.2f;
    /** Ampoules sans verre (douille vide) et grillees. */
    static final int[] MISSING = {6, 19, 33}, DEAD = {11, 26, 40};

    private final boolean left;
    private Mesh body = Mesh.EMPTY, panel = Mesh.EMPTY;
    private final Mesh[] lit = new Mesh[3], off = new Mesh[3];
    private Mesh dead = Mesh.EMPTY;
    /** Positions des ampoules (px) et classe de phase du chenillard. */
    private float[][] bulbs = new float[0][];
    private int[] phase = new int[0];
    private final Mesh[] labels = new Mesh[DecorNames.ARROW_LABELS.length];

    public FlecheDesign(boolean left) {
        super(left ? "fleche_neon_g" : "fleche_neon", 0xFFD36A);
        this.left = left;
        itemScale = 0.62f;
        itemCenter = new float[]{16, 8, 1.4f};
    }

    /** Abscisse dans le sens de la fleche (miroir pour la fleche vers la gauche). */
    private float x(float x) {
        return left ? 32 - x : x;
    }

    /** Contour convexe (sens trigonometrique apres miroir eventuel). */
    private float[][] poly(float[][] pts) {
        float[][] o = new float[pts.length][];
        for (int i = 0; i < pts.length; i++) {
            float[] q = pts[left ? pts.length - 1 - i : i];
            o[i] = new float[]{x(q[0]), q[1]};
        }
        return o;
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // ---- entretoises rouillees contre le mur
        for (float xx : new float[]{4.5f, 14f, 24f}) {
            b.bevelBox(x(xx) - 0.8f, 6.4f, 0, x(xx) + 0.8f, 9.6f, ZP - DEPTH / 2, 0.08f, RUST);
        }
        // ---- cadre jaune (tole de fond) : corps + pointe
        float[][] bodyY = {{1.0f, 4.2f}, {22.0f, 4.2f}, {22.0f, 11.8f}, {1.0f, 11.8f}};
        float[][] headY = {{21.6f, 2.6f}, {31.4f, 8.0f}, {21.6f, 13.4f}};
        b.push().translate(0, 0, ZP - 0.1f);
        b.slab(poly(bodyY), DEPTH, 0.18f, false, PAINT_YEL, RUST, PAINT_YEL);
        b.slab(poly(headY), DEPTH, 0.18f, false, PAINT_YEL, RUST, PAINT_YEL);
        b.pop();
        // ---- tole rouge devant, en retrait du bord (on voit le cadre jaune)
        float[][] bodyR = {{1.8f, 5.0f}, {22.4f, 5.0f}, {22.4f, 11.0f}, {1.8f, 11.0f}};
        float[][] headR = inset(headY, 0.8f);
        b.push().translate(0, 0, ZP + 0.22f);
        b.slab(poly(bodyR), 0.5f, 0.12f, false, PAINT_RED, RUST, PAINT_RED);
        b.slab(poly(headR), 0.5f, 0.12f, false, PAINT_RED, RUST, PAINT_RED);
        b.pop();
        float zf = ZP + 0.47f;
        // ---- panneau noir du texte, visse
        b.bevelBox(Math.min(x(LX0 - 0.5f), x(LX1 + 0.5f)), LY0 - 0.5f, zf - 0.02f, Math.max(x(LX0 - 0.5f), x(LX1 + 0.5f)), LY1 + 0.5f, zf + 0.12f, 0.08f, DARK);
        for (float xx : new float[]{LX0 - 0.1f, LX1 + 0.1f}) {
            for (float yy : new float[]{LY0 - 0.15f, LY1 + 0.15f}) screw(b, x(xx), yy, zf + 0.12f, 0.14f, RUST);
        }
        // ---- impacts de balles (tole repoussee autour du trou)
        for (float[] h : new float[][]{{25.6f, 9.6f}, {26.9f, 6.2f}, {8.2f, 10.55f}}) {
            b.push().translate(x(h[0]), h[1], zf + 0.02f);
            b.torusXY(0.28f, 0.09f, 8, 4, STEEL);
            b.push().rotateX(90);
            b.cylinder(0.22f, -0.02f, 0.01f, 8, DARK, null, DARK);
            b.pop();
            b.pop();
        }
        // ---- ampoules : douilles
        List<float[]> pts = bulbPath();
        bulbs = pts.toArray(new float[0][]);
        phase = new int[bulbs.length];
        for (int i = 0; i < bulbs.length; i++) {
            float[] q = bulbs[i];
            b.push().translate(q[0], q[1], zf).rotateX(90);
            b.cylinder(0.3f, 0, 0.36f, 8, STEEL, null, contains(MISSING, i) ? DARK : STEEL);
            b.pop();
            float along = left ? -q[0] : q[0];
            phase[i] = Math.floorMod((int) Math.floor(along / 1.6f), 3);
        }
        // cable d'alimentation qui descend au mur
        cable(b, new float[][]{p(x(12), 4.3f, ZP - 0.3f), p(x(12.4f), 3.0f, 0.8f), p(x(12.2f), 1.5f, 0.4f), p(x(12), 0.2f, 0.2f)}, 0.2f, CABLE);
        Occlusion.bake(b, 0.3f, 2.0f, 0.42f, Float.NaN);
        body = b.build();

        // ---- verre des ampoules par classe de phase (allumees / eteintes) et ampoules grillees
        MeshBuilder[] on = new MeshBuilder[3], of = new MeshBuilder[3];
        for (int c = 0; c < 3; c++) {
            on[c] = new MeshBuilder();
            of[c] = new MeshBuilder();
        }
        MeshBuilder dd = new MeshBuilder();
        for (int i = 0; i < bulbs.length; i++) {
            if (contains(MISSING, i)) continue;
            float[] q = bulbs[i];
            if (contains(DEAD, i)) {
                bulb(dd, q, BULB_OFF);
                continue;
            }
            bulb(on[phase[i]], q, BULB_ON);
            bulb(of[phase[i]], q, BULB_OFF);
        }
        for (int c = 0; c < 3; c++) {
            lit[c] = on[c].build();
            off[c] = of[c].build();
        }
        dead = dd.build();
        for (int k = 0; k < labels.length; k++) {
            labels[k] = plate(label(k), Math.min(x(LX0), x(LX1)), LY0, Math.max(x(LX0), x(LX1)), LY1, zf + 0.16f);
        }
    }

    private void bulb(MeshBuilder b, float[] q, Mat m) {
        b.push().translate(q[0], q[1], ZP + 0.47f + 0.66f);
        b.sphere(0.4f, 8, 5, m);
        b.pop();
    }

    /** Ampoules le long du bord, dans le sens de lecture (deja en miroir). */
    private List<float[]> bulbPath() {
        List<float[]> out = new ArrayList<>();
        float[][][] segs = {
                {{1.5f, 10.8f}, {1.5f, 5.2f}},
                {{2.5f, 4.6f}, {21.0f, 4.6f}},
                {{22.3f, 3.45f}, {30.2f, 7.75f}},
                {{30.2f, 8.25f}, {22.3f, 12.55f}},
                {{21.0f, 11.4f}, {2.5f, 11.4f}}};
        for (float[][] s : segs) {
            float len = (float) Math.hypot(s[1][0] - s[0][0], s[1][1] - s[0][1]);
            int n = Math.max(2, Math.round(len / 1.55f) + 1);
            for (int i = 0; i < n; i++) {
                float t = (float) i / (n - 1);
                out.add(new float[]{x(s[0][0] + (s[1][0] - s[0][0]) * t), s[0][1] + (s[1][1] - s[0][1]) * t});
            }
        }
        return out;
    }

    /** Contour convexe decale vers l'interieur de d (intersection des cotes decales). */
    static float[][] inset(float[][] pts, float d) {
        int n = pts.length;
        float[][] out = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] a0 = pts[(i + n - 1) % n], a1 = pts[i], b1 = pts[(i + 1) % n];
            float[] l1 = offsetLine(a0, a1, d), l2 = offsetLine(a1, b1, d);
            out[i] = intersect(l1, l2);
        }
        return out;
    }

    private static float[] offsetLine(float[] a, float[] b, float d) {
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float l = (float) Math.hypot(dx, dy);
        float nx = -dy / l, ny = dx / l;
        return new float[]{a[0] + nx * d, a[1] + ny * d, dx, dy};
    }

    private static float[] intersect(float[] l1, float[] l2) {
        float det = l1[2] * l2[3] - l1[3] * l2[2];
        if (Math.abs(det) < 1e-6f) return new float[]{l2[0], l2[1]};
        float t = ((l2[0] - l1[0]) * l2[3] - (l2[1] - l1[1]) * l2[2]) / det;
        return new float[]{l1[0] + l1[2] * t, l1[1] + l1[3] * t};
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
        out.solid(p, dead, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        float t = s.time + s.seed * 300;
        int step = (int) (t / 3.5f);
        // toutes les dix secondes : toutes les ampoules clignotent ensemble deux fois
        float c = t % 200;
        boolean flash = c < 16;
        boolean all = flash && ((int) (c / 4)) % 2 == 0;
        // coupure de courant du groupe electrogene de temps en temps
        boolean brown = !s.item && Ease.hash((int) (t / 5) * 7 + 11) > 0.97f;
        float k = brown ? 0.35f : 1f;
        for (int g = 0; g < 3; g++) {
            boolean on = v.on && (flash ? all : Math.floorMod(g - step, 3) != 0);
            out.solid(p, on ? lit[g] : off[g], on ? 0.95f * k : 0);
            if (on && !s.item) {
                for (int i = 0; i < bulbs.length; i++) {
                    if (phase[i] != g || contains(MISSING, i) || contains(DEAD, i)) continue;
                    out.billboard(p, Sprite.HALO, bulbs[i][0], bulbs[i][1], ZP + 1.2f, 0.95f, 0, 0xFFC860, 0.32f * k);
                }
            }
        }
        // texte, eclaire par les ampoules
        out.sheet(SCREENS);
        int lab = DecorNames.arrowLabel(v.mode);
        out.solid(p, labels[lab], v.on ? 0.55f * k : 0);
        out.sheet(null);
        if (v.on && !s.item) {
            p.push().translate(0, 0, ZP + 0.8f);
            out.quad(p, Sprite.HALO, Math.min(x(0.5f), x(31)), 1.5f, Math.max(x(0.5f), x(31)), 14.5f, 0xFFB050, 0.09f * k);
            p.pop();
        }
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (!v.on) return;
        // une ampoule grille qui gresille
        if (r.nextInt(500) == 0 && bulbs.length > DEAD[0]) {
            float[] q = bulbs[DEAD[r.nextInt(DEAD.length)]];
            fx.particle(FxSink.SPARK, q[0], q[1], ZP + 1.3f, rnd(r, 0.04f), 0.05f, 0.05f, 0xFFE0A0, 0.3f, 8);
        }
    }

    @Override
    public DecorView demoView(float t) {
        DecorView v = newView();
        v.mode = ((int) (t / 50) % DecorNames.ARROW_LABELS.length) * 2 + (left ? 1 : 0);
        return v;
    }
}
