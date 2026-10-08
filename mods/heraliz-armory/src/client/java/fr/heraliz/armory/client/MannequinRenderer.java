package fr.heraliz.armory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.client.devtools.CinemaClient;
import fr.heraliz.armory.entity.Mannequin;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
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
        if (CinemaClient.ACTIVE) shadowRadius = 0.0F;   // tournage : les ombres viennent du shader
    }

    /** Tournage video (outils de dev) : pose pilotee par le scenario, dont la position (decalage du rendu seulement). */
    @Override
    public void render(Mannequin e, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        CinemaClient.Pose p = CinemaClient.pose(e);
        if (p == null) {
            super.render(e, yaw, partialTicks, pose, buffers, light);
            return;
        }
        pose.pushPose();
        pose.translate(p.x() - Mth.lerp(partialTicks, e.xo, e.getX()), p.y() - Mth.lerp(partialTicks, e.yo, e.getY()),
                p.z() - Mth.lerp(partialTicks, e.zo, e.getZ()));
        super.render(e, yaw, partialTicks, pose, buffers, light);
        pose.popPose();
    }

    @Override
    public boolean shouldRender(Mannequin e, Frustum frustum, double x, double y, double z) {
        return CinemaClient.pose(e) != null || super.shouldRender(e, frustum, x, y, z);
    }

    @Override
    public ResourceLocation getTextureLocation(Mannequin e) {
        // tournage video (outils de dev) : peaux de joueur par defaut, une par membre de l'escouade (nom « x#3 » : 4e peau)
        if (!CinemaClient.ACTIVE) return TEXTURE;
        String n = e.getCustomName() == null ? "" : e.getCustomName().getString();
        int i = n.indexOf('#');
        int k = i < 0 ? 6 : Integer.parseInt(n.substring(i + 1));
        return new ResourceLocation("textures/entity/player/wide/" + SKINS[Math.floorMod(k, SKINS.length)] + ".png");
    }

    private static final String[] SKINS = {"alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"};

    @Override
    protected void setupRotations(Mannequin e, PoseStack pose, float bob, float yRot, float partialTicks) {
        CinemaClient.Pose p = CinemaClient.pose(e);
        if (p != null) {
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - p.yaw()));
            pose.mulPose(Axis.XP.rotationDegrees(-p.lean()));
            pose.mulPose(Axis.ZP.rotationDegrees(p.roll()));
            return;
        }
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
