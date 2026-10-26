package com.heraliz.extraction.client;

import com.heraliz.extraction.item.ExtractionTerminalItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

/** Rendu custom du terminal en main + pose des bras (3e personne). */
public final class TerminalClientExtensions implements IClientItemExtensions {
    public static final TerminalClientExtensions INSTANCE = new TerminalClientExtensions();

    /** PC tenu ouvert : main droite dessous, main gauche au-dessus du clavier. */
    private static final HumanoidModel.ArmPose HOLDING = HumanoidModel.ArmPose.create("HERALIZ_TERMINAL_HOLD", true,
            (model, entity, arm) -> TerminalPoses.applyArms(model.rightArm, model.leftArm, model.head.xRot,
                    entity.tickCount + Minecraft.getInstance().getFrameTime(), false));

    /** Pendant la saisie : à genoux, la main gauche tape sur le clavier. */
    private static final HumanoidModel.ArmPose TYPING = HumanoidModel.ArmPose.create("HERALIZ_TERMINAL_TYPING", true,
            (model, entity, arm) -> {
                TerminalPoses.applyArms(model.rightArm, model.leftArm, model.head.xRot,
                        entity.tickCount + Minecraft.getInstance().getFrameTime(), true);
                TerminalPoses.applyKneelLegs(model.rightLeg, model.leftLeg);
                if (model instanceof net.minecraft.client.model.PlayerModel<?> player) {
                    player.rightPants.visible = false;
                    player.leftPants.visible = false;
                }
            });

    @Nullable
    private TerminalItemRenderer renderer;

    private TerminalClientExtensions() {
    }

    public static boolean isTyping(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUseItem().getItem() instanceof ExtractionTerminalItem;
    }

    /** Le PC est en main principale : il est alors dessiné devant le torse par {@link TerminalHeldLayer}. */
    public static boolean isHeldInMainHand(LivingEntity entity) {
        return entity.getMainHandItem().getItem() instanceof ExtractionTerminalItem;
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (this.renderer == null) {
            this.renderer = new TerminalItemRenderer();
        }
        return this.renderer;
    }

    @Nullable
    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (hand != InteractionHand.MAIN_HAND) {
            return null;
        }
        return isTyping(entity) ? TYPING : HOLDING;
    }
}
