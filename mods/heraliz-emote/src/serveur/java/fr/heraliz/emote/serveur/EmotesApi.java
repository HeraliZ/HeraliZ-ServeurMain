package fr.heraliz.emote.serveur;

import fr.heraliz.emote.ListeEmotes;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * API des emotes pour les autres mods (caisse des emotes de HeraliZ Caisses), appelée par réflexion : ne jamais renommer
 * cette classe ni changer ses signatures. Serveur seulement.
 */
public final class EmotesApi {
    private EmotesApi() {
    }

    /** Débloque une emote au hasard parmi celles qu'il n'a pas ; rend son id, ou null s'il les a toutes. */
    @Nullable
    public static String debloquerAuHasard(ServerPlayer joueur) {
        ListeEmotes.Emote e = Deblocages.debloquerAuHasard(joueur);
        return e == null ? null : e.id();
    }

    /** Débloque cette emote (id de la liste) ; vrai si elle vient d'être ajoutée. */
    public static boolean debloquer(ServerPlayer joueur, String id) {
        ListeEmotes.Emote e = ListeEmotes.parId(id);
        return e != null && Deblocages.debloquer(joueur, e.uuid());
    }

    /** Nom affiché d'une emote (id de la liste), ou l'id s'il est inconnu. */
    public static String nom(String id) {
        ListeEmotes.Emote e = ListeEmotes.parId(id);
        return e == null ? id : e.nom();
    }

    /** Vrai s'il ne lui reste plus aucune emote à débloquer. */
    public static boolean toutes(ServerPlayer joueur) {
        return Deblocages.de(joueur.getUUID()).size() >= ListeEmotes.toutes().size();
    }
}
