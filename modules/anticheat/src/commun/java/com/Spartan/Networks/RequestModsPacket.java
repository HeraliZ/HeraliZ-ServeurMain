package com.Spartan.Networks;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Paquet hérité de la 1.0.0, jamais envoyé. Conservé pour garder les IDs de
 * paquets stables ; son traitement est neutralisé (il exposait la liste des
 * mods du SERVEUR à n'importe quel client qui l'envoyait).
 */
public class RequestModsPacket {
    public static RequestModsPacket decode(FriendlyByteBuf buf) {
        return new RequestModsPacket();
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
    }
}
