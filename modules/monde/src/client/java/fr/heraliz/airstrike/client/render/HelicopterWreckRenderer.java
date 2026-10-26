package fr.heraliz.airstrike.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.airstrike.client.model.GeoModel;
import fr.heraliz.airstrike.client.model.ModModels;
import fr.heraliz.airstrike.entity.HelicopterWreck;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Épave de l'hélicoptère (modèle {@code helicopter_wreck}), immobile, tournée comme l'hélicoptère à l'impact, éclairée
 * par la lumière du monde. Flammes et fumée : {@code HelicoClient.epave}. Modèle pas encore livré : rien n'est dessiné.
 */
public class HelicopterWreckRenderer extends EntityRenderer<HelicopterWreck> {
    private static final ResourceLocation TEXTURE = ModModels.texture("helicopter_wreck");

    public HelicopterWreckRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(HelicopterWreck w, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        GeoModel m = ModModels.helicopterWreck();
        if (!m.topLevel.isEmpty()) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180f - w.getYRot()));
            ps.scale(-1f, -1f, 1f);
            m.renderList(m.solid, ps, buffers.getBuffer(RenderType.entityCutout(TEXTURE)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            if (!m.glass.isEmpty()) {
                m.renderList(m.glass, ps, buffers.getBuffer(RenderType.entityTranslucentCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            }
            ps.popPose();
        }
        super.render(w, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HelicopterWreck w) {
        return TEXTURE;
    }
}
