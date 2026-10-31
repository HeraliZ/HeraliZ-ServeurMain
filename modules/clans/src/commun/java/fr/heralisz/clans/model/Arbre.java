package fr.heralisz.clans.model;

import java.util.EnumMap;
import java.util.Locale;

/**
 * Arbre d'ameliorations d'un clan (saison des clans, decision du 22/10, docs/SAISON_CLANS.md §6) : chaque niveau du clan
 * donne un point de competence ; le palier n d'une branche coute n points et n x 5 000 herals (banque du clan) ; jamais
 * rembourse. Branche Membres (25/10) : 4 a 8 membres, bien plus chers ({@link #HERALS_MEMBRES}) ; depuis le 31/10
 * (decision du proprietaire, avec les niveaux beaucoup plus durs) paliers reserves aux niveaux 2, 5, 10, 15 du clan
 * (avant : 10, 20, 30, 40), reglables dans niveaux.json (section « membres »). Branche Territoires (31/10, demande du proprietaire) : aucune zone au depart, capture
 * ouverte au niveau {@link #NIVEAU_CAPTURE} du clan, 1 puis 2 zones tenues en meme temps au plus
 * ({@link #TERRITOIRES_PLAFOND}). Tout ce qui touche au jeu passe par ici (membres, territoires, revenu, ravitaillement, loot, taille de la
 * base, delai du TP vers la base) ; la deco (banniere, tag) reste donnee par le niveau ({@link Avantages}).
 * Lu des deux cotes (serveur : regles ; joueur : onglet Niveau) : memes chiffres partout.
 */
public final class Arbre {
    private Arbre() {
    }

    /** Herals de base du palier n : n x PRIX_PALIER (multiplies par le facteur de prix, {@link #facteurPrixPct()}). */
    public static final long PRIX_PALIER = 5_000L;

    /**
     * Facteur des herals de l'arbre, en pourcents (28/10 : x2 par defaut, « progression beaucoup trop facile »). Lu par le
     * serveur dans niveaux.json ({@code facteur_prix_arbre}, {@link Niveaux#charger}) et envoye aux joueurs avec l'etat du
     * clan ({@link ClanSnapshot}) : le prix affiche est celui que le serveur demande.
     */
    private static volatile int facteurPrixPct = (int) Math.round(Niveaux.FACTEUR_PRIX_ARBRE_DEFAUT * 100);

    public static int facteurPrixPct() {
        return facteurPrixPct;
    }

    /** Borne 10 % a 10 000 %. */
    public static void facteurPrixPct(int pct) {
        facteurPrixPct = Math.max(10, Math.min(10_000, pct));
    }

    private static long appliquer(long base) {
        return base * facteurPrixPct / 100L;
    }

    public enum Branche {
        /**
         * Membres (25/10, demande du propriétaire) : 4, puis 5 a 8 ; chaque palier demande un niveau de clan (31/10 : 2, 5,
         * 10, 15 ; avant : 10, 20, 30, 40) et beaucoup de herals ({@link #HERALS_MEMBRES}). 8 reste le maximum (le nombre
         * de paliers ne change pas) ; niveaux, herals et membres au depart : niveaux.json, section « membres ».
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
        /**
         * Territoires tenus en meme temps (31/10, demande du proprietaire) : 0 au depart (aucune capture), 1 au palier 1
         * (niveau 5 du clan), 2 au palier 2 (niveau 10) ; jamais plus de 2. Avant : 1, puis 2, 3.
         */
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

    /**
     * Herals par defaut des paliers de la branche Membres (5e, 6e, 7e, 8e membre) : 455 000 en tout (le reste de l'arbre :
     * 150 000), avant le facteur de prix.
     */
    public static final long[] HERALS_MEMBRES = {25_000L, 60_000L, 120_000L, 250_000L};
    /** Niveau du clan demande par defaut pour chaque palier de la branche Membres (31/10 ; avant : 10, 20, 30, 40). */
    public static final int[] NIVEAUX_MEMBRES = {2, 5, 10, 15};
    /** Membres d'un clan sans amelioration, par defaut. */
    public static final int MEMBRES_DEPART = 4;

    /**
     * Reglages de la branche Membres en cours : lus par le serveur dans niveaux.json (section « membres »,
     * {@link Niveaux#charger}) et envoyes aux joueurs avec l'etat du clan ({@link ClanSnapshot}) : l'onglet Niveau montre
     * les memes niveaux et les memes prix que ceux que le serveur demande. Tableaux remplaces d'un bloc, jamais modifies.
     */
    private static volatile int membresDepart = MEMBRES_DEPART;
    private static volatile int[] niveauxMembres = NIVEAUX_MEMBRES.clone();
    private static volatile long[] heralsMembres = HERALS_MEMBRES.clone();

