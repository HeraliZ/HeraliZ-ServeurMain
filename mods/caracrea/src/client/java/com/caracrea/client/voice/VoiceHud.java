package com.caracrea.client.voice;

import com.caracrea.CaraCreaConfig;
import com.caracrea.client.CompositeSkins;
import com.caracrea.network.CaraCreaNetwork.GroupInfo;
import com.caracrea.voice.VoiceActivity;
import com.caracrea.voice.net.VoiceProtocol;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the voice chat shows: a speaker above the head of whoever talks
 * (blue when whispering, green for your group, crossed out for players who
 * turned their voice chat off), your own microphone state at the bottom
 * left (avec le haut-parleur barré quand vous avez coupé tout le monde, et le
 * mode chuchoter / crier), the members of your group at the top left, and a
 * recording mark.
 */
public final class VoiceHud {
    private VoiceHud() {
    }

    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("voice", (gui, graphics, partialTick, width, height) -> render(graphics, width, height));
    }

    // ------------------------------------------------------------------ icons

    /** Which icon a player gets above the head, or -1 (their own muted microphone shows only in the player list). */
    private static int iconFor(Player player) {
        int state = TabIcons.state(player.getUUID());
        return state == TabIcons.MUTED ? -1 : state;
    }

    /** Speakers above the heads of the players around, facing the camera like name tags. */
    public static void renderIcons(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!VoiceClient.serverSupported() || VoiceClient.iconsHidden() || mc.options.hideGui || mc.player == null || mc.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.text(TabIcons.TEXTURE);
        float partial = event.getPartialTick();
        boolean any = false;
        for (Player player : mc.level.players()) {
            if (player == mc.player && mc.options.getCameraType().isFirstPerson() || player.isInvisibleTo(mc.player)
                    || player.distanceToSqr(cam) > 48 * 48) {
                continue;
            }
            int icon = iconFor(player);
            if (icon < 0) {
                continue;
            }
            Vec3 at = player.getPosition(partial);
            double lift = player.getBbHeight() + (player.shouldShowName() || player != mc.player ? 0.85 : 0.5);
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y + lift - cam.y, at.z - cam.z);
            pose.mulPose(camera.rotation());
            pose.scale(-0.025F, -0.025F, 0.025F);
            Matrix4f matrix = pose.last().pose();
            VertexConsumer consumer = buffers.getBuffer(type);
            float u0 = icon / (float) TabIcons.COUNT;
            float u1 = (icon + 1) / (float) TabIcons.COUNT;
            int light = 0xF000F0;
            consumer.vertex(matrix, -8, -8, 0).color(255, 255, 255, 255).uv(u0, 0).uv2(light).endVertex();
            consumer.vertex(matrix, -8, 8, 0).color(255, 255, 255, 255).uv(u0, 1).uv2(light).endVertex();
            consumer.vertex(matrix, 8, 8, 0).color(255, 255, 255, 255).uv(u1, 1).uv2(light).endVertex();
            consumer.vertex(matrix, 8, -8, 0).color(255, 255, 255, 255).uv(u1, 0).uv2(light).endVertex();
            pose.popPose();
            any = true;
        }
        if (any) {
            buffers.endBatch(type);
        }
    }

    // ------------------------------------------------------------------ screen

    private static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!VoiceClient.serverSupported() || mc.options.hideGui || mc.player == null) {
            return;
        }
        boolean adminMuted = VoiceClient.adminMuted(mc.player.getUUID());
        renderGroup(g, mc);
        renderSelf(g, mc.font, height, adminMuted);
        if (VoiceRecorder.recording()) {
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            g.fill(width - 46, 6, width - 6, 18, 0x90101418);
            if (blink) {
                g.fill(width - 42, 9, width - 36, 15, 0xFFE0453F);
            }
            g.drawString(mc.font, "REC", width - 32, 8, 0xFFFFFFFF, false);
        }
    }

    /**
     * Votre état, sur une ligne : le micro quand vous parlez (vert, bleu en chuchotant, orange en criant), coupé (rouge,
     * barré) ou introuvable (gris) ; puis le haut-parleur barré quand vous avez coupé tout le monde ; puis le mode de voix
     * (« Chuchoter », « Crier ») tant qu'il n'est pas « Parler ». Chat vocal coupé : seulement le haut-parleur barré.
     */
    private static void renderSelf(GuiGraphics g, Font font, int height, boolean adminMuted) {
        boolean disabled = !CaraCreaConfig.CLIENT.voiceChat.get();
        int x = 6;
        int y = height - 24;
        if (disabled) {
            g.fill(x - 2, y - 2, x + 18, y + 18, 0x80101418);
            g.blit(TabIcons.TEXTURE, x, y, 16, 16, TabIcons.OFF * 16, 0, 16, 16, TabIcons.COUNT * 16, 16);
            return;
        }
        boolean muted = CaraCreaConfig.CLIENT.muted.get() || adminMuted;
        boolean unavailable = VoiceClient.microphoneUnavailable();
        boolean talking = VoiceClient.transmitting();
        int mode = VoiceClient.voiceMode();
        int modeColor = mode == VoiceProtocol.MODE_WHISPER ? 0xFF6FB6F0 : mode == VoiceProtocol.MODE_SHOUT ? 0xFFF0A24A : 0xFF6FD98A;
        if (muted || unavailable || talking) {
            renderMicrophone(g, x, y, unavailable ? 0xFF9A9A9A : muted ? 0xFFE0605A : modeColor, muted || unavailable);
            x += 18;
        }
        if (VoiceClient.everyoneMuted()) {
            // Tout le monde coupé : le même haut-parleur barré que « chat vocal coupé », à côté du micro.
            g.fill(x - 2, y - 2, x + 18, y + 18, 0x80101418);
            g.blit(TabIcons.TEXTURE, x, y, 16, 16, TabIcons.OFF * 16, 0, 16, 16, TabIcons.COUNT * 16, 16);
            x += 22;
        }
        if (mode != VoiceProtocol.MODE_TALK) {
            Component label = Component.translatable(VoiceClient.modeKey(mode));
            g.fill(x - 2, y + 2, x + font.width(label) + 3, y + 14, 0x80101418);
            g.drawString(font, label, x + 1, y + 4, modeColor, false);
        }
    }

    /** Le petit micro, barré de rouge quand il est coupé ou introuvable. */
    private static void renderMicrophone(GuiGraphics g, int x, int y, int color, boolean crossed) {
        g.fill(x - 2, y - 2, x + 14, y + 18, 0x80101418);
        // A small microphone: capsule, cradle, stem and foot.
        g.fill(x + 4, y, x + 8, y + 9, color);
        g.fill(x + 3, y + 1, x + 9, y + 8, color);
        g.fill(x + 1, y + 5, x + 2, y + 10, color);
        g.fill(x + 10, y + 5, x + 11, y + 10, color);
        g.fill(x + 2, y + 10, x + 10, y + 11, color);
        g.fill(x + 5, y + 11, x + 7, y + 14, color);
        g.fill(x + 3, y + 14, x + 9, y + 15, color);
        if (crossed) {
            for (int i = 0; i < 14; i++) {
                g.fill(x - 1 + i, y + i, x + 1 + i, y + i + 2, 0xFFE0605A);
            }
        }
    }

    /** The members of your group, with a speaker next to whoever talks. Returns the y below the list. */
    private static int renderGroup(GuiGraphics g, Minecraft mc) {
        UUID me = mc.player.getUUID();
        int group = VoiceClient.groupOf(me);
        GroupInfo info = VoiceClient.group(group);
        if (group == 0 || info == null || mc.getConnection() == null) {
            return 6;
        }
        List<PlayerInfo> members = new ArrayList<>();
        for (PlayerInfo player : mc.getConnection().getOnlinePlayers()) {
            if (VoiceClient.groupOf(player.getProfile().getId()) == group) {
                members.add(player);
            }
        }
        int x = 6;
        int y = 6;
        g.drawString(mc.font, Component.literal(info.name()), x, y, 0xFF7BE08F, true);
        y += 12;
        for (PlayerInfo member : members) {
            UUID uuid = member.getProfile().getId();
            boolean talking = VoiceActivity.isTalking(uuid) || uuid.equals(me) && VoiceClient.transmitting();
            g.fill(x - 1, y - 1, x + 11, y + 11, talking ? 0xFF7BE08F : 0x80101418);
            ResourceLocation skin = CompositeSkins.skinFor(uuid);
            // Face and hat from the painted skin (vanilla layout at 4x).
            g.blit(skin, x + 1, y + 1, 8, 8, 32, 32, 32, 32, 256, 256);
            g.blit(skin, x + 1, y + 1, 8, 8, 160, 32, 32, 32, 256, 256);
            g.drawString(mc.font, member.getProfile().getName(), x + 15, y + 1, talking ? 0xFFFFFFFF : 0xFFB4BBC6, true);
            int state = TabIcons.state(uuid);
            if (state >= TabIcons.MUTED) {
                g.blit(TabIcons.TEXTURE, x + 17 + mc.font.width(member.getProfile().getName()), y, 10, 10, state * 16, 0, 16, 16,
                        TabIcons.COUNT * 16, 16);
            }
            y += 13;
        }
        return y + 4;
    }
}
