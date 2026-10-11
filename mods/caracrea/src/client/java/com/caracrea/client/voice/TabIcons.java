package com.caracrea.client.voice;

import com.caracrea.CaraCrea;
import com.caracrea.voice.VoiceActivity;
import com.caracrea.voice.net.VoiceProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The voice icons: a sheet of six 16 x 16 pictures ({@code textures/font/voix.png}, made by
 * {@code tools/caracrea/gen_icones_voix.py}), also a font ({@code caracrea:voix}, characters U+E100 to U+E105) so the
 * player list can show them right after the names (see {@code mixin.PlayerTabOverlayMixin}).
 */
public final class TabIcons {
    public static final int TALK = 0;
    public static final int WHISPER = 1;
    public static final int GROUP = 2;
    public static final int MUTED = 3;
    public static final int OFF = 4;
    public static final int ADMIN = 5;
    public static final int COUNT = 6;
    public static final ResourceLocation TEXTURE = new ResourceLocation(CaraCrea.MOD_ID, "textures/font/voix.png");
    private static final ResourceLocation FONT = new ResourceLocation(CaraCrea.MOD_ID, "voix");
    private static final Component[] GLYPHS = new Component[COUNT];

    static {
        for (int i = 0; i < COUNT; i++) {
            GLYPHS[i] = Component.literal(" ").append(Component.literal(String.valueOf((char) (0xE100 + i))).withStyle(s -> s.withFont(FONT)));
        }
    }

    private TabIcons() {
    }

    /** Which icon a player gets, or -1: muted by an operator, voice chat off, own mic muted, talking. */
    public static int state(UUID player) {
        if (VoiceClient.adminMuted(player)) {
            return ADMIN;
        }
        if (VoiceClient.disabled(player)) {
            return OFF;
        }
        if (VoiceClient.selfMuted(player)) {
            return MUTED;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean me = mc.player != null && player.equals(mc.player.getUUID());
        if (!VoiceActivity.isTalking(player) && !(me && VoiceClient.transmitting())) {
            return -1;
        }
        // Un cri garde l'icône « parle » (pas d'icône à part).
        if (me ? VoiceClient.voiceMode() == VoiceProtocol.MODE_WHISPER : VoiceActivity.isWhispering(player)) {
            return WHISPER;
        }
        int group = VoiceClient.groupOf(player);
        return group != 0 && mc.player != null && group == VoiceClient.groupOf(mc.player.getUUID()) ? GROUP : TALK;
    }

    /** The icon to append to a name in the player list, or null. */
    @Nullable
    public static Component suffix(UUID player) {
        if (player == null || !VoiceClient.serverSupported() || VoiceClient.iconsHidden()) {
            return null;
        }
        int state = state(player);
        return state < 0 ? null : GLYPHS[state];
    }
}
