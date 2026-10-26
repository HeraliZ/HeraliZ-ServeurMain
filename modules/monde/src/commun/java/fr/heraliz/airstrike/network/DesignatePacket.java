package fr.heraliz.airstrike.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Client -> server: the player marked a target through the binoculars (the loaded cartridge is called). */
public class DesignatePacket {
    public final double x, y, z;

    public DesignatePacket(Vec3 target) {
        this.x = target.x;
        this.y = target.y;
        this.z = target.z;
    }

    public DesignatePacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Network.RECEPTION_SERVEUR.accept(new Network.Recu(ctx.get().getSender(), this));   // server: PartieServeur
        ctx.get().setPacketHandled(true);
    }
}
