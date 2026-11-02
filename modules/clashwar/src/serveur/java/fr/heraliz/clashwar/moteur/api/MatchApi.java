package fr.heraliz.clashwar.moteur.api;

import fr.heraliz.clashwar.pont.Services;

import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parties en cours du ClashWar. Fil serveur uniquement. Les autres mods ne lisent pas cette classe : ils passent par le
 * noyau ({@code fr.heraliz.noyau.api.Matchs}), branche sur elle par {@code MoteurLogique}.
 * <p>Un joueur est « en match » entre {@link #rejoindre(ServerPlayer, Partie)} et {@link #quitter(ServerPlayer)} :
 * son {@code persistentData} porte alors le drapeau booleen {@link #DRAPEAU_EN_MATCH} (lu par les mods clans et camo
 * pour ignorer ses morts et ses kills). En match : jamais de tag de combat, malus d'ArkaniaZ refuses et retires
 * ({@code moteur.combat.RestrictionsMatch}).
 */
public final class MatchApi {
    private MatchApi() {
    }

    /** Cle booleenne de {@code player.getPersistentData()} posee pendant un match. */
    public static final String DRAPEAU_EN_MATCH = fr.heraliz.noyau.api.Matchs.CLE_EN_MATCH;
    /** Cle NBT (chaine = id de la partie) des objets pretes en match (voir {@link Equipement}). */
    public static final String NBT_PRET = fr.heraliz.noyau.api.Matchs.NBT_PRET;

    private static final Map<String, Partie> PARTIES = new LinkedHashMap<>();
    private static final Map<UUID, Partie> PAR_JOUEUR = new HashMap<>();

    /** Vrai si le joueur est dans une partie (inscrit, vivant ou spectateur). */
    public static boolean enMatch(ServerPlayer joueur) {
        return joueur != null && PAR_JOUEUR.containsKey(joueur.getUUID());
    }

    /** Variante par UUID (joueur eventuellement deconnecte). */
    public static boolean enMatch(UUID joueur) {
        return PAR_JOUEUR.containsKey(joueur);
    }

    /** Partie du joueur, ou null. */
    @Nullable
    public static Partie partieDe(ServerPlayer joueur) {
        return joueur == null ? null : PAR_JOUEUR.get(joueur.getUUID());
    }

    @Nullable
    public static Partie partieDe(UUID joueur) {
        return PAR_JOUEUR.get(joueur);
    }

    /** Partie par identifiant, ou null. */
    @Nullable
    public static Partie partie(String id) {
        return PARTIES.get(id);
    }

    /** Parties actives (ordre de creation), non modifiable. */
    public static Collection<Partie> parties() {
        return Collections.unmodifiableCollection(PARTIES.values());
    }

    /** Inscrit la partie dans le moteur (elle recoit alors ses ticks). */
    public static void enregistrer(Partie partie) {
        PARTIES.put(partie.id(), partie);
    }

    /** Retire la partie du moteur et detache ses joueurs encore rattaches (appele par {@link Partie#fermer()}). */
    public static void retirer(Partie partie) {
        PARTIES.remove(partie.id());
        PAR_JOUEUR.values().removeIf(p -> p == partie);
    }

    /**
     * Rattache le joueur a la partie et pose le drapeau {@link #DRAPEAU_EN_MATCH}. A appeler <b>apres</b>
     * {@link Coffre#deposer} reussi. Un joueur n'est que dans une partie a la fois (l'ancienne est remplacee).
     */
    public static void rejoindre(ServerPlayer joueur, Partie partie) {
        PAR_JOUEUR.put(joueur.getUUID(), partie);
        joueur.getPersistentData().putBoolean(DRAPEAU_EN_MATCH, true);
        // malus d'ArkaniaZ retires, etat envoye au client (tout de suite, sans attendre le tick)
        Services.Moteur m = Services.MOTEUR;   // moteur/combat/RestrictionsMatch (serveur)
        if (m != null) m.restrictionsSynchroniser(joueur);
    }

    /** Detache le joueur de sa partie et retire le drapeau (avant ou apres {@link Coffre#rendre}). */
    public static void quitter(ServerPlayer joueur) {
        PAR_JOUEUR.remove(joueur.getUUID());
        joueur.getPersistentData().remove(DRAPEAU_EN_MATCH);
        Services.Moteur m = Services.MOTEUR;   // moteur/combat/RestrictionsMatch (serveur)
        if (m != null) m.restrictionsSynchroniser(joueur);
    }

    /** Detache un joueur deconnecte (le drapeau sera retire a sa reconnexion par le moteur). */
    public static void quitter(UUID joueur) {
        PAR_JOUEUR.remove(joueur);
    }

    /**
     * Rattache un joueur deconnecte a une autre partie (son drapeau est deja pose) : a sa reconnexion, c'est elle qui le
     * reprend ({@link Partie#surReconnexion}). Sert quand la partie ou il s'est deconnecte se ferme sans lui (match du
     * ClashWar fini pendant son absence : il garde sa place dans la salle d'attente au lieu d'etre sorti du tournoi).
     */
    public static void rattacher(UUID joueur, Partie partie) {
        PAR_JOUEUR.put(joueur, partie);
    }

    /** Joueurs rattaches a cette partie (nouvelle liste d'UUID). */
    public static List<UUID> joueursDe(Partie partie) {
        List<UUID> l = new ArrayList<>();
        for (Map.Entry<UUID, Partie> e : PAR_JOUEUR.entrySet()) if (e.getValue() == partie) l.add(e.getKey());
        return l;
    }

    /** Reserve au moteur : oublie tout (arret du serveur). */
    public static void vider() {
        PARTIES.clear();
        PAR_JOUEUR.clear();
    }
}
