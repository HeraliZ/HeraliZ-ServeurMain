package fr.heraliz.casino.client.render.scene.decor;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;
import static fr.heraliz.casino.decor.DecorGeom.*;

/**
 * Le Coffre : porte ronde de la salle des coffres d'une banque, dans son dormant d'acier (trois blocs de large et de
 * haut). Elle s'ouvre et se ferme pour de bon : le volant tourne, les pennes rentrent, puis le lourd vantail pivote
 * sur son gond et se rabat contre le mur ; a la fermeture, il revient, les pennes ressortent et le volant se bloque.
 * Acier peint vert bouteille ecaille, anneaux de laiton, cadran a chiffres ; au dos du vantail, la tringlerie des
 * pennes. Pose contre un mur : on casse le mur derriere pour en faire un passage.
 */
public final class CoffreDesign extends DecorDesign {
    static final float CX = VAULT_CX, CY = VAULT_CY, R = VAULT_R, HOLE = VAULT_HOLE, FZ = VAULT_FRAME_Z;
    /** Vantail : bouchon (dans le rond) de PZ0 a FZ, puis le disque avant jusqu'a Z1. */
    static final float PZ0 = -1.6f, PR = 21.0f, Z1 = FZ + 4.4f;
    static final int BOLTS = 12;
    /** Course des pennes (px) : rentrees quand la porte est ouverte. */
    static final float BOLT_TRAVEL = 2.8f;
    /** Volant : profondeur du croisillon. */
    static final float WZ = Z1 + 2.6f;

    private Mesh frame = Mesh.EMPTY, leaf = Mesh.EMPTY, wheel = Mesh.EMPTY, bolt = Mesh.EMPTY;

    public CoffreDesign() {
        super("porte_coffre", 0xD8B070);
        itemScale = 0.3f;
        itemCenter = new float[]{8, VAULT_CY, 3};
    }

