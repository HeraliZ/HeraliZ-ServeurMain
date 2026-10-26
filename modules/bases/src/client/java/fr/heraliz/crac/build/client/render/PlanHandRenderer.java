package fr.heraliz.crac.build.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import fr.heraliz.crac.build.client.PlanState;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.world.Costs;
import fr.heraliz.crac.item.BuildingPlanItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * First person Building Plan, Rust style: a big worn blueprint held in both hands, showing the schematic, name and
 * cost of the selected piece. Hands and swing come from the vanilla two-handed map, but the sheet stays low and fixed so
 * it never hides the ground being aimed at.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlanHandRenderer {
    private static final ResourceLocation PAPER = new ResourceLocation("crac", "textures/item/plan/paper.png");
    private static final Map<PieceKind, ResourceLocation> SCHEMATIC = new EnumMap<>(PieceKind.class);

    /** Paper rectangle in map units (the vanilla map is -7..135); 4:3 sheet, bottom edge where the hands hold it. */
    private static final float X0 = -24, X1 = 152, Y0 = 9, Y1 = 141;
    private static final int STRIPS = 8;
    /** How far the left and right edges curl towards the viewer, in map units (-z faces the viewer here). */
    private static final float CURL = 4f;
    /** Fixed holding pose: tilt back (0 = facing the camera, 1 = flat), height and distance from the camera. The top of the
     *  sheet stays well below the crosshair, so the ghost of a piece aimed at a few blocks away is never hidden. */
    private static final float HOLD_TILT = 0.3f, HOLD_DY = -0.40f, HOLD_DZ = -0.95f;
    /** Hands moved outwards and up along the sheet from the vanilla map grip. */
    private static final float HAND_OUT = 0.07f, HAND_UP = 0.27f;

    /** Third person: both arms forward, holding the sheet open in front of the chest. */
    public static final HumanoidModel.ArmPose ARM_POSE = HumanoidModel.ArmPose.create("CRAC_BLUEPRINT", true, (model, entity, arm) -> {
        model.rightArm.xRot = -0.95F + model.head.xRot * 0.35F;
        model.rightArm.yRot = -0.38F;
        model.leftArm.xRot = -0.95F + model.head.xRot * 0.35F;
        model.leftArm.yRot = 0.38F;
    });

    private PlanHandRenderer() {
    }

    private static ResourceLocation schematic(PieceKind kind) {
        return SCHEMATIC.computeIfAbsent(kind, k -> new ResourceLocation("crac", "textures/item/plan/schematic/" + k.id + ".png"));
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof BuildingPlanItem)) {
            return;
        }
        // both hands are on the plan: nothing is drawn for the off hand
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        renderTwoHanded(mc, player, pose, event.getMultiBufferSource(), event.getPackedLight(), event.getEquipProgress(), event.getSwingProgress());
        pose.popPose();
    }

    /** ItemInHandRenderer#renderTwoHandedMap with a fixed tilt and the blueprint instead of the map. */
    private static void renderTwoHanded(Minecraft mc, AbstractClientPlayer player, PoseStack pose, MultiBufferSource buffers, int light,
                                        float equip, float swing) {
        float s = Mth.sqrt(swing);
        float lift = -0.2F * Mth.sin(swing * (float) Math.PI);
        float push = -0.4F * Mth.sin(s * (float) Math.PI);
        pose.translate(0.0F, -lift / 2.0F, push);
        // held low and fixed in front of the body (unlike the vanilla map, it does not rise when looking down)
        pose.translate(0.0F, 0.04F + equip * -1.2F + HOLD_TILT * -0.5F + HOLD_DY, -0.72F + HOLD_DZ);
        pose.mulPose(Axis.XP.rotationDegrees(HOLD_TILT * -85.0F));
        if (!player.isInvisible()) {
            for (HumanoidArm arm : HumanoidArm.values()) {
                float f = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
                pose.pushPose();
                // the hands grip the sides of the sheet, well above the hotbar (vanilla holds the map by its bottom corners)
                pose.translate(f * HAND_OUT, HAND_UP, 0.0F);
                pose.mulPose(Axis.YP.rotationDegrees(90.0F));
                hand(mc, player, pose, buffers, light, arm);
                pose.popPose();
            }
        }
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(s * (float) Math.PI) * 20.0F));
        pose.scale(2.0F, 2.0F, 2.0F);
        blueprint(mc, pose, buffers, light);
    }

    private static void hand(Minecraft mc, AbstractClientPlayer player, PoseStack pose, MultiBufferSource buffers, int light, HumanoidArm arm) {
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().<AbstractClientPlayer>getRenderer(player);
        pose.pushPose();
        float f = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        pose.mulPose(Axis.YP.rotationDegrees(92.0F));
        pose.mulPose(Axis.XP.rotationDegrees(45.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(f * -41.0F));
        pose.translate(f * 0.3F, -1.1F, 0.45F);
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(pose, buffers, light, player);
        } else {
            renderer.renderLeftHand(pose, buffers, light, player);
        }
        pose.popPose();
    }

    private static void blueprint(Minecraft mc, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(0.38F, 0.38F, 0.38F);
        pose.translate(-0.5F, -0.5F, 0.0F);
        pose.scale(1 / 128F, 1 / 128F, 1 / 128F);
        Matrix4f m = pose.last().pose();
        PieceKind kind = PlanState.selected();
        sheet(buffers.getBuffer(RenderType.text(PAPER)), m, light, 0f);
        sheet(buffers.getBuffer(RenderType.text(schematic(kind))), m, light, -0.4f);

        // title block (bottom right of the sheet): piece name and twig cost, in white ink
        Font font = mc.font;
        float tx = X0 + (X1 - X0) * (256 - 88) / 256f;
        float ty = Y0 + (Y1 - Y0) * (192 - 32) / 192f;
        // in front of the curled sheet over the whole width of the title block
        float z = Math.min(curl((tx - X0) / (X1 - X0)), curl((X1 - (X1 - X0) * 12 / 256f - X0) / (X1 - X0))) - 0.8f;
        pose.pushPose();
        pose.translate(tx, ty, z);
        pose.scale(0.62F, 0.62F, 0.62F);
        Matrix4f tm = pose.last().pose();
        font.drawInBatch(Component.translatable("piece.crac." + kind.id).getString().toUpperCase(), 0, 0, 0xFFE8F4FF, false, tm, buffers,
            Font.DisplayMode.NORMAL, 0, light);
        font.drawInBatch(Costs.describe(Costs.place(kind)).getString(), 0, 16, 0xFFB8D8F4, false, tm, buffers,
            Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }

    /** Depth of the sheet at horizontal position u (0..1): the side edges curl towards the viewer. */
    private static float curl(float u) {
        float c = 2 * u - 1;
        return -CURL * c * c;
    }

    private static void sheet(VertexConsumer vc, Matrix4f m, int light, float dz) {
        for (int i = 0; i < STRIPS; i++) {
            float u0 = i / (float) STRIPS;
            float u1 = (i + 1) / (float) STRIPS;
            float xa = X0 + (X1 - X0) * u0;
            float xb = X0 + (X1 - X0) * u1;
            float za = curl(u0) + dz;
            float zb = curl(u1) + dz;
            vc.vertex(m, xa, Y1, za).color(255, 255, 255, 255).uv(u0, 1.0F).uv2(light).endVertex();
            vc.vertex(m, xb, Y1, zb).color(255, 255, 255, 255).uv(u1, 1.0F).uv2(light).endVertex();
            vc.vertex(m, xb, Y0, zb).color(255, 255, 255, 255).uv(u1, 0.0F).uv2(light).endVertex();
            vc.vertex(m, xa, Y0, za).color(255, 255, 255, 255).uv(u0, 0.0F).uv2(light).endVertex();
        }
    }
}
