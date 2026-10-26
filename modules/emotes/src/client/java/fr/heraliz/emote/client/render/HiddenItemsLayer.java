package fr.heraliz.emote.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.emote.client.EmotePlayback;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** Wraps the held-item layer: the items stay hidden while an emote puts props in the hands. */
public class HiddenItemsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> wrapped;

    public HiddenItemsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                            RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> wrapped) {
        super(parent);
        this.wrapped = wrapped;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        EmotePlayback playback = EmotePlayback.of(player);
        if (playback != null && playback.info().hideHeldItems()) return;
        wrapped.render(pose, buffers, light, player, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
    }
}
