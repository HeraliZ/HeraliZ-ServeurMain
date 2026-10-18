package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Ce que montre l'écran des accès de l'armoire (CRac 1.10.0) : la base (nom du groupe, niveau, rayon, protection restante
 * en ms, 0 sans protection), si le joueur peut gérer la liste, qui a l'accès, et les joueurs trouvés par la recherche.
 */
public record S2CAccesBase(BlockPos pos, String base, int tier, int rayon, long protection, boolean gerer, List<Ligne> ont,
                           List<Ligne> trouves, String recherche) {
    /** Rôle d'une ligne : propriétaire, membre du groupe, accès donné ; pour les joueurs trouvés : à qui donner l'accès. */
    public static final int PROPRIETAIRE = 0, GROUPE = 1, ACCES = 2, TROUVE = 3;
    static final int MAX = 128;

    public record Ligne(UUID id, String nom, int role, boolean enLigne) {
    }

    public static void encode(S2CAccesBase m, FriendlyByteBuf buf) {
        buf.writeBlockPos(m.pos);
        buf.writeUtf(m.base, 64);
        buf.writeVarInt(m.tier);
        buf.writeVarInt(m.rayon);
        buf.writeVarLong(m.protection);
        buf.writeBoolean(m.gerer);
        lignes(buf, m.ont);
        lignes(buf, m.trouves);
        buf.writeUtf(m.recherche, 16);
    }

    private static void lignes(FriendlyByteBuf buf, List<Ligne> l) {
        buf.writeVarInt(l.size());
        for (Ligne x : l) {
            buf.writeUUID(x.id);
            buf.writeUtf(x.nom, 32);
            buf.writeVarInt(x.role);
            buf.writeBoolean(x.enLigne);
        }
    }

    private static List<Ligne> lignes(FriendlyByteBuf buf) {
        int n = PacketLimits.readCount(buf, MAX, 19);
        List<Ligne> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            l.add(new Ligne(buf.readUUID(), buf.readUtf(32), buf.readVarInt(), buf.readBoolean()));
        }
        return l;
    }

    public static S2CAccesBase decode(FriendlyByteBuf buf) {
        return new S2CAccesBase(buf.readBlockPos(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readBoolean(),
            lignes(buf), lignes(buf), buf.readUtf(16));
    }

    public static void handle(S2CAccesBase msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
