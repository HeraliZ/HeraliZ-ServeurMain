package com.Spartan.Networks;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * S->C : le serveur demande une capture de l'écran du jeu. Le jeu ne la prend que si le joueur l'a autorisée dans le
 * launcher (variable SPARTAN_CAPTURE_CONSENT) ; sinon il ne répond rien.
 */
public class CaptureRequestPacket {
    private final int captureId;

    public CaptureRequestPacket(int captureId) {
        this.captureId = captureId;
    }

    public static CaptureRequestPacket decode(FriendlyByteBuf buf) {
        return new CaptureRequestPacket(buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.captureId);
    }

    public int captureId() {
        return this.captureId;
    }

    /** Traité par la partie client (PartieClient). */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_CLIENT.accept(this));
        ctx.get().setPacketHandled(true);
    }
}
