package fr.heralisz.clans.saison;

import fr.heralisz.clans.model.Arbre;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.server.ClanStore;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * API de la saison des clans pour les autres mods HeraliZ, cote serveur, appelee PAR REFLEXION (Class.forName) :
 * ne jamais renommer cette classe ni changer ces signatures (CLAUDE.md §5). Fil serveur seulement. Les points eux-memes
 * sont dans le bareme (config/heralisz_clans/saison.json) : on n'envoie qu'une source et une quantite.
 */
public final class SaisonApi {
    private SaisonApi() {
    }

    /** Saison en cours. */
    public static boolean active() {
        return Saison.active();
    }

    /**
     * Points PVE pour le clan du joueur : {@code source} = cle du bareme (« conteneur_t3 », « salle_rouge »,
     * « vente_1000 », « craft_rare », « quete_principale », « oil_rig »...), {@code quantite} = unites (1 le plus souvent ;
     * herals / 1 000 pour une vente). Sans clan, sans coeur ou hors saison : rien.
     */
    public static void pve(ServerPlayer joueur, String source, double quantite) {
        Saison.pve(joueur, source, quantite);
    }

    /**
     * Evenement de la carte reussi par un joueur (25/10, module heraliz_evenements) : points de la categorie events
     * pour son clan ({@code events.points.<source>} du bareme, plafond {@code events.par_jour.<source>} par clan et par
     * jour). Rend les points donnes (0 : sans clan, sans coeur, hors saison ou plafond).
     */
    public static double evenement(ServerPlayer joueur, String source, double quantite) {
        return Saison.evenement(joueur, source, quantite);
    }

    /**
     * Points du bareme pour un evenement ou un objet d'evenement ({@code events.points.<source>}), sans rien donner : pour
     * les afficher avant (marchand qui rachete un objet d'evenement). 0 si la source est inconnue.
     */
    public static double pointsEvenement(String source) {
        Double v = Saison.bareme().evenementsPoints.get(source);
        return v == null ? 0 : Math.max(0, v);
    }

    /** Resultat d'un event : clans dans l'ordre (1er, 2e...) et clans qui ont participe. */
    public static void event(String event, List<UUID> classement, Collection<UUID> participants) {
        Saison.event(event, classement, participants);
    }

    /** Prise du coeur pendant une Purge (CRac) : une seule par clan pris et par Purge. Faux si deja pris. */
    public static boolean raid(UUID attaquant, UUID victime, String purge, String joueur) {
        return Saison.raid(attaquant, victime, purge, joueur);
    }

    /** Le clan perd tous ses points (coeur deplace et pas repose a temps, CRac). */
    public static void perdreTout(UUID clan, String motif) {
        Saison.perdreTout(clan, motif);
    }

    /** Total des points du clan, arrondi a l'unite (coeur, mur du spawn). */
    public static long points(UUID clan) {
        double[] s = Saison.scores(clan);
        return Saison.entiers(s[0] + s[1] + s[2]);
    }

    /** {pve, pvp, events, general} ; 0 = pas classe. */
    public static int[] rangs(UUID clan) {
        return Saison.rangs(clan);
    }

    /** Lignes du classement general : {place, tag, nom, total, pve, pvp, events} (affichage, n premieres). */
    public static List<String[]> classement(int n) {
        List<String[]> l = new ArrayList<>();
        for (Saison.Ligne x : Saison.classement()) {
            if (l.size() >= n) break;
            l.add(new String[]{String.valueOf(x.rGeneral()), x.tag(), x.nom(), String.valueOf(Saison.entiers(x.total())),
                    String.valueOf(Saison.entiers(x.pve())), String.valueOf(Saison.entiers(x.pvp())), String.valueOf(Saison.entiers(x.events()))});
        }
        return l;
    }

    /** Palier achete d'une branche de l'arbre du clan (« base », « tp »...), 0 sans clan. */
    public static int palier(UUID clan, String branche) {
        ClanStore s = ClanStore.get();
        Clan c = s == null || clan == null ? null : s.get(clan);
        Arbre.Branche b = Arbre.Branche.parId(branche);
        return c == null || b == null ? 0 : c.palier(b);
    }

    /** Le clan a son coeur pose (CRac). */
    public static boolean aUnCoeur(UUID clan) {
        return Ponts.aUnCoeur(clan);
    }

    /** Clan du joueur (ou null), pour les mods qui n'ont pas l'API des clans sous la main. */
    public static UUID clanDe(UUID joueur) {
        ClanStore s = ClanStore.get();
        Clan c = s == null ? null : s.clanDe(joueur);
        return c == null ? null : c.id;
    }
}
