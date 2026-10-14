package fr.heralisz.matchs.duels.client.bombe;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Types de rendu de la bombe et de l'ordinateur :
 * <ul>
 *     <li>{@link #solide} : modèle opaque (découpe alpha), éclairé par le monde, texture HD mipmappée ;</li>
 *     <li>{@link #lumiere} : masque lumineux (lettre, écrans, liserés) en plein éclat, SANS ombrage directionnel ni
 *     addition : la couleur du masque remplace celle du modèle (mélange alpha), donc l'orange reste orange en plein
 *     jour ; pas d'écriture de profondeur (dessiné sur le modèle, à la même profondeur) ;</li>
 *     <li>{@link #COULEUR} : quads de couleur sans texture, des deux côtés (câble) ; {@link #COULEUR_ECRAN} : idem,
 *     décalés vers l'avant (barres, anneau et coches dessinés en direct sur l'écran).</li>
 * </ul>
 */
final class TypesRendu extends RenderStateShard {
    private TypesRendu(String nom, Runnable avant, Runnable apres) {
        super(nom, avant, apres);
    }

    private static final Function<ResourceLocation, RenderType> SOLIDE = Util.memoize(tex -> RenderType.create(
            "heralisz_matchs_bombe_solide", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)));

    private static final Function<ResourceLocation, RenderType> LUMIERE = Util.memoize(tex -> RenderType.create(
            "heralisz_matchs_bombe_lumiere", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    static final RenderType COULEUR = RenderType.create(
            "heralisz_matchs_bombe_couleur", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_TEXT_BACKGROUND_SHADER)
                    .setTextureState(NO_TEXTURE)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .createCompositeState(false));

    static final RenderType COULEUR_ECRAN = RenderType.create(
            "heralisz_matchs_bombe_couleur_ecran", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_TEXT_BACKGROUND_SHADER)
                    .setTextureState(NO_TEXTURE)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setLayeringState(POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false));

    static RenderType solide(ResourceLocation texture) {
        return SOLIDE.apply(texture);
    }

    static RenderType lumiere(ResourceLocation texture) {
        return LUMIERE.apply(texture);
    }

    private static final Function<ResourceLocation, ResourceLocation> MASQUE = Util.memoize(texture -> {
        String p = texture.getPath();
        int point = p.lastIndexOf('.');
        return new ResourceLocation(texture.getNamespace(), (point < 0 ? p : p.substring(0, point)) + "_glowmask" + (point < 0 ? "" : p.substring(point)));
    });

    /** Masque lumineux d'une texture : {@code <texture>_glowmask.png} (outils/bombe/modeles.py). */
    static ResourceLocation masque(ResourceLocation texture) {
        return MASQUE.apply(texture);
    }
}
