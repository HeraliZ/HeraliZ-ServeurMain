package fr.heralisz.clans.model;

/**
 * Avantages des niveaux de clan (21/10, maquette validee) : en plus des membres et territoires de {@link Niveaux}.
 * Jamais d'avantage en combat : argent, ravitaillement, un peu de loot, apparence. Lu par le serveur (qui les applique)
 * et par l'onglet NIVEAU du menu K (qui les affiche) : les deux cotes doivent avoir la meme version.
 */
public final class Avantages {
    private Avantages() {
    }

    /** Niveaux qui debloquent quelque chose (texte : gui.niveau.avantage.N). */
    public static final int[] PALIERS = {1, 2, 3, 4, 5, 6, 8, 10, 12, 14, 16, 18, 20};

    /** Formes de banniere reservees au niveau 12 : dragon, kraken, lion, couronne, oeil, soleil. */
    public static final int[] FORMES_RARES = {5, 6, 15, 25, 28, 30};
    public static final int NIVEAU_DEGRADES = 2, NIVEAU_TAG = 8, NIVEAU_FORMES = 12, NIVEAU_LEGENDAIRE = 20;

    /** Revenu des zones en plus (%). */
    public static int revenuPct(int n) {
        return n >= 16 ? 30 : n >= 10 ? 20 : n >= 6 ? 10 : 0;
    }

    /** Delai entre deux ravitaillements en moins (%). */
    public static int ravitaillementPct(int n) {
        return n >= 18 ? 45 : n >= 10 ? 30 : n >= 4 ? 15 : 0;
    }

    /** Bonus de loot en plus dans les zones du clan (points de %). */
    public static int lootPct(int n) {
        return n >= 14 ? 4 : n >= 5 ? 2 : 0;
    }

    /** Fonds RADIAL et PARTAGE (les autres sont libres). */
    public static boolean degradeLibre(int n, Banniere.Degrade d) {
        return n >= NIVEAU_DEGRADES || (d != Banniere.Degrade.RADIAL && d != Banniere.Degrade.PARTAGE);
    }

    public static boolean formeRare(int forme) {
        for (int f : FORMES_RARES) if (f == forme) return true;
        return false;
    }
}
