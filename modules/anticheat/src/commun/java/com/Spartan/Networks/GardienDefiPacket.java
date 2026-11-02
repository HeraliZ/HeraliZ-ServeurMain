package com.Spartan.Networks;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * S->C : défi du Gardien pour cette connexion (16 octets en hexadécimal, renouvelé chaque minute). Le jeu l'écrit dans
 * spartan/gardien-defi.txt ; le launcher le recopie dans ses rapports signés : le serveur sait alors qu'un rapport a été
 * écrit APRÈS ce défi, pour CETTE connexion (un vieux rapport rejoué ne l'a pas).
 */
public class GardienDefiPacket {
    private static final int MAX = 32;

    private final String defi;

    public GardienDefiPacket(String defi) {
        this.defi = defi;
    }

    public static GardienDefiPacket decode(FriendlyByteBuf buf) {
        return new GardienDefiPacket(buf.readUtf(MAX));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.defi.length() > MAX ? this.defi.substring(0, MAX) : this.defi, MAX);
    }

    public String defi() {
        return this.defi;
    }

    /** Traité par la partie client (PartieClient), sur le thread du jeu. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_CLIENT.accept(this));
        ctx.get().setPacketHandled(true);
    }
}
