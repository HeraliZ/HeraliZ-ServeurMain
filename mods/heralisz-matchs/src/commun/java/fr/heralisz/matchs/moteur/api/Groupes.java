package fr.heralisz.matchs.moteur.api;

import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Groupes de joueurs ({@code /groupe inviter|accepter|refuser|quitter|exclure|chef}, invitations 60 s, toast + ecran
 * leger : MATCHS_SPEC §1.6). Lecture pour les modes ; les commandes et invitations sont dans le moteur
 * ({@code moteur.groupes}). Etat en memoire (un groupe ne survit pas a un redemarrage). Fil serveur uniquement.
 */
public final class Groupes {
    /** Actions du paquet GroupeC2S (écran du client) ; reprises par moteur/groupes/GroupesService (serveur). */
    public static final int ACTION_OUVRIR = 0, ACTION_INVITER = 1, ACTION_ACCEPTER = 2, ACTION_REFUSER = 3, ACTION_QUITTER = 4,
            ACTION_EXCLURE = 5, ACTION_CHEF = 6;

    private Groupes() {
    }

    /** Duree de validite d'une invitation (ms). */
    public static final long INVITATION_MS = 60_000L;

    private static final Map<UUID, Groupe> PAR_JOUEUR = new HashMap<>();

    /** Groupe du joueur, ou null s'il n'est dans aucun groupe. */
    @Nullable
    public static Groupe de(UUID joueur) {
        return PAR_JOUEUR.get(joueur);
    }

    /** Groupe du joueur, ou un groupe solo s'il n'en a pas (jamais null). */
    public static Groupe deOuSolo(ServerPlayer joueur) {
        Groupe g = PAR_JOUEUR.get(joueur.getUUID());
        return g != null ? g : Groupe.solo(joueur.getUUID());
    }

    /** Vrai si les deux joueurs sont dans le meme groupe. */
    public static boolean ensemble(UUID a, UUID b) {
        Groupe g = PAR_JOUEUR.get(a);
        return g != null && g.contient(b);
    }

    /** Tous les groupes existants (nouvelle collection, sans doublon). */
    public static Collection<Groupe> tous() {
        return Collections.unmodifiableCollection(new LinkedHashSet<>(PAR_JOUEUR.values()));
    }

    // ------------------------------------------------------------------ reserve au moteur
    /**
     * Reserve au moteur : enregistre (ou remplace) un groupe pour tous ses membres. Les anciens membres absents du
     * nouveau groupe sont detaches. Un groupe d'un seul joueur est dissous (retire).
     */
    public static void definir(Groupe ancien, @Nullable Groupe nouveau) {
        if (ancien != null) for (UUID u : ancien.membres()) PAR_JOUEUR.remove(u, ancien);
        if (nouveau != null && nouveau.taille() > 1) for (UUID u : nouveau.membres()) PAR_JOUEUR.put(u, nouveau);
    }

    /** Reserve au moteur : oublie tout (arret du serveur). */
    public static void vider() {
        PAR_JOUEUR.clear();
    }
}
