package fr.heraliz.crac.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Zones où l'on ne construit pas, autour d'un joueur qui tient le plan (HeraliZ, 27/10, protocole 26) : un morceau du
 * masque de {@code ZonesConstruction} (serveur), cases de {@code pas} blocs, coin nord-ouest en (x0, z0) (blocs du monde),
 * {@code largeur} x {@code hauteur} cases, un bit par case (ligne par ligne), 1 = interdit. {@code largeur} 0 : aucune zone
 * (autre monde, pas de masque). Le jeu du joueur les dessine en rouge ({@code client.ZonesInterdites}).
 */
public record S2CZonesInterdites(int x0, int z0, int pas, int largeur, int hauteur, long[] bits) {
    /** Côté maximal d'une fenêtre, en cases (le serveur en envoie 25). */
    public static final int COTE_MAX = 64;

    public static S2CZonesInterdites vide() {
        return new S2CZonesInterdites(0, 0, 1, 0, 0, new long[0]);
    }

    public boolean interdit(int i, int j) {
        if (i < 0 || j < 0 || i >= this.largeur || j >= this.hauteur) {
            return false;
        }
        int n = j * this.largeur + i;
        return (this.bits[n >> 6] & (1L << (n & 63))) != 0;
    }

    public static void encode(S2CZonesInterdites msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.x0);
        buf.writeInt(msg.z0);
        buf.writeVarInt(msg.pas);
        buf.writeVarInt(msg.largeur);
        buf.writeVarInt(msg.hauteur);
        buf.writeLongArray(msg.bits);
    }

    public static S2CZonesInterdites decode(FriendlyByteBuf buf) {
        int x0 = buf.readInt();
        int z0 = buf.readInt();
        int pas = Math.max(1, Math.min(64, buf.readVarInt()));
        int largeur = Math.max(0, Math.min(COTE_MAX, buf.readVarInt()));
        int hauteur = Math.max(0, Math.min(COTE_MAX, buf.readVarInt()));
        long[] bits = buf.readLongArray(null, (COTE_MAX * COTE_MAX + 63) / 64);
        if (bits.length * 64L < (long) largeur * hauteur) {
            return vide();
        }
        return new S2CZonesInterdites(x0, z0, pas, largeur, hauteur, bits);
    }

    public static void handle(S2CZonesInterdites msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
