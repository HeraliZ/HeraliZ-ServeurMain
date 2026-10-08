package fr.heraliz.armory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.item.PieceItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/** Vue a la premiere personne : la manche du haut et les gants apparaissent sur le bras du joueur (rendu translucide, verre). */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class FirstPersonArms {
    private FirstPersonArms() {}

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        boolean right = event.getArm() == HumanoidArm.RIGHT;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean hasChest = chest.getItem() instanceof PieceItem;
        var curios = CuriosApi.getCuriosInventory(player).resolve()
                .map(inv -> inv.findCurios(st -> PieceItem.is(st, "hands") || PieceItem.is(st, "vest"))).orElse(java.util.List.of());
        if (!hasChest && curios.isEmpty()) return;
        ModelPart pose = handPose(player, right);
        if (hasChest) {
            render(((PieceItem) chest.getItem()).def(), chest, player, right, pose, event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight());
        }
        for (var r : curios) {
            if (r.slotContext().visible()) {
                render(((PieceItem) r.stack().getItem()).def(), r.stack(), player, right, pose, event.getPoseStack(),
                        event.getMultiBufferSource(), event.getPackedLight());
            }
        }
    }

    /**
     * Pose exacte du bras vanilla a la premiere personne (PlayerRenderer.renderHand : setupAnim a zero puis xRot = 0 ;
     * le bras garde un leger penchement de ~6 degres). Sans elle, la manche est decalee de presque 1 pixel au poignet
     * et la peau du bras depasse.
     */
    private static ModelPart handPose(AbstractClientPlayer player, boolean right) {
        if (Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer) {
            PlayerModel<AbstractClientPlayer> pm = renderer.getModel();
            pm.attackTime = 0.0F;
            pm.crouching = false;
            pm.swimAmount = 0.0F;
            pm.rightArmPose = HumanoidModel.ArmPose.EMPTY;
            pm.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            pm.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
            ModelPart arm = right ? pm.rightArm : pm.leftArm;
            arm.xRot = 0.0F;
            return arm;
        }
        return null;
    }

    private static void render(PieceDef def, ItemStack item, LivingEntity player, boolean right, ModelPart pose, PoseStack stack,
                               MultiBufferSource buffers, int light) {
        ArmorModels.Variants v = ArmorModels.INSTANCE.get(def);
        if (v == null) return;
        HumanoidModel<LivingEntity> model = ArmorModels.INSTANCE.modelFor(def, player);
        ModelPart arm = right ? model.rightArm : model.leftArm;
        if (pose != null) arm.copyFrom(pose);
        else arm.resetPose();
        arm.visible = true;
        VertexConsumer vc = ItemRenderer.getArmorFoilBuffer(buffers, v.renderType(), false, item.hasFoil());
        arm.render(stack, vc, light, OverlayTexture.NO_OVERLAY);
        // surfaces libres du bras (0.9.0 : combinaisons)
        if (model instanceof MeshArmorModel mesh) mesh.renderPart(stack, vc, right ? "right_arm" : "left_arm", light, OverlayTexture.NO_OVERLAY);
    }
}
