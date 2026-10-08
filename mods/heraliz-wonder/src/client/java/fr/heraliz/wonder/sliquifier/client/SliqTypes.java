package fr.heraliz.wonder.sliquifier.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Deux types de rendu, une seule texture (planche 4 x 4 cases, {@code textures/fx/sliquifier.png},
 * {@code tools/wonder/sq_fx.py}) : tous les effets du Sliquifier d'une image tiennent en deux lots.
 * <ul>
 *   <li>{@link #GELEE} : translucide, ECLAIRE PAR LE MONDE (lumiere du bloc et du ciel par sommet : la gelee est sombre
 *   la nuit, comme le sol) ; flaques, boules, gouttes, bouffees ; leger decalage de profondeur (pas de scintillement avec
 *   le sol sous les flaques) ;</li>
 *   <li>{@link #LUEUR} : additif (la gelee luit un peu : halo des boules, reflets mouvants des flaques, lueur au sol).</li>
 * </ul>
 */
public final class SliqTypes extends RenderType {
    public static final ResourceLocation PLANCHE = new ResourceLocation(HeralizWonder.MODID, "textures/fx/sliquifier.png");

    private SliqTypes(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean a, boolean b, Runnable c, Runnable d) {
        super(n, f, m, s, a, b, c, d);
    }

    public static final RenderType GELEE = create("heraliz_wonder_sliq_gelee", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 65536,
            false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                    .setTextureState(new TextureStateShard(PLANCHE, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setLayeringState(POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false));

    public static final RenderType LUEUR = create("heraliz_wonder_sliq_lueur", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 65536,
            false, false, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_SHADER)
                    .setTextureState(new TextureStateShard(PLANCHE, true, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setLayeringState(POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false));

    // cases de la planche (colonne, ligne)
    public static final int FLAQUE = 0, FLAQUE_B = 1, GOUTTE = 2, ECLAT = 3, REFLET_0 = 4, HALO = 8, BULLE = 9, BOUFFEE = 10, ANNEAU = 11, TRAINEE = 12,
            COEUR = 13, COULURE = 14, ETOILE = 15;
    private static final float MARGE = 1.5f / 256f;

    public static float u0(int c) {
        return (c % 4) * 0.25f + MARGE;
    }

    public static float v0(int c) {
        return (c / 4) * 0.25f + MARGE;
    }

    public static float u1(int c) {
        return (c % 4) * 0.25f + 0.25f - MARGE;
    }

    public static float v1(int c) {
        return (c / 4) * 0.25f + 0.25f - MARGE;
    }
}
