package com.caracrea.client;

import com.caracrea.client.gui.CharacterCreatorScreen;
import com.caracrea.client.voice.VoiceClient;
import com.caracrea.network.CaraCreaNetwork;
import com.caracrea.network.CaraCreaNetwork.Entry;
import com.caracrea.network.CaraCreaNetwork.OpenCreator;
import com.caracrea.network.CaraCreaNetwork.SyncAppearances;
import net.minecraft.client.Minecraft;

/** Client side of the packets (kept out of the network class so the server never loads client code). */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void sync(SyncAppearances packet) {
        for (Entry entry : packet.entries()) {
            ClientAppearances.put(entry.player(), entry.appearance());
        }
    }

    public static void voiceConfig(CaraCreaNetwork.VoiceConfig packet) {
        VoiceClient.configure(packet);
    }

    public static void roster(CaraCreaNetwork.VoiceRoster packet) {
        VoiceClient.roster(packet);
    }

    /** Network thread: a voice frame through the game connection (the UDP channel is blocked or not offered). */
    public static void voice(CaraCreaNetwork.VoiceDown packet) {
        VoiceClient.receiveTcp(packet);
    }

    /** {@code /voix}: the voice chat settings. */
    public static void openVoiceSettings(CaraCreaNetwork.OpenVoiceSettings packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !(mc.screen instanceof com.caracrea.client.gui.VoiceSettingsScreen)) {
            mc.setScreen(new com.caracrea.client.gui.VoiceSettingsScreen(null));
        }
    }

    /** The server closes the creator (the player became busy: match, downed, dead). */
    public static void closeCreator(CaraCreaNetwork.CloseCreator packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CharacterCreatorScreen creator) {
            creator.forceClose();
        }
    }

    public static void openCreator(OpenCreator packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mc.screen instanceof CharacterCreatorScreen) {
            return;
        }
        mc.setScreen(new CharacterCreatorScreen(ClientAppearances.resolve(mc.player.getUUID()), packet.firstTime()));
    }
}
