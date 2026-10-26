package fr.heraliz.crac.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Raid block timers of the receiving player: {@code remainingSeconds} for the zone they stand in (HUD overlay),
 * {@code ownSeconds} for their own group's base (what stops them building, upgrading and repairing).
 */
public class S2CRaidBlock {
    /** A raid block lasts at most a day (config cap); anything larger is clamped. */
    static final int MAX_SECONDS = 86400;
    final int remainingSeconds;
    final int ownSeconds;

    public S2CRaidBlock(int remainingSeconds, int ownSeconds) {
        this.remainingSeconds = clamp(remainingSeconds);
        this.ownSeconds = clamp(ownSeconds);
    }

    static int clamp(int seconds) {
        return Math.max(0, Math.min(MAX_SECONDS, seconds));
    }

    public static void encode(S2CRaidBlock msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.remainingSeconds);
        buf.writeVarInt(msg.ownSeconds);
    }

    public static S2CRaidBlock decode(FriendlyByteBuf buf) {
        return new S2CRaidBlock(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(S2CRaidBlock msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }

}
