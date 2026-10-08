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

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Baril-brasero : fut de 200 L decoupe au burin, perce de trous, noirci par la suie, une grille de ferraille dessus.
 * Du bois brule dedans ; les trous rougeoient, les flammes depassent, braises et fumee montent.
 */
public final class BarilDesign extends DecorDesign {
    static final float R = 4.6f, TOP = 14.1f, CX = 8, CZ = 8;
    /** Trous : deux rangees decalees. */
    static final int HOLES = 9;

    private Mesh body = Mesh.EMPTY, holesHot = Mesh.EMPTY, embers = Mesh.EMPTY, logsHot = Mesh.EMPTY;

    public BarilDesign() {
        super("baril_feu", 0xFF8A2A);
        itemScale = 0.98f;
        itemCenter = new float[]{8, 8, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        b.push().translate(CX, 0, CZ);
        // ---- fut : peau exterieure, interieur noirci, cerclages
        b.cylinder(R, 0.3f, TOP, 22, DRUM, RUST, null);
        b.lathe(new float[][]{{R - 0.14f, 1.0f}, {R - 0.14f, TOP}}, 22, 0, 360, SOOT, 1, 1, 0, 0, 0, true);
        for (float y : new float[]{0.55f, 4.9f, 9.6f}) {
            b.push().translate(0, y, 0);
            b.torus(R + 0.03f, 0.17f, 22, 4, 0, 360, RUST);
            b.pop();
        }
        b.push().translate(0, TOP, 0);
        b.torus(R - 0.07f, 0.12f, 22, 4, 0, 360, SOOT);
        b.pop();
        // bord decoupe au burin : languettes de tole tordues vers l'exterieur
        for (int k = 0; k < 7; k++) {
            float a = k * 51 + 13;
            b.push().rotateY(a).translate(0, TOP - 0.05f, R - 0.05f).rotateX(-25 - (k % 3) * 10);
            b.bevelBox(-0.55f - (k % 2) * 0.2f, 0, -0.06f, 0.55f, 0.8f + (k % 3) * 0.25f, 0.06f, 0.02f, RUST);
            b.pop();
        }
        // trous perces (sombres quand le feu est eteint)
        MeshBuilder hot = new MeshBuilder();
        for (int row = 0; row < 2; row++) {
            for (int k = 0; k < HOLES; k++) {
                float a = k * 360f / HOLES + row * 20 + 7;
                float y = row == 0 ? 2.8f : 7.1f;
                b.push().rotateY(a).translate(0, y, R + 0.02f).rotateX(90);
                b.cylinder(0.42f, 0, 0.02f, 8, DARK, null, DARK);
                b.pop();
                hot.push().rotateY(a).translate(0, y, R + 0.08f).rotateX(90);
                hot.cylinder(0.4f, 0, 0.02f, 8, HOT, null, HOT);
                hot.pop();
            }
        }
        // ---- grille de ferraille posee dessus (trois fers a beton)
        for (float z : new float[]{-2.3f, 0, 2.3f}) {
            b.push().translate(-5.4f, TOP + 0.2f, z).rotateZ(-90);
            b.cylinder(0.16f, 0, 10.8f, 6, RUST, RUST, RUST);
            b.pop();
        }
        // ---- bouts de bois carbonises qui depassent
        MeshBuilder logHot = new MeshBuilder();
        float[][] logs = {{-1.2f, 11.4f, 0.6f, 38, 20}, {1.6f, 11.8f, -1.0f, -30, 140}, {0.2f, 12.2f, 1.9f, 25, 260}};
        for (float[] l : logs) {
            b.push().translate(l[0], l[1], l[2]).rotateY(l[4]).rotateZ(l[3]);
            b.cylinder(0.55f, -1.6f, 2.6f, 7, SOOT, SOOT, SOOT);
            b.pop();
            logHot.push().translate(l[0], l[1], l[2]).rotateY(l[4]).rotateZ(l[3]);
            logHot.cylinder(0.56f, 1.9f, 2.62f, 7, HOT, null, HOT);
            logHot.pop();
        }
        b.pop();
        Occlusion.bake(b, 0.35f, 2.4f, 0.5f, 0f);
        body = b.build();
        holesHot = translate(hot.build());
        logsHot = translate(logHot.build());
        MeshBuilder em = new MeshBuilder();
        em.push().translate(CX, 11.0f, CZ);
        em.disc(R - 0.3f, 0, 16, HOT, true);
        em.pop();
        embers = em.build();
    }

    /** Les maillages "chauds" sont construits autour de l'axe (0,0) : on les remet au centre du bloc. */
    private static Mesh translate(Mesh m) {
        float[] d = m.data().clone();
        for (int i = 0; i < m.vertexCount(); i++) {
            d[i * Mesh.STRIDE] += CX;
            d[i * Mesh.STRIDE + 2] += CZ;
        }
        return Mesh.of(d, true);
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        if (!v.on) {
            out.solid(p, embers, 0.05f);
            return;
        }
        float t = s.time + s.seed * 377;
        // le feu respire : plusieurs frequences melangees
        float f = 0.78f + 0.12f * (float) Math.sin(t * 0.37f) + 0.07f * (float) Math.sin(t * 1.3f + 1.1f) + 0.05f * (float) Math.sin(t * 3.7f);
        out.solid(p, embers, 0.9f * f);
        out.solid(p, holesHot, 0.75f * f);
        out.solid(p, logsHot, 0.85f * f);
        if (s.item) return;
        // flammes : sprites face camera qui ondulent
        for (int k = 0; k < 6; k++) {
            float ph = k * 1.7f + s.seed * 6;
            float life = (t * 0.09f + k * 0.37f) % 1f;
            float x = CX + (float) Math.sin(ph + t * 0.05f) * 1.6f * (1 - life);
            float z = CZ + (float) Math.cos(ph * 1.3f + t * 0.04f) * 1.6f * (1 - life);
            float y = TOP - 1.2f + life * 4.5f;
            float size = (2.6f + Ease.hash(k * 7) * 1.2f) * (1 - life * 0.55f);
            float a = Ease.bell(life, -0.05f, 1.0f) * 0.85f;
            int col = life < 0.35f ? 0xFFD070 : 0xFF7A20;
            out.billboard(p, Sprite.FLAME, x, y, z, size, (float) Math.sin(ph + t * 0.1f) * 12, col, a);
        }
        // lueur du foyer
        out.billboard(p, Sprite.HALO, CX, TOP + 1.5f, CZ, 7.5f * f, 0, 0xFF6A18, 0.32f * f);
        out.ground(p, Sprite.HALO, CX, TOP - 0.4f, CZ, R * 0.95f, 0, 0xFF8A30, 0.6f * f);
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (!v.on) {
            if (r.nextInt(12) == 0) fx.particle(FxSink.SMOKE, CX + rnd(r, 1.5f), TOP, CZ + rnd(r, 1.5f), rnd(r, 0.02f), 0.04f, rnd(r, 0.02f), 0x5A5550, 2.4f, 60);
            return;
        }
        if (r.nextInt(2) == 0) {
            fx.particle(FxSink.FLAME, CX + rnd(r, 2.2f), TOP + 0.2f, CZ + rnd(r, 2.2f), rnd(r, 0.02f), 0.09f + r.nextFloat() * 0.05f, rnd(r, 0.02f),
                    r.nextBoolean() ? 0xFFB040 : 0xFF7020, 1.3f + r.nextFloat(), 16 + r.nextInt(8));
        }
        if (r.nextInt(4) == 0) {
            fx.particle(FxSink.EMBER, CX + rnd(r, 2.5f), TOP + 0.5f, CZ + rnd(r, 2.5f), rnd(r, 0.08f), 0.18f + r.nextFloat() * 0.15f, rnd(r, 0.08f),
                    0xFFA030, 0.35f, 30 + r.nextInt(30));
        }
        if (r.nextInt(5) == 0) {
            fx.particle(FxSink.SMOKE, CX + rnd(r, 1.8f), TOP + 3.5f, CZ + rnd(r, 1.8f), rnd(r, 0.03f), 0.06f, rnd(r, 0.03f), 0x4A4642, 3.2f, 70);
        }
        if (r.nextInt(30) == 0) fx.sound(Snd.FIRE, CX, TOP, CZ, 0.6f + r.nextFloat() * 0.3f, 0.8f + r.nextFloat() * 0.4f);
    }
}
