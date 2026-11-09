package fr.heralisz.utils.train;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bagages à fouiller dans le train (demande du propriétaire, 09/11, refait le 10/11) : six bagages par voiture, à des
 * places fixes tirées une fois pour toutes à partir du numéro du véhicule (mêmes places chez le serveur et chez tous
 * les joueurs). Chaque bagage est un vrai modèle de conteneur de HeraliZ Loot, d'un palier de T1 à T5 (T1 le plus
 * souvent, T5 rare). Seuls deux bagages par voiture sont « à fouiller » à la fois : ils changent toutes les 20 minutes
 * de jeu (tirage à partir du numéro du véhicule et de la période : le même partout, sans paquet) ; les autres sont du
 * décor. Repère local des voitures : lx le long (+ vers l'avant), lz en travers (+ à droite), plancher en
 * Reseau.PLANCHER.
 */
public final class Bagages {
    /** Bagages par voiture, dont ceux à fouiller à la fois. */
    public static final int PAR_VOITURE = 6, ACTIFS = 2;
    /** Distance (repère de la voiture) au-delà de laquelle on ne fouille pas. */
    public static final float PORTEE = 2.6f;
    /** Durée d'une période (ticks de jeu) : les bagages à fouiller changent et se remplissent à chaque période. */
    public static final long RECHARGE = 20L * 60 * 20;
    /** Taille affichée (1 = un bloc) et demi-côtés de la boîte visée. */
    public static final float ECHELLE = 0.8f, DEMI = 0.36f, HAUT = 0.62f;
    private static final String[] PALIERS = {"t1", "t2", "t3", "t4", "t5"};
    private static final int[] POIDS = {34, 28, 20, 12, 6};
    /** Modèles d'un seul bloc de HeraliZ Loot, par palier (sacs, valises, cantines : ce qu'on laisse dans un train). */
    private static final String[][] MODELES = {
            {"t1_valise_ouverte", "t1_sac_a_main", "t1_cartable", "t1_grand_cabas", "t1_sacs_courses", "t1_tas_vetements"},
            {"t2_valise", "t2_sac_sport", "t2_sacoche", "t2_sac_randonnee", "t2_malle", "t2_valises_anciennes", "t2_trousse_secours"},
            {"t3_sac_secours", "t3_trousse_soins", "t3_sac_tactique", "t3_sac_police", "t3_caisse_police"},
            {"t4_paquetage", "t4_sac_marin", "t4_cantine", "t4_sac_a_dos", "t4_caisse_medicale"},
            {"t5_cantine_onu", "t5_caisse_medicale_onu", "t5_caisse_optiques", "t5_sac_parachute"}};
    private static final Map<Integer, List<Place>> PLACES = new ConcurrentHashMap<>();

    /** Une place : position dans la voiture, quart de tour (0 à 3), palier (t1 à t5) et modèle de HeraliZ Loot. */
    public record Place(float lx, float lz, int quart, String palier, String modele) {}

    private Bagages() {}

    /** Places des bagages d'un véhicule (vide pour la locomotive). */
    public static List<Place> places(int veh) {
        return PLACES.computeIfAbsent(veh, Bagages::tirer);
    }

    private static List<Place> tirer(int veh) {
        List<Place> out = new ArrayList<>();
        if (!Voyageurs.voiture(veh)) return out;
        List<float[]> cands = new ArrayList<>();
        if (Voyageurs.sieges(veh)) {
            for (float fx : Voyageurs.FEN_X) {                             // au pied des tablettes, contre la paroi
                cands.add(new float[]{fx, Voyageurs.TABLE_Z + 0.1f});
                cands.add(new float[]{fx, -(Voyageurs.TABLE_Z + 0.1f)});
            }
            for (float sx : new float[]{-1f, 1f}) {                        // coins des vestibules
                cands.add(new float[]{sx * (Voyageurs.VEST + 0.45f), 1.55f});
                cands.add(new float[]{sx * (Voyageurs.VEST + 0.45f), -1.55f});
            }
        } else {
            for (float x = -7.5f; x <= 7.5f; x += 2.5f) {                  // fourgon : le long des parois
                cands.add(new float[]{x, 1.6f});
                cands.add(new float[]{x, -1.6f});
            }
        }
        Random r = new Random(veh * 7919L + 104729L);
        for (int k = 0; k < PAR_VOITURE && !cands.isEmpty(); k++) {
            float[] c = cands.remove(r.nextInt(cands.size()));
            int t = r.nextInt(100), i = 0;
            while (i < POIDS.length - 1 && t >= POIDS[i]) t -= POIDS[i++];
            out.add(new Place(c[0], c[1], r.nextInt(4), PALIERS[i], MODELES[i][r.nextInt(MODELES[i].length)]));
        }
        return out;
    }

    /** Période en cours (les bagages à fouiller changent à chaque période). */
    public static long epoque(long tempsDeJeu) {
        return tempsDeJeu / RECHARGE;
    }

    /** Ce bagage est-il à fouiller pendant cette période ? Même réponse chez le serveur et chez tous les joueurs. */
    public static boolean actif(int veh, int place, long epoque) {
        int n = places(veh).size();
        if (place < 0 || place >= n) return false;
        Random r = new Random(veh * 31337L + epoque * 104729L + 17L);
        int[] ordre = new int[n];
        for (int i = 0; i < n; i++) ordre[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = r.nextInt(i + 1), tmp = ordre[i];
            ordre[i] = ordre[j];
            ordre[j] = tmp;
        }
        for (int i = 0; i < Math.min(ACTIFS, n); i++) if (ordre[i] == place) return true;
        return false;
    }

    public static long cle(int veh, int place) {
        return ((long) veh << 8) | place;
    }
}
