package fr.heralisz.matchs.duels.client.bombe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Passe lumineuse de la bombe et de l'ordinateur : le modèle redessiné avec son masque {@code <texture>_glowmask.png}
 * en plein éclat ({@link TypesRendu#lumiere}). Le masque n'est PAS traité par GeckoLib (son calque lumineux découpe les
 * texels lumineux de la texture de base : les grands écrans deviendraient transparents) : la texture de base garde ses
 * couleurs, le masque se pose dessus. Un rendu qui implémente {@link Passe} peut cacher des os pendant cette passe
 * (gyrophare qui clignote, ou éteint).
 */
final class CalqueLumineux<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    /** Rendu prévenu du début et de la fin de la passe lumineuse. */
    interface Passe {
        void passeLumineuse(boolean active);
    }

    CalqueLumineux(GeoRenderer<T> rendu) {
        super(rendu);
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel modele, RenderType type, MultiBufferSource tampons, VertexConsumer tampon,
                       float partial, int lumiere, int overlay) {
        ResourceLocation masque = TextureHD.get(TypesRendu.masque(getTextureResource(animatable)));
        RenderType rt = TypesRendu.lumiere(masque);
        Passe passe = getRenderer() instanceof Passe p ? p : null;
        if (passe != null) passe.passeLumineuse(true);
        try {
            getRenderer().reRender(modele, poseStack, tampons, animatable, rt, tampons.getBuffer(rt), partial, LightTexture.FULL_BRIGHT, overlay,
                    1f, 1f, 1f, 1f);
        } finally {
            if (passe != null) passe.passeLumineuse(false);
        }
    }
}
