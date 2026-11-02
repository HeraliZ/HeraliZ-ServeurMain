package fr.heraliz.armory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.entity.Mannequin;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Rendu du mannequin : corps neutre, armures vanilla et du mod, objets en main, et tous les objets Curios.
 * Les pieces du mod dans les slots vanilla sont dessinees par SetArmorLayer (ajoutee par ClientSetup.onAddLayers, comme
 * pour les joueurs) : la couche d'armure vanilla les ignore.
 */
public class MannequinRenderer extends LivingEntityRenderer<Mannequin, MannequinModel> {
    private static final ResourceLocation TEXTURE = HeralizArmory.id("textures/entity/mannequin.png");

    public MannequinRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MannequinModel(ctx.bakeLayer(MannequinModel.LAYER)), 0.35F);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        addLayer(new ElytraLayer<>(this, ctx.getModelSet()));
        addLayer(new CustomHeadLayer<>(this, ctx.getModelSet(), ctx.getItemInHandRenderer()));
        addLayer(new MannequinCuriosLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Mannequin e) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(Mannequin e, PoseStack pose, float bob, float yRot, float partialTicks) {
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yRot));
        float f = (float) (e.level().getGameTime() - e.lastHit) + partialTicks;
        if (f < 5.0F) pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(f / 1.5F * (float) Math.PI) * 3.0F));
    }

    @Override
    protected boolean shouldShowName(Mannequin e) {
        double d = entityRenderDispatcher.distanceToSqr(e);
        float max = e.isCrouching() ? 32.0F : 64.0F;
        return d < (double) (max * max) && e.isCustomNameVisible();
    }
}
