package com.Spartan.Networks;

import com.Spartan.Spartan;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C->S : packs de ressources actifs (journalisés). Traité une fois par connexion. */
public class ResourcePackPacket {
    private final List<String> resourcePacks;

    public ResourcePackPacket(List<String> resourcePacks) {
        this.resourcePacks = resourcePacks;
    }

    public static ResourcePackPacket decode(FriendlyByteBuf buf) {
        return new ResourcePackPacket(PacketUtil.readStringList(buf));
    }

    public void encode(FriendlyByteBuf buf) {
        PacketUtil.writeStringList(buf, this.resourcePacks);
    }

    public List<String> resourcePacks() {
        return this.resourcePacks;
    }

    /** Traité par la partie serveur (ReceptionServeur), sur le thread du serveur. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_SERVEUR.accept(new ModMessages.Recu(ctx.get().getSender(), this)));
        ctx.get().setPacketHandled(true);
    }
}
