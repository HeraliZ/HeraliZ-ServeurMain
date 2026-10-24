package fr.heraliz.airstrike.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Server -> client: play the visual/sound effects of an impact. */
public class ImpactPacket {
    public static final byte BOMB = 0;
    public static final byte HEAVY = 1;
    public static final byte SMALL = 2;
    public static final byte NAPALM = 3;
    public static final byte POP = 4;
    public static final byte CANISTER = 5;
    public static final byte BULLET = 6;
    public static final byte CRATE_LAND = 7;
    /** Balle de la mitrailleuse de l'hélicoptère : traçante jusqu'ici (power 1 : poussière sur un bloc, 0.5 : créature touchée, 0 : perdue). */
    public static final byte HELICO_BALLE = 8;

    public final byte kind;
    public final double x, y, z;
    public final float power;

    public ImpactPacket(byte kind, double x, double y, double z, float power) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.power = power;
    }

    public ImpactPacket(FriendlyByteBuf buf) {
        this.kind = buf.readByte();
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.power = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(kind);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(power);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Network.RECEPTION_CLIENT.accept(this);   // client: ImpactFx.play (PartieClient)
        ctx.get().setPacketHandled(true);
    }
}
