package fr.heraliz.crac.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * À la mort d'un joueur (25/10) : peut-il réapparaître à la base de son clan ? Raison ({@code bases/TeleportBase} :
 * 0 oui, 1 pas de base, 2 pas le droit, 3 match, 4 délai pas fini, 5 carte absente) et temps restant du délai partagé
 * avec /home. Le choix du joueur est revérifié par le serveur à la réapparition.
 */
public record S2CReapparition(int raison, long resteMs) {
    public static void encode(S2CReapparition msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.raison);
        buf.writeVarLong(Math.max(0, msg.resteMs));
    }

    public static S2CReapparition decode(FriendlyByteBuf buf) {
        return new S2CReapparition(Math.max(0, Math.min(9, buf.readVarInt())), Math.min(86_400_000L, buf.readVarLong()));
    }

    public static void handle(S2CReapparition msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
