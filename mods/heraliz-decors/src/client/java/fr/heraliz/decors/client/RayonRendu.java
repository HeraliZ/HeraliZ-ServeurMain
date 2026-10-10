package fr.heraliz.decors.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.decors.HeralizDecors;
import fr.heraliz.decors.effet.RayonBE;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Gros rayon de soleil doré : 12 lames douces disposées en étoile autour de l'axe, qui s'évasent en descendant (leurs
 * couleurs s'ajoutent : le rayon paraît plein au centre et se fond sur les bords), une très lente respiration ; il suit
 * le jour (plein midi, rien la nuit). 96 faces par rayon, aucune lumière calculée (les vraies sources de lumière sont
 * posées au sol par la carte).
 */
public final class RayonRendu implements BlockEntityRenderer<RayonBE> {
    private static final ResourceLocation RAYON = new ResourceLocation(HeralizDecors.MODID, "textures/entity/rayon.png");
    private static final float R = 1.0f, G = 0.80f, B = 0.48f;

    @Override
    public void render(RayonBE be, float pt, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        Level lv = be.getLevel();
        if (lv == null) {
            return;
        }
        double temps = (lv.getGameTime() + (double) pt) / 20.0;
        float jour = 1f;                                                  // le soleil : plein jour 1, nuit 0
        if (lv instanceof ClientLevel cl) {
            jour = Mth.clamp((cl.getSkyDarken(pt) - 0.25f) / 0.7f, 0f, 1f);
        }
        float respire = 0.9f + 0.1f * Mth.sin((float) (temps % 10.471975511965978) * 0.6f);
        float a = 0.2f * be.force * respire * jour;
        if (a < 0.004f) {
            return;
        }
        Vector3f ax = new Vector3f(be.dx, -be.longueur, be.dz);
        Vector3f e1 = new Vector3f(ax).cross(0, 0, 1);
        if (e1.lengthSquared() < 1e-4f) {
            e1.set(1, 0, 0);
        }
        e1.normalize();
        Vector3f e2 = new Vector3f(ax).cross(e1).normalize();
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenduAdditif.de(RAYON));
        int lames = 12, seg = 8;
        for (int k = 0; k < lames; k++) {
            float ang = (float) (k * Math.PI / lames), c = Mth.cos(ang), s = Mth.sin(ang);
            float dx = c * e1.x + s * e2.x, dy = c * e1.y + s * e2.y, dz = c * e1.z + s * e2.z;
            for (int j = 0; j < seg; j++) {
                float t0 = (float) j / seg, t1 = (float) (j + 1) / seg;
                float ra = be.r0 + (be.r1 - be.r0) * t0, rb = be.r0 + (be.r1 - be.r0) * t1;
                float x0 = ax.x * t0, y0 = ax.y * t0, z0 = ax.z * t0, x1 = ax.x * t1, y1 = ax.y * t1, z1 = ax.z * t1;
                v(vc, m, x0 - dx * ra, y0 - dy * ra, z0 - dz * ra, 0, t0, a);
                v(vc, m, x0 + dx * ra, y0 + dy * ra, z0 + dz * ra, 1, t0, a);
                v(vc, m, x1 + dx * rb, y1 + dy * rb, z1 + dz * rb, 1, t1, a);
                v(vc, m, x1 - dx * rb, y1 - dy * rb, z1 - dz * rb, 0, t1, a);
            }
        }
        ps.popPose();
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float t, float a) {
        vc.vertex(m, x, y, z).color(R, G, B, a).uv(u, t).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(RayonBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
