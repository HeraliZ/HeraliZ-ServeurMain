package fr.heraliz.armory.client.cible;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.cible.CibleEssai;
import fr.heraliz.armory.client.MannequinCuriosLayer;
import fr.heraliz.armory.client.MannequinModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Rendu de la cible d'essai (03/11) : le mannequin d'armurerie (modèle et texture existants), à l'échelle d'un joueur
 * (0,9375, comme le rendu des joueurs : les morceaux visés par les balles tombent sur le dessin), armure du mod
 * (SetArmorLayer, ajoutée par ClientSetup à tout rendu humanoïde) et objets Curios. Quand on la vise de près : son nom et
 * l'armure qu'elle porte.
 */
public class CibleRenderer extends LivingEntityRenderer<CibleEssai, CibleModel> {
    private static final ResourceLocation TEXTURE = HeralizArmory.id("textures/entity/mannequin.png");
    /** Échelle du rendu des joueurs. */
    private static final float ECHELLE = 0.9375F;
    private static final double NOM_DISTANCE = 10.0D;

    public CibleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CibleModel(ctx.bakeLayer(MannequinModel.LAYER)), 0.35F);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        addLayer(new MannequinCuriosLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(CibleEssai e) {
        return TEXTURE;
    }

    @Override
    protected void scale(CibleEssai e, PoseStack pose, float partialTicks) {
        pose.scale(ECHELLE, ECHELLE, ECHELLE);
    }

    @Override
    protected void setupRotations(CibleEssai e, PoseStack pose, float bob, float yRot, float partialTicks) {
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yRot));
    }

    @Override
    protected boolean shouldShowName(CibleEssai e) {
        Minecraft mc = Minecraft.getInstance();
        return Minecraft.renderNames() && e == entityRenderDispatcher.crosshairPickEntity
                && entityRenderDispatcher.distanceToSqr(e) < NOM_DISTANCE * NOM_DISTANCE && mc.player != null && !mc.options.hideGui;
    }

    @Override
    protected void renderNameTag(CibleEssai e, Component nom, PoseStack pose, MultiBufferSource buffers, int light) {
        super.renderNameTag(e, TextesCible.etiquette(e.tenue(), e.casque()), pose, buffers, light);
    }
}
