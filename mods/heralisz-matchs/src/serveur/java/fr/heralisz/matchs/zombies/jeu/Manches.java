package fr.heralisz.matchs.zombies.jeu;

import fr.heralisz.matchs.zombies.jeu.api.Allure;
import net.minecraft.util.RandomSource;

/**
 * Formules des manches, calquees sur les CoD Zombies de Treyarch (ZOMBIES_V3_SPEC §2), fonctions pures sans etat :
 * <ul>
 *     <li>nombre de zombies : formule de Treyarch (24 + 6 par joueur en plus, × manche / 5, × 0,15 r des la manche 10),
 *     reduite sur les 5 premieres manches (solo : 6, 8, 13, 18, 24, 27…) ;</li>
 *     <li>PV « CoD » : 150 + 100 × (r − 1) jusqu'a r = 9, puis × 1,1 par manche ; ramenes a l'echelle Minecraft par
 *     {@link #ECHELLE_PV} (0.2.3 : 0,09, manche 1 ≈ 13,5 PV : 2 balles de M1911 au corps, 4 a la manche 4) ;</li>
 *     <li>allures : tirage de Treyarch, {@code alea(8 r, 8 r + 35)} (≤ 35 marche, ≤ 70 court, au-dela sprinte) : quelques
 *     coureurs des la manche 1, presque tous sprinteurs a la manche 9 ;</li>
 *     <li>degats PROGRESSIFS (0.2.3, demande du joueur apres le 1er test, remplace la regle « degats fixes ») : 4 a la
 *     manche 1 (5 coups a terre), puis {@code 4 + 6 t^1,2} avec {@code t = (r - 1) / 19}, plafond 10 des la manche 20
 *     (2 coups a terre, 5 avec Blindage, comme CoD) ; au-dela ce sont la vie et la vitesse qui montent ;</li>
 *     <li>manche speciale « Rodeurs » toutes les 5 a 7 manches (premiere entre 5 et 7) ;</li>
 *     <li>au plus {@link #VIVANTS_MAX} vivants a la fois ; cadence d'apparition qui accelere.</li>
 * </ul>
 */
public final class Manches {
    private Manches() {
    }

    /** Zombies vivants au plus en meme temps. */
    public static final int VIVANTS_MAX = 24;
    /** Facteur PV CoD -> PV Minecraft. */
    public static final double ECHELLE_PV = 0.09;
    /** PV maximaux d'un attribut vanilla : au-dela, les degats recus sont reduits d'autant. */
    public static final double PV_ATTRIBUT_MAX = 1024.0;
    /** Points de depart. */
    public static final int POINTS_DEPART = 500;
    /** Duree de la pause entre deux manches (ticks). */
    public static final int PAUSE_TICKS = 200;
    /** Delai avant la manche 1 (ticks). */
    public static final int DEPART_TICKS = 100;

    /** Zombies en plus par joueur supplementaire (Treyarch : zombie_ai_per_player). */
    private static final int PAR_JOUEUR = 6;

    /**
     * Nombre de zombies de la manche {@code r} (>= 1) pour {@code joueurs} joueurs (1..4), formule de Treyarch :
     * {@code 24 + 6 × (joueurs − 1 ; 0,5 en solo) × m}, avec {@code m = max(1, r / 5)}, multiplie par {@code 0,15 r} des
     * la manche 10 ; les manches 1 a 5 n'en gardent que 25 / 30 / 50 / 70 / 90 %.
     */
    public static int nombre(int r, int joueurs) {
        r = Math.max(1, r);
        int n = Math.max(1, Math.min(4, joueurs));
        double m = Math.max(1.0, r / 5.0);
        if (r >= 10) m *= r * 0.15;
        int max = VIVANTS_MAX + (int) ((n == 1 ? 0.5 * PAR_JOUEUR : (n - 1) * PAR_JOUEUR) * m);
        double debut = switch (r) {
            case 1 -> 0.25;
            case 2 -> 0.3;
            case 3 -> 0.5;
            case 4 -> 0.7;
            case 5 -> 0.9;
            default -> 1.0;
        };
        return Math.max(1, (int) (max * debut));
    }

    /** PV a l'echelle CoD. */
    public static double pvCod(int r) {
        r = Math.max(1, r);
        if (r <= 9) return 150 + 100.0 * (r - 1);
        return 950.0 * Math.pow(1.1, r - 9);
    }

