package fr.heraliz.decors.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Rendu « lumière » des rayons de soleil : texture douce, couleurs ajoutées à ce qui est derrière (les lames se
 * cumulent au centre et le rayon paraît plein, comme un vrai volume de lumière), pleine lumière, sans écriture de
 * profondeur, visible des deux côtés. La classe n'est jamais instanciée : elle sert à atteindre les états de rendu.
 */
final class RenduAdditif extends RenderType {
    private static final Function<ResourceLocation, RenderType> TYPES = Util.memoize(t -> RenderType.create("heraliz_lumiere_additive",
            DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 1024, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_SHADER)
                    .setTextureState(new TextureStateShard(t, false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false)));

    private RenduAdditif(String n, VertexFormat f, VertexFormat.Mode m, int taille, boolean a, boolean b, Runnable avant, Runnable apres) {
        super(n, f, m, taille, a, b, avant, apres);
    }

    static RenderType de(ResourceLocation texture) {
        return TYPES.apply(texture);
    }
}
