package fr.heraliz.usure;

import fr.heraliz.usure.regles.Regles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import fr.heralisz.utils.pont.Crochet;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Messages du mod (canal {@code heraliz_usure:main}). Le serveur decide de tout : l'etat des armes voyage dans leur NBT
 * (synchronisation normale de l'inventaire, deja faite a chaque tir pour les munitions). Paquets minuscules :
 * <ul>
 *   <li>{@link SyncRegles} (serveur -> client) : les regles, a la connexion et quand elles changent ; drapeau « ouvrir
 *   l'ecran d'administration » pour {@code /usure config} ;</li>
 *   <li>{@link EnregistrerRegles} (client -> serveur) : l'ecran d'administration enregistre (op 2 verifie).</li>
 * </ul>
 */
public final class Reseau {
    private Reseau() {
    }

    /** Règles reçues du serveur (SyncRegles) : traitées par la partie client (client.PartieClient). */
    public static final Crochet<SyncRegles> RECEPTION_CLIENT = new Crochet<>();
    /**
     * Paquets reçus par le serveur (EnregistrerRegles), avec le joueur qui les envoie : traités par la partie serveur
     * (HeralizUsureServeur, branché par serveur.PartieServeur), neutre sans elle (jar -client).
     */
    public static final Crochet<Recu> RECEPTION_SERVEUR = new Crochet<>();

    /** Paquet reçu par le serveur et son expéditeur. */
    public record Recu(ServerPlayer joueur, Object paquet) {
    }

    /** 2 = 1.0.1 (plus d'enrayage : regles plus courtes, plus de paquet de desenrayage). */
    private static final String VERSION = "2";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(HeralizUsure.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    static void enregistrer() {
        int i = 0;
        CANAL.registerMessage(i++, SyncRegles.class, SyncRegles::ecrire, SyncRegles::lire, SyncRegles::traiter, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(i, EnregistrerRegles.class, EnregistrerRegles::ecrire, EnregistrerRegles::lire, EnregistrerRegles::traiter,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void envoyer(ServerPlayer p, Object message) {
        CANAL.send(PacketDistributor.PLAYER.with(() -> p), message);
    }

    public static void envoyerATous(Object message) {
        CANAL.send(PacketDistributor.ALL.noArg(), message);
    }

    public static void envoyerAuServeur(Object message) {
        CANAL.sendToServer(message);
    }

    // ---------------------------------------------------------------- messages
    public record SyncRegles(Regles regles, boolean ouvrirEcran) {
        static void ecrire(SyncRegles m, FriendlyByteBuf b) {
            b.writeBoolean(m.ouvrirEcran);
            m.regles.ecrire(b);
        }

        static SyncRegles lire(FriendlyByteBuf b) {
            boolean ouvrir = b.readBoolean();
            return new SyncRegles(Regles.lire(b), ouvrir);
        }

        static void traiter(SyncRegles m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
            ctx.get().setPacketHandled(true);
        }
    }

    public record EnregistrerRegles(Regles regles) {
        static void ecrire(EnregistrerRegles m, FriendlyByteBuf b) {
            m.regles.ecrire(b);
        }

        static EnregistrerRegles lire(FriendlyByteBuf b) {
            return new EnregistrerRegles(Regles.lire(b));
        }

        static void traiter(EnregistrerRegles m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ctx.get().enqueueWork(() -> RECEPTION_SERVEUR.accept(new Recu(p, m)));
            ctx.get().setPacketHandled(true);
        }
    }
}
