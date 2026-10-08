package fr.heraliz.crates.client.render;

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
 *  - translucent : verre, liquides, anneaux (melange alpha, eclaire, trie, pas d'ecriture de profondeur)
 */
public final class HzRenderTypes extends RenderStateShard {
    private HzRenderTypes(String name, Runnable setup, Runnable clear) {
        super(name, setup, clear);
    }

    private static final Function<ResourceLocation, RenderType> SOLID = Util.memoize(tex -> RenderType.create(
            "heralizcrates_solid", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)));

    private static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(tex -> RenderType.create(
            "heralizcrates_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> TRANSLUCENT = Util.memoize(tex -> RenderType.create(
            "heralizcrates_translucent", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(true)));

    /** Caisses figees : eclairage deja cuit dans la couleur des sommets (position, couleur, texture, lumiere). */
    private static final ShaderStateShard BAKED_SHADER = new ShaderStateShard(GameRenderer::getPositionColorTexLightmapShader);

    private static final Function<ResourceLocation, RenderType> STATIC_SOLID = Util.memoize(tex -> RenderType.create(
            "heralizcrates_static_solid", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(BAKED_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> STATIC_TRANSLUCENT = Util.memoize(tex -> RenderType.create(
            "heralizcrates_static_translucent", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(BAKED_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> STATIC_GLOW = Util.memoize(tex -> RenderType.create(
            "heralizcrates_static_glow", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(BAKED_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, true))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

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
