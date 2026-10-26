package com.heraliz.extraction.reseau;

import com.heraliz.extraction.HeralizExtraction;
import com.heraliz.extraction.api.ExtractionApi;
import com.heraliz.extraction.pont.Crochet;
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
 * Messages du mod (canal {@code heraliz_extraction:main}), du serveur vers les joueurs :
 * <ul><li>{@link SyncExtractions} : la liste des extractions ouvertes, à la connexion et à chaque ouverture ou fermeture ;
 * elle remplit {@link ExtractionApi} chez le joueur (lue par la carte du monde) ;</li>
 * <li>{@link Progression} : le chrono d'extraction du joueur, affiché en haut de son écran (partie client).</li></ul>
 */
public final class Reseau {
    private static final String VERSION = "2";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HeralizExtraction.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    /** Chrono reçu par le joueur : branché par la partie client (HUD d'extraction). */
    public static final Crochet<Progression> PROGRESSION_CLIENT = new Crochet<>();

    /** Plus de zones que ça dans un paquet = paquet abîmé (une carte en a une dizaine). */
    private static final int MAX_ZONES = 256;

    private Reseau() {
    }

    public static void enregistrer() {
        CANAL.registerMessage(0, SyncExtractions.class, SyncExtractions::ecrire, SyncExtractions::lire, SyncExtractions::traiter,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(1, Progression.class, Progression::ecrire, Progression::lire, Progression::traiter,
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

    /**
     * Chrono d'extraction du joueur : ticks tenus, ticks à tenir (0 : plus d'extraction, la carte disparaît), ticks avant la
     * fermeture de la zone (-1 : zone permanente), joueur dans la zone (sinon : tolérance au bord), ciel ouvert exigé.
     */
    public record Progression(int tenus, int aTenir, int zone, boolean dedans, boolean cielOuvert) {
        /** Plus d'extraction en cours pour ce joueur. */
        public static Progression fin() {
            return new Progression(0, 0, -1, false, false);
        }

        static void ecrire(Progression m, FriendlyByteBuf b) {
            b.writeVarInt(m.tenus);
            b.writeVarInt(m.aTenir);
            b.writeInt(m.zone);
            b.writeBoolean(m.dedans);
            b.writeBoolean(m.cielOuvert);
        }

        static Progression lire(FriendlyByteBuf b) {
            return new Progression(b.readVarInt(), b.readVarInt(), b.readInt(), b.readBoolean(), b.readBoolean());
        }

        static void traiter(Progression m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> PROGRESSION_CLIENT.accept(m));
            ctx.get().setPacketHandled(true);
        }
    }
}
