package fr.heralisz.utils.train;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bagages à fouiller dans le train (demande du propriétaire, 09/11) : quelques places fixes par voiture, tirées une fois
 * pour toutes à partir du numéro du véhicule (mêmes places chez le serveur et chez tous les joueurs). Voitures de
 * voyageurs : au pied d'une tablette, contre la paroi, ou dans un coin de vestibule ; fourgon : le long des parois.
 * Chaque place a un palier de loot de HeraliZ Loot, de T1 à T4 (T1 le plus souvent). Repère local des voitures :
 * lx le long (+ vers l'avant), lz en travers (+ à droite), plancher en Reseau.PLANCHER.
 */
public final class Bagages {
    /** Bagages par voiture. */
    public static final int PAR_VOITURE = 3;
    /** Distance (repère de la voiture) au-delà de laquelle on ne fouille pas. */
    public static final float PORTEE = 2.4f;
    private static final String[] PALIERS = {"t1", "t2", "t3", "t4"};
    private static final int[] POIDS = {45, 30, 18, 7};

    /** Une place : position dans la voiture, orientation (radians, autour de la verticale) et palier (t1 à t4). */
    public record Place(float lx, float lz, float yaw, String palier) {}

    private Bagages() {}

    /** Places des bagages d'un véhicule (vide pour la locomotive). */
    public static List<Place> places(int veh) {
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
            for (float x = -6f; x <= 6f; x += 3f) {                        // fourgon : le long des parois
                cands.add(new float[]{x, 1.6f});
                cands.add(new float[]{x, -1.6f});
            }
        }
        Random r = new Random(veh * 7919L + 104729L);
        for (int k = 0; k < PAR_VOITURE && !cands.isEmpty(); k++) {
            float[] c = cands.remove(r.nextInt(cands.size()));
            int t = r.nextInt(100), i = 0;
            while (i < POIDS.length - 1 && t >= POIDS[i]) t -= POIDS[i++];
            out.add(new Place(c[0], c[1], r.nextFloat() * 6.2831855f, PALIERS[i]));
        }
        return out;
    }
}
