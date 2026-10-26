package fr.heraliz.airstrike.network;

import fr.heraliz.airstrike.strike.Strike;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Server -> client: a strike has been planned, replay it visually. */
public class StrikePacket {
    public final Strike strike;

    public StrikePacket(Strike strike) {
        this.strike = strike;
    }

    public StrikePacket(FriendlyByteBuf buf) {
        this.strike = Strike.read(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        strike.write(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Network.RECEPTION_CLIENT.accept(this);   // client: ClientStrikes.add(strike) (PartieClient)
        ctx.get().setPacketHandled(true);
    }
}
