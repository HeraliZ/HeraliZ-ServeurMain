package com.caracrea.client.voice;

import com.caracrea.CaraCrea;
import com.caracrea.CaraCreaConfig;
import com.caracrea.client.CompositeSkins;
import com.caracrea.network.CaraCreaNetwork.GroupInfo;
import com.caracrea.voice.VoiceActivity;
import com.caracrea.voice.net.VoiceProtocol;
import com.mojang.blaze3d.systems.RenderSystem;
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
     * Icône du mode de voix (27/11, retour du propriétaire : « une icône de micro avec 1 barre de chaque côté chuchoter,
     * deux parler, 3 crier, plus propre que le mot » ; maquette validée {@code maquettes/icone_allonge/voix_micro_27nov.png}) :
     * 18 x 11 unités, textures blanches teintées, écrites par {@code modules/voix/outils/icone_voix.py}.
     */
    private static final int ICONE_L = 18, ICONE_H = 11, TEX_L = 72, TEX_H = 44;
    private static final ResourceLocation MICRO = icone("micro"), BARRE = icone("barre");
    private static final ResourceLocation[] ONDES = {icone("ondes_1"), icone("ondes_2"), icone("ondes_3")};

    private static ResourceLocation icone(String nom) {
        return new ResourceLocation(CaraCrea.MOD_ID, "textures/gui/voix/" + nom + ".png");
    }

    /**
     * Votre état (27/11) : toujours le micro et ses ondes (1 chuchoter, 2 parler, 3 crier ; couleur du mode) ; le micro
     * prend la couleur du mode quand vous parlez, rouge et barré quand il est coupé (gris si introuvable) ; puis le
     * haut-parleur barré quand vous avez coupé tout le monde. Chat vocal coupé : seulement le haut-parleur barré. Placé
     * au-dessus de l'icône « s'allonger » de Tactique (HudAllonge, haut à 18 unités du bas) : avant, le mot « Crier »
     * s'écrivait par-dessus.
     */
    private static void renderSelf(GuiGraphics g, Font font, int height, boolean adminMuted) {
        boolean disabled = !CaraCreaConfig.CLIENT.voiceChat.get();
        int x = 6;
        int ySon = height - 38;
        if (disabled) {
            g.fill(x - 2, ySon - 2, x + 18, ySon + 18, 0x80101418);
            g.blit(TabIcons.TEXTURE, x, ySon, 16, 16, TabIcons.OFF * 16, 0, 16, 16, TabIcons.COUNT * 16, 16);
            return;
        }
        boolean muted = CaraCreaConfig.CLIENT.muted.get() || adminMuted;
        boolean unavailable = VoiceClient.microphoneUnavailable();
        boolean talking = VoiceClient.transmitting();
        int mode = VoiceClient.voiceMode();
        int modeColor = mode == VoiceProtocol.MODE_WHISPER ? 0x6FB6F0 : mode == VoiceProtocol.MODE_SHOUT ? 0xF0A24A : 0x6FD98A;
        int n = mode == VoiceProtocol.MODE_WHISPER ? 1 : mode == VoiceProtocol.MODE_SHOUT ? 3 : 2;
        boolean coupe = muted || unavailable;
        int corps = coupe ? (unavailable ? 0x9A9A9A : 0xE0605A) : talking ? modeColor : 0xC8CCD0;
        int ondes = coupe ? 0x9A9A9A : modeColor;
        float aOndes = coupe ? 0.35f : talking ? 1f : 0.8f;
        int y = height - 34;
        // ombre d'un pixel d'écran, puis l'icône
        float px = 1f / (float) Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        g.pose().pushPose();
        g.pose().translate(px, px, 0f);
        dessiner(g, MICRO, x, y, 0x000000, 0.45f);
        dessiner(g, ONDES[n - 1], x, y, 0x000000, 0.45f * aOndes);
        if (coupe) dessiner(g, BARRE, x, y, 0x000000, 0.45f);
        g.pose().popPose();
        dessiner(g, ONDES[n - 1], x, y, ondes, aOndes);
        dessiner(g, MICRO, x, y, corps, 1f);
        if (coupe) dessiner(g, BARRE, x, y, 0xE0605A, 1f);
        x += ICONE_L + 4;
        if (VoiceClient.everyoneMuted()) {
            // Tout le monde coupé : le même haut-parleur barré que « chat vocal coupé », à côté du micro.
            g.fill(x - 2, ySon - 2, x + 18, ySon + 18, 0x80101418);
            g.blit(TabIcons.TEXTURE, x, ySon, 16, 16, TabIcons.OFF * 16, 0, 16, 16, TabIcons.COUNT * 16, 16);
        }
    }

    /** Une couche de l'icône, teintée {@code rgb}, opacité {@code a}, lissée (filtre linéaire de la texture). */
    private static void dessiner(GuiGraphics g, ResourceLocation tex, int x, int y, int rgb, float a) {
        Minecraft.getInstance().getTextureManager().getTexture(tex).setFilter(true, false);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, a);
        g.blit(tex, x, y, ICONE_L, ICONE_H, 0, 0, TEX_L, TEX_H, TEX_L, TEX_H);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
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
