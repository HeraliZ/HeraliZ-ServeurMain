package fr.heralisz.matchs.duels.client;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.CibleEntrainement;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Rendu de la cible d'entrainement : silhouette humaine (squelette du joueur) avec la texture maison du mannequin
 * ({@code textures/entity/duel_cible.png}, generee par {@code tools/matchs/duels/art.py}).
 */
public class CibleRenderer extends HumanoidMobRenderer<CibleEntrainement, HumanoidModel<CibleEntrainement>> {
    private static final ResourceLocation TEXTURE = HeraliszMatchs.id("textures/entity/duel_cible.png");

    public CibleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.4f);
    }

    @Override
    public ResourceLocation getTextureLocation(CibleEntrainement e) {
        return TEXTURE;
    }

    /** Pas d'etiquette de nom vanilla au-dessus de la cible. */
    @Override
    protected boolean shouldShowName(CibleEntrainement e) {
        return false;
    }
}
