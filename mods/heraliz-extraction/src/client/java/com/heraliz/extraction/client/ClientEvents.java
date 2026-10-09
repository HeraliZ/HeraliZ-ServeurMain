package com.heraliz.extraction.client;

import com.heraliz.extraction.HeralizExtraction;
import com.heraliz.extraction.item.ExtractionTerminalItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HeralizExtraction.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    /** Le joueur en cours de rendu a été descendu (à genoux) : il faut refermer le pushPose. */
    private static boolean kneelPushed;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        ClientState.renderingEntity = event.getEntity();
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        ClientState.renderingEntity = null;
    }

    /** Pendant la saisie : impossible de bouger ou sauter, et le joueur s'accroupit (caméra plus basse). */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!TerminalClientExtensions.isTyping(event.getEntity())) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = true;
    }

    /**
     * 3e personne : au lieu de l'accroupi vanilla (dos penché), le joueur est à genoux, le dos droit.
     * Les jambes sont posées par le TYPING ArmPose, ici on descend juste tout le corps.
     */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (TerminalClientExtensions.isTyping(event.getEntity())) {
            event.getRenderer().getModel().crouching = false;
            event.getPoseStack().pushPose();
            // l'accroupi vanilla descend déjà le rendu de 0.125 : on complète jusqu'à la hauteur à genoux
            float drop = KneelLegs.DROP - (event.getEntity().isCrouching() ? 0.125F : 0.0F);
            event.getPoseStack().translate(0.0F, -drop, 0.0F);
            kneelPushed = true;
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (kneelPushed) {
            event.getPoseStack().popPose();
            kneelPushed = false;
        }
    }

    /** 1re personne : le PC ouvert devant soi, la main droite le tient, la main gauche tape. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !TerminalClientExtensions.isTyping(player)) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != player.getUsedItemHand()) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float partial = event.getPartialTick();
        float useTicks = player.getTicksUsingItem() + partial;
        float time = player.tickCount + partial;
        // le PC "monte" dans le champ de vision au début de la saisie
        float lower = 1.0F - Mth.clamp(useTicks / 6.0F, 0.0F, 1.0F);

        pose.pushPose();
        TerminalPoses.applyFirstPersonTransform(pose, lower);
        TerminalModel.render(pose, buffers, light, OverlayTexture.NO_OVERLAY, TerminalModel.State.HELD,
                TerminalScreen.typing(useTicks, time, ExtractionTerminalItem.launchError(player.level(), player)));
        pose.popPose();

        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ResourceLocation skin = player.getSkinTextureLocation();
        renderHand(pose, buffers, light, skin, model.rightArm, model.rightSleeve, true, time, lower);
        renderHand(pose, buffers, light, skin, model.leftArm, model.leftSleeve, false, time, lower);
    }

    private static void renderHand(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation skin,
                                   ModelPart arm, ModelPart sleeve, boolean right, float time, float lower) {
        // on dessine le bras "à plat" : la position est entièrement donnée par applyFirstPersonHand
        float x = arm.x, y = arm.y, z = arm.z, xr = arm.xRot, yr = arm.yRot, zr = arm.zRot;
        arm.x = arm.y = arm.z = 0.0F;
        arm.xRot = arm.yRot = arm.zRot = 0.0F;
        sleeve.copyFrom(arm);

        pose.pushPose();
        TerminalPoses.applyFirstPersonHand(pose, right, time, lower);
        arm.render(pose, buffers.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
        sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();

        arm.x = x;
        arm.y = y;
        arm.z = z;
        arm.xRot = xr;
        arm.yRot = yr;
        arm.zRot = zr;
        sleeve.copyFrom(arm);
    }
}
