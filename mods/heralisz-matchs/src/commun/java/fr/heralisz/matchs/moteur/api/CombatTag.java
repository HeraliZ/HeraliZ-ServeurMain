package fr.heralisz.matchs.moteur.api;

import fr.heralisz.matchs.pont.Services;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tag de combat du serveur (MATCHS_SPEC §1.4, RETOURS du 01/10 §3) : un joueur est tague {@link #dureeMs()} (15 s par
 * defaut, config {@code combat.tag_secondes}) apres avoir inflige ou recu des degats d'un AUTRE joueur (projectiles,
 * explosifs, armes TaCZ, melee heralisz compris : auteur reel de la source). Jamais par un mob, une chute, etc. ; jamais
 * pendant un match. Utilise par les matchs (refus du Coffre, joueur qui rate son match) et par les bases (entree,
 * pillage).
 * <p><b>Contrat pour les autres mods</b> (sans dependre de ce code) : tant que le joueur est tague, son
 * {@code persistentData} porte la cle long {@link #CLE_JUSQUA} = {@value #CLE_JUSQUA} (epoch ms de fin du tag) ; cle
 * absente ou 0 = libre. Lecture : {@code p.getPersistentData().getLong("heralisz:combat_jusqua") >
 * System.currentTimeMillis()}. La cle est posee a chaque marquage et retiree a la fin du tag, a la mort, au depot du
 * Coffre et a la deconnexion. En Java (dependance ou reflexion) : {@link #estTague(UUID)}, {@link #restantMs(UUID)}.
 * <p>Ce que le moteur fait du tag ({@code moteur.combat.CombatService}) : indicateur HUD discret « EN COMBAT 12 s »
 * (aucun message dans le chat), deconnexion pendant le tag = mort (le corps reste), commandes de teleportation
 * refusees (config). Horloge : {@link System#currentTimeMillis()}. Fil serveur uniquement ; lectures sans allocation.
 */
public final class CombatTag {
    private CombatTag() {
    }

    /** Duree par defaut du tag (ms). */
    public static final long DUREE_DEFAUT_MS = 15_000L;
    /** Cle long du {@code persistentData} du joueur : fin du tag (epoch ms) ; absente ou 0 = libre (ajout). */
    public static final String CLE_JUSQUA = "heralisz:combat_jusqua";

    private static long dureeMs = DUREE_DEFAUT_MS;
    /** Fin du tag (epoch ms) par joueur. */
    private static final Map<UUID, Long> FIN = new HashMap<>();

    /** Vrai si le joueur est tague en combat maintenant. */
    public static boolean estTague(ServerPlayer joueur) {
        return joueur != null && restantMs(joueur.getUUID()) > 0;
    }

    public static boolean estTague(UUID joueur) {
        return restantMs(joueur) > 0;
    }

    /** Temps restant du tag en ms (0 si non tague). */
    public static long restantMs(ServerPlayer joueur) {
        return joueur == null ? 0 : restantMs(joueur.getUUID());
    }

    public static long restantMs(UUID joueur) {
        Long fin = FIN.get(joueur);
        if (fin == null) return 0;
        long r = fin - System.currentTimeMillis();
        return Math.max(0, r);
    }

    /** Fin du tag (epoch ms), 0 si non tague (ajout). */
    public static long finMs(UUID joueur) {
        Long fin = FIN.get(joueur);
        return fin == null || fin <= System.currentTimeMillis() ? 0 : fin;
    }

    /**
     * Tague (ou re-tague pour une duree pleine) un joueur : cle {@link #CLE_JUSQUA} posee, indicateur HUD envoye.
     * Sans effet pour un joueur en match (jamais de tag pendant un match).
     */
    public static void marquer(ServerPlayer joueur) {
        if (joueur == null || MatchApi.enMatch(joueur)) return;
        long fin = System.currentTimeMillis() + dureeMs;
        FIN.put(joueur.getUUID(), fin);
        joueur.getPersistentData().putLong(CLE_JUSQUA, fin);
        Services.Moteur m = Services.MOTEUR;   // moteur/combat/CombatService (serveur)
        if (m != null) m.combatApresMarquage(joueur);
    }

    /** Tague l'attaquant et la victime (degats entre deux joueurs distincts). */
    public static void marquer(ServerPlayer attaquant, ServerPlayer victime) {
        if (attaquant == null || victime == null || attaquant == victime) return;
        marquer(attaquant);
        marquer(victime);
    }

    /** Retire le tag (mort, deconnexion traitee, entree en match) : cle retiree et indicateur masque si connecte. */
    public static void effacer(UUID joueur) {
        boolean etait = FIN.remove(joueur) != null;
        ServerPlayer p = enLigne(joueur);
        if (p != null) {
            p.getPersistentData().remove(CLE_JUSQUA);
            Services.Moteur m = Services.MOTEUR;
            if (etait && m != null) m.combatApresEffacement(p);
        }
    }

    /** Duree courante du tag (ms). */
    public static long dureeMs() {
        return dureeMs;
    }

    /** Reserve au moteur : duree lue en config (bornee a 1..300 s). */
    public static void dureeMs(long ms) {
        dureeMs = Math.max(1000L, Math.min(300_000L, ms));
    }

    /** Reserve au moteur : purge les tags expires et retire leur cle (appel periodique, pas a chaque tick). */
    public static void purger() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Long>> it = FIN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (e.getValue() > now) continue;
            it.remove();
            ServerPlayer p = enLigne(e.getKey());
            if (p != null) p.getPersistentData().remove(CLE_JUSQUA);
        }
    }

    /** Reserve au moteur : vrai si aucun joueur n'est tague (le tick du moteur s'arrete alors tout de suite). */
    public static boolean aucun() {
        return FIN.isEmpty();
    }

    /** Reserve au moteur : oublie tout (arret du serveur). */
    public static void vider() {
        FIN.clear();
    }

    private static ServerPlayer enLigne(UUID u) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? null : s.getPlayerList().getPlayer(u);
    }
}
