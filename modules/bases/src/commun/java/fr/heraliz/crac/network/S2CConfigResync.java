package fr.heraliz.crac.network;

import java.util.function.Supplier;
import fr.heraliz.crac.Crac;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Raw content of one of CRac's SERVER config files, sent again after the server changed it (admin panel edit or file
 * reload). Forge only syncs these files at login; the client applies this copy the same way.
 */
public record S2CConfigResync(String fileName, byte[] data) {
    /** Leaves room under the 1 MiB clientbound custom payload limit. */
    public static final int MAX_BYTES = 1000000;

    public static void encode(S2CConfigResync m, FriendlyByteBuf buf) {
        buf.writeUtf(m.fileName, 128);
        buf.writeByteArray(m.data);
    }

    public static S2CConfigResync decode(FriendlyByteBuf buf) {
        return new S2CConfigResync(buf.readUtf(128), buf.readByteArray(MAX_BYTES));
    }

    public static void handle(S2CConfigResync msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }

}
