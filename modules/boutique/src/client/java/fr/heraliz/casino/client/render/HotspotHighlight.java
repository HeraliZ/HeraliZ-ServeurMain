package fr.heraliz.casino.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.casino.client.render.scene.Hotspots;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

/**
 * Zone survolee a la souris (case du tapis, dalle, carte...) : cadre lumineux dessine sur la machine.
 */
public final class HotspotHighlight {
    private HotspotHighlight() {
    }

    private static BlockPos pos;
    private static final Hotspots.Spot SPOT = new Hotspots.Spot();
    private static int color = 0xFFD36A;

    public static void set(BlockPos machine, Hotspots.Spot spot, int rgb) {
        pos = machine;
        SPOT.copyFrom(spot);
        color = rgb;
    }

    public static void clear() {
        pos = null;
    }

    public static boolean isOn(BlockPos machine) {
        return pos != null && pos.equals(machine);
    }

    /** Dessine le cadre (repere du modele -> pose du bloc via "model"). */
    static void render(PoseStack pose, MultiBufferSource buf, Matrix4f model, float time, float camX, float camY, float camZ) {
        Hotspots.Spot s = SPOT;
        Matrix4f m = new Matrix4f(pose.last().pose()).mul(model);
        VertexConsumer vc = buf.getBuffer(CasinoRenderTypes.highlight());
        float r = ((color >> 16) & 255) / 255f, g = ((color >> 8) & 255) / 255f, b = (color & 255) / 255f;
        // decalage le long de la normale pour ne pas se melanger a la surface
        float nx = s.uy * s.vz - s.uz * s.vy, ny = s.uz * s.vx - s.ux * s.vz, nz = s.ux * s.vy - s.uy * s.vx;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-6f) return;
        // decale du cote de la camera (la normale u x v peut pointer dans les deux sens)
        float side = nx * (camX - s.ox) + ny * (camY - s.oy) + nz * (camZ - s.oz) >= 0 ? 1 : -1;
        float off = side * 0.06f / nl;
        float ox = s.ox + nx * off, oy = s.oy + ny * off, oz = s.oz + nz * off;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 0.35f);
        // remplissage doux
        quad(vc, m, ox, oy, oz, s.ux, s.uy, s.uz, s.vx, s.vy, s.vz, 0, 0, 1, 1, r, g, b, 0.16f + 0.1f * pulse);
        // bordure d'epaisseur constante (0.22 px)
        float ul = (float) Math.sqrt(s.ux * s.ux + s.uy * s.uy + s.uz * s.uz), vl = (float) Math.sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz);
        float bu = Math.min(0.5f, 0.22f / Math.max(ul, 1e-3f)), bv = Math.min(0.5f, 0.22f / Math.max(vl, 1e-3f));
        float a = 0.75f + 0.25f * pulse;
        quad(vc, m, ox, oy, oz, s.ux, s.uy, s.uz, s.vx, s.vy, s.vz, 0, 0, 1, bv, r, g, b, a);
        quad(vc, m, ox, oy, oz, s.ux, s.uy, s.uz, s.vx, s.vy, s.vz, 0, 1 - bv, 1, 1, r, g, b, a);
        quad(vc, m, ox, oy, oz, s.ux, s.uy, s.uz, s.vx, s.vy, s.vz, 0, bv, bu, 1 - bv, r, g, b, a);
        quad(vc, m, ox, oy, oz, s.ux, s.uy, s.uz, s.vx, s.vy, s.vz, 1 - bu, bv, 1, 1 - bv, r, g, b, a);
    }

    /** Sous-rectangle [s0, s1] x [t0, t1] du parallelogramme. */
    private static void quad(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz, float ux, float uy, float uz, float vx, float vy, float vz,
                             float s0, float t0, float s1, float t1, float r, float g, float b, float a) {
        vert(vc, m, ox + ux * s0 + vx * t0, oy + uy * s0 + vy * t0, oz + uz * s0 + vz * t0, r, g, b, a);
        vert(vc, m, ox + ux * s1 + vx * t0, oy + uy * s1 + vy * t0, oz + uz * s1 + vz * t0, r, g, b, a);
        vert(vc, m, ox + ux * s1 + vx * t1, oy + uy * s1 + vy * t1, oz + uz * s1 + vz * t1, r, g, b, a);
        vert(vc, m, ox + ux * s0 + vx * t1, oy + uy * s0 + vy * t1, oz + uz * s0 + vz * t1, r, g, b, a);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).endVertex();
    }
}
