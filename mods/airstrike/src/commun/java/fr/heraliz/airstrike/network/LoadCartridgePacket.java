package fr.heraliz.airstrike.network;

import fr.heraliz.airstrike.strike.StrikeType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Client -> server: put a payload cartridge of this type into the held binoculars. */
public class LoadCartridgePacket {
    public final StrikeType type;

    public LoadCartridgePacket(StrikeType type) {
        this.type = type;
    }

    public LoadCartridgePacket(FriendlyByteBuf buf) {
        this.type = StrikeType.byId(buf.readByte());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(type.ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Network.RECEPTION_SERVEUR.accept(new Network.Recu(ctx.get().getSender(), this));   // server: PartieServeur
        ctx.get().setPacketHandled(true);
    }
}
