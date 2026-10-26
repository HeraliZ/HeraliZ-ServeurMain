package fr.heraliz.crac.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Fin du tutoriel des bases : la caméra du joueur fait le tour de sa nouvelle base (centre et rayon en blocs), pendant
 * que le Commandant Varga lui parle à la radio (HeraliZ Quêtes). Rien d'autre ne change : c'est un plan de cinéma.
 */
public record S2CTutoFin(double x, double y, double z, float rayon) {
    public static void encode(S2CTutoFin msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.rayon);
    }

    public static S2CTutoFin decode(FriendlyByteBuf buf) {
        return new S2CTutoFin(buf.readDouble(), buf.readDouble(), buf.readDouble(), Math.max(4F, Math.min(40F, buf.readFloat())));
    }

    public static void handle(S2CTutoFin msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
