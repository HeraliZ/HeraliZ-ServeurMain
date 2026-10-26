package fr.heraliz.crac.network;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Joueurs protégés « nouveau joueur » en ce moment (dans le monde des bases, base pas encore posée) : leur modèle est
 * entouré de vert chez les autres joueurs. Envoyé à tous à chaque changement, et à la connexion.
 */
public record S2CProteges(Set<UUID> joueurs) {
    static final int MAX = 4096;

    public static void encode(S2CProteges msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.joueurs.size());
        for (UUID u : msg.joueurs) {
            buf.writeUUID(u);
        }
    }

    public static S2CProteges decode(FriendlyByteBuf buf) {
        int n = PacketLimits.readCount(buf, MAX, 16);
        Set<UUID> s = new HashSet<>(n);
        for (int i = 0; i < n; i++) {
            s.add(buf.readUUID());
        }
        return new S2CProteges(s);
    }

    public static void handle(S2CProteges msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