    public static int membresDepart() {
        return membresDepart;
    }

    /** Copie des niveaux du clan demandes par les paliers Membres (un par palier). */
    public static int[] niveauxMembres() {
        return niveauxMembres.clone();
    }

    /** Copie des herals (avant le facteur de prix) des paliers Membres (un par palier). */
    public static long[] heralsMembres() {
        return heralsMembres.clone();
    }

    /**
     * Regle la branche Membres. Bornes : 1 a 20 membres au depart ; niveaux de 1 a {@link Niveaux#MAX}, jamais plus bas que
     * celui du palier d'avant ; herals positifs. Une valeur manquante (tableau trop court ou null) garde celle par defaut.
     */
    public static void reglerMembres(int depart, int[] niveaux, long[] herals) {
        int n = Branche.MEMBRES.max;
        int[] nv = NIVEAUX_MEMBRES.clone();
        long[] hm = HERALS_MEMBRES.clone();
        for (int i = 0; i < n; i++) {
            if (niveaux != null && i < niveaux.length) nv[i] = Math.max(1, Math.min(Niveaux.MAX, niveaux[i]));
            if (i > 0) nv[i] = Math.max(nv[i], nv[i - 1]);
            if (herals != null && i < herals.length) hm[i] = Math.max(0L, herals[i]);
        }
        membresDepart = Math.max(1, Math.min(20, depart));
        niveauxMembres = nv;
        heralsMembres = hm;
    }
    /**
     * Niveau du clan a partir duquel il peut capturer des zones (31/10) : verifie par le serveur a chaque cycle de capture
     * ({@link Clan#territoiresMax()} vaut 0 avant), et c'est le niveau demande par le premier palier de la branche
     * Territoires.
     */
    public static final int NIVEAU_CAPTURE = 5;
    /** Niveau du clan demande par palier de la branche Territoires : 5 (1 zone), 10 (2 zones). */
    public static final int[] NIVEAUX_TERRITOIRES = {NIVEAU_CAPTURE, 10};
    /** Zones tenues en meme temps, au plus, quel que soit l'arbre (31/10). */
    public static final int TERRITOIRES_PLAFOND = 2;

    /** Points du palier p (1..max) d'une branche. */
    public static int coutPoints(int palier) {
        return palier;
    }

    public static int coutPoints(Branche b, int palier) {
        return coutPoints(palier);
    }

    public static long coutHerals(int palier) {
        return appliquer(palier * PRIX_PALIER);
    }

    /** Herals du palier p d'une branche (Membres : section « membres » de niveaux.json), facteur de prix compris. */
    public static long coutHerals(Branche b, int palier) {
        if (b == Branche.MEMBRES) {
            long[] hm = heralsMembres;
            return appliquer(hm[Math.max(0, Math.min(hm.length - 1, palier - 1))]);
        }
        return coutHerals(palier);
    }

    /**
     * Niveau du clan demande pour acheter le palier p (Membres : 2, 5, 10, 15 par defaut, niveaux.json ; Territoires :
     * 5, 10 ; les autres branches : aucun).
     */
    public static int niveauRequis(Branche b, int palier) {
        if (b == Branche.MEMBRES) {
            int[] nv = niveauxMembres;
            return nv[Math.max(0, Math.min(nv.length - 1, palier - 1))];
        }
        if (b == Branche.TERRITOIRES) return NIVEAUX_TERRITOIRES[Math.max(0, Math.min(NIVEAUX_TERRITOIRES.length - 1, palier - 1))];
        return 0;
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
    /** Membres au plus avec p paliers de la branche Membres : membres au depart (4) + p. */
    public static int membresMax(int palier) {
        return membresDepart + Math.max(0, Math.min(Branche.MEMBRES.max, palier));
    }

    /** Palier de la branche Membres debloque en atteignant ce niveau du clan (1 a 4), ou 0. */
    public static int palierMembresDuNiveau(int niveau) {
        int[] nv = niveauxMembres;
        for (int i = 0; i < nv.length; i++) if (nv[i] == niveau) return i + 1;
        return 0;
    }

    /** Zones tenues en meme temps au palier p de la branche Territoires : 0, 1, 2 (31/10 ; avant : 1, 2, 3). */
    public static int territoiresMax(int palier) {
        return Math.max(0, Math.min(TERRITOIRES_PLAFOND, palier));
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
