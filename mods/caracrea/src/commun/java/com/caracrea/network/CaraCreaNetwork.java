package com.caracrea.network;

import com.caracrea.pont.Crochet;
import com.caracrea.CaraCrea;
import com.caracrea.character.Appearance;
import com.caracrea.character.AppearanceCodec;
import com.caracrea.voice.net.VoiceProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Small packets: the server syncs appearances (about 40 bytes per player)
 * and asks a client to open the creator; the client asks to open it or
 * saves its choices. The built-in voice chat adds its settings, session key,
 * states and groups, and, only when its UDP channel cannot be used, the voice
 * frames (about 70 bytes every 20 ms while someone talks), handled on the
 * network threads so they never wait for, or slow down, the game.
 */
public final class CaraCreaNetwork {
    /** Paquet reçu par le serveur, avec son expéditeur. */
    public record Recu(ServerPlayer joueur, Object paquet) {
    }

    /** Réception côté client (ClientPacketHandler) : branchée par la partie client. */
    public static final Crochet<Object> RECEPTION_CLIENT = new Crochet<>();
    /** Réception côté serveur (CharacterService, VoiceServer) : branchée par la partie serveur. */
    public static final Crochet<Recu> RECEPTION_SERVEUR = new Crochet<>();

    // 4 (1.0.1): CloseCreator added. 5 (1.1.0): voice over UDP (session key in VoiceConfig), voice numbers, flags,
    // positions, OpenVoiceSettings.
    private static final String PROTOCOL = "5";

