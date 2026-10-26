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
 * Le Coffre : potelet de file d'attente recupere dans un vieux cinema, laiton astique sur un pied de fonte. Un cordon
 * de velours rouge pend jusqu'au potelet voisin (a l'est ou au sud, jusqu'a six blocs), agrafe de laiton a chaque bout.
 */
public final class PoteletDesign extends DecorDesign {
    static final float HOOK_Y = 12.9f, HOOK_R = 0.75f;

    private Mesh body = Mesh.EMPTY;
    private final Map<Long, Mesh> ropes = new HashMap<>();

    public PoteletDesign() {
        super("potelet_velours", 0xC83A3A);
        itemScale = 0.9f;
        itemCenter = new float[]{8, 8, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        b.push().translate(8, 0, 8);
        // pied de fonte bombe, bague de laiton
        b.lathe(new float[][]{{2.7f, 0}, {2.7f, 0.35f}, {2.2f, 0.75f}, {1.1f, 1.1f}, {0.6f, 1.35f}}, 22, 0, 360, DARK, 1, 1, 0, 0, 0, false);
        b.disc(2.7f, 0, 22, DARK, false);
        b.push().translate(0, 0.38f, 0);
        b.torus(2.62f, 0.12f, 22, 4, 0, 360, POLISHED);
        b.pop();
        // fut de laiton, bagues, boule sommitale
        b.cylinder(0.44f, 1.3f, 13.8f, 14, POLISHED, null, null);
        for (float y : new float[]{1.6f, 7.4f, 12.2f}) {
            b.push().translate(0, y, 0);
            b.torus(0.5f, 0.12f, 14, 4, 0, 360, POLISHED);
            b.pop();
        }
        b.lathe(new float[][]{{0.44f, 13.8f}, {0.7f, 14.3f}, {0.62f, 14.6f}}, 14, 0, 360, POLISHED, 1, 1, 0, 0, 0, false);
        b.push().translate(0, 15.3f, 0);
        b.sphere(0.82f, 14, 10, POLISHED);
        b.pop();
        // piton ou s'agrafent les cordons
        b.push().translate(0, HOOK_Y, 0);
        b.torus(HOOK_R, 0.1f, 14, 4, 0, 360, POLISHED);
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
            Mesh m = ropes.computeIfAbsent(key, k -> rope(l[0], l[1], l[2]));
            out.solid(p, m, 0);
        }
    }

    /** Cordon de velours qui pend entre ce potelet et le voisin (decalage en px), agrafes de laiton aux deux bouts. */
    private static Mesh rope(float dx, float dy, float dz) {
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1) return Mesh.EMPTY;
        float ux = dx / len, uz = dz / len;
        float[] a = {8 + ux * (HOOK_R + 0.5f), HOOK_Y - 0.2f, 8 + uz * (HOOK_R + 0.5f)};
        float[] b = {8 + dx - ux * (HOOK_R + 0.5f), HOOK_Y - 0.2f + dy, 8 + dz - uz * (HOOK_R + 0.5f)};
        float sag = 2.0f + len * 0.06f;
        int n = 22;
        float[][] path = new float[n + 1][];
        float[] radii = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float t = (float) i / n;
            path[i] = p(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t - sag * 4 * t * (1 - t), a[2] + (b[2] - a[2]) * t);
            radii[i] = 0.42f;
        }
        MeshBuilder mb = new MeshBuilder();
        mb.tube(path, radii, 8, VELVET, true, true, 0, false);
        for (float[] end : new float[][]{a, b}) {
            mb.push().translate(end[0], end[1], end[2]);
            mb.sphere(0.5f, 8, 6, POLISHED);
            mb.pop();
        }
        // agrafes : petit crochet vers le piton
        mb.tube(new float[][]{p(a[0], a[1], a[2]), p(8 + ux * HOOK_R, HOOK_Y, 8 + uz * HOOK_R)}, new float[]{0.12f, 0.12f}, 5, POLISHED, true, true, 0, false);
        mb.tube(new float[][]{p(b[0], b[1], b[2]), p(8 + dx - ux * HOOK_R, HOOK_Y + dy, 8 + dz - uz * HOOK_R)}, new float[]{0.12f, 0.12f}, 5, POLISHED, true, true, 0, false);
        return mb.build();
    }

    @Override
    public DecorView demoView(float t) {
        DecorView v = newView();
        v.links = new float[][]{{48, 0, 0}};
        return v;
    }
}
