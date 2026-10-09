package com.heraliz.extraction.reseau;

import com.heraliz.extraction.HeralizExtraction;
import com.heraliz.extraction.api.ExtractionApi;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Messages du mod (canal {@code heraliz_extraction:main}). Un seul pour l'instant : {@link SyncExtractions} (serveur →
 * joueurs), la liste des extractions ouvertes, à la connexion et à chaque ouverture ou fermeture ; elle remplit
 * {@link ExtractionApi} chez le joueur (lue par la carte du monde).
 */
public final class Reseau {
    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HeralizExtraction.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    /** Plus de zones que ça dans un paquet = paquet abîmé (une carte en a une dizaine). */
    private static final int MAX_ZONES = 256;

    private Reseau() {
    }

    public static void enregistrer() {
        CANAL.registerMessage(0, SyncExtractions.class, SyncExtractions::ecrire, SyncExtractions::lire, SyncExtractions::traiter,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void envoyer(ServerPlayer joueur, Object message) {
        CANAL.send(PacketDistributor.PLAYER.with(() -> joueur), message);
    }

    public static void envoyerATous(Object message) {
        CANAL.send(PacketDistributor.ALL.noArg(), message);
    }

    /** Les extractions ouvertes (remplace la liste du joueur). */
    public record SyncExtractions(List<ExtractionApi.Extraction> zones) {
        static void ecrire(SyncExtractions m, FriendlyByteBuf b) {
            b.writeVarInt(m.zones.size());
            for (ExtractionApi.Extraction z : m.zones) {
                b.writeUtf(z.nom(), 64);
                b.writeUtf(z.libelle(), 128);
                b.writeResourceLocation(z.dimension());
                b.writeDouble(z.x());
                b.writeDouble(z.y());
                b.writeDouble(z.z());
                b.writeFloat(z.rayon());
                b.writeVarInt(z.secondesATenir());
                b.writeLong(z.finTick());
            }
        }

        static SyncExtractions lire(FriendlyByteBuf b) {
            int n = b.readVarInt();
            if (n < 0 || n > MAX_ZONES) {
                throw new IllegalArgumentException("Extractions : " + n + " zones");
            }
            List<ExtractionApi.Extraction> zones = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                zones.add(new ExtractionApi.Extraction(b.readUtf(64), b.readUtf(128), b.readResourceLocation(),
                        b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readVarInt(), b.readLong()));
            }
            return new SyncExtractions(zones);
        }

        static void traiter(SyncExtractions m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ExtractionApi.recevoir(m.zones));
            ctx.get().setPacketHandled(true);
        }
    }
}
