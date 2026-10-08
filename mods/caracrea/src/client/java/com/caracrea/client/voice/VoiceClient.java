package com.caracrea.client.voice;

import com.caracrea.CaraCreaConfig;
import com.caracrea.client.gui.CharacterCreatorScreen;
import com.caracrea.client.gui.VoiceGroupsScreen;
import com.caracrea.client.gui.VoiceSettingsScreen;
import com.caracrea.client.gui.VoiceVolumesScreen;
import com.caracrea.network.CaraCreaNetwork;
import com.caracrea.network.CaraCreaNetwork.GroupInfo;
import com.caracrea.network.CaraCreaNetwork.PlayerVoice;
import com.caracrea.voice.VoiceActivity;
import com.caracrea.voice.VoiceGate;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.ModList;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The built-in proximity voice chat, client side: keys, settings, the
 * microphone and playback threads, the transport (dedicated UDP channel or
 * the game connection), voice states and groups. Runs only when the server
 * has it on. The game thread only reads keys and copies positions once per
 * tick (plus, with the walls option, one ray per speaker who talks); capture,
 * processing, encoding, sending, receiving, decoding and output happen on the
 * voice threads.
 */
public final class VoiceClient {
    public static final String CATEGORY = "key.categories.caracrea.voice";
    /**
     * Default keys chosen for HeraliZ, where V, G and H are taken by TaCZ (zoom and melee, fire mode, inspect), Curios,
     * TCTcore and the vehicles, N by the bases and M by Xaero's map, I / = / Backspace by Reanimation, ² / 0 by the
     * emotes, J by the tags, K by the clans, B by the backpack: Caps Lock to talk, the key right of L to mute ("M" on an
     * AZERTY keyboard), the key right of 0 for the settings (")" on AZERTY). The rest are buttons of the settings
     * screen and stay unbound. They do nothing while the server does not run our voice chat.
     */
    public static final KeyMapping PUSH_TO_TALK = key("talk", GLFW.GLFW_KEY_CAPS_LOCK);
    public static final KeyMapping WHISPER = key("whisper", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping MUTE = key("mute", GLFW.GLFW_KEY_SEMICOLON);
    public static final KeyMapping DISABLE = key("disable", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping SETTINGS = key("settings", GLFW.GLFW_KEY_MINUS);
    public static final KeyMapping GROUPS = key("groups", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping HIDE_ICONS = key("hide_icons", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping RECORD = key("record", GLFW.GLFW_KEY_UNKNOWN);

    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key.caracrea.voice." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    public static List<KeyMapping> keys() {
        return List.of(PUSH_TO_TALK, WHISPER, MUTE, DISABLE, SETTINGS, GROUPS, HIDE_ICONS, RECORD);
    }

    /** What the audio threads need from the settings, copied every tick. */
    record Settings(boolean pushToTalk, float threshold, float micGain, float volume, boolean canTalk, boolean testing,
                    int denoise, boolean automaticGain, boolean automaticActivation, String outputDevice, boolean hrtf,
                    int jitterMs, int bitrate) {
    }

    /**
     * Where the listener and the players around are, copied every tick for the playback thread: the camera position
     * and its axes (right, up, forward), the players' eye positions (x, y, z, occlusion 0..1) and names.
     */
    record Listener(double x, double y, double z, float[] axes, float range, float whisperRange,
                    Map<UUID, double[]> positions, Map<UUID, String> names) {
        /** A world offset (speaker minus listener) in the listener's space: x right, y up, z behind (OpenAL's axes). */
        void toLocal(double dx, double dy, double dz, float[] out) {
            out[0] = (float) (dx * axes[0] + dy * axes[1] + dz * axes[2]);
            out[1] = (float) (dx * axes[3] + dy * axes[4] + dz * axes[5]);
            out[2] = (float) -(dx * axes[6] + dy * axes[7] + dz * axes[8]);
        }
    }

    private static final float[] NO_AXES = {1, 0, 0, 0, 1, 0, 0, 0, -1};
    private static volatile Settings settings = new Settings(true, 0.35F, 1, 1, false, false, 2, true, true, "", false, 0, 24000);
    private static volatile Listener listener = new Listener(0, 0, 0, NO_AXES, 48, 24, Map.of(), Map.of());
    private static volatile boolean pushToTalkDown;
    private static volatile boolean whisperDown;
    private static volatile CaraCreaNetwork.VoiceConfig config = CaraCreaNetwork.VoiceConfig.off();
    private static volatile VoicePlayback playback;
    private static volatile VoiceTransport transport;
    private static volatile UUID self;
    private static volatile String selfName;
    /** Voice number (in the voice packets) to player. */
    private static volatile UUID[] byVoiceId = new UUID[0];
    private static Microphone microphone;
    private static volatile boolean testing;
    private static long testingSince;
    private static boolean transportForced;
    private static String sentState;
    // Roster from the server: groups and the voice state of everyone.
    private static List<GroupInfo> groups = List.of();
    private static Map<UUID, PlayerVoice> states = Map.of();
    // Statistics for the settings screen.
    public static final AtomicLong RECEIVED = new AtomicLong();
    static final AtomicLong TCP_SENT = new AtomicLong();
    static final AtomicLong TCP_RECEIVED = new AtomicLong();
    static final AtomicLong TCP_BYTES_SENT = new AtomicLong();
    static final AtomicLong TCP_BYTES_RECEIVED = new AtomicLong();
    /** Packet counter of the current UDP session, kept across reconnections. */
    private static AtomicLong udpCounter = new AtomicLong();
    private static long rateTime;
    private static long rateUp;
    private static long rateDown;
    private static float kbpsUp;
    private static float kbpsDown;

    private VoiceClient() {
    }

    // ------------------------------------------------------------- state

    /** Simple Voice Chat installed on this client (it carries the voices only when the server has it too). */
    public static boolean otherVoiceChat() {
        return ModList.get().isLoaded("voicechat");
    }

    /** Whether the server runs our voice chat. */
    public static boolean serverSupported() {
        return config.enabled();
    }

    /**
     * Whether the voice chat runs (and may open the microphone and the sound output): on for this server, not turned
     * off by the player, and Simple Voice Chat not connected here (it then owns the microphone and the speakers).
     */
    public static boolean active() {
        return VoiceGate.devicesAllowed(serverSupported(), CaraCreaConfig.CLIENT.voiceChat.get(), VoiceGate.svcConnected);
    }

    /** Simple Voice Chat carries the voices here: our screens only show where to find its settings. */
    public static boolean svcActive() {
        return VoiceGate.svcConnected || !serverSupported() && otherVoiceChat();
    }

    /** Server thread of the client (main thread): a new voice configuration and session. */
    public static void configure(CaraCreaNetwork.VoiceConfig next) {
        if (next.session() != config.session()) {
            udpCounter = new AtomicLong();
        }
        config = next;
        closeTransport();
        sentState = null; // tell the server our state again
        if (next.enabled()) {
            transportForced = CaraCreaConfig.CLIENT.transport.get() == CaraCreaConfig.Transport.TCP;
            transport = VoiceTransport.open(next, transportForced, udpCounter);
        }
    }

    private static void closeTransport() {
        VoiceTransport current = transport;
        transport = null;
        if (current != null) {
            current.close();
        }
    }

    public static void roster(CaraCreaNetwork.VoiceRoster roster) {
        groups = List.copyOf(roster.groups());
        Map<UUID, PlayerVoice> map = new HashMap<>();
        int max = 0;
        for (PlayerVoice voice : roster.players()) {
            map.put(voice.player(), voice);
            max = Math.max(max, voice.voiceId());
        }
        UUID[] ids = new UUID[Math.min(65536, max + 1)];
        for (PlayerVoice voice : roster.players()) {
            if (voice.voiceId() >= 0 && voice.voiceId() < ids.length) {
                ids[voice.voiceId()] = voice.player();
            }
        }
        states = map;
        byVoiceId = ids;
    }

    /** The player behind a voice number, or null (any thread). */
    static UUID player(int voiceId) {
        UUID[] ids = byVoiceId;
        return voiceId > 0 && voiceId < ids.length ? ids[voiceId] : null;
    }

    public static boolean groupsAllowed() {
        return config.groups() && serverSupported();
    }

    public static boolean recordingAllowed() {
        return config.recording() && serverSupported();
    }

    public static List<GroupInfo> groups() {
        return groups;
    }

    /** The voice group a player is in (0 = none). */
    public static int groupOf(UUID player) {
        PlayerVoice voice = states.get(player);
        return voice == null ? 0 : voice.group();
    }

    public static GroupInfo group(int id) {
        for (GroupInfo group : groups) {
            if (group.id() == id) {
                return group;
            }
        }
        return null;
    }

    /** Whether a player has turned their voice chat off. */
    public static boolean disabled(UUID player) {
        PlayerVoice voice = states.get(player);
        return voice != null && voice.disabled();
    }

    /** Whether a player muted their own microphone. */
    public static boolean selfMuted(UUID player) {
        PlayerVoice voice = states.get(player);
        return voice != null && voice.muted();
    }

    /** Whether an operator (or an addon) muted a player. */
    public static boolean adminMuted(UUID player) {
        PlayerVoice voice = states.get(player);
        return voice != null && voice.adminMuted();
    }

    public static boolean iconsHidden() {
        return !CaraCreaConfig.CLIENT.icons.get();
    }

    static Settings settings() {
        return settings;
    }

    static Listener listener() {
        return listener;
    }

    static VoiceTransport transport() {
        return transport;
    }

    static boolean pushToTalkDown() {
        return pushToTalkDown;
    }

    static boolean whispering() {
        return whisperDown;
    }

    public static boolean whisperHeld() {
        return whisperDown;
    }

    static UUID self() {
        return self;
    }

    static String selfName() {
        return selfName;
    }

    /** The playback thread, or null when the voice chat is not running. */
    static VoicePlayback playback() {
        return playback;
    }

    /** Microphone level 0..1, for the meter in the settings. */
    public static float micLevel() {
        Microphone mic = microphone;
        return mic == null ? 0 : mic.level;
    }

    /** Microphone loudness and background, in dBFS, for the microphone test. */
    public static float micLevelDb() {
        Microphone mic = microphone;
        return mic == null ? -100 : mic.levelDb;
    }

    public static float micFloorDb() {
        Microphone mic = microphone;
        return mic == null ? -100 : mic.floorDb;
    }

    /** Whether automatic voice activation hears a voice right now. */
    public static boolean voiceDetected() {
        Microphone mic = microphone;
        return mic != null && mic.detected;
    }

    public static boolean microphoneUnavailable() {
        Microphone mic = microphone;
        return mic != null && mic.unavailable;
    }

    /** Which capture is used ("OpenAL", "Java Sound", ""), for the settings. */
    public static String microphoneKind() {
        Microphone mic = microphone;
        return mic == null ? "" : mic.kind;
    }

    public static boolean transmitting() {
        Microphone mic = microphone;
        return mic != null && mic.transmitting;
    }

    /** Kind of sound output in use ("3D (HRTF)", "3D", "stereo", "none"), empty when not running. */
    public static String outputKind() {
        VoicePlayback current = playback;
        return current == null ? "" : current.output;
    }

    /** "UDP", "CONNECTING", "TCP", or "" when the voice chat does not run here. */
    public static String transportState() {
        VoiceTransport current = transport;
        return current == null ? "" : current.state();
    }

    public static int rttMs() {
        VoiceTransport current = transport;
        return current == null ? -1 : current.rttMs();
    }

    /** Lost or late frames among the voices heard, per mille. */
    public static int lossPermille() {
        VoicePlayback current = playback;
        return current == null ? 0 : current.lossPermille();
    }

    /** Current jitter buffer delay of the voices heard (the largest), in ms. */
    public static int bufferMs() {
        VoicePlayback current = playback;
        return current == null ? 0 : current.bufferMs();
    }

    public static float kbpsUp() {
        return kbpsUp;
    }

    public static float kbpsDown() {
        return kbpsDown;
    }

    public static List<String> microphones() {
        return Microphone.devices();
    }

    public static List<String> speakers() {
        return VoiceOutput.devices();
    }

    /**
     * The settings screen plays the microphone back to the player instead of sending it. It stops when the screen
     * closes, on disconnection, if the microphone fails, and by itself after 30 seconds.
     */
    public static void setTesting(boolean on) {
        if (on && !active()) {
            on = false;
        }
        testing = on;
        testingSince = on ? System.nanoTime() : 0;
        if (!on) {
            VoicePlayback current = playback;
            if (current != null) {
                current.stopLoopback();
            }
        }
    }

    /** Stops the microphone test and the sound test (a voice screen closes, disconnection, error). */
    public static void stopTests() {
        setTesting(false);
        VoicePlayback current = playback;
        if (current != null) {
            current.stopTest();
        }
    }

    public static boolean testing() {
        return testing;
    }

    public static void testSound() {
        VoicePlayback current = playback;
        if (current != null) {
            current.testSound();
        }
    }

    // ------------------------------------------------------------- network

    /** UDP or network thread: a frame of another player's voice. */
    static void received(int speaker, int flags, int sequence, int dx, int dy, int dz, byte[] buf, int off, int len) {
        RECEIVED.incrementAndGet();
        VoicePlayback current = playback;
        UUID player = player(speaker);
        if (current != null && player != null) {
            current.receive(player, flags, sequence, dx, dy, dz, buf, off, len);
        }
    }

    /** Network thread: a frame through the game connection. */
    public static void receiveTcp(CaraCreaNetwork.VoiceDown packet) {
        TCP_RECEIVED.incrementAndGet();
        TCP_BYTES_RECEIVED.addAndGet(packet.data().length + 24L);
        received(packet.speaker(), packet.flags(), packet.sequence(), packet.dx(), packet.dy(), packet.dz(), packet.data(), 0,
                packet.data().length);
    }

    /** Sends a frame as if the microphone had (test bench: synthetic voices through the real transport). */
    public static void devSend(int sequence, int flags, byte[] opus, int length) {
        VoiceTransport current = transport;
        if (current != null) {
            current.send(sequence, flags, opus, length);
        }
    }

    /** UDP port offered by the server (0 = none). */
    public static int udpPort() {
        return config.udpPort();
    }

    public static long tcpSent() {
        return TCP_SENT.get();
    }

    public static long tcpReceived() {
        return TCP_RECEIVED.get();
    }

    public static void createGroup(String name, String password, int type) {
        CaraCreaNetwork.CHANNEL.sendToServer(new CaraCreaNetwork.GroupAction(CaraCreaNetwork.GroupAction.CREATE, 0, name, password, type));
    }

    public static void joinGroup(int id, String password) {
        CaraCreaNetwork.CHANNEL.sendToServer(new CaraCreaNetwork.GroupAction(CaraCreaNetwork.GroupAction.JOIN, id, "", password, 0));
    }

    public static void leaveGroup() {
        CaraCreaNetwork.CHANNEL.sendToServer(new CaraCreaNetwork.GroupAction(CaraCreaNetwork.GroupAction.LEAVE, 0, "", "", 0));
    }

    // ------------------------------------------------------------- tick

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            stop();
            return;
        }
        self = mc.player.getUUID();
        selfName = mc.player.getName().getString();
        boolean supported = serverSupported();
        CaraCreaConfig.Client c = CaraCreaConfig.CLIENT;
        boolean enabled = c.voiceChat.get();
        if (supported) {
            boolean forced = c.transport.get() == CaraCreaConfig.Transport.TCP;
            if (forced != transportForced) {
                transportForced = forced;
                closeTransport();
                transport = VoiceTransport.open(config, forced, udpCounter);
            }
            VoiceTransport current = transport;
            boolean udp = current != null && current.udpConnected();
            String state = !enabled + "|" + c.muted.get() + "|" + udp;
            if (!state.equals(sentState)) {
                sentState = state;
                CaraCreaNetwork.CHANNEL.sendToServer(new CaraCreaNetwork.VoiceStateUp(!enabled, c.muted.get(), udp));
            }
            if (current != null) {
                current.report(lossPermille(), bufferMs());
            }
            rates(current);
        }

        if (!supported) {
            // Not our voice chat on this server (off, or Simple Voice Chat carries the voices): its keys do nothing,
            // so they never open our screens on top of the other mod's.
            stop();
            for (KeyMapping key : keys()) {
                while (key.consumeClick()) {
                    // ignored
                }
            }
            return;
        }
        while (SETTINGS.consumeClick()) {
            mc.setScreen(new VoiceSettingsScreen(null));
        }
        while (DISABLE.consumeClick()) {
            c.voiceChat.set(!enabled);
            CaraCreaConfig.CLIENT_SPEC.save();
            mc.player.displayClientMessage(Component.translatable(enabled ? "caracrea.voice.disabled" : "caracrea.voice.enabled_again"), true);
        }
        while (HIDE_ICONS.consumeClick()) {
            c.icons.set(!c.icons.get());
            CaraCreaConfig.CLIENT_SPEC.save();
        }
        while (GROUPS.consumeClick()) {
            if (groupsAllowed()) {
                mc.setScreen(new VoiceGroupsScreen(null));
            } else {
                mc.player.displayClientMessage(Component.translatable("caracrea.voice.group.not_allowed"), true);
            }
        }
        while (RECORD.consumeClick()) {
            if (!recordingAllowed()) {
                mc.player.displayClientMessage(Component.translatable("caracrea.voice.record.not_allowed"), true);
            } else {
                Path done = VoiceRecorder.toggle();
                mc.player.displayClientMessage(done == null
                        ? Component.translatable("caracrea.voice.record.started")
                        : Component.translatable("caracrea.voice.record.saved", done.getFileName().toString()), true);
            }
        }

        if (testing && VoiceGate.testExpired(testingSince, System.nanoTime())) {
            stopTests();
            mc.player.displayClientMessage(Component.translatable("caracrea.voice.test.stopped"), true);
        }
        if (microphone != null && microphone.unavailable && testing) {
            stopTests();
        }
        if (!active()) {
            stopAudio();
            while (MUTE.consumeClick()) {
                // Nothing to mute.
            }
            return;
        }
        if (playback == null) {
            playback = new VoicePlayback();
        }
        String device = c.microphone.get();
        if (microphone != null && !microphone.device().equals(device)) {
            microphone.stop();
            microphone = null;
        }
        if (microphone == null) {
            microphone = new Microphone(device);
        }

        while (MUTE.consumeClick()) {
            boolean muted = !c.muted.get();
            c.muted.set(muted);
            CaraCreaConfig.CLIENT_SPEC.save();
            mc.player.displayClientMessage(Component.translatable(muted ? "caracrea.voice.muted" : "caracrea.voice.unmuted"), true);
        }

        // Keys are read directly, so push-to-talk also works in the character creator and the voice
        // screens, but never while typing in the chat or a sign.
        boolean talkScreen = mc.screen == null || mc.screen instanceof CharacterCreatorScreen
                || mc.screen instanceof VoiceSettingsScreen || mc.screen instanceof VoiceVolumesScreen;
        pushToTalkDown = talkScreen && isDown(PUSH_TO_TALK);
        whisperDown = talkScreen && isDown(WHISPER);

        float volume = (float) (c.voiceVolume.get() * mc.options.getSoundSourceVolume(SoundSource.MASTER)
                * mc.options.getSoundSourceVolume(SoundSource.VOICE));
        settings = new Settings(c.pushToTalk.get(), c.activationThreshold.get().floatValue(), c.micGain.get().floatValue(), volume,
                !c.muted.get() && mc.player.isAlive(), testing, c.denoise.get() ? c.denoiseStrength.get() : 0, c.automaticGain.get(),
                c.automaticActivation.get(), c.speaker.get(), mc.options.directionalAudio().get(), c.jitterBufferMs.get(),
                config.bitrate());

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        Vector3f look = camera.getLookVector();
        float[] axes = {-left.x(), -left.y(), -left.z(), up.x(), up.y(), up.z(), look.x(), look.y(), look.z()};
        Map<UUID, double[]> positions = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        double reach = Math.max(config.range(), config.whisperRange()) + 16;
        boolean walls = c.occlusion.get();
        for (Player player : mc.level.players()) {
            if (player != mc.player && player.distanceToSqr(eye) <= reach * reach) {
                double occlusion = walls && VoiceActivity.isTalking(player.getUUID()) ? occlusion(mc, eye, player) : 0;
                positions.put(player.getUUID(), new double[]{player.getX(), player.getEyeY(), player.getZ(), occlusion});
            }
        }
        if (VoiceRecorder.recording()) {
            mc.getConnection().getOnlinePlayers().forEach(info -> names.put(info.getProfile().getId(), info.getProfile().getName()));
        }
        listener = new Listener(eye.x, eye.y, eye.z, axes, config.range(), config.whisperRange(), positions, names);
    }

    /** 0 in the open, 0.5 behind a thin wall, up to 1 behind thick or several walls. */
    private static double occlusion(Minecraft mc, Vec3 eye, Player speaker) {
        Vec3 mouth = speaker.getEyePosition();
        BlockHitResult front = mc.level.clip(new ClipContext(eye, mouth, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (front.getType() == HitResult.Type.MISS) {
            return 0;
        }
        BlockHitResult back = mc.level.clip(new ClipContext(mouth, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        double thickness = back.getType() == HitResult.Type.MISS ? 1 : front.getLocation().distanceTo(back.getLocation());
        return Math.min(1, 0.5 + thickness / 4);
    }

    private static void rates(VoiceTransport current) {
        long now = System.nanoTime();
        if (now - rateTime < 1_000_000_000L) {
            return;
        }
        long up = current == null ? 0 : current.bytesSent();
        long down = current == null ? 0 : current.bytesReceived();
        if (rateTime != 0) {
            double seconds = (now - rateTime) / 1e9;
            kbpsUp = (float) Math.max(0, (up - rateUp) * 8 / seconds / 1000);
            kbpsDown = (float) Math.max(0, (down - rateDown) * 8 / seconds / 1000);
        }
        rateTime = now;
        rateUp = up;
        rateDown = down;
    }

    private static boolean isDown(KeyMapping key) {
        InputConstants.Key bound = key.getKey();
        if (bound == InputConstants.UNKNOWN) {
            return false;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (bound.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, bound.getValue());
    }

    /** Stops the audio threads (voice chat turned off by the player, Simple Voice Chat connected). */
    private static void stopAudio() {
        testing = false;
        testingSince = 0;
        if (microphone != null) {
            microphone.stop();
            microphone = null;
        }
        VoicePlayback current = playback;
        if (current != null) {
            playback = null;
            current.stop();
        }
        pushToTalkDown = false;
        whisperDown = false;
    }

    /** Stops everything (leaving the server, or the server does not run our voice chat). */
    public static void stop() {
        stopAudio();
    }

    public static void disconnect() {
        stop();
        closeTransport();
        VoiceRecorder.stop();
        config = CaraCreaNetwork.VoiceConfig.off();
        testing = false;
        VoiceGate.svcConnected = false;
        sentState = null;
        groups = List.of();
        states = Map.of();
        byVoiceId = new UUID[0];
    }
}
