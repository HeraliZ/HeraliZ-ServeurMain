package fr.heralisz.clans;

/**
 * Réglages des clans lus par le code commun (audit du 02/11, point 11). Le fichier (ClansConfig,
 * config/heralisz_clans-common.toml : règle anti-boost, gains d'XP, territoires…) est un réglage du serveur seulement,
 * déclaré par la partie serveur : jamais écrit sur le PC des joueurs. Le serveur recopie ici, à chaque chargement du
 * fichier :
 * <ul>
 *    <li>le prix de création d'un clan, envoyé aux joueurs avec l'état de leur clan (Packets.ClanSnapshotS2C) pour les
 *    écrans « sans clan » et de fondation (§8 de split : avant, l'écran lisait la copie du fichier du joueur) ;</li>
 *    <li>le cercle de capture des drapeaux (serveur seulement : le serveur le recopie dans chaque drapeau, que le rendu
 *    lit).</li>
 * </ul>
 * Valeurs d'origine tant que rien n'est lu ni reçu.
 */
public final class ReglagesClans {
    private ReglagesClans() {
    }

    /** Prix de création d'un clan à l'origine (hérals). */
    public static final long COUT_CREATION_DEFAUT = 1750L;

    private static volatile long coutCreation = COUT_CREATION_DEFAUT;
    private static volatile int rayonCapture = -1, hauteurCapture = -1;

    /** Prix de création d'un clan (hérals) : réglage du serveur (reçu par le joueur avec l'état de son clan). */
    public static long coutCreation() {
        return coutCreation;
    }

    public static void definirCoutCreation(long cout) {
        coutCreation = Math.max(0L, cout);
    }

    /** Le cercle de capture a été lu dans le fichier (serveur seulement). */
    public static boolean cercleLu() {
        return rayonCapture >= 0;
    }

    public static int rayonCapture() {
        return rayonCapture;
    }

    public static int hauteurCapture() {
        return hauteurCapture;
    }

    /** Serveur : cercle de capture lu dans le fichier. */
    public static void definirCercle(int rayon, int hauteur) {
        hauteurCapture = Math.max(0, hauteur);
        rayonCapture = Math.max(0, rayon);
    }
}
