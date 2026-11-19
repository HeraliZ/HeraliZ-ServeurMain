package fr.heraliz.noyau.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Téléportations sans gel (19/11, audit des lenteurs, point C1) : un joueur posé dans un tronçon pas encore prêt le fait
 * charger tout de suite par le fil du serveur (Forge charge le tronçon d'arrivée dès que la position change), avec tout le
 * travail en file : 0,4 à 2,5 s de gel pour tout le serveur, vus à chaque {@code /tp}, {@code /rtp}, {@code /spawn} et
 * retour de match du 18/11.
 * <p>Ici, l'arrivée est d'abord demandée à Minecraft (ticket de 5 × 5 tronçons, 30 s), qui la lit en tâche de fond et la
 * décode entre les ticks, comme quand on marche ; l'action (la téléportation) est faite dès que les 3 × 3 tronçons autour
 * du point sont complets ({@code getChunkNow}), le plus souvent au tick même ou 0,1 à 0,5 s plus tard. Au plus 3 s
 * d'attente : passé ce délai, l'action est faite quand même (comme avant). Le joueur déconnecté, mort ou réapparu entre-temps
 * : rien n'est fait (l'action d'abandon seulement, si elle est donnée). Une nouvelle demande pour le même joueur remplace
 * l'ancienne (son action d'abandon est jouée).
 * <p>À appeler sur le fil du serveur. Le travail est fait par la partie serveur du noyau ; sans elle (jar du joueur), les
 * actions sont faites tout de suite. Réglage : {@code [perf] arrivees_prechargees} de {@code heraliz_noyau-server.toml}
 * (false : tout de suite, comme avant). Compteurs dans {@code /diag etat}.
 * <p>API stable, lue par d'autres mods (ClashWar, boutique, monde, bases…) : ne jamais renommer la classe ni ses méthodes.
 * <pre>
 *    Arrivees.teleporter(joueur, niveau, x, y, z, lacet, tangage);          // le plus simple
 *    Arrivees.puis(joueur, niveau, x, z, () -> { ...; joueur.teleportTo(...); });
 *    Arrivees.precharger(niveau, x, z);                                     // pendant un compte à rebours, sans attendre
 * </pre>
 */
public final class Arrivees {
    private Arrivees() {
    }

    /** Attente la plus longue avant de faire l'action quand même (ticks) : 3 s. */
    public static final int ATTENTE_MAX = 60;

    /** Le travail, branché par la partie serveur du noyau. */
    public interface Service {
        void puis(ServerPlayer joueur, ServerLevel niveau, double x, double z, int attenteMax, Runnable action, @Nullable Runnable siAbandon);

        void precharger(ServerLevel niveau, double x, double z);

        boolean prete(ServerLevel niveau, double x, double z);

        void garder(ServerLevel niveau, double x, double z);

        void lacher(ServerLevel niveau, double x, double z);

        boolean enAttente(ServerPlayer joueur);

        void annuler(ServerPlayer joueur);
    }

    private static volatile Service service;

    /** Partie serveur du noyau, au chargement. */
    public static void brancher(Service s) {
        service = s;
    }

    /**
     * L'action dès que l'arrivée (x, z) de {@code niveau} est prête, 3 s au plus ; rien si le joueur part ou meurt avant.
     * Tout de suite si l'arrivée est déjà prête.
     */
    public static void puis(ServerPlayer joueur, ServerLevel niveau, double x, double z, Runnable action) {
        puis(joueur, niveau, x, z, ATTENTE_MAX, action, null);
    }

    /**
     * Comme {@link #puis(ServerPlayer, ServerLevel, double, double, Runnable)}, avec l'attente la plus longue (ticks, 1 à
     * 200) et l'action jouée si la téléportation n'a pas lieu (joueur parti, mort, réapparu, monde fermé, demande remplacée).
     */
    public static void puis(ServerPlayer joueur, ServerLevel niveau, double x, double z, int attenteMax, Runnable action,
                            @Nullable Runnable siAbandon) {
        Service s = service;
        if (s == null || joueur == null || niveau == null) {
            action.run();
            return;
        }
        s.puis(joueur, niveau, x, z, Math.max(1, Math.min(200, attenteMax)), action, siAbandon);
    }

    /**
     * Vraie téléportation du joueur (descend de sa monture, suivie par l'anticheat) dès que l'arrivée est prête, 3 s au
     * plus. La vitesse du joueur est remise à zéro à l'arrivée.
     */
    public static void teleporter(ServerPlayer joueur, ServerLevel niveau, double x, double y, double z, float lacet, float tangage) {
        teleporter(joueur, niveau, x, y, z, lacet, tangage, null);
    }

    /** Comme {@link #teleporter(ServerPlayer, ServerLevel, double, double, double, float, float)}, puis {@code apres}. */
    public static void teleporter(ServerPlayer joueur, ServerLevel niveau, double x, double y, double z, float lacet, float tangage,
                                  @Nullable Runnable apres) {
        puis(joueur, niveau, x, z, () -> {
            if (joueur.isPassenger()) joueur.stopRiding();
            joueur.teleportTo(niveau, x, y, z, lacet, tangage);
            joueur.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            joueur.hurtMarked = true;
            if (apres != null) apres.run();
        });
    }

    /** Demande l'arrivée à Minecraft (5 × 5 tronçons, ticket de 30 s), en tâche de fond ; rend tout de suite. */
    public static void precharger(ServerLevel niveau, double x, double z) {
        Service s = service;
        if (s != null && niveau != null) s.precharger(niveau, x, z);
    }

    /** Les 3 × 3 tronçons autour de (x, z) sont complets (rien n'est chargé pour le savoir). */
    public static boolean prete(ServerLevel niveau, double x, double z) {
        Service s = service;
        return s == null || niveau == null || s.prete(niveau, x, z);
    }

    /** Point d'arrivée fixe (point de réapparition, salle d'attente) gardé chargé jusqu'à {@link #lacher}. */
    public static void garder(ServerLevel niveau, double x, double z) {
        Service s = service;
        if (s != null && niveau != null) s.garder(niveau, x, z);
    }

    /** Ne garde plus chargé le point posé par {@link #garder} (mêmes coordonnées). */
    public static void lacher(ServerLevel niveau, double x, double z) {
        Service s = service;
        if (s != null && niveau != null) s.lacher(niveau, x, z);
    }

    /** Une téléportation attend l'arrivée de ce joueur. */
    public static boolean enAttente(ServerPlayer joueur) {
        Service s = service;
        return s != null && joueur != null && s.enAttente(joueur);
    }

    /** Oublie la téléportation en attente de ce joueur (son action d'abandon est jouée). */
    public static void annuler(ServerPlayer joueur) {
        Service s = service;
        if (s != null && joueur != null) s.annuler(joueur);
    }
}
