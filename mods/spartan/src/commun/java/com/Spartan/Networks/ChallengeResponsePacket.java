package com.Spartan.Networks;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C->S : réponse à un défi d'intégrité. */
public class ChallengeResponsePacket {
    private static final int MAX_ANSWER = 64;

    private final long nonce;
    private final byte[] answer;

    public ChallengeResponsePacket(long nonce, byte[] answer) {
        this.nonce = nonce;
        this.answer = answer;
    }

    public static ChallengeResponsePacket decode(FriendlyByteBuf buf) {
        return new ChallengeResponsePacket(buf.readLong(), buf.readByteArray(MAX_ANSWER));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(this.nonce);
        buf.writeByteArray(this.answer);
    }

    public long nonce() {
        return this.nonce;
    }

    public byte[] answer() {
        return this.answer;
    }

    /** Traité par la partie serveur (ReceptionServeur), sur le thread du serveur. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_SERVEUR.accept(new ModMessages.Recu(ctx.get().getSender(), this)));
        ctx.get().setPacketHandled(true);
    }
}
