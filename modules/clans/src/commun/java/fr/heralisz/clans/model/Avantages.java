package fr.heralisz.clans.model;

/**
 * Avantages de deco des niveaux de clan (donnes tout seuls par le niveau, decision du 22/10) : fonds de banniere, tag en
 * couleur, formes rares, tag dore au niveau maximum. Tout ce qui touche au jeu (membres, territoires, revenu,
 * ravitaillement, loot, base, TP) se prend dans l'arbre d'ameliorations ({@link Arbre}). Lu par le serveur (qui les
 * applique) et par l'onglet NIVEAU du menu K : les deux cotes doivent avoir la meme version.
 */
public final class Avantages {
    private Avantages() {
    }

    /** Niveaux qui debloquent quelque chose (texte : gui.niveau.avantage.N). */
    public static final int[] PALIERS = {3, 15, 25, 45};

    /** Formes de banniere reservees : dragon, kraken, lion, couronne, oeil, soleil. */
    public static final int[] FORMES_RARES = {5, 6, 15, 25, 28, 30};
    public static final int NIVEAU_DEGRADES = 3, NIVEAU_TAG = 15, NIVEAU_FORMES = 25, NIVEAU_LEGENDAIRE = Niveaux.MAX;

    /** Fonds RADIAL et PARTAGE (les autres sont libres). */
    public static boolean degradeLibre(int n, Banniere.Degrade d) {
        return n >= NIVEAU_DEGRADES || (d != Banniere.Degrade.RADIAL && d != Banniere.Degrade.PARTAGE);
    }

    public static boolean formeRare(int forme) {
        for (int f : FORMES_RARES) if (f == forme) return true;
        return false;
    }
}
