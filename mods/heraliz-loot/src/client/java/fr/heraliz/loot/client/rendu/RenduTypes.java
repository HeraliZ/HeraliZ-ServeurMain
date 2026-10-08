package fr.heraliz.loot.client.rendu;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Types de rendu du mod :
 *  - solid : modele opaque (decoupe alpha), eclaire, texture HD mipmappee
 *  - glow  : calque lumineux additif (plein eclat, pas d'ecriture de profondeur)
 *  - translucent : verre, ecrans (melange alpha, eclaire, pas d'ecriture de profondeur)
 *  - static* : carrosserie figee, eclairage deja cuit dans la couleur des sommets
 */
public final class RenduTypes extends RenderStateShard {
    private RenduTypes(String name, Runnable setup, Runnable clear) {
        super(name, setup, clear);
    }

    /** Ordre de dessin des types du mod quand ils sont regroupes (regroupement) : opaque, translucide, lumineux. */
    public static final int CAT_SOLID = 0, CAT_TRANSLUCENT = 1, CAT_GLOW = 2, CAT_OTHER = 3;
    private static final java.util.Map<RenderType, Integer> CATEGORY = new java.util.concurrent.ConcurrentHashMap<>();

    private static RenderType tag(RenderType t, int cat) {
        CATEGORY.put(t, cat);
        return t;
    }

    /** Categorie d'un type de rendu (textes de la police et types inconnus : en dernier). */
    public static int category(RenderType t) {
        return CATEGORY.getOrDefault(t, CAT_OTHER);
    }

    private static final Function<ResourceLocation, RenderType> SOLID = Util.memoize(tex -> tag(RenderType.create(
            "heraliz_loot_solid", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)), CAT_SOLID));

    private static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(tex -> tag(RenderType.create(
            "heraliz_loot_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)), CAT_GLOW));

    private static final Function<ResourceLocation, RenderType> TRANSLUCENT = Util.memoize(tex -> tag(RenderType.create(
            "heraliz_loot_translucent", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(true)), CAT_TRANSLUCENT));

    private static final ShaderStateShard BAKED_SHADER = new ShaderStateShard(GameRenderer::getPositionColorTexLightmapShader);

    private static final Function<ResourceLocation, RenderType> STATIC_SOLID = Util.memoize(tex -> tag(RenderType.create(
            "heraliz_loot_static_solid", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(BAKED_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .createCompositeState(false)), CAT_SOLID));

    private static final Function<ResourceLocation, RenderType> STATIC_TRANSLUCENT = Util.memoize(tex -> tag(RenderType.create(
            "heraliz_loot_static_translucent", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(BAKED_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)), CAT_TRANSLUCENT));

    private static final Function<ResourceLocation, RenderType> STATIC_GLOW = Util.memoize(tex -> tag(RenderType.create(
            "heraliz_loot_static_glow", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(BAKED_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)), CAT_GLOW));

    /** Surlignage des zones cliquables : couleur pure additive, sans texture ni ecriture de profondeur. */
    private static final RenderType HIGHLIGHT = RenderType.create(
            "heraliz_loot_highlight", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    public static RenderType highlight() {
        return HIGHLIGHT;
    }

    public static RenderType staticSolid(ResourceLocation texture) {
        return STATIC_SOLID.apply(HDTexture.get(texture));
    }

    public static RenderType staticTranslucent(ResourceLocation texture) {
        return STATIC_TRANSLUCENT.apply(HDTexture.get(texture));
    }

    public static RenderType staticGlow(ResourceLocation texture) {
        return STATIC_GLOW.apply(HDTexture.get(texture));
    }

    public static RenderType solid(ResourceLocation texture) {
        return SOLID.apply(HDTexture.get(texture));
    }

    public static RenderType glow(ResourceLocation texture) {
        return GLOW.apply(HDTexture.get(texture));
    }

    public static RenderType translucent(ResourceLocation texture) {
        return TRANSLUCENT.apply(HDTexture.get(texture));
    }
}
