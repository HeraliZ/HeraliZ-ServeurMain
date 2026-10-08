package fr.heraliz.wonder.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Deux types de rendu, une seule texture (planche de 4 x 4 cases, {@code textures/fx/planche.png}) : tout l'effet d'une
 * image tient en deux lots.
 * <ul>
 *   <li>{@link #LUMIERE} : additif (l'energie eclaire ce qu'il y a derriere), sans ecriture de profondeur, double face ;</li>
 *   <li>{@link #FUMEE} : translucide classique (fumee de l'electrocution).</li>
 * </ul>
 */
public final class FxTypes extends RenderType {
    public static final ResourceLocation PLANCHE = new ResourceLocation(HeralizWonder.MODID, "textures/fx/planche.png");

    private FxTypes(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean a, boolean b, Runnable c, Runnable d) {
        super(n, f, m, s, a, b, c, d);
    }

    public static final RenderType LUMIERE = create("heraliz_wonder_lumiere", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 65536,
            false, false, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_SHADER)
                    .setTextureState(new TextureStateShard(PLANCHE, true, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    public static final RenderType FUMEE = create("heraliz_wonder_fumee", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 16384,
            false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_SHADER)
                    .setTextureState(new TextureStateShard(PLANCHE, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    // cases de la planche (colonne, ligne) -> u0, v0 ; une case = 0,25 ; marge interieure contre les bavures du filtrage
    public static final int LUEUR = 0, COEUR = 1, FAISCEAU = 2, ANNEAU = 3, ETINCELLE = 4, FUMEE_A = 5, FUMEE_B = 6, ETOILE = 7, DISQUE = 8,
            ARC = 9, ECLABOUSSURE = 10, ONDE = 11;
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
