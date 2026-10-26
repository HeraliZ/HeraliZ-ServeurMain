package com.Spartan.Networks;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C->S : jeton du launcher (peut être vide) + empreinte réelle du dossier mods. */
public class LaunchTokenPacket {
    private static final int MAX_TOKEN = 1024;
    private static final int MAX_HASH = 64;

    private final String token;
    private final String modsHash;

    public LaunchTokenPacket(String token, String modsHash) {
        this.token = token;
        this.modsHash = modsHash;
    }

    public static LaunchTokenPacket decode(FriendlyByteBuf buf) {
        return new LaunchTokenPacket(buf.readUtf(MAX_TOKEN), buf.readUtf(MAX_HASH));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cut(this.token, MAX_TOKEN), MAX_TOKEN);
        buf.writeUtf(cut(this.modsHash, MAX_HASH), MAX_HASH);
    }

    public String token() {
        return this.token;
    }

    public String modsHash() {
        return this.modsHash;
    }

    /** Traité par la partie serveur (ReceptionServeur), sur le thread du serveur. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_SERVEUR.accept(new ModMessages.Recu(ctx.get().getSender(), this)));
        ctx.get().setPacketHandled(true);
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
