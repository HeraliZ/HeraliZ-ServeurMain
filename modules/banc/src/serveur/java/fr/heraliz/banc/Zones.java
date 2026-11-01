package fr.heraliz.banc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Zones de groupe du banc (01/11) : une par groupe de bots sur la carte ({@code /bots zone <rayon> <arrivée>}, tapée par
 * {@code outils/banc_charge.py} à la position du groupe). Chaque zone sert :
 * <ul>
 *     <li>de lieu de vie aux bots du groupe ({@link Place}) ;</li>
 *     <li>de zone d'arrivée de la vraie borne de départ ({@link Camp#zoneArrivee}, rayon {@code arrivée}) : un bot qui
 *     repart du spawn arrive dans l'une d'elles, et vit ensuite dans la plus proche ;</li>
 *     <li>de rythme des combats : chaque zone se bat une part du temps seulement ({@link Rythmes#combat}), par fenêtres de
 *     20 à 60 s ; entre deux, ses bots ne tirent que sur les monstres (comme de vrais joueurs qui fouillent et marchent
 *     la plupart du temps).</li>
 * </ul>
 * Fil serveur seulement.
 */
final class Zones {
    private Zones() {
    }

    record Zone(int n, ServerLevel niveau, Vec3 centre, int rayon, int arrivee) {
        Place place() {
            return new Place(niveau, centre, rayon, n);
        }
    }

    static final List<Zone> LISTE = new ArrayList<>();
    private static final RandomSource R = RandomSource.create();
    /** Fenêtres de combat : [0] hors zone, [1 + n] zone n ; en combat, et tick du prochain changement (-1 : à tirer). */
    private static boolean[] combat = new boolean[1];
    private static int[] fin = {-1};

    static Zone ajouter(ServerLevel l, Vec3 c, int rayon, int arrivee) {
        Zone z = new Zone(LISTE.size(), l, c, rayon, arrivee);
        LISTE.add(z);
        combat = Arrays.copyOf(combat, LISTE.size() + 1);
        fin = Arrays.copyOf(fin, LISTE.size() + 1);
        fin[LISTE.size()] = -1;
        if (arrivee > 0) Camp.zoneArrivee(l, "banc_" + (z.n() + 1), (int) Math.floor(c.x), (int) Math.floor(c.z), arrivee);
        return z;
    }

    static void vider(MinecraftServer srv) {
        if (!LISTE.isEmpty()) Camp.retirerZonesArrivee(srv);
        LISTE.clear();
        combat = new boolean[1];
        fin = new int[]{-1};
    }

    /** La place d'une zone posée à moins de 2 blocs de {@code c} dans ce monde, sinon une place sans zone. */
    static Place place(ServerLevel l, Vec3 c, int rayon) {
        for (Zone z : LISTE) {
            if (z.niveau() == l && z.centre().distanceToSqr(c.x, z.centre().y, c.z) < 4) return z.place();
        }
        return new Place(l, c, rayon);
    }

    /** La zone la plus proche de {@code p} dans ce monde (arrivée par la borne), ou null s'il n'y en a pas. */
    @Nullable
    static Zone plusProche(ServerLevel l, Vec3 p) {
        Zone mieux = null;
        double d = Double.MAX_VALUE;
        for (Zone z : LISTE) {
            if (z.niveau() != l) continue;
            double dz = z.centre().distanceToSqr(p.x, z.centre().y, p.z);
            if (dz < d) {
                d = dz;
                mieux = z;
            }
        }
        return mieux;
    }

    /** La zone {@code n} se bat-elle en ce moment ? (-1 : hors zone, fenêtre commune) */
    static boolean enCombat(int n) {
        int i = n + 1;
        return i >= 0 && i < combat.length && combat[i];
    }

    /** Fenêtres de combat, une fois par seconde. */
    static void tick(int t) {
        if (t % 20 != 0) return;
        int pct = Rythmes.combat;
        for (int i = 0; i < combat.length; i++) {
            if (pct >= 100) {
                combat[i] = true;
                fin[i] = -1;
            } else if (pct <= 0) {
                combat[i] = false;
                fin[i] = -1;
            } else if (fin[i] < 0) {
                // première fenêtre : au hasard, en cours depuis un moment (les zones ne commencent pas toutes ensemble)
                combat[i] = R.nextInt(100) < pct;
                fin[i] = t + R.nextInt(duree(combat[i], pct));
            } else if (t >= fin[i]) {
                combat[i] = !combat[i];
                fin[i] = t + duree(combat[i], pct);
            }
        }
    }

    /** Durée d'une fenêtre (ticks) : combat de 20 à 60 s, calme en proportion pour tenir la part demandée. */
    private static int duree(boolean enCombat, int pct) {
        int c = 400 + R.nextInt(800);
        return enCombat ? c : Math.max(20, (int) (c * (100.0 - pct) / pct));
    }

    /** Part du temps en combat, en moyenne sur toutes les zones en ce moment (pour le rapport). */
    static int enCombatMaintenant() {
        int n = 0;
        for (int i = 1; i < combat.length; i++) if (combat[i]) n++;
        return n;
    }
}
