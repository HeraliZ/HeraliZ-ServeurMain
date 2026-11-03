package com.heraliz.extraction.client;

import com.heraliz.extraction.TerminalGeometry;
import com.heraliz.extraction.entity.ExtractionTerminalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Terminal posé au sol (caisse qui s'ouvre, fumigène qui se redresse) ; la zone : {@link ZonesExtraction} (03/11). */
public class TerminalEntityRenderer extends EntityRenderer<ExtractionTerminalEntity> {

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

        // la zone elle-même : dessin commun des zones (ZonesExtraction, 03/11)
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
}
