package fr.heralisz.tags.server;

import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.network.Packets;
import fr.heralisz.tags.network.TagsNet;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reception des messages du client (thread serveur). Anti-abus : au plus une action (roue, collection) toutes les
 * 200 ms par joueur, et au plus dix demandes de pose par seconde (la cadence reelle, cadence_s, est dans TagService ;
 * cette borne ne fait qu'ecarter un client qui inonderait le serveur). Les refus utiles sont signales au joueur.
 */
public final class ServerHandlers {
    private ServerHandlers() {
    }

    /** Forme des ids de tags (precompilee : String.matches recompilerait le motif a chaque paquet). */
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Map<UUID, Long> DERNIERE_ACTION = new HashMap<>();
    private static final Map<UUID, Long> DERNIERE_DEMANDE_TAG = new HashMap<>();

    private static boolean tropVite(ServerPlayer p) {
        long now = System.currentTimeMillis();
        Long d = DERNIERE_ACTION.get(p.getUUID());
        if (d != null && now - d < 200) return true;
        DERNIERE_ACTION.put(p.getUUID(), now);
        return false;
    }

    public static void tag(ServerPlayer p, Packets.TagC2S msg) {
        if (msg.tagId() == null || !ID.matcher(msg.tagId()).matches()) return;
        long now = System.currentTimeMillis();
        Long d = DERNIERE_DEMANDE_TAG.get(p.getUUID());
        if (d != null && now - d < 100) return;
        DERNIERE_DEMANDE_TAG.put(p.getUUID(), now);
        TagService.Refus refus = TagService.poser(p, msg);
        if (refus != TagService.Refus.OK && HeraliszTags.LOGGER.isDebugEnabled()) {
            HeraliszTags.LOGGER.debug("[HeraliZ Tags] pose refusee ({}) : {} par {} en {} face {}", refus, msg.tagId(),
                    p.getGameProfile().getName(), msg.point(), msg.face());
        }
        TagService.signaler(p, refus);
    }

    public static void roue(ServerPlayer p, Packets.RoueC2S msg) {
        if (tropVite(p)) return;
        TagService.roue(p, msg.ids());
    }

    public static void ouvrirIndex(ServerPlayer p) {
        if (tropVite(p)) return;
        TagService.envoyerEtat(p);
        TagsNet.toPlayer(p, new Packets.OuvrirIndexS2C());
    }

    static void oublier(UUID joueur) {
        DERNIERE_ACTION.remove(joueur);
        DERNIERE_DEMANDE_TAG.remove(joueur);
    }

    static void reinitialiser() {
        DERNIERE_ACTION.clear();
        DERNIERE_DEMANDE_TAG.clear();
    }
}
