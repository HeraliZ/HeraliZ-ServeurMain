package fr.heraliz.emote.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.util.Vec3f;
import fr.heraliz.emote.client.EmotePlayback;
import fr.heraliz.emote.client.catalog.PropSpec;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** Draws the props of the Heraliz emote a player is playing, with the animated model parts. */
public class PropLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public PropLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        EmotePlayback playback = EmotePlayback.of(player);
        if (playback == null || playback.info().props().isEmpty()) return;
        float tick = playback.tick(partialTick);
        // playerAnimator's whole-body transform, which props on the ground ("world") take back out
        KeyframeAnimationPlayer anim = (KeyframeAnimationPlayer) playback.player();
        Vec3f p = anim.get3DTransform("body", TransformType.POSITION, partialTick, Vec3f.ZERO);
        Vec3f r = anim.get3DTransform("body", TransformType.ROTATION, partialTick, Vec3f.ZERO);
        float[] body = {p.getX(), p.getY(), p.getZ(), r.getX(), r.getY(), r.getZ()};
        for (PropSpec prop : playback.info().props()) {
            PropRenderer.render(prop, tick, body, player, getParentModel(), pose, buffers, light);
        }
    }
}
