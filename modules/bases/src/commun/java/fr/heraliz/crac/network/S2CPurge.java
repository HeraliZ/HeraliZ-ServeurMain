package fr.heraliz.crac.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * État de la Purge pour la petite icône en haut à gauche (25/10, à la place de la barre rouge de Minecraft) : temps
 * restant et durée totale en ms (0 = pas de Purge). Envoyé au début, à la fin et à la connexion ; le joueur décompte
 * lui-même à partir de la réception (aucune horloge du serveur à comparer).
 */
public record S2CPurge(long resteMs, long totalMs) {
    public static void encode(S2CPurge msg, FriendlyByteBuf buf) {
        buf.writeVarLong(Math.max(0, msg.resteMs));
        buf.writeVarLong(Math.max(0, msg.totalMs));
    }

    public static S2CPurge decode(FriendlyByteBuf buf) {
        // une Purge dure au plus un jour (créneaux et /purge forcer)
        return new S2CPurge(Math.min(86_400_000L, buf.readVarLong()), Math.min(86_400_000L, buf.readVarLong()));
    }

    public static void handle(S2CPurge msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
