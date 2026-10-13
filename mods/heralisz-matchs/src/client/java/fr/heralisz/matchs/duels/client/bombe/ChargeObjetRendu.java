package fr.heralisz.matchs.duels.client.bombe;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.bombe.ChargeObjet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Ordinateur d'amorçage fermé en main ({@code heralisz_matchs:duel_charge}) : même modèle et même texture que
 * l'ordinateur fermé posé au sol, avec ses liserés lumineux. Place en main (1re et 3e personne) et dans l'inventaire :
 * {@code models/item/duel_charge.json}.
 */
public final class ChargeObjetRendu extends GeoItemRenderer<ChargeObjet> {
    private static ChargeObjetRendu instance;

    private ChargeObjetRendu() {
        super(new Modele());
        addRenderLayer(new CalqueLumineux<>(this));
    }

    /** Créé à la première utilisation (fil de rendu). */
    public static ChargeObjetRendu instance() {
        if (instance == null) instance = new ChargeObjetRendu();
        return instance;
    }

    @Override
    public RenderType getRenderType(ChargeObjet objet, ResourceLocation texture, MultiBufferSource tampons, float partial) {
        return TypesRendu.solide(texture);
    }

    static final class Modele extends GeoModel<ChargeObjet> {
        private static final ResourceLocation GEO = HeraliszMatchs.id("geo/duel_ordinateur_ferme.geo.json");
        private static final ResourceLocation TEXTURE = HeraliszMatchs.id("textures/entity/duel_ordinateur_ferme.png");
        private static final ResourceLocation ANIMATIONS = HeraliszMatchs.id("animations/duel_bombe.animation.json");

        @Override
        public ResourceLocation getModelResource(ChargeObjet objet) {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(ChargeObjet objet) {
            return TextureHD.get(TEXTURE);
        }

        @Override
        public ResourceLocation getAnimationResource(ChargeObjet objet) {
            return ANIMATIONS;
        }
    }
}