    /** Name of the channel: {@code server.VoiceInbound} recognises the voice frames by it. */
    public static final ResourceLocation NAME = new ResourceLocation(CaraCrea.MOD_ID, "main");
    /** Discriminator of {@link VoiceUp} (first byte of the payload). */
    public static final int VOICE_UP = 5;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            NAME, () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private CaraCreaNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(SyncAppearances.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncAppearances::encode).decoder(SyncAppearances::decode)
                .consumerMainThread(SyncAppearances::handle).add();
        CHANNEL.messageBuilder(OpenCreator.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenCreator::encode).decoder(OpenCreator::decode)
                .consumerMainThread(OpenCreator::handle).add();
        CHANNEL.messageBuilder(SaveAppearance.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SaveAppearance::encode).decoder(SaveAppearance::decode)
                .consumerMainThread(SaveAppearance::handle).add();
        CHANNEL.messageBuilder(RequestCreator.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestCreator::encode).decoder(RequestCreator::decode)
                .consumerMainThread(RequestCreator::handle).add();
        CHANNEL.messageBuilder(VoiceConfig.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VoiceConfig::encode).decoder(VoiceConfig::decode)
                .consumerMainThread(VoiceConfig::handle).add();
        CHANNEL.messageBuilder(VoiceUp.class, VOICE_UP, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VoiceUp::encode).decoder(VoiceUp::decode)
                .consumerNetworkThread(VoiceUp::handle).add();
        CHANNEL.messageBuilder(VoiceDown.class, 6, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VoiceDown::encode).decoder(VoiceDown::decode)
                .consumerNetworkThread(VoiceDown::handle).add();
        CHANNEL.messageBuilder(VoiceStateUp.class, 7, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VoiceStateUp::encode).decoder(VoiceStateUp::decode)
                .consumerMainThread(VoiceStateUp::handle).add();
        CHANNEL.messageBuilder(VoiceRoster.class, 8, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VoiceRoster::encode).decoder(VoiceRoster::decode)
                .consumerMainThread(VoiceRoster::handle).add();
        CHANNEL.messageBuilder(GroupAction.class, 9, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GroupAction::encode).decoder(GroupAction::decode)
                .consumerMainThread(GroupAction::handle).add();
        CHANNEL.messageBuilder(CloseCreator.class, 10, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CloseCreator::encode).decoder(CloseCreator::decode)
                .consumerMainThread(CloseCreator::handle).add();
        CHANNEL.messageBuilder(OpenVoiceSettings.class, 11, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenVoiceSettings::encode).decoder(OpenVoiceSettings::decode)
                .consumerMainThread(OpenVoiceSettings::handle).add();
    }

    public static void sendTo(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToAll(Object message) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), message);
    }

    /** One player's appearance, as sent over the network. */
    public record Entry(UUID player, Appearance appearance) {
    }

    /** Server to client: appearances of one or more players. */
    public record SyncAppearances(List<Entry> entries) {
        public static SyncAppearances of(UUID player, Appearance appearance) {
            return new SyncAppearances(List.of(new Entry(player, appearance)));
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entries.size());
            for (Entry entry : entries) {
                buf.writeUUID(entry.player());
                AppearanceCodec.write(buf, entry.appearance());
            }
        }

        static SyncAppearances decode(FriendlyByteBuf buf) {
            int count = Math.min(buf.readVarInt(), 4096);
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(buf.readUUID(), AppearanceCodec.read(buf)));
            }
            return new SyncAppearances(entries);
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            RECEPTION_CLIENT.accept(this);
            context.get().setPacketHandled(true);
        }
    }

    /** Server to client: open the creator (the first time, it cannot be dismissed). */
    public record OpenCreator(boolean firstTime) {
        void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(firstTime);
        }

        static OpenCreator decode(FriendlyByteBuf buf) {
            return new OpenCreator(buf.readBoolean());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            RECEPTION_CLIENT.accept(this);
            context.get().setPacketHandled(true);
        }
    }

    /**
     * Server to client: close the creator now (the player was downed, joined a match or died while it was open;
     * a first-time creator opens again once the player is free).
     */
    public record CloseCreator() {
        void encode(FriendlyByteBuf buf) {
        }

        static CloseCreator decode(FriendlyByteBuf buf) {
            return new CloseCreator();
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            RECEPTION_CLIENT.accept(this);
            context.get().setPacketHandled(true);
        }
    }

    /** Client to server: the player confirmed this look. */
    public record SaveAppearance(Appearance appearance) {
        void encode(FriendlyByteBuf buf) {
            AppearanceCodec.write(buf, appearance);
        }

        static SaveAppearance decode(FriendlyByteBuf buf) {
            return new SaveAppearance(AppearanceCodec.read(buf));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) RECEPTION_SERVEUR.accept(new Recu(player, this));
            context.get().setPacketHandled(true);
        }
    }

    /** Client to server: the player pressed the key to open the creator. */
    public record RequestCreator() {
        void encode(FriendlyByteBuf buf) {
        }

        static RequestCreator decode(FriendlyByteBuf buf) {
            return new RequestCreator();
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) RECEPTION_SERVEUR.accept(new Recu(player, this));
            context.get().setPacketHandled(true);
        }
    }

    /**
     * Server to client, on joining (and whenever the voice chat is switched on or off): whether the built-in voice chat
     * runs, its ranges, the Opus bit rate, and the dedicated UDP channel: port (0 = none), host to use (empty = the
     * server's address), the session id and key this player's voice packets are encrypted with, and its voice number.
     */
    public record VoiceConfig(boolean enabled, float range, float whisperRange, boolean groups, boolean recording, int bitrate,
                              int udpPort, String udpHost, long session, byte[] key, int voiceId) {
        public static VoiceConfig off() {
            return new VoiceConfig(false, 48, 24, false, false, 24000, 0, "", 0, new byte[32], 0);
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(enabled);
            buf.writeFloat(range);
            buf.writeFloat(whisperRange);
            buf.writeBoolean(groups);
            buf.writeBoolean(recording);
            buf.writeVarInt(bitrate);
            buf.writeVarInt(udpPort);
            buf.writeUtf(udpHost, 255);
            buf.writeLong(session);
            buf.writeBytes(key, 0, 32);
            buf.writeVarInt(voiceId);
        }

        static VoiceConfig decode(FriendlyByteBuf buf) {
            boolean enabled = buf.readBoolean();
            float range = buf.readFloat();
            float whisper = buf.readFloat();
            boolean groups = buf.readBoolean();
            boolean recording = buf.readBoolean();
            int bitrate = buf.readVarInt();
            int port = buf.readVarInt();
            String host = buf.readUtf(255);
            long session = buf.readLong();
            byte[] key = new byte[32];
            buf.readBytes(key);
            return new VoiceConfig(enabled, range, whisper, groups, recording, bitrate, port, host, session, key, buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            RECEPTION_CLIENT.accept(this);
            context.get().setPacketHandled(true);
        }
    }

    /**
     * Client to server, when UDP is not usable: 20 ms of the player's voice (Opus). {@code flags}: whisper, end of
     * sentence ({@code VoiceProtocol.UP_FLAGS}). Read on the network thread ({@code server.VoiceInbound}).
     */
    public record VoiceUp(int sequence, int flags, byte[] data) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(sequence);
            buf.writeByte(flags);
            buf.writeByteArray(data);
        }

        public static VoiceUp decode(FriendlyByteBuf buf) {
            int sequence = buf.readVarInt();
            int flags = buf.readUnsignedByte() & VoiceProtocol.UP_FLAGS;
            return new VoiceUp(sequence, flags, buf.readByteArray(VoiceProtocol.MAX_PACKET));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) RECEPTION_SERVEUR.accept(new Recu(player, this));
            context.get().setPacketHandled(true);
        }
    }

    /**
     * Server to client, when UDP is not usable: 20 ms of a player's voice. {@code speaker} is its voice number (see the
     * roster), {@code flags} the {@code VoiceProtocol.F_*} flags, {@code dx dy dz} where the speaker stands relative to
     * the listener, in 1/8 block. Handled on the network thread.
     */
    public record VoiceDown(int speaker, int sequence, int flags, short dx, short dy, short dz, byte[] data) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(speaker);
            buf.writeVarInt(sequence);
            buf.writeByte(flags);
            buf.writeShort(dx);
            buf.writeShort(dy);
            buf.writeShort(dz);
            buf.writeByteArray(data);
        }

        static VoiceDown decode(FriendlyByteBuf buf) {
            return new VoiceDown(buf.readVarInt(), buf.readVarInt(), buf.readUnsignedByte(), buf.readShort(), buf.readShort(), buf.readShort(),
                    buf.readByteArray(VoiceProtocol.MAX_PACKET));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            RECEPTION_CLIENT.accept(this);
            context.get().setPacketHandled(true);
        }
    }

    /**
     * Client to server: the player's own voice state: voice chat turned off (hears no one, is not heard), microphone
     * muted (shown to the others), and whether the UDP channel works for it (otherwise voices come through the game
     * connection).
     */
    public record VoiceStateUp(boolean disabled, boolean muted, boolean udp) {
        void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(disabled);
            buf.writeBoolean(muted);
            buf.writeBoolean(udp);
        }

        static VoiceStateUp decode(FriendlyByteBuf buf) {
            return new VoiceStateUp(buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) RECEPTION_SERVEUR.accept(new Recu(player, this));
            context.get().setPacketHandled(true);
        }
    }

    /** Server to client: open the voice chat settings (command /voix). */
    public record OpenVoiceSettings() {
        void encode(FriendlyByteBuf buf) {
        }

        static OpenVoiceSettings decode(FriendlyByteBuf buf) {
            return new OpenVoiceSettings();
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            RECEPTION_CLIENT.accept(this);
            context.get().setPacketHandled(true);
        }
    }

    /** A voice group as the clients see it (the password stays on the server). */
    public record GroupInfo(int id, String name, int type, boolean locked) {
    }

    /**
     * A player's voice state as the other clients see it: voice number (in the voice packets), voice chat off,
     * microphone muted by the player, muted by an operator, and group (0 = none).
     */
    public record PlayerVoice(UUID player, int voiceId, boolean disabled, boolean muted, boolean adminMuted, int group) {
    }

    /** Server to client: the groups and everyone's voice state, sent again whenever they change. */
    public record VoiceRoster(List<GroupInfo> groups, List<PlayerVoice> players) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(groups.size());
            for (GroupInfo group : groups) {
                buf.writeVarInt(group.id());
                buf.writeUtf(group.name(), 32);
                buf.writeByte(group.type());
                buf.writeBoolean(group.locked());
            }
            buf.writeVarInt(players.size());
            for (PlayerVoice player : players) {
                buf.writeUUID(player.player());
                buf.writeVarInt(player.voiceId());
                buf.writeByte((player.disabled() ? 1 : 0) | (player.muted() ? 2 : 0) | (player.adminMuted() ? 4 : 0));
                buf.writeVarInt(player.group());
            }
        }

        static VoiceRoster decode(FriendlyByteBuf buf) {
            int groupCount = Math.min(buf.readVarInt(), 1024);
            List<GroupInfo> groups = new ArrayList<>(groupCount);
            for (int i = 0; i < groupCount; i++) {
                groups.add(new GroupInfo(buf.readVarInt(), buf.readUtf(32), buf.readByte(), buf.readBoolean()));
            }
            int playerCount = Math.min(buf.readVarInt(), 4096);
            List<PlayerVoice> players = new ArrayList<>(playerCount);
            for (int i = 0; i < playerCount; i++) {
                UUID id = buf.readUUID();
                int voiceId = buf.readVarInt();
                int flags = buf.readByte();
                players.add(new PlayerVoice(id, voiceId, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, buf.readVarInt()));
            }
            return new VoiceRoster(groups, players);
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            RECEPTION_CLIENT.accept(this);
            context.get().setPacketHandled(true);
        }
    }

    /** Client to server: create, join or leave a voice group. */
    public record GroupAction(int action, int group, String name, String password, int type) {
        public static final int CREATE = 0;
        public static final int JOIN = 1;
        public static final int LEAVE = 2;

        void encode(FriendlyByteBuf buf) {
            buf.writeByte(action);
            buf.writeVarInt(group);
            buf.writeUtf(name, 32);
            buf.writeUtf(password, 32);
            buf.writeByte(type);
        }

        static GroupAction decode(FriendlyByteBuf buf) {
            return new GroupAction(buf.readByte(), buf.readVarInt(), buf.readUtf(32), buf.readUtf(32), buf.readByte());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) RECEPTION_SERVEUR.accept(new Recu(player, this));
            context.get().setPacketHandled(true);
        }
    }
}
