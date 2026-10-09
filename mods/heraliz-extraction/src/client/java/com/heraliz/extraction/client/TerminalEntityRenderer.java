package com.heraliz.extraction.client;

import com.heraliz.extraction.HeralizExtraction;
import com.heraliz.extraction.TerminalGeometry;
import com.heraliz.extraction.ZoneShape;
import com.heraliz.extraction.entity.ExtractionTerminalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Terminal posé au sol (caisse qui s'ouvre, fumigène qui se redresse) + mur de la zone. */
public class TerminalEntityRenderer extends EntityRenderer<ExtractionTerminalEntity> {
    private static final ResourceLocation ZONE_TEX =
            new ResourceLocation(HeralizExtraction.MODID, "textures/entity/extraction_zone.png");
    private static final float ZONE_HEIGHT = 1.6F;

    public TerminalEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.6F;
    }

    @Override
    public ResourceLocation getTextureLocation(ExtractionTerminalEntity entity) {
        return TerminalModel.TEXTURE;
    }

    @Override
    public void render(ExtractionTerminalEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        float ticks = entity.tickCount + partialTick;
        boolean active = entity.getState() == ExtractionTerminalEntity.STATE_ACTIVE;

        if (!entity.isFixed()) {
            renderTerminal(entity, partialTick, ticks, active, pose, buffers, packedLight);
        }

        float strength = entity.getVisualStrength(partialTick);
        if (strength > 0.01F) {
            float[] rgb = ExtractionTerminalEntity.progressColor(entity.getProgress(partialTick));
            renderZone(entity, pose, buffers, ticks, rgb, strength);
        }
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    /** Le PC posé au sol (absent pour les zones fixes d'admin). */
    private static void renderTerminal(ExtractionTerminalEntity entity, float partialTick, float ticks, boolean active,
                                       PoseStack pose, MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
        float s = TerminalGeometry.GROUND_SCALE / 16.0F;
        pose.scale(s, s, s);
        TerminalModel.State state = new TerminalModel.State(100.0F, TerminalGeometry.lidOpen(ticks),
                TerminalGeometry.deploy(ticks), active && ticks >= TerminalGeometry.DEPLOY_END);
        TerminalModel.render(pose, buffers, packedLight, OverlayTexture.NO_OVERLAY, state,
                TerminalScreen.ground(entity, partialTick, ticks));
        pose.popPose();
    }

    /**
     * Mur translucide sur chaque bord de la zone. La zone suit le relief et s'arrête sur les murs,
     * donc le mur dessine exactement où le joueur a le droit d'être.
     */
    private static void renderZone(ExtractionTerminalEntity entity, PoseStack pose, MultiBufferSource buffers,
                                   float time, float[] rgb, float strength) {
        ZoneShape zone = entity.getZone();
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        VertexConsumer vc = buffers.getBuffer(RenderType.beaconBeam(ZONE_TEX, true));

        float alpha = (0.55F + 0.15F * Mth.sin(time * 0.15F)) * strength;
        float uScroll = time * 0.02F;
        double oy = entity.getY();
        int segments = ZoneShape.OUTLINE_SEGMENTS;

        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            float d0 = zone.outlineDistance(i);
            float d1 = zone.outlineDistance(i + 1);
            float g0 = (float) (zone.outlineGround(i) - oy) + 0.02F;
            float g1 = (float) (zone.outlineGround(i + 1) - oy) + 0.02F;
            float u0 = uScroll + i * 0.5F;
            wall(vc, m, n, Mth.cos(a0) * d0, Mth.sin(a0) * d0, g0, Mth.cos(a1) * d1, Mth.sin(a1) * d1, g1,
                    ZONE_HEIGHT, rgb, alpha, u0);
        }
    }

    /**
     * Un panneau vertical de hauteur {@code h} entre (x0,z0) posé à g0 et (x1,z1) posé à g1,
     * émis dans les deux sens pour ignorer le culling.
     */
    private static void wall(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float z0, float g0,
                             float x1, float z1, float g1, float h, float[] rgb, float a, float u0) {
        float u1 = u0 + 0.5F;
        glowVertex(vc, m, n, x0, g0 + h, z0, rgb, a, u0, 0.0F);
        glowVertex(vc, m, n, x0, g0, z0, rgb, a, u0, 1.0F);
        glowVertex(vc, m, n, x1, g1, z1, rgb, a, u1, 1.0F);
        glowVertex(vc, m, n, x1, g1 + h, z1, rgb, a, u1, 0.0F);

        glowVertex(vc, m, n, x1, g1 + h, z1, rgb, a, u1, 0.0F);
        glowVertex(vc, m, n, x1, g1, z1, rgb, a, u1, 1.0F);
        glowVertex(vc, m, n, x0, g0, z0, rgb, a, u0, 1.0F);
        glowVertex(vc, m, n, x0, g0 + h, z0, rgb, a, u0, 0.0F);
    }

    private static void glowVertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z,
                                   float[] rgb, float a, float u, float v) {
        vc.vertex(m, x, y, z).color(rgb[0], rgb[1], rgb[2], a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }
}
