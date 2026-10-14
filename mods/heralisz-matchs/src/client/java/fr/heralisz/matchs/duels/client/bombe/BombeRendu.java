package fr.heralisz.matchs.duels.client.bombe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.bombe.BombeDuel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Rendu de la bombe d'un site (modèle GeckoLib {@code geo/duel_bombe.geo.json}, textures HD par site et par état,
 * outils/bombe/modeles.py) : texture A ou B, variante amorcée (écran ARMED, lettre et voyants rouges) ; gyrophare ambre
 * qui tourne en veille, rouge qui tourne vite et clignote une fois amorcée, éteint après le désamorçage ; plus rien
 * après l'explosion.
 */
public final class BombeRendu extends GeoEntityRenderer<BombeDuel> implements CalqueLumineux.Passe {
    private boolean passeLumineuse;
    private int etat;
    private float age;

    public BombeRendu(EntityRendererProvider.Context ctx) {
        super(ctx, new Modele());
        this.shadowRadius = 0.7f;
        this.shadowStrength = 0.6f;
        addRenderLayer(new CalqueLumineux<>(this));
    }

    @Override
    public void render(BombeDuel bombe, float lacet, float partial, PoseStack ps, MultiBufferSource tampons, int lumiere) {
        if (bombe.etat() == BombeDuel.EXPLOSEE) return;
        this.etat = bombe.etat();
        this.age = bombe.tickCount + partial;
        super.render(bombe, lacet, partial, ps, tampons, lumiere);
    }

    @Override
    protected void applyRotations(BombeDuel bombe, PoseStack ps, float age, float lacet, float partial) {
        ps.mulPose(Axis.YP.rotationDegrees(180f - bombe.getYRot()));
    }

    @Override
    public RenderType getRenderType(BombeDuel bombe, ResourceLocation texture, MultiBufferSource tampons, float partial) {
        return TypesRendu.solide(texture);
    }

    @Override
    public void passeLumineuse(boolean active) {
        this.passeLumineuse = active;
    }

    /** Gyrophare non lumineux : désamorcée, ou phase éteinte du clignotement de la bombe amorcée (2 fois par seconde). */
    private boolean gyrophareEteint() {
        if (this.etat == BombeDuel.DESAMORCEE) return true;
        return this.etat == BombeDuel.ARMEE && (int) (this.age / 5f) % 2 == 1;
    }

    @Override
    public void renderRecursively(PoseStack ps, BombeDuel bombe, GeoBone os, RenderType type, MultiBufferSource tampons, VertexConsumer tampon,
                                  boolean reRendu, float partial, int lumiere, int overlay, float r, float g, float b, float a) {
        if (this.passeLumineuse && "gyrophare".equals(os.getName()) && gyrophareEteint()) return;
        super.renderRecursively(ps, bombe, os, type, tampons, tampon, reRendu, partial, lumiere, overlay, r, g, b, a);
    }

    @Override
    public boolean shouldShowName(BombeDuel bombe) {
        return false;
    }

    /** Modèle : géométrie commune, texture selon le site et l'état, gyrophare tourné à chaque image. */
    static final class Modele extends GeoModel<BombeDuel> {
        private static final ResourceLocation GEO = HeraliszMatchs.id("geo/duel_bombe.geo.json");
        private static final ResourceLocation ANIMATIONS = HeraliszMatchs.id("animations/duel_bombe.animation.json");
        private static final ResourceLocation A = HeraliszMatchs.id("textures/entity/duel_bombe_a.png"),
                A_ARMEE = HeraliszMatchs.id("textures/entity/duel_bombe_a_arme.png"),
                B = HeraliszMatchs.id("textures/entity/duel_bombe_b.png"),
                B_ARMEE = HeraliszMatchs.id("textures/entity/duel_bombe_b_arme.png");

        @Override
        public ResourceLocation getModelResource(BombeDuel bombe) {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(BombeDuel bombe) {
            boolean armee = bombe.etat() == BombeDuel.ARMEE;
            boolean b = "B".equals(bombe.site());
            return TextureHD.get(b ? (armee ? B_ARMEE : B) : (armee ? A_ARMEE : A));
        }

        @Override
        public ResourceLocation getAnimationResource(BombeDuel bombe) {
            return ANIMATIONS;
        }

        @Override
        public void setCustomAnimations(BombeDuel bombe, long id, AnimationState<BombeDuel> etat) {
            float vitesse = bombe.etat() == BombeDuel.ARMEE ? 0.55f : bombe.etat() == BombeDuel.VEILLE ? 0.2f : 0f;
            float t = bombe.tickCount + etat.getPartialTick();
            getBone("gyrophare").ifPresent(os -> os.setRotY(t * vitesse));
        }
    }
}
