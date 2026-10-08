package fr.heraliz.crac.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

public record S2COpenGroupCreate() {
    public static void encode(S2COpenGroupCreate msg, FriendlyByteBuf buf) {
    }

    public static S2COpenGroupCreate decode(FriendlyByteBuf buf) {
        return new S2COpenGroupCreate();
    }

    public static void handle(S2COpenGroupCreate msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }

}
