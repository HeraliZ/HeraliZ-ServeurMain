package fr.heraliz.wonder.wintershowl.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Deux types de rendu pour tous les effets du Winter's Howl, une seule texture (planche 4 x 4 de
 * {@code textures/fx/givre.png}, dessinee par {@code tools/wonder/wh_fx.py}) :
 * <ul>
 *   <li>{@link #LUMIERE} : additif (scintillement des cristaux, anneaux, coeur des eclats), sans ecriture de profondeur ;</li>
 *   <li>{@link #BRUME} : translucide (brume d'azote, givre au sol, eclats de glace, pointes sur les gelees).</li>
 * </ul>
 * Plus les textures de la couche de glace des creatures gelees ({@link CoucheGivre}) et du voile de givre de l'ecran.
 */
public final class TypesGivre extends RenderType {
    public static final ResourceLocation PLANCHE = new ResourceLocation(HeralizWonder.MODID, "textures/fx/givre.png");
    public static final ResourceLocation ECRAN = new ResourceLocation(HeralizWonder.MODID, "textures/fx/givre_ecran.png");
    public static final ResourceLocation[] COUCHE = {new ResourceLocation(HeralizWonder.MODID, "textures/entity/couche_givre_0.png"),
            new ResourceLocation(HeralizWonder.MODID, "textures/entity/couche_givre_1.png"),
            new ResourceLocation(HeralizWonder.MODID, "textures/entity/couche_givre_2.png")};
    public static final ResourceLocation COUCHE_LUEUR = new ResourceLocation(HeralizWonder.MODID, "textures/entity/couche_givre_lueur.png");

    private TypesGivre(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean a, boolean b, Runnable c, Runnable d) {
        super(n, f, m, s, a, b, c, d);
    }

    public static final RenderType LUMIERE = create("heraliz_wonder_givre_lumiere", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 65536,
            false, false, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_SHADER)
                    .setTextureState(new TextureStateShard(PLANCHE, true, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    public static final RenderType BRUME = create("heraliz_wonder_givre_brume", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 65536,
            false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_SHADER)
                    .setTextureState(new TextureStateShard(PLANCHE, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    // cases de la planche (colonne, ligne)
    public static final int BRUME_A = 0, BRUME_B = 1, ETINCELLE = 2, FLOCON = 3, ECLAT_A = 4, ECLAT_B = 5, ANNEAU = 6, TRAIT = 7, GIVRE_SOL = 8, LUEUR = 9,
            POUSSIERE = 10, POINTE = 11;
    private static final float MARGE = 1.0f / 256f;

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
