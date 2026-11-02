package com.Spartan.Networks;

import io.netty.handler.codec.DecoderException;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * S->C : touches des menus de triche à surveiller (codes GLFW), réglées sur le serveur
 * ({@code integrity.touchesSurveillees}, audit du 02/11, point 13) : la liste n'est plus écrite dans le jar du joueur.
 */
public class TouchesPacket {
    public static final int MAX = 32;

    private final int[] codes;

    public TouchesPacket(int[] codes) {
        this.codes = codes;
    }

    public static TouchesPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX) {
            throw new DecoderException("Spartan: touches invalides (" + n + ")");
        }
        int[] c = new int[n];
        for (int i = 0; i < n; i++) {
            c[i] = buf.readVarInt();
        }
        return new TouchesPacket(c);
    }

    public void encode(FriendlyByteBuf buf) {
        int n = Math.min(this.codes.length, MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeVarInt(this.codes[i]);
        }
    }

    public int[] codes() {
        return this.codes;
    }

    /** Traité par la partie client (PartieClient), sur le thread du jeu. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_CLIENT.accept(this));
        ctx.get().setPacketHandled(true);
    }
}
