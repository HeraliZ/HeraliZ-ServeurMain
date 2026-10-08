package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.client.render.scene.Sprite;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Coffre : lustre de recuperation. Une roue de charrette cerclee de fer pend a trois chaines ; huit ampoules
 * flamme sur des douilles de laiton en font le tour, et quatre bouteilles vertes coupees, tete en bas, servent
 * d'abat-jour a des ampoules en dessous. Lumiere chaude et stable (le groupe du Coffre est mieux entretenu).
 */
public final class LustreDesign extends DecorDesign {
    static final float CX = 8, CZ = 8, TOP = 15.8f, RIM_Y = 8.4f, RIM_R = 6.3f;
    static final int CANDLES = 8, SHADES = 4;

    private Mesh rose = Mesh.EMPTY, frame = Mesh.EMPTY, on = Mesh.EMPTY, off = Mesh.EMPTY;

    public LustreDesign() {
        super("lustre", 0xFFD890);
        itemScale = 0.85f;
        itemCenter = new float[]{8, 10, 8};
    }

    @Override
    protected void build() {
        MeshBuilder r = new MeshBuilder();
        r.push().translate(CX, TOP, CZ);
        r.cylinder(1.4f, -0.2f, 0.2f, 14, POLISHED, POLISHED, POLISHED);
        r.pop();
        rose = r.build();

        MeshBuilder b = new MeshBuilder();
        // trois chaines du plafond a la roue
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(90 + k * 120);
            float x = CX + (float) Math.cos(a) * (RIM_R - 0.3f), z = CZ + (float) Math.sin(a) * (RIM_R - 0.3f);
            b.chain(new float[][]{p(CX, TOP - 0.2f, CZ), p(x, RIM_Y + 0.4f, z)}, 0.9f, 0.55f, 0.1f, STEEL, 6, 4);
        }
        b.push().translate(CX, RIM_Y, CZ);
        // roue : bandage de fer, jante de bois, rayons, moyeu
        b.torus(RIM_R + 0.1f, 0.3f, 32, 5, 0, 360, RUST);
        b.torus(RIM_R - 0.45f, 0.42f, 32, 5, 0, 360, PALLET);
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(k * 45 + 22.5f);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            b.tube(new float[][]{p(c * 0.9f, 0, s * 0.9f), p(c * (RIM_R - 0.7f), 0, s * (RIM_R - 0.7f))}, new float[]{0.26f, 0.2f}, 6, PALLET, false, false, 0, false);
        }
        b.cylinder(1.0f, -0.6f, 0.6f, 12, RUST, RUST, RUST);
        // douilles des ampoules flamme
        for (int k = 0; k < CANDLES; k++) {
            double a = Math.toRadians(k * 360.0 / CANDLES);
            b.push().translate((float) Math.cos(a) * RIM_R, 0.3f, (float) Math.sin(a) * RIM_R);
            b.cylinder(0.3f, 0, 0.75f, 8, POLISHED, POLISHED, POLISHED);
            b.pop();
        }
        // bouteilles vertes coupees, tete en bas, pendues aux rayons
        for (int k = 0; k < SHADES; k++) {
            double a = Math.toRadians(45 + k * 90);
            float x = (float) Math.cos(a) * 3.6f, z = (float) Math.sin(a) * 3.6f;
            b.tube(new float[][]{p(x, 0, z), p(x, -1.9f, z)}, new float[]{0.06f, 0.06f}, 4, CABLE, false, false, 0, false);
            b.push().translate(x, -1.9f, z);
            b.lathe(new float[][]{{0.3f, 0}, {0.36f, -0.9f}, {0.7f, -1.5f}, {0.95f, -2.2f}, {0.95f, -4.4f}}, 12, 0, 360, BOTTLE, 1, 1, 0, 0, 0, false);
            b.lathe(new float[][]{{0.3f, -0.1f}, {0.6f, -1.5f}, {0.85f, -2.2f}, {0.85f, -4.4f}}, 12, 0, 360, SOOT, 1, 1, 0, 0, 0, true);
            b.pop();
        }
        b.pop();
        Occlusion.bake(b, 0.25f, 1.8f, 0.45f, Float.NaN);
        frame = b.build();

        MeshBuilder lo = new MeshBuilder(), lf = new MeshBuilder();
        for (MeshBuilder m : new MeshBuilder[]{lo, lf}) {
            m.push().translate(CX, RIM_Y, CZ);
            for (int k = 0; k < CANDLES; k++) {
                double a = Math.toRadians(k * 360.0 / CANDLES);
                m.push().translate((float) Math.cos(a) * RIM_R, 1.45f, (float) Math.sin(a) * RIM_R).scale(1, 1.35f, 1);
                m.sphere(0.5f, 10, 6, m == lo ? BULB_ON : BULB_OFF);
                m.pop();
            }
            for (int k = 0; k < SHADES; k++) {
                double a = Math.toRadians(45 + k * 90);
                m.push().translate((float) Math.cos(a) * 3.6f, -6.0f, (float) Math.sin(a) * 3.6f);
                m.sphere(0.62f, 10, 8, m == lo ? BULB_ON : BULB_OFF);
                m.pop();
            }
            m.pop();
        }
        on = lo.build();
        off = lf.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, rose, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        float t = s.time + s.seed * 900;
        float sway = s.item ? 0 : 0.6f * (float) Math.sin(t * 0.03f + s.seed * 3);
        p.push().translate(CX, TOP, CZ).rotX(sway).rotZ(sway * 0.7f).translate(-CX, -TOP, -CZ);
        out.solid(p, frame, 0);
        float lit = v.on ? (s.item ? 1 : 0.93f + 0.05f * (float) Math.sin(t * 0.21f) + (Ease.hash((int) t / 5 + 11) > 0.985f ? -0.3f : 0)) : 0;
        out.solid(p, lit > 0.05f ? on : off, lit > 0.05f ? lit : 0);
        if (lit > 0.05f && !s.item) {
            for (int k = 0; k < CANDLES; k++) {
                double a = Math.toRadians(k * 360.0 / CANDLES);
                out.billboard(p, Sprite.HALO, CX + (float) Math.cos(a) * RIM_R, RIM_Y + 1.5f, CZ + (float) Math.sin(a) * RIM_R, 2.0f * lit, 0, 0xFFD08A, 0.5f * lit);
            }
            for (int k = 0; k < SHADES; k++) {
                double a = Math.toRadians(45 + k * 90);
                out.billboard(p, Sprite.HALO, CX + (float) Math.cos(a) * 3.6f, RIM_Y - 6.0f, CZ + (float) Math.sin(a) * 3.6f, 2.8f * lit, 0, 0xFFE2A8, 0.6f * lit);
            }
            out.billboard(p, Sprite.HALO, CX, RIM_Y - 1.5f, CZ, 13f * lit, 0, 0xFFB868, 0.22f * lit);
        }
        p.pop();
    }
}
