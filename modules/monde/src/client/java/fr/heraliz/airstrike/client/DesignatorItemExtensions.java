package fr.heraliz.airstrike.client;

import fr.heraliz.airstrike.client.render.DesignatorHold;
import fr.heraliz.airstrike.client.render.DesignatorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Third person pose (both hands on the binoculars, see {@link DesignatorHold}) and the animated item renderer. The
 * first person view is drawn by DesignatorHold on RenderHandEvent.
 */
public final class DesignatorItemExtensions implements IClientItemExtensions {
    public static final DesignatorItemExtensions INSTANCE = new DesignatorItemExtensions();

    private static final HumanoidModel.ArmPose HOLD = HumanoidModel.ArmPose.create("AIRSTRIKE_HOLD", true,
            DesignatorHold::poseArms);

    private DesignatorItemExtensions() {
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        // first person arms: the plain pose that DesignatorHold places them from
        if (DesignatorHold.drawingFirstPersonArms) return HumanoidModel.ArmPose.EMPTY;
        // in the off hand with something in the main hand: held in one hand, as an ordinary item
        if (hand == InteractionHand.OFF_HAND && !entity.getMainHandItem().isEmpty()) return null;
        return HOLD;
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return DesignatorRenderer.get();
    }
}