    @Override
    protected void build() {
        // ---------------------------------------------------------------- dormant (fixe)
        MeshBuilder f = new MeshBuilder();
        plateWithHole(f, -16, 0, 32, VAULT_H, CX, CY, HOLE, -1.0f, FZ, 56, VAULT, STEEL);
        f.push().translate(CX, CY, FZ);
        f.torusXY(HOLE + 0.7f, 0.5f, 56, 6, POLISHED);
        f.pop();
        // rivets sur les montants (au-dessus et au-dessous du rond, le dormant est trop etroit)
        for (float x : new float[]{-14.6f, 30.6f}) {
            for (int k = 0; k < 7; k++) {
                f.push().translate(x, 6 + k * 6, FZ).rotateX(90);
                f.rivet(0.36f, 0.22f, STEEL);
                f.pop();
            }
        }
        for (float[] c : new float[][]{{-13.2f, 3}, {29.2f, 3}, {-13.2f, VAULT_H - 3}, {29.2f, VAULT_H - 3}}) {
            f.push().translate(c[0], c[1], FZ).rotateX(90);
            f.cylinder(1.1f, 0, 0.5f, 6, STEEL, null, STEEL);
            f.pop();
        }
        // ferrures du gond sur le montant gauche, noeuds fixes et axe
        f.bevelBox(-16, 3, FZ, -11.6f, VAULT_H - 3, FZ + 1.8f, 0.3f, STEEL);
        for (float y : new float[]{6.5f, 20.5f, 34.5f}) {
            f.bevelBox(-16, y, FZ + 1.6f, -14.2f, y + 7, VAULT_HINGE_Z + 0.6f, 0.2f, STEEL);
            f.push().translate(VAULT_HINGE_X, y, VAULT_HINGE_Z);
            f.cylinder(1.55f, 0, 7, 14, POLISHED, POLISHED, POLISHED);
            f.pop();
        }
        f.push().translate(VAULT_HINGE_X, 5.6f, VAULT_HINGE_Z);
        f.cylinder(0.5f, 0, 37.4f, 8, STEEL, STEEL, STEEL);
        f.pop();
        // plaque du casino dans le coin du linteau, a cote du rond
        f.push().translate(24.5f, VAULT_H - 3.4f, FZ + 0.02f);
        f.decalZ(STICKER, 0, 0, 0, 7.0f, 1.75f);
        f.pop();
        Occlusion.bake(f, 0.4f, 3.0f, 0.5f, 0f);
        frame = f.build();

        // ---------------------------------------------------------------- vantail (pivote sur le gond)
        MeshBuilder b = new MeshBuilder();
        // bouchon qui entre dans le rond, puis le disque avant
        b.push().translate(CX, CY, PZ0).rotateX(90);
        b.cylinder(PR, 0, FZ - PZ0, 48, STEEL, null, null);
        b.pop();
        b.push().translate(CX, CY, FZ).rotateX(90);
        b.cylinder(R, 0, Z1 - FZ, 56, VAULT, VAULT, VAULT);
        b.pop();
        // dos du vantail : plaque d'acier, tringlerie vers chaque penne, boitier central
        b.push().translate(CX, CY, PZ0);
        b.push().rotateX(-90);
        b.disc(PR, 0, 48, STEEL, true);
        b.pop();
        b.bevelBox(-4, -4, -1.6f, 4, 4, 0, 0.3f, DARK);
        for (int k = 0; k < BOLTS; k++) {
            float a = k * 360f / BOLTS + 15;
            b.push().rotateZ(a);
            b.bevelBox(3.6f, -0.45f, -0.9f, PR - 2.2f, 0.45f, 0, 0.12f, POLISHED);
            b.pop();
        }
        b.pop();
        // bourrelet de laiton, anneaux, rivets
        b.push().translate(CX, CY, Z1);
        b.torusXY(R - 0.6f, 0.45f, 56, 6, POLISHED);
        b.torusXY(R - 4.2f, 0.3f, 48, 5, STEEL);
        b.torusXY(10.5f, 0.35f, 36, 5, POLISHED);
        b.pop();
        for (int k = 0; k < 24; k++) {
            double a = Math.PI * 2 * k / 24;
            b.push().translate(CX + (float) Math.cos(a) * (R - 2.3f), CY + (float) Math.sin(a) * (R - 2.3f), Z1).rotateX(90);
            b.rivet(0.35f, 0.2f, STEEL);
            b.pop();
        }
        // moyeu du volant, cadran a chiffres
        b.push().translate(CX, CY, Z1).rotateX(90);
        b.cylinder(2.3f, 0, 1.2f, 18, POLISHED, null, POLISHED);
        b.cylinder(0.6f, 1.2f, 2.6f, 10, STEEL, null, STEEL);
        b.pop();
        b.push().translate(CX, CY + 10.2f, Z1).rotateX(90);
        b.cylinder(2.6f, 0, 0.6f, 24, POLISHED, null, DIAL);
        b.cylinder(0.7f, 0.6f, 1.2f, 12, DARK, null, DARK);
        b.pop();
        b.bevelBox(CX - 0.2f, CY + 12.9f, Z1, CX + 0.2f, CY + 13.6f, Z1 + 0.5f, 0.05f, POLISHED);
        // bras du gond et noeuds mobiles
        b.bevelBox(VAULT_HINGE_X, CY - 10.4f, FZ + 2.2f, CX - R + 3.6f, CY + 10.4f, VAULT_HINGE_Z + 1.4f, 0.4f, STEEL);
        for (float y : new float[]{CY - 10.4f, CY + 3.6f}) {
            b.push().translate(VAULT_HINGE_X, y, VAULT_HINGE_Z);
            b.cylinder(1.55f, 0, 6.8f, 14, POLISHED, POLISHED, POLISHED);
            b.pop();
        }
        // autocollant du casino
        b.push().translate(CX, CY - 13.5f, Z1 + 0.01f);
        b.decalZ(STICKER, 0, 0, 0, 9.0f, 2.25f);
        b.pop();
        Occlusion.bake(b, 0.4f, 3.0f, 0.5f, Float.NaN, frame);
        leaf = b.build();

        // ---------------------------------------------------------------- volant a quatre branches
        MeshBuilder w = new MeshBuilder();
        w.push().translate(CX, CY, WZ);
        w.torusXY(5.4f, 0.42f, 32, 6, POLISHED);
        w.pop();
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            w.tube(new float[][]{p(CX + c * 0.5f, CY + s * 0.5f, WZ), p(CX + c * 5.4f, CY + s * 5.4f, WZ)}, new float[]{0.32f, 0.28f}, 8, POLISHED, false, false, 0, false);
            w.push().translate(CX + c * 6.6f, CY + s * 6.6f, WZ).rotateZ((float) Math.toDegrees(a) - 90);
            w.cylinder(0.32f, 0, 1.3f, 8, POLISHED, null, null);
            w.push().translate(0, 1.5f, 0);
            w.sphere(0.55f, 10, 8, POLISHED);
            w.pop();
            w.pop();
        }
        wheel = w.build();

