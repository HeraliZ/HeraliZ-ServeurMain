package com.heraliz.extraction.client;

import com.heraliz.extraction.TerminalGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Rendu de l'item (inventaire, au sol, en main). Les transformations par contexte sont dans le JSON du modèle. */
public class TerminalItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float SCALE = 0.92F / TerminalGeometry.MODEL_WIDTH;

    public TerminalItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        boolean thirdPerson = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        LivingEntity holder = ClientState.renderingEntity;
        if (thirdPerson && holder != null && holder.getMainHandItem() == stack) {
            return; // dessiné devant le torse par TerminalHeldLayer
        }

        pose.pushPose();
        pose.translate(0.5F, 0.25F, 0.5F);
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-TerminalGeometry.MODEL_CENTER_X, 0.0F, 1.0F);
        TerminalModel.render(pose, buffers, light, overlay, TerminalModel.State.HELD,
                TerminalScreen.idle(ClientState.time()));
        pose.popPose();
    }
}
