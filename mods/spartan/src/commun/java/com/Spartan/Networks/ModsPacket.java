package com.Spartan.Networks;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * C->S : liste des mods du client, comparée à la liste noire de la config.
 * Mesure best-effort : un client modifié peut mentir ou ne rien envoyer.
 * Traité une seule fois par connexion (anti-spam d'alertes).
 */
public class ModsPacket {
    private final List<String> mods;

    public ModsPacket(List<String> mods) {
        this.mods = mods;
    }

    public static ModsPacket decode(FriendlyByteBuf buf) {
        return new ModsPacket(PacketUtil.readStringList(buf));
    }

    public void encode(FriendlyByteBuf buf) {
        PacketUtil.writeStringList(buf, this.mods);
    }

    public List<String> mods() {
        return this.mods;
    }

    /** Traité par la partie serveur (ReceptionServeur), sur le thread du serveur. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_SERVEUR.accept(new ModMessages.Recu(ctx.get().getSender(), this)));
        ctx.get().setPacketHandled(true);
    }
}
