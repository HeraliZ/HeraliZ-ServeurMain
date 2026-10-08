package fr.heraliz.wonder.jetgun.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Deux types de rendu pour les effets du Jet Gun, une seule texture ({@code textures/fx/jetgun.png}, 4 x 4 cases,
 * générée par {@code tools/wonder/jg_fx.py}) : tout l'effet d'une image tient en deux lots.
 * <ul>
 *   <li>{@link #LUMIERE} : additif (cœur des traits d'air, étincelles, lueurs), sans écriture de profondeur ;</li>
 *   <li>{@link #FUMEE} : translucide (voiles d'air, poussière, vapeur, fumée, brume, lambeaux).</li>
 * </ul>
 */
public final class JetFxTypes extends RenderType {
    public static final ResourceLocation PLANCHE = new ResourceLocation(HeralizWonder.MODID, "textures/fx/jetgun.png");

    private JetFxTypes(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean a, boolean b, Runnable c, Runnable d) {
        super(n, f, m, s, a, b, c, d);
    }

    public static final RenderType LUMIERE = create("heraliz_wonder_jetgun_lumiere", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 65536,
            false, false, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_SHADER)
                    .setTextureState(new TextureStateShard(PLANCHE, true, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    public static final RenderType FUMEE = create("heraliz_wonder_jetgun_fumee", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 65536,
            false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_SHADER)
                    .setTextureState(new TextureStateShard(PLANCHE, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    public static final int LUEUR = 0, TRAIT = 1, TOURBILLON = 2, FUMEE_A = 3, FUMEE_B = 4, VAPEUR = 5, ETINCELLE = 6, CHAIR = 7, OS = 8, GOUTTE = 9,
            BRUME = 10, ANNEAU = 11, DISTORSION = 12, POUSSIERE = 13, DEBRIS = 14, ECLAT = 15;
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
