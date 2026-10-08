package com.Spartan.Networks;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C->S : identifiant de build du mod client (empreinte de ses classes). */
public class BuildPacket {
    private static final int MAX = 64;

    private final String buildId;

    public BuildPacket(String buildId) {
        this.buildId = buildId;
    }

    public static BuildPacket decode(FriendlyByteBuf buf) {
        return new BuildPacket(buf.readUtf(MAX));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.buildId.length() > MAX ? this.buildId.substring(0, MAX) : this.buildId, MAX);
    }

    public String buildId() {
        return this.buildId;
    }

    /** Traité par la partie serveur (ReceptionServeur), sur le thread du serveur. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_SERVEUR.accept(new ModMessages.Recu(ctx.get().getSender(), this)));
        ctx.get().setPacketHandled(true);
    }
}
