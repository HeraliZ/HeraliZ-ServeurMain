package com.heraliz.extraction.client;

import com.heraliz.extraction.item.ExtractionTerminalItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * 3e personne : le PC est tenu ouvert devant le torse, posé sur la main droite.
 * Pendant la saisie, dessine aussi les jambes pliées (à genoux).
 */
public class TerminalHeldLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public TerminalHeldLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!TerminalClientExtensions.isHeldInMainHand(player)) {
            return;
        }
        boolean typing = TerminalClientExtensions.isTyping(player);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);

        if (typing && !player.isInvisible()) {
            KneelLegs.get().render(pose, buffers.getBuffer(RenderType.entityTranslucent(player.getSkinTextureLocation())),
                    light, overlay,
                    player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG),
                    player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG));
        }

        pose.pushPose();
        TerminalPoses.applyHeldTransform(pose, getParentModel().body);
        TerminalModel.render(pose, buffers, light, OverlayTexture.NO_OVERLAY, TerminalModel.State.HELD,
                typing ? TerminalScreen.typing(player.getTicksUsingItem() + partialTick, ageInTicks,
                        ExtractionTerminalItem.launchError(player.level(), player))
                        : TerminalScreen.idle(ageInTicks));
        pose.popPose();
    }
}
