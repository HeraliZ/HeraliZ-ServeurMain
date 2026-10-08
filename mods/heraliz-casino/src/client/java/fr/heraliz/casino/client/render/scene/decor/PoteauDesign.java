package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import java.util.HashMap;
import java.util.Map;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Poteau de file d'attente du bunker : un bout de tuyau rouille soude sur une jante de voiture, bande de danger,
 * anneau en haut. Une chaine pend jusqu'au poteau voisin (a l'est ou au sud, jusqu'a six blocs).
 */
public final class PoteauDesign extends DecorDesign {
    static final float RING_Y = 13.9f, RING_R = 0.72f;

    private Mesh body = Mesh.EMPTY;
    /** Chaines deja construites, par decalage vers le poteau voisin. */
    private final Map<Long, Mesh> chains = new HashMap<>();

    public PoteauDesign() {
        super("poteau_chaine", 0xD8B070);
        itemScale = 0.9f;
        itemCenter = new float[]{8, 8, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        b.push().translate(8, 0, 8);
        // ---- jante couchee : bord, voile creuse, moyeu, ecrous, trous d'aeration
        b.cylinder(2.8f, 0, 0.36f, 20, STEEL, STEEL, null);
        b.push().translate(0, 0.36f, 0);
        b.torus(2.72f, 0.2f, 20, 5, 0, 360, STEEL);
        b.pop();
        b.cylinder(2.35f, 0.3f, 0.62f, 20, RUST, null, RUST);
        b.cylinder(1.05f, 0.62f, 1.1f, 14, STEEL, null, STEEL);
        for (int k = 0; k < 5; k++) {
            double a = Math.PI * 2 * k / 5;
            b.push().translate((float) Math.cos(a) * 0.7f, 1.1f, (float) Math.sin(a) * 0.7f);
            b.cylinder(0.16f, 0, 0.22f, 6, RUST, null, RUST);
            b.pop();
            double h = a + Math.PI / 5;
            b.push().translate((float) Math.cos(h) * 1.7f, 0.63f, (float) Math.sin(h) * 1.7f);
            b.disc(0.36f, 0, 8, DARK, true);
            b.pop();
        }
        // ---- tuyau, cordon de soudure, bande de danger, bouchon
        b.cylinder(0.5f, 1.1f, 14.7f, 12, RUST, null, null);
        b.push().translate(0, 1.2f, 0);
        b.torus(0.52f, 0.12f, 12, 4, 0, 360, SOOT);
        b.pop();
        b.cylinder(0.53f, 11.4f, 12.8f, 12, HAZARD, null, null);
        b.cylinder(0.64f, 14.7f, 15.05f, 12, RUST, RUST, RUST);
        b.push().translate(0, 15.05f, 0);
        b.sphereSection(0.64f, 12, 4, 0, 90, RUST);
        b.pop();
        // anneau (piton) ou s'accrochent les chaines
        b.push().translate(0, RING_Y, 0);
        b.torus(RING_R, 0.11f, 16, 5, 0, 360, STEEL);
        b.pop();
        b.pop();
        Occlusion.bake(b, 0.3f, 2.0f, 0.45f, 0f);
        body = b.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        if (!v.on) return;
        for (float[] l : v.links) {
            long key = (Math.round(l[0] * 4) & 0xFFFFL) | ((Math.round(l[1] * 4) & 0xFFFFL) << 16) | ((Math.round(l[2] * 4) & 0xFFFFL) << 32);
            Mesh m = chains.computeIfAbsent(key, k -> chain(l[0], l[1], l[2]));
            out.solid(p, m, 0);
        }
    }

    /** Chaine qui pend entre l'anneau de ce poteau et celui du voisin (decalage en px). */
    private static Mesh chain(float dx, float dy, float dz) {
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1) return Mesh.EMPTY;
        float ux = dx / len, uz = dz / len;
        float[] a = {8 + ux * RING_R, RING_Y, 8 + uz * RING_R};
        float[] b = {8 + dx - ux * RING_R, RING_Y + dy, 8 + dz - uz * RING_R};
        float sag = 1.1f + len * 0.045f;
        int n = 18;
        float[][] path = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            float t = (float) i / n;
            path[i] = p(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t - sag * 4 * t * (1 - t), a[2] + (b[2] - a[2]) * t);
        }
        MeshBuilder mb = new MeshBuilder();
        mb.chain(path, 1.6f, 0.95f, 0.24f, RUST, 6, 4);
        return mb.build();
    }

    @Override
    public DecorView demoView(float t) {
        DecorView v = newView();
        v.links = new float[][]{{48, 0, 0}};
        return v;
    }
}
