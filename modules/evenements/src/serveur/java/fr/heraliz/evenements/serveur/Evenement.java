package fr.heraliz.evenements.serveur;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Un événement de la carte (docs/EVENEMENTS.md : sa liste de branchement). Une classe par événement, déclarée au
 * {@link Directeur} dans {@link PartieServeur}. Le Directeur vérifie les conditions communes (joueurs sur la carte, pas de
 * ClashWar, un seul exemplaire actif) ; l'événement vérifie les siennes ({@link #refus}). Tout se passe sur le fil serveur.
 */
public interface Evenement {
    /** Identifiant (« largage ») : clé du planning, du barème de la saison, des lieux et des textes. */
    String id();

    /** Raison pour laquelle il ne peut pas partir maintenant (pour le staff et le journal), null s'il peut. */
    @Nullable
    String refus(MinecraftServer server);

    /**
     * Lance l'événement n° {@code numero} (« largage#12 ») ; {@code points} : il rapporte des points de saison (planning,
     * ou staff avec « avec_points »). Rend faux s'il n'a pas pu partir (aucun lieu valide...) : rien n'a changé.
     */
    boolean demarrer(MinecraftServer server, int numero, boolean points, String par);

    boolean actif();

    /** Une fois par seconde tant qu'il est actif. */
    void seconde(MinecraftServer server);

    /** Arrêt (staff, arrêt du serveur) : rien n'est donné. */
    void annuler(MinecraftServer server, String motif);

    /**
     * Une fois par minute, actif ou non : pour un événement sans heure dans le planning, qui décide lui-même quand partir
     * (hélicoptère : 4 h après le précédent, puis un instant au hasard ; il demande son départ par {@code Directeur.lancer}).
     * Rien par défaut.
     */
    default void minute(MinecraftServer server) {
    }

    /** Relit ses réglages (démarrage du serveur et {@code /event recharger}). */
    default void recharger() {
    }

    /** Un coffre de cet événement est ouvert pour la première fois ({@code id} : « largage#12 »). */
    default void coffreOuvert(ServerPlayer p, String id) {
    }
}
