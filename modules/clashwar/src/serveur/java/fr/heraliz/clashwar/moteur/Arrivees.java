package fr.heraliz.clashwar.moteur;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Arrivées préchargées des téléportations du ClashWar (19/11, audit des lenteurs C1 et C3). Forge charge le tronçon
 * d'arrivée dès que la position d'un joueur change ; un tronçon absent était alors lu sur le disque et décodé avec ses
 * voisins par le fil du serveur (170 ms à l'entrée dans la salle, jusqu'à 1,5 s au retour d'un match, le 18/11 avec trois
 * joueurs). Le travail est fait par l'API commune du noyau ({@link fr.heraliz.noyau.api.Arrivees} : ticket de 5 × 5
 * tronçons, téléportation dès que les 3 × 3 du centre sont prêts, 3 s au plus, compteurs dans {@code /diag etat}) ; ici,
 * seulement ce qui est propre au ClashWar :
 * <ul>
 *     <li>le réglage {@code precharger_arrivees} de clashwar.json (faux : téléportations immédiates, comme avant) ;</li>
 *     <li>{@link #oublier} : une arrivée du ClashWar encore en attente (salle) ne ramène plus le joueur, sans toucher aux
 *     téléportations demandées par les autres mods ;</li>
 *     <li>{@link #enAttente} : une arrivée du ClashWar attend ce joueur (la garde hors match le laisse tranquille).</li>
 * </ul>
 * Fil serveur ; rien ne tourne ici à chaque tick.
 */
public final class Arrivees {
    private Arrivees() {
    }

    /** Au plus ce délai entre la demande et l'action (ticks), comme le noyau. */
    private static final int ATTENTE_MAX = fr.heraliz.noyau.api.Arrivees.ATTENTE_MAX;

    /** Jeton de la dernière arrivée du ClashWar en attente de chaque joueur (oublié : l'action ne fait plus rien). */
    private static final Map<UUID, Object> EN_COURS = new HashMap<>();
    private static boolean actif = true;

    /** Réglage {@code precharger_arrivees} (faux : téléportations immédiates, comme avant le 19/11). */
    public static void regler(boolean precharger) {
        actif = precharger;
    }

    /** Demande à Minecraft les tronçons autour de (x, z) de ce monde, en tâche de fond ; rend tout de suite. */
    public static void precharger(ServerLevel l, double x, double z) {
        if (actif) fr.heraliz.noyau.api.Arrivees.precharger(l, x, z);
    }

    /** Les 3 × 3 tronçons autour de (x, z) sont-ils chargés (sans rien charger) ? Toujours vrai sans préchargement. */
    public static boolean pret(ServerLevel l, double x, double z) {
        return !actif || fr.heraliz.noyau.api.Arrivees.prete(l, x, z);
    }

    /**
     * Fait {@code action} (la téléportation de {@code joueur} vers (x, z) de {@code l}) dès que l'arrivée est chargée : tout
     * de suite si elle l'est déjà (ou sans préchargement), sinon dans quelques ticks (au plus 3 s). Rien si le joueur se
     * déconnecte ou meurt avant, ou si {@link #oublier} est appelé entre-temps ; une nouvelle demande pour ce joueur remplace
     * celle qui attendait.
     */
    public static void puis(ServerPlayer joueur, ServerLevel l, double x, double z, Runnable action) {
        UUID u = joueur.getUUID();
        if (!actif) {
            EN_COURS.remove(u);
            action.run();
            return;
        }
        // jeton posé avant la demande : le noyau peut faire l'action tout de suite (arrivée déjà prête)
        Object jeton = new Object();
        EN_COURS.put(u, jeton);
        fr.heraliz.noyau.api.Arrivees.puis(joueur, l, x, z, ATTENTE_MAX, () -> {
            if (EN_COURS.remove(u, jeton)) action.run();
        }, () -> EN_COURS.remove(u, jeton));
    }

    /** Ce joueur attend-il une arrivée du ClashWar ? */
    public static boolean enAttente(UUID joueur) {
        return !EN_COURS.isEmpty() && EN_COURS.containsKey(joueur);
    }

    /** Retour, spectateur, déconnexion : son arrivée du ClashWar en attente ne fera plus rien. */
    public static void oublier(UUID joueur) {
        if (!EN_COURS.isEmpty()) EN_COURS.remove(joueur);
    }

    /** Nombre d'arrivées du ClashWar en attente (moniteur des listes du noyau). */
    public static int attentes() {
        return EN_COURS.size();
    }

    static void vider() {
        EN_COURS.clear();
    }
}
