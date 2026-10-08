package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Types de rendu maison du decor zombies (sous-classe de {@link RenderType} pour atteindre ses etats proteges) :
 * {@link #lueur} = emission additive visible des deux cotes (halos, faisceau de la boite, neons et voyants), pleine
 * luminosite, sans ecriture de profondeur.
 */
public final class ZbTypesRendu extends RenderType {
    @SuppressWarnings("unused")
    private ZbTypesRendu(String nom, VertexFormat f, VertexFormat.Mode m, int taille, boolean a, boolean b, Runnable avant, Runnable apres) {
        super(nom, f, m, taille, a, b, avant, apres);
    }

    private static final Function<ResourceLocation, RenderType> LUEUR = Util.memoize(tex -> create("heralisz_matchs_zb_lueur",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER).setTextureState(new TextureStateShard(tex, false, false))
                    .setTransparencyState(ADDITIVE_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    /** Emission additive double face pour cette texture. */
    public static RenderType lueur(ResourceLocation tex) {
        return LUEUR.apply(tex);
    }
}
