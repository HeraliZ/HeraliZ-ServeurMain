package com.Spartan.Networks;

import com.Spartan.integrity.Fingerprint;
import io.netty.handler.codec.DecoderException;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * S->C : défi d'intégrité. Le client répond avec l'empreinte de ses propres
 * classes calculée selon la variante demandée (voir Fingerprint).
 * Code commun : aucune classe client n'est référencée.
 */
public class ChallengePacket {
    /** Chaque défi couvre toutes les classes du mod : borne large mais finie (anti-crash). */
    public static final int MAX_INDICES = 4096;

    private final long nonce;
    private final int variant;
    private final int[] indices;

    public ChallengePacket(long nonce, int variant, int[] indices) {
        this.nonce = nonce;
        this.variant = variant;
        this.indices = indices;
    }

    public static ChallengePacket decode(FriendlyByteBuf buf) {
        long nonce = buf.readLong();
        int variant = buf.readVarInt();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_INDICES) {
            throw new DecoderException("Spartan: défi invalide (" + n + ")");
        }
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) {
            idx[i] = buf.readVarInt();
        }
        return new ChallengePacket(nonce, variant, idx);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(this.nonce);
        buf.writeVarInt(this.variant);
        buf.writeVarInt(this.indices.length);
        for (int i : this.indices) {
            buf.writeVarInt(i);
        }
    }

    public long nonce() {
        return this.nonce;
    }

    public int variant() {
        return this.variant;
    }

    public int[] indices() {
        return this.indices;
    }

    /** Traité par la partie client (PartieClient), sur le thread du jeu. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_CLIENT.accept(this));
        ctx.get().setPacketHandled(true);
    }
}
