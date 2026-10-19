package fr.heraliz.crac.client.render;

import static fr.heraliz.crac.client.render.TenueExplosifsDonnees.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.crac.item.C4Item;
import fr.heraliz.crac.item.ExplosiveChargeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * C4 et charge explosive tenues à deux mains (CRac 1.12.0, demande du propriétaire du 19/10) : en première personne, les
 * deux mains du joueur tiennent l'objet devant lui, au milieu ; vu des autres, les deux bras sont tendus devant le ventre
 * et l'objet est entre les mains (transformation « thirdperson_righthand » des modèles d'objet). Tous les nombres sont
 * écrits par mods/crac/outils/tenue_explosifs.py ({@link TenueExplosifsDonnees}), qui refait ce rendu par le calcul.
 */
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TenueExplosifs {
    /** Vu des autres : les deux bras tendus devant le ventre, rentrés vers le milieu (ne suit pas le regard, pour que
     *  l'objet reste entre les mains). */
    public static final HumanoidModel.ArmPose ARM_POSE = HumanoidModel.ArmPose.create("CRAC_EXPLOSIF", true, (model, entity, arm) -> {
        model.rightArm.xRot = BRAS_X;
        model.rightArm.yRot = -BRAS_Y;
        model.leftArm.xRot = BRAS_X;
        model.leftArm.yRot = BRAS_Y;
    });

    private TenueExplosifs() {
    }

    /** Pose des bras pour IClientItemExtensions#getArmPose : seulement en main principale. */
    public static @Nullable HumanoidModel.ArmPose pose(LivingEntity entity, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? ARM_POSE : null;
    }

    private static float[][] mains(ItemStack stack) {
        if (stack.getItem() instanceof C4Item) {
            return new float[][]{C4_DROITE, C4_GAUCHE};
        }
        if (stack.getItem() instanceof ExplosiveChargeItem) {
            return new float[][]{EXPLOSIVE_CHARGE_DROITE, EXPLOSIVE_CHARGE_GAUCHE};
        }
        return null;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        float[][] mains = mains(stack);
        if (mains == null) {
            return;
        }
        // les deux mains sont sur l'objet : rien n'est dessiné pour la main gauche
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        pose.pushPose();
        // geste du clic (lancer) et sortie de l'objet, comme la carte tenue à deux mains de Minecraft
        float swing = event.getSwingProgress();
        float s = Mth.sqrt(swing);
        pose.translate(0.0F, 0.1F * Mth.sin(swing * (float) Math.PI), -0.4F * Mth.sin(s * (float) Math.PI));
        pose.translate(0.0F, event.getEquipProgress() * -1.2F, 0.0F);
        if (!player.isInvisible()) {
            main(mc, player, pose, buffers, light, HumanoidArm.RIGHT, mains[0]);
            main(mc, player, pose, buffers, light, HumanoidArm.LEFT, mains[1]);
        }
        pose.pushPose();
        pose.translate(FP_X, FP_Y, FP_Z);
        pose.mulPose(Axis.YP.rotationDegrees(FP_LACET));
        pose.mulPose(Axis.XP.rotationDegrees(FP_INCLINE));
        pose.scale(FP_TAILLE, FP_TAILLE, FP_TAILLE);
        mc.getItemRenderer().renderStatic(player, stack, ItemDisplayContext.NONE, false, pose, buffers, player.level(), light,
            OverlayTexture.NO_OVERLAY, player.getId());
        pose.popPose();
        pose.popPose();
    }

    /**
     * Une main : posée à sa place, puis la rotation que Minecraft laisse sur le bras en le dessinant (pose de tenue en
     * lacet, balancement au repos) annulée autour de l'épaule, pour qu'il ne reste que la nôtre.
     */
    private static void main(Minecraft mc, AbstractClientPlayer player, PoseStack pose, MultiBufferSource buffers, int light,
                             HumanoidArm arm, float[] m) {
        float f = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        pose.pushPose();
        pose.translate(m[0], m[1], m[2]);
        pose.mulPose(Axis.XP.rotationDegrees(m[3]));
        pose.mulPose(Axis.YP.rotationDegrees(m[4]));
        pose.mulPose(Axis.ZP.rotationDegrees(m[5]));
        pose.translate(m[6], m[7], m[8]);
        pose.translate(-5.0F * f / 16.0F, 2.0F / 16.0F, 0.0F);
        pose.mulPose(Axis.YP.rotation(BRAS_Y * f));
        pose.mulPose(Axis.ZP.rotation(-BOB_Z * f));
        pose.translate(5.0F * f / 16.0F, -2.0F / 16.0F, 0.0F);
        // manches d'armure de l'Armurerie et autres mods : même évènement que pour les mains de Minecraft
        if (!ForgeHooksClient.renderSpecificFirstPersonArm(pose, buffers, light, player, arm)) {
            PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().<AbstractClientPlayer>getRenderer(player);
            if (arm == HumanoidArm.RIGHT) {
                renderer.renderRightHand(pose, buffers, light, player);
            } else {
                renderer.renderLeftHand(pose, buffers, light, player);
            }
        }
        pose.popPose();
    }
}
