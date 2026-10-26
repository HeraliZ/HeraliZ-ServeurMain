package fr.heraliz.clashwar.reseau;

import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.Modules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Canal reseau {@code heraliz_clashwar:main}, version "1" (identique des deux cotes). Chaque module enregistre ses
 * paquets dans sa methode statique {@code paquets(MatchsNet.Registre r)} (voir {@link Modules}), appelee par
 * {@link #init()} dans l'ordre de {@link Modules#COMMUNS} : les identifiants sont donc stables pour un meme jar.
 * <p>Patron d'un paquet : un record avec {@code write(FriendlyByteBuf)}, {@code static read(FriendlyByteBuf)} et
 * {@code static handle(paquet, Supplier<NetworkEvent.Context>)} (execute sur le fil principal). Un S2C dont le
 * traitement touche au client passe par une classe du sous-paquet client (jamais d'import client dans le record).
 * Toute chaine ecrite est bornee ({@code writeUtf(s, max)}), toute liste lue est bornee.
 */
public final class MatchsNet {
    /** Paquet client -> serveur reçu, avec son contexte (expéditeur, fil). */
    public record Recu(Object paquet, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
    }

    /** Réception côté serveur (Reception*Serveur) : branchée par la partie serveur. */
    public static final fr.heraliz.clashwar.pont.Crochet<Recu> RECEPTION_SERVEUR = new fr.heraliz.clashwar.pont.Crochet<>();

    /** Appelé par le handle des paquets client -> serveur ; sans partie serveur, le paquet est ignoré. */
    public static void recevoirServeur(Object paquet, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        if (RECEPTION_SERVEUR.branche()) {
            RECEPTION_SERVEUR.accept(new Recu(paquet, ctx));
        } else {
            ctx.get().setPacketHandled(true);
        }
    }

    private MatchsNet() {
    }

    public static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(HeralizClashWar.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static boolean initialise;

    /** Enregistreur passe aux modules : un appel par type de paquet. */
    public static final class Registre {
        private int prochainId;

        private Registre() {
        }

        /** Paquet serveur vers client. */
        public <T> void s2c(Class<T> type, BiConsumer<T, FriendlyByteBuf> ecrire, Function<FriendlyByteBuf, T> lire,
                            BiConsumer<T, Supplier<NetworkEvent.Context>> traiter) {
            ajouter(type, NetworkDirection.PLAY_TO_CLIENT, ecrire, lire, traiter);
        }

        /** Paquet client vers serveur (le serveur revalide tout : cadence, droits, etat). */
        public <T> void c2s(Class<T> type, BiConsumer<T, FriendlyByteBuf> ecrire, Function<FriendlyByteBuf, T> lire,
                            BiConsumer<T, Supplier<NetworkEvent.Context>> traiter) {
            ajouter(type, NetworkDirection.PLAY_TO_SERVER, ecrire, lire, traiter);
        }

        private <T> void ajouter(Class<T> type, NetworkDirection dir, BiConsumer<T, FriendlyByteBuf> ecrire,
                                 Function<FriendlyByteBuf, T> lire, BiConsumer<T, Supplier<NetworkEvent.Context>> traiter) {
            CANAL.messageBuilder(type, prochainId++, dir).encoder(ecrire).decoder(lire).consumerMainThread(traiter).add();
        }

        /** Nombre de paquets enregistres jusqu'ici. */
        public int nombre() {
            return prochainId;
        }
    }

    /** Enregistre les paquets de tous les modules presents (une seule fois). */
    public static void init() {
        if (initialise) return;
        initialise = true;
        Registre r = new Registre();
        int modules = Modules.paquets(r);
        HeralizClashWar.LOG.info("[matchs] reseau {} v{} : {} paquets de {} modules", CANAL, VERSION, r.nombre(), modules);
    }

    // ------------------------------------------------------------------ envoi
    public static void versJoueur(ServerPlayer joueur, Object paquet) {
        CANAL.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
    }

    /** A chaque joueur de la liste (les deconnectes sont ignores). */
    public static void versJoueurs(Collection<ServerPlayer> joueurs, Object paquet) {
        for (ServerPlayer p : joueurs) {
            if (p != null && !p.hasDisconnected()) versJoueur(p, paquet);
        }
    }

    public static void versTous(Object paquet) {
        CANAL.send(PacketDistributor.ALL.noArg(), paquet);
    }

    /** Aux joueurs a moins de rayon blocs du point, dans ce monde. */
    public static void versProches(ServerLevel niveau, Vec3 pos, double rayon, Object paquet) {
        CANAL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, rayon, niveau.dimension())), paquet);
    }

    public static void versServeur(Object paquet) {
        CANAL.sendToServer(paquet);
    }
}
