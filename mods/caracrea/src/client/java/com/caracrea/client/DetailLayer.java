package com.caracrea.client;

import com.caracrea.CaraCreaConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Draws the 3D body of a player with its own texture, posed like the
 * vanilla model (whose body, arms and legs are hidden meanwhile, see
 * {@link CaraCreaClient}). Armour and other layers render around it as usual.
 */
public class DetailLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final DetailModel model;

    public DetailLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, DetailModel model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!CaraCreaConfig.CLIENT.detailedBody.get() || player.isSpectator()) {
            return;
        }
        float alpha = 1;
        if (player.isInvisible()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || player.isInvisibleTo(mc.player)) {
                return;
            }
            alpha = 0.15F; // a teammate under invisibility: ghostly, like the vanilla body
        }
        getParentModel().copyPropertiesTo(model);
        model.showFor(ClientAppearances.resolve(player.getUUID()), player);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(CompositeSkins.detailFor(player.getUUID())));
        model.renderToBuffer(poseStack, buffer, light, LivingEntityRenderer.getOverlayCoords(player, 0), 1, 1, 1, alpha);
    }
}
