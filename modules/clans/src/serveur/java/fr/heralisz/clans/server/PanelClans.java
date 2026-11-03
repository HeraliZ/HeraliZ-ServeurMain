package fr.heralisz.clans.server;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Niveaux;
import fr.heralisz.clans.model.SourceXp;
import fr.heralisz.clans.saison.Saison;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Les clans vus et changés depuis le panel du staff de HeraliZ Grades (onglet Économie, 03/11, demande du propriétaire :
 * « donner / retirer des points de clan ») : la liste des clans, leurs points de saison par catégorie et leur XP.
 * Appelée directement par HeraliZ Grades (même fabrication) : ne pas renommer sans changer le panel
 * ({@code fr.heraliz.grades.serveur.PanelEconomie}). Le panel a déjà vérifié le Fondateur ; ici, tout le reste est revérifié
 * (clan, catégorie, montant, assez à retirer). Fil serveur seulement.
 * <ul>
 *     <li><b>points de saison</b> : la correction du staff de {@code /saison points} ({@link Saison#corriger}), notée au
 *     journal de la saison ({@code heralisz_clans/saison_journal.log}) avec le Fondateur et son motif ; jamais en dessous
 *     de 0 (retirer plus que le clan n'a est refusé) ;</li>
 *     <li><b>XP du clan</b> : donnée comme {@code /clan admin xp} (source « admin », jamais plafonnée ; le clan peut monter
 *     de niveau) ; retirée seulement dans le niveau en cours (un clan ne redescend jamais de niveau : ses places de membre
 *     et ses améliorations restent) ; notée au journal du serveur.</li>
 * </ul>
 */
public final class PanelClans {
    private PanelClans() {
    }

    /** Un clan dans la liste du panel : tag, nom, niveau. */
    public record Ligne(String tag, String nom, int niveau) {
    }

    /**
     * Résultat d'une lecture ou d'un changement : {@code code} = « ok », « introuvable » (aucun clan à ce tag),
     * « categorie » (catégorie inconnue), « insuffisant » (pas assez à retirer : {@code avant}), « indisponible » (clans pas
     * chargés), « montant » (montant hors bornes) ; tag et nom du clan ; valeur avant et après (points entiers, ou XP du
     * niveau en cours) ; niveau du clan avant et après.
     */
    public record Resultat(String code, String tag, String nom, long avant, long apres, int niveauAvant, int niveauApres) {
        static Resultat non(String code, String tag) {
            return new Resultat(code, tag, "", 0, 0, 0, 0);
        }
    }

    /** Plus gros changement par opération (une faute de frappe ne fausse pas tout le classement). */
    public static final long MAX_POINTS = 100_000L, MAX_XP = 10_000_000L;

    /** Les clans, par tag (au plus {@code max}). */
    public static List<Ligne> clans(int max) {
        ClanStore s = ClanStore.get();
        List<Ligne> l = new ArrayList<>();
        if (s == null) return l;
        for (Clan c : s.tous()) l.add(new Ligne(c.tag, c.nom, c.niveau));
        l.sort((a, b) -> a.tag().compareToIgnoreCase(b.tag()));
        return l.size() > max ? new ArrayList<>(l.subList(0, max)) : l;
    }

    private static int categorie(String c) {
        if (c == null) return -1;
        String s = c.toLowerCase(Locale.ROOT);
        for (int i = 0; i < Saison.CATEGORIES.length; i++) if (Saison.CATEGORIES[i].equals(s)) return i;
        return -1;
    }

    /** Points de saison (entiers) du clan dans une catégorie (« pve », « pvp », « events »), sans rien changer. */
    public static Resultat voirPoints(String tag, String categorie) {
        ClanStore s = ClanStore.get();
        if (s == null) return Resultat.non("indisponible", tag);
        Clan c = s.parTag(tag);
        if (c == null) return Resultat.non("introuvable", tag);
        int cat = categorie(categorie);
        if (cat < 0) return Resultat.non("categorie", c.tag);
        long v = Saison.entiers(Saison.scores(c.id)[cat]);
        return new Resultat("ok", c.tag, c.nom, v, v, c.niveau, c.niveau);
    }

    /**
     * Donne ({@code delta} &gt; 0) ou retire ({@code delta} &lt; 0) des points de saison au clan dans une catégorie : la
     * correction du staff, notée au journal de la saison (« correction : motif », au nom du Fondateur).
     */
    public static Resultat points(String tag, String categorie, long delta, String motif, String staff) {
        ClanStore s = ClanStore.get();
        if (s == null) return Resultat.non("indisponible", tag);
        Clan c = s.parTag(tag);
        if (c == null) return Resultat.non("introuvable", tag);
        int cat = categorie(categorie);
        if (cat < 0) return Resultat.non("categorie", c.tag);
        if (delta == 0 || Math.abs(delta) > MAX_POINTS) return Resultat.non("montant", c.tag);
        long avant = Saison.entiers(Saison.scores(c.id)[cat]);
        if (delta < 0 && -delta > avant) return new Resultat("insuffisant", c.tag, c.nom, avant, avant, c.niveau, c.niveau);
        Saison.corriger(c, cat, delta, "panel du staff : " + motif, staff);
        long apres = Saison.entiers(Saison.scores(c.id)[cat]);
        return new Resultat("ok", c.tag, c.nom, avant, apres, c.niveau, c.niveau);
    }

    /** XP du clan dans son niveau en cours, sans rien changer ({@code avant} = XP, niveau). */
    public static Resultat voirXp(String tag) {
        ClanStore s = ClanStore.get();
        if (s == null) return Resultat.non("indisponible", tag);
        Clan c = s.parTag(tag);
        if (c == null) return Resultat.non("introuvable", tag);
        return new Resultat("ok", c.tag, c.nom, c.xp, c.xp, c.niveau, c.niveau);
    }

    /**
     * Donne ({@code delta} &gt; 0, comme {@code /clan admin xp} : le clan peut monter de niveau) ou retire ({@code delta}
     * &lt; 0, seulement l'XP du niveau en cours : jamais de niveau perdu) de l'XP au clan ; noté au journal du serveur.
     */
    public static Resultat xp(String tag, long delta, String motif, String staff) {
        ClanStore s = ClanStore.get();
        if (s == null) return Resultat.non("indisponible", tag);
        Clan c = s.parTag(tag);
        if (c == null) return Resultat.non("introuvable", tag);
        if (delta == 0 || Math.abs(delta) > MAX_XP) return Resultat.non("montant", c.tag);
        long avant = c.xp;
        int niveauAvant = c.niveau;
        if (delta > 0) {
            ClanService.ajouterXp(c, SourceXp.ADMIN, delta, null);
        } else {
            if (-delta > c.xp) return new Resultat("insuffisant", c.tag, c.nom, avant, avant, niveauAvant, niveauAvant);
            c.xp += delta;
            s.marquerDirty();
        }
        Sync.marquer(c);
        HeraliszClans.LOG.info("[clans] [{}] XP du staff (panel) : {}{} par {} ({}) : niveau {} {}/{} -> niveau {} {}/{}", c.tag, delta > 0 ? "+" : "",
                delta, staff, motif, niveauAvant, avant, Niveaux.xpPour(niveauAvant), c.niveau, c.xp, Niveaux.xpPour(c.niveau));
        return new Resultat("ok", c.tag, c.nom, avant, c.xp, niveauAvant, c.niveau);
    }
}