        // ---------------------------------------------------------------- une penne (rentree), le long de +X
        MeshBuilder pb = new MeshBuilder();
        pb.push().translate(PR - 2.4f, 0, 0.4f).rotateZ(-90);
        pb.cylinder(0.85f, 0, 2.8f, 12, STEEL, STEEL, POLISHED);
        pb.pop();
        bolt = pb.build();
    }

    /**
     * Plaque rectangulaire percee d'un rond (dormant) : face avant en z1, paroi du rond de z0 a z1, chants exterieurs.
     * Les angles du rectangle sont des sommets : chaque quadrilatere reste plan.
     */
    static void plateWithHole(MeshBuilder b, float x0, float y0, float x1, float y1, float cx, float cy, float r, float z0, float z1, int segs,
                              Mat front, Mat lining) {
        List<Double> angles = new ArrayList<>();
        for (int i = 0; i < segs; i++) angles.add(Math.PI * 2 * i / segs);
        for (float[] c : new float[][]{{x1, y1}, {x0, y1}, {x0, y0}, {x1, y0}}) {
            double a = Math.atan2(c[1] - cy, c[0] - cx);
            angles.add(a < 0 ? a + Math.PI * 2 : a);
        }
        angles.sort(Double::compare);
        int n = angles.size();
        for (int i = 0; i < n; i++) {
            double a0 = angles.get(i), a1 = i + 1 < n ? angles.get(i + 1) : angles.get(0) + Math.PI * 2;
            if (a1 - a0 < 1e-5) continue;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[] q0 = toRect(cx, cy, c0, s0, x0, y0, x1, y1), q1 = toRect(cx, cy, c1, s1, x0, y0, x1, y1);
            b.face(front, v(0, 0, 1), p(cx + r * c0, cy + r * s0, z1), p(q0[0], q0[1], z1), p(q1[0], q1[1], z1), p(cx + r * c1, cy + r * s1, z1));
            float[] nIn = {-(c0 + c1) / 2, -(s0 + s1) / 2, 0};
            b.face(lining, nIn, p(cx + r * c0, cy + r * s0, z0), p(cx + r * c1, cy + r * s1, z0), p(cx + r * c1, cy + r * s1, z1), p(cx + r * c0, cy + r * s0, z1));
        }
        b.face(front, v(-1, 0, 0), p(x0, y0, 0), p(x0, y1, 0), p(x0, y1, z1), p(x0, y0, z1));
        b.face(front, v(1, 0, 0), p(x1, y0, 0), p(x1, y0, z1), p(x1, y1, z1), p(x1, y1, 0));
        b.face(front, v(0, 1, 0), p(x0, y1, 0), p(x1, y1, 0), p(x1, y1, z1), p(x0, y1, z1));
        b.face(front, v(0, -1, 0), p(x0, y0, 0), p(x0, y0, z1), p(x1, y0, z1), p(x1, y0, 0));
    }

    /** Point ou la demi-droite partant de (cx, cy) dans la direction (c, s) sort du rectangle. */
    private static float[] toRect(float cx, float cy, float c, float s, float x0, float y0, float x1, float y1) {
        float t = Float.MAX_VALUE;
        if (c > 1e-6f) t = Math.min(t, (x1 - cx) / c);
        if (c < -1e-6f) t = Math.min(t, (x0 - cx) / c);
        if (s > 1e-6f) t = Math.min(t, (y1 - cy) / s);
        if (s < -1e-6f) t = Math.min(t, (y0 - cy) / s);
        return new float[]{cx + c * t, cy + s * t};
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, frame, 0);
    }

    /** Etat de la porte a un instant : ouverture du vantail (0..1), volant (degres), pennes (0 rentrees, 1 sorties). */
    static float[] state(DecorView v, boolean item) {
        if (item) return new float[]{0, 0, 1};
        float t = v.since;
        if (v.on) {
            // ouverture : le volant tourne, les pennes rentrent, puis le vantail pivote
            float wheel = -540 * Ease.inOutSine(Ease.range(t, 0, 16));
            float bolts = 1 - Ease.inOutSine(Ease.range(t, 7, 19));
            float swing = Ease.inOutSine(Ease.range(t, VAULT_UNLOCK, VAULT_UNLOCK + VAULT_SWING));
            return new float[]{swing, wheel, bolts};
        }
        // fermeture : le vantail revient (choc a la fin), les pennes sortent, le volant se bloque
        float swing = 1 - Ease.inOutSine(Ease.range(t, 0, VAULT_SWING));
        float bolts = Ease.inOutSine(Ease.range(t, VAULT_SWING + 2, VAULT_SWING + 12));
        float wheel = -540 * (1 - Ease.inOutSine(Ease.range(t, VAULT_SWING + 4, VAULT_SWING + 20)));
        if (t > 1e5f) wheel = 0;
        return new float[]{swing, wheel, bolts};
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        float[] st = state(v, s.item);
        float angle = -VAULT_OPEN_DEG * st[0];
        p.push().translate(VAULT_HINGE_X, 0, VAULT_HINGE_Z).rotY(angle).translate(-VAULT_HINGE_X, 0, -VAULT_HINGE_Z);
        out.solid(p, leaf, 0);
        p.push().translate(CX, CY, 0).rotZ(st[1]).translate(-CX, -CY, 0);
        out.solid(p, wheel, 0);
        p.pop();
        for (int k = 0; k < BOLTS; k++) {
            float a = k * 360f / BOLTS + 15;
            p.push().translate(CX, CY, 0).rotZ(a).translate(BOLT_TRAVEL * st[2], 0, 0);
            out.solid(p, bolt, 0);
            p.pop();
        }
        p.pop();
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (v.since > 200) return;
        int t = (int) Math.floor(v.since);
        float hx = VAULT_HINGE_X, hz = VAULT_HINGE_Z;
        if (v.on) {
            if (t == 1) fx.sound(Snd.SAFE, CX, CY, Z1 + 3, 0.9f, 0.8f);
            if (t == 4 || t == 9) fx.sound(Snd.WHEEL_TICK, CX, CY, Z1 + 3, 0.6f, 0.6f);
            if (t == 13) fx.sound(Snd.UNLOCK, CX, CY, Z1, 0.9f, 0.55f);
            if (t == (int) VAULT_UNLOCK + 2) fx.sound(Snd.UNLOCK, hx, CY, hz, 0.7f, 0.4f);
        } else {
            if (t == (int) VAULT_SWING - 1) {
                fx.sound(Snd.SMASH, CX, CY, Z1, 0.7f, 0.5f);
                for (int k = 0; k < 6; k++) {
                    fx.particle(FxSink.SMOKE, CX + rnd(r, 18), 1 + r.nextFloat() * 4, Z1 + 2, rnd(r, 0.05f), 0.02f, 0.06f, 0x8A847A, 1.2f, 30);
                }
            }
            if (t == (int) VAULT_SWING + 6) fx.sound(Snd.SAFE, CX, CY, Z1 + 3, 0.9f, 0.7f);
        }
    }

    @Override
    public DecorView demoView(float t) {
        // demonstration : ouverture, puis fermeture
        DecorView v = newView();
        v.on = t < 100;
        v.since = v.on ? t : t - 100;
        return v;
    }
}
