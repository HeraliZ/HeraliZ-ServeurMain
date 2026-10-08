package fr.heraliz.airstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.airstrike.client.render.DesignatorRenderer;
import fr.heraliz.airstrike.item.DesignatorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Third person poses (binoculars at the eyes, both hands on the bay while reloading), first person raising and
 * reloading animation, and the animated item renderer.
 */
public final class DesignatorItemExtensions implements IClientItemExtensions {
    public static final DesignatorItemExtensions INSTANCE = new DesignatorItemExtensions();

    private static final HumanoidModel.ArmPose BINOCULARS = HumanoidModel.ArmPose.create("AIRSTRIKE_BINOCULARS", true,
            (model, entity, arm) -> {
                float crouch = entity.isCrouching() ? 0.26f : 0f;
                float x = Mth.clamp(model.head.xRot - 1.62f - crouch, -2.6f, 3.3f);
                model.rightArm.xRot = x;
                model.leftArm.xRot = x;
                model.rightArm.yRot = model.head.yRot - 0.52f;
                model.leftArm.yRot = model.head.yRot + 0.52f;
                model.rightArm.zRot = 0f;
                model.leftArm.zRot = 0f;
            });

    private static final HumanoidModel.ArmPose RELOADING = HumanoidModel.ArmPose.create("AIRSTRIKE_RELOADING", true,
            (model, entity, arm) -> {
                float t = entity.tickCount + Minecraft.getInstance().getFrameTime();
                model.rightArm.xRot = -0.95f;
                model.leftArm.xRot = -1.05f + Mth.sin(t * 0.9f) * 0.12f;
                model.rightArm.yRot = -0.32f;
                model.leftArm.yRot = 0.55f;
                model.rightArm.zRot = 0f;
                model.leftArm.zRot = 0f;
            });

    private DesignatorItemExtensions() {
    }

    private static boolean raised(LivingEntity entity, ItemStack stack) {
        return entity.isUsingItem() && entity.getUseItem() == stack;
    }

    private static float reload(LivingEntity entity, ItemStack stack, float partialTick) {
        return DesignatorItem.reloadProgress(stack, entity.level().getGameTime() + partialTick);
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (raised(entity, stack)) return BINOCULARS;
        if (reload(entity, stack, 0f) < 1f) return RELOADING;
        return null;
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return DesignatorRenderer.get();
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equipProcess, float swingProcess) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float r = 0f;
        if (raised(player, stack)) {
            r = Mth.clamp((player.getTicksUsingItem() + partialTick) / 5.0f, 0f, 1f);
            r = r * r * (3 - 2 * r);
        }
        float p = reload(player, stack, partialTick);
        float w = DesignatorRenderer.presentation(p);
        // idle at the hip (three quarter view), blended towards the eyes or towards the reload pose
        float ix = side * 0.46f, iy = -0.36f + equipProcess * -0.6f, iz = -0.90f;
        float tx = 0.0f, ty = -0.18f, tz = -0.36f;
        float x = Mth.lerp(r, ix, tx), y = Mth.lerp(r, iy, ty), z = Mth.lerp(r, iz, tz);
        x = Mth.lerp(w, x, side * 0.10f);
        y = Mth.lerp(w, y, -0.24f);
        z = Mth.lerp(w, z, -0.88f);
        poseStack.translate(x, y, z);
        float k = (1f - r) * (1f - w);
        if (k > 0f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(22f * k));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 20f * k));
        }
        if (w > 0f) {
            // tilted towards the eyes to look into the bay, plus the moves of the reload (latch, flip, click, slam)
            float[] sw = DesignatorRenderer.sway(p, side);
            poseStack.translate(sw[0], sw[1], sw[2]);
            poseStack.mulPose(Axis.XP.rotationDegrees(55f * w + sw[3]));
            poseStack.mulPose(Axis.YP.rotationDegrees(-side * 8f * w + sw[4]));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * 3f * w + sw[5]));
        }
        if (r > 0f) {
            float bob = Mth.sin((player.tickCount + partialTick) * 0.08f) * 0.006f * r;
            poseStack.translate(0, bob, 0);
        }
        if (swingProcess > 0f && r <= 0f && w <= 0f) {
            float s = Mth.sin(swingProcess * (float) Math.PI);
            poseStack.mulPose(Axis.XP.rotationDegrees(-s * 20f));
        }
        return true;
    }
}
