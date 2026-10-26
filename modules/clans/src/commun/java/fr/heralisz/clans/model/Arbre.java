package fr.heralisz.clans.model;

import java.util.EnumMap;
import java.util.Locale;

/**
 * Arbre d'ameliorations d'un clan (saison des clans, decision du 22/10, docs/SAISON_CLANS.md §6) : chaque niveau du clan
 * donne un point de competence ; le palier n d'une branche coute n points et n x 5 000 herals (banque du clan) ; jamais
 * rembourse. Branche Membres (25/10) : 4 a 8 membres, paliers reserves aux niveaux 10, 20, 30, 40 du clan et bien plus
 * chers ({@link #HERALS_MEMBRES}). Tout ce qui touche au jeu passe par ici (membres, territoires, revenu, ravitaillement, loot, taille de la
 * base, delai du TP vers la base) ; la deco (banniere, tag) reste donnee par le niveau ({@link Avantages}).
 * Lu des deux cotes (serveur : regles ; joueur : onglet Niveau) : memes chiffres partout.
 */
public final class Arbre {
    private Arbre() {
    }

    /** Herals du palier n : n x PRIX_PALIER. */
    public static final long PRIX_PALIER = 5_000L;

    public enum Branche {
        /**
         * Membres (25/10, demande du propriétaire) : 4, puis 5 a 8 ; chaque palier demande un niveau de clan (10, 20, 30,
         * 40) et beaucoup de herals ({@link #HERALS_MEMBRES}) : 8 membres doit rester un objectif de fin de saison.
         */
        MEMBRES(4),
        /** Rayon de construction autour de l'armoire : 20, puis 23, 26, 29 blocs (CRac). */
        BASE(3),
        /**
         * Delai entre deux TP vers la base (/home et reapparition a la base, delai partage de CRac) : 8 min pour tous
         * (25/10), puis 7, 6, 5 min.
         */
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

    /** Herals des paliers de la branche Membres (5e, 6e, 7e, 8e membre) : 455 000 en tout (le reste de l'arbre : 150 000). */
    public static final long[] HERALS_MEMBRES = {25_000L, 60_000L, 120_000L, 250_000L};
    /** Niveau du clan demande par palier de la branche Membres. */
    public static final int NIVEAU_PAR_PALIER_MEMBRES = 10;

    /** Points du palier p (1..max) d'une branche. */
    public static int coutPoints(int palier) {
        return palier;
    }

    public static int coutPoints(Branche b, int palier) {
        return coutPoints(palier);
    }

    public static long coutHerals(int palier) {
        return palier * PRIX_PALIER;
    }

    /** Herals du palier p d'une branche (Membres : {@link #HERALS_MEMBRES}). */
    public static long coutHerals(Branche b, int palier) {
        if (b == Branche.MEMBRES) return HERALS_MEMBRES[Math.max(0, Math.min(HERALS_MEMBRES.length - 1, palier - 1))];
        return coutHerals(palier);
    }

    /** Niveau du clan demande pour acheter le palier p (Membres : 10, 20, 30, 40 ; les autres branches : aucun). */
    public static int niveauRequis(Branche b, int palier) {
        return b == Branche.MEMBRES ? NIVEAU_PAR_PALIER_MEMBRES * palier : 0;
    }

    /** Points que coutent tous les paliers achetes. */
    public static int pointsDepenses(EnumMap<Branche, Integer> arbre) {
        int t = 0;
        for (var e : arbre.entrySet()) for (int p = 1; p <= Math.min(e.getKey().max, e.getValue()); p++) t += coutPoints(e.getKey(), p);
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
        return 4 + Math.max(0, Math.min(Branche.MEMBRES.max, palier));
    }

    public static int territoiresMax(int palier) {
        return 1 + palier;
    }

    public static int rayonBase(int palier) {
        return 20 + 3 * palier;
    }

    public static int delaiTpMinutes(int palier) {
        return 8 - palier;
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
