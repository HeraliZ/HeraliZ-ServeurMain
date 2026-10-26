package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import fr.heraliz.crac.bases.CarriereType;
import fr.heraliz.crac.bases.Carrieres;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Carrières du monde des bases, pour que le jeu du joueur laisse démarrer la casse de leurs blocs (le serveur décide
 * quand même). Envoyé à la connexion, à l'entrée dans le monde des bases et à chaque carrière posée ou retirée.
 */
public record S2CCarrieres(List<Carrieres.Zone> zones) {
    static final int MAX_ZONES = 4096;
    static final int MAX_NOM = 32;

    public static void encode(S2CCarrieres msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.zones.size());
        for (Carrieres.Zone z : msg.zones) {
            buf.writeUtf(z.nom(), MAX_NOM);
            buf.writeBlockPos(z.centre());
            buf.writeVarInt(z.rayon());
            buf.writeVarInt(z.type().ordinal());
        }
    }

    public static S2CCarrieres decode(FriendlyByteBuf buf) {
        int n = PacketLimits.readCount(buf, MAX_ZONES, 11);
        List<Carrieres.Zone> zones = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String nom = buf.readUtf(MAX_NOM);
            var centre = buf.readBlockPos();
            int rayon = Math.max(0, Math.min(1024, buf.readVarInt()));
            CarriereType type = CarriereType.parRang(buf.readVarInt());
            if (type != null) {
                zones.add(new Carrieres.Zone(nom, centre, rayon, type));
            }
        }
        return new S2CCarrieres(zones);
    }

    public static void handle(S2CCarrieres msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                Carrieres.vues(msg.zones());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
