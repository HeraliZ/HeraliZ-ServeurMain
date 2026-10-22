package fr.heralisz.clans.model;

import java.util.EnumMap;
import java.util.Locale;

/**
 * Arbre d'ameliorations d'un clan (saison des clans, decision du 22/10, docs/SAISON_CLANS.md §6) : chaque niveau du clan
 * donne un point de competence ; le palier n d'une branche coute n points et n x 5 000 herals (banque du clan) ; jamais
 * rembourse. Tout ce qui touche au jeu passe par ici (membres, territoires, revenu, ravitaillement, loot, taille de la
 * base, delai du TP vers la base) ; la deco (banniere, tag) reste donnee par le niveau ({@link Avantages}).
 * Lu des deux cotes (serveur : regles ; joueur : onglet Niveau) : memes chiffres partout.
 */
public final class Arbre {
    private Arbre() {
    }

    /** Herals du palier n : n x PRIX_PALIER. */
    public static final long PRIX_PALIER = 5_000L;

    public enum Branche {
        /** Membres : 5, puis 6 a 10. */
        MEMBRES(5),
        /** Rayon de construction autour de l'armoire : 20, puis 23, 26, 29 blocs (CRac). */
        BASE(3),
        /** Delai entre deux TP vers la base (/home de CRac) : 10, puis 8, 6, 4 min. */
        TP(3),
        /** Revenu des territoires : +10 / +20 / +30 %. */
        REVENU(3),
        /** Delai de ravitaillement : -15 / -30 / -45 %. */
        RAVITAILLEMENT(3),
        /** Bonus de loot dans ses territoires : +2 / +4 %. */
        LOOT(2),
        /** Territoires tenus en meme temps : 1, puis 2, 3. */
        TERRITOIRES(2);

        public final int max;

        Branche(int max) {
            this.max = max;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Branche parId(String s) {
            for (Branche b : values()) if (b.id().equalsIgnoreCase(s)) return b;
            return null;
        }
    }

    /** Points du palier p (1..max) d'une branche. */
    public static int coutPoints(int palier) {
        return palier;
    }

    public static long coutHerals(int palier) {
        return palier * PRIX_PALIER;
    }

    /** Points que coutent tous les paliers achetes. */
    public static int pointsDepenses(EnumMap<Branche, Integer> arbre) {
        int t = 0;
        for (var e : arbre.entrySet()) for (int p = 1; p <= e.getValue(); p++) t += coutPoints(p);
        return t;
    }

    /** Points pour tout acheter : le niveau maximum du clan ({@link Niveaux#MAX}). */
    public static int pointsTotaux() {
        int t = 0;
        for (Branche b : Branche.values()) for (int p = 1; p <= b.max; p++) t += coutPoints(p);
        return t;
    }

    /** Points de competence gagnes au niveau n : un par niveau. */
    public static int pointsGagnes(int niveau) {
        return Math.max(0, niveau);
    }

    public static int palier(EnumMap<Branche, Integer> arbre, Branche b) {
        Integer p = arbre.get(b);
        return p == null ? 0 : Math.max(0, Math.min(b.max, p));
    }

    // ------------------------------------------------------------------ effets (palier -> valeur)
    public static int membresMax(int palier) {
        return 5 + palier;
    }

    public static int territoiresMax(int palier) {
        return 1 + palier;
    }

    public static int rayonBase(int palier) {
        return 20 + 3 * palier;
    }

    public static int delaiTpMinutes(int palier) {
        return 10 - 2 * palier;
    }

    public static int revenuPct(int palier) {
        return 10 * palier;
    }

    public static int ravitaillementPct(int palier) {
        return 15 * palier;
    }

    public static int lootPct(int palier) {
        return 2 * palier;
    }

    /** Valeur de l'effet d'une branche au palier p (affichage : membres, %, blocs, minutes). */
    public static int effet(Branche b, int palier) {
        return switch (b) {
            case MEMBRES -> membresMax(palier);
            case BASE -> rayonBase(palier);
            case TP -> delaiTpMinutes(palier);
            case REVENU -> revenuPct(palier);
            case RAVITAILLEMENT -> ravitaillementPct(palier);
            case LOOT -> lootPct(palier);
            case TERRITOIRES -> territoiresMax(palier);
        };
    }
}
