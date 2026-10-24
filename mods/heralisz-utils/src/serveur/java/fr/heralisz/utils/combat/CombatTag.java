package fr.heralisz.utils.combat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tag de combat du serveur (déplacé de HeraliZ Matchs le 24/10, quand les matchs ont été retirés : même logique, même
 * clé) : un joueur est tagué {@link #dureeMs()} (15 s par défaut, {@code [combat] tag_secondes} de
 * {@code heraliz_combat-server.toml}) après avoir infligé ou reçu des dégâts d'un AUTRE joueur (projectiles, explosifs,
 * armes TaCZ, mêlée compris : auteur réel de la source). Jamais par un monstre, une chute, etc.
 * <p><b>Contrat pour les autres mods</b> (sans dépendre de ce code) : tant que le joueur est tagué, son
 * {@code persistentData} porte la clé long {@link #CLE_JUSQUA} = {@value #CLE_JUSQUA} (heure de fin du tag, ms) ; clé
 * absente ou 0 = libre. Lecture : {@code p.getPersistentData().getLong("heralisz:combat_jusqua") >
 * System.currentTimeMillis()}. La clé est posée à chaque marquage et retirée à la fin du tag, à la mort et à la
 * déconnexion. Par réflexion (API stable, à ne jamais renommer) : {@code fr.heralisz.utils.combat.CombatTag.restantMs(UUID)}
 * et {@link #estTague(UUID)}. Côté serveur seulement. Horloge : {@link System#currentTimeMillis()}.
 */
public final class CombatTag {
    private CombatTag() {
    }

    /** Durée par défaut du tag (ms). */
    public static final long DUREE_DEFAUT_MS = 15_000L;
    /** Clé long du {@code persistentData} du joueur : fin du tag (ms) ; absente ou 0 = libre. */
    public static final String CLE_JUSQUA = "heralisz:combat_jusqua";

    private static long dureeMs = DUREE_DEFAUT_MS;
    /** Fin du tag (ms) par joueur. */
    private static final Map<UUID, Long> FIN = new HashMap<>();

    /** Vrai si le joueur est tagué en combat maintenant. */
    public static boolean estTague(ServerPlayer joueur) {
        return joueur != null && restantMs(joueur.getUUID()) > 0;
    }

    public static boolean estTague(UUID joueur) {
        return restantMs(joueur) > 0;
    }

    /** Temps restant du tag en ms (0 si non tagué). */
    public static long restantMs(ServerPlayer joueur) {
        return joueur == null ? 0 : restantMs(joueur.getUUID());
    }

    public static long restantMs(UUID joueur) {
        Long fin = FIN.get(joueur);
        if (fin == null) return 0;
        return Math.max(0, fin - System.currentTimeMillis());
    }

    /** Fin du tag (ms), 0 si non tagué. */
    public static long finMs(UUID joueur) {
        Long fin = FIN.get(joueur);
        return fin == null || fin <= System.currentTimeMillis() ? 0 : fin;
    }

    /** Tague (ou re-tague pour une durée pleine) un joueur : clé {@link #CLE_JUSQUA} posée, indicateur envoyé. */
    public static void marquer(ServerPlayer joueur) {
        if (joueur == null) return;
        long fin = System.currentTimeMillis() + dureeMs;
        FIN.put(joueur.getUUID(), fin);
        joueur.getPersistentData().putLong(CLE_JUSQUA, fin);
        CombatServeur.indicateur(joueur);
    }

    /** Tague l'attaquant et la victime (dégâts entre deux joueurs distincts). */
    public static void marquer(ServerPlayer attaquant, ServerPlayer victime) {
        if (attaquant == null || victime == null || attaquant == victime) return;
        marquer(attaquant);
        marquer(victime);
    }

    /** Retire le tag (mort, déconnexion traitée) : clé retirée et indicateur masqué si le joueur est connecté. */
    public static void effacer(UUID joueur) {
        boolean etait = FIN.remove(joueur) != null;
        ServerPlayer p = enLigne(joueur);
        if (p != null) {
            p.getPersistentData().remove(CLE_JUSQUA);
            if (etait) CombatServeur.indicateurMasque(p);
        }
    }

    /** Durée courante du tag (ms). */
    public static long dureeMs() {
        return dureeMs;
    }

    /** Réservé à la config : durée bornée à 1..300 s. */
    static void dureeMs(long ms) {
        dureeMs = Math.max(1000L, Math.min(300_000L, ms));
    }

    /** Purge les tags expirés et retire leur clé (appel périodique, pas à chaque tick). */
    static void purger() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Long>> it = FIN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (e.getValue() > now) continue;
            it.remove();
            ServerPlayer p = enLigne(e.getKey());
            if (p != null) p.getPersistentData().remove(CLE_JUSQUA);
        }
    }

    /** Vrai si aucun joueur n'est tagué. */
    static boolean aucun() {
        return FIN.isEmpty();
    }

    /** Oublie tout (arrêt du serveur). */
    static void vider() {
        FIN.clear();
    }

    private static ServerPlayer enLigne(UUID u) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? null : s.getPlayerList().getPlayer(u);
    }
}