    /** PV Minecraft d'un zombie de la manche {@code r} (non plafonnes). */
    public static double pv(int r) {
        return Math.max(4.0, pvCod(r) * ECHELLE_PV);
    }

    /** PV Minecraft d'un Rodeur (moitie d'un zombie, plancher 10). */
    public static double pvRodeur(int r) {
        return Math.max(10.0, pv(r) * 0.5);
    }

    /** PV en plus avec Blindage, sur 20 de base (CoD : Mastodonte, 100 -> 250, soit x 2,5). */
    public static final double PV_BLINDAGE = 30.0;

    /** Manche ou les degats des zombies cessent de monter (plafond). */
    public static final int MANCHE_PLAFOND_DEGATS = 20;

    /**
     * Degats d'un coup selon la manche (0.2.3) : {@code t = clamp((r - 1) / 19, 0, 1)}, zombie {@code 4 + 6 t^1,2}
     * (4 -> 10), Rodeur {@code 3 + 4 t^1,2} (3 -> 7). Manche 1 : 5 coups mettent a terre (20 PV) ; manche 20 et plus : 2
     * coups, 5 avec Blindage (50 PV). Bornes reglables dans {@code regles.json} ({@code degats.zombie_min/max},
     * {@code rodeur_min/max}, {@code manche_plafond}). Appliques tels quels, sans le reglage de difficulte du serveur
     * ({@code PartieZombies.surDegats}).
     */
    public static float degats(int r, boolean rodeur) {
        int plafond = Math.max(2, ConfigZombies.entier("degats", "manche_plafond", MANCHE_PLAFOND_DEGATS));
        double t = Math.max(0.0, Math.min(1.0, (Math.max(1, r) - 1) / (double) (plafond - 1)));
        double k = Math.pow(t, 1.2);
        double min = rodeur ? ConfigZombies.reel("degats", "rodeur_min", 3.0) : ConfigZombies.reel("degats", "zombie_min", 4.0);
        double max = rodeur ? ConfigZombies.reel("degats", "rodeur_max", 7.0) : ConfigZombies.reel("degats", "zombie_max", 10.0);
        return (float) (min + (max - min) * k);
    }

    /** Nombre de Rodeurs d'une manche speciale. */
    public static int nombreRodeurs(int r, int joueurs) {
        int n = Math.max(1, Math.min(4, joueurs));
        return Math.min(40, 6 * n + Math.max(0, (r - 5) / 5) * 2 * n);
    }

    /** Ticks entre deux apparitions (2 s en manche 1, x 0,95 par manche, plancher 5 ticks ; plus rapide a plusieurs). */
    public static int cadence(int r, int joueurs) {
        double t = 40.0 * Math.pow(0.95, Math.max(0, r - 1)) / (1.0 + 0.15 * (Math.max(1, joueurs) - 1));
        return Math.max(5, (int) Math.round(t));
    }

    /** Ticks pour arracher une planche (25 en manche 1, plancher 10). */
    public static int arrachage(int r) {
        return Math.max(10, 26 - r);
    }

    /** Multiplicateur de vitesse de Treyarch (zombie_move_speed_multiplier). */
    private static final int VITESSE_PAR_MANCHE = 8;

    /**
     * Allure tiree au hasard pour la manche {@code r}, comme Treyarch : {@code t = alea(8 r, 8 r + 35)}, marche si
     * {@code t <= 35}, court si {@code t <= 70}, sprinte au-dela. Manche 1 : 80 % de marcheurs ; manche 5 : surtout des
     * coureurs ; manche 9 et plus : que des sprinteurs.
     */
    public static Allure allure(int r, RandomSource alea) {
        int v = Math.max(1, r) * VITESSE_PAR_MANCHE;
        int t = v + alea.nextInt(35);
        if (t <= 35) return Allure.MARCHEUR;
        if (t <= 70) return Allure.COUREUR;
        return Allure.SPRINTEUR;
    }

    /** Premiere manche speciale (5..7). */
    public static int premiereSpeciale(RandomSource alea) {
        return 5 + alea.nextInt(3);
    }

    /** Manche speciale suivante apres {@code r} (r + 5..7). */
    public static int speciale(int r, RandomSource alea) {
        return r + 5 + alea.nextInt(3);
    }
}
