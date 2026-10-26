package fr.heralisz.clans.api;

import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Grade;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Photo immuable d'un clan pour les autres mods (HeraliZ Bases, Matchs), prise au moment de l'appel a
 * {@link ClansApi#infos} / {@link ClansApi#tous} : ne pas la garder longtemps, la redemander.
 *
 * @param id             identifiant stable du clan
 * @param tag            tag (2 a 5 caracteres A-Z 0-9)
 * @param nom            nom affiche
 * @param banniere       banniere du clan (immuable ; {@code banniere.accent()} = couleur du tag)
 * @param chef           UUID du chef
 * @param membres        joueur -> grade (le chef y figure avec {@link Grade#CHEF}), ordre d'arrivee
 * @param noms           joueur -> dernier pseudo connu (affichage hors ligne)
 * @param enLigne        membres connectes a ce serveur
 * @param niveau         niveau du clan (1 a 20)
 * @param clashwarJoues  ClashWar disputes
 * @param clashwarGagnes ClashWar gagnes
 */
public record ClanInfo(UUID id, String tag, String nom, Banniere banniere, UUID chef, Map<UUID, Grade> membres,
                       Map<UUID, String> noms, Set<UUID> enLigne, int niveau, int clashwarJoues, int clashwarGagnes) {

    /** Grade d'un joueur dans ce clan, ou null s'il n'en fait pas partie. */
    public Grade gradeDe(UUID joueur) {
        return membres.get(joueur);
    }

    public boolean estMembre(UUID joueur) {
        return membres.containsKey(joueur);
    }

    /** « [TAG] » sans couleur. */
    public String tagCrochets() {
        return "[" + tag + "]";
    }
}
