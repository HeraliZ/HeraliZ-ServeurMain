package fr.heralisz.utils.zombies;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.util.Mth;

/**
 * Réserves de zombies par secteur de la carte (25/10) : la population suit les lieux, pas les joueurs. Chaque secteur
 * (carré de {@code secteur} blocs) a une capacité (nature, ville selon sa valeur, zones de {@link ZonesZombies}) et une
 * réserve qui part pleine. Poser un zombie prend une place ; un zombie tué ne la rend pas (elle revient d'elle-même au
 * bout de {@code regen} minutes) ; un zombie retiré parce que plus personne n'est près de lui la rend aussitôt. Un secteur
 * absent de la table est plein. Gardé en mémoire seulement (un redémarrage remplit tout). Fil serveur seulement.
 */
final class ReservesZombies {
    private ReservesZombies() {
    }

    private static final Long2IntOpenHashMap RESERVE = new Long2IntOpenHashMap();
    /** Heure (ms) à partir de laquelle compte la prochaine place qui revient, par secteur. */
    private static final Long2LongOpenHashMap DEPUIS = new Long2LongOpenHashMap();

    static void vider() {
        RESERVE.clear();
        DEPUIS.clear();
    }

    static int taille() {
        return ZombiesConfig.SECTEUR.get();
    }

    static long cle(double x, double z) {
        int t = taille();
        return ((long) Math.floorDiv(Mth.floor(x), t) << 32) | (Math.floorDiv(Mth.floor(z), t) & 0xFFFFFFFFL);
    }

    /** Centre du secteur de la clé. */
    static double centreX(long cle) {
        return ((int) (cle >> 32) + 0.5) * taille();
    }

    static double centreZ(long cle) {
        return ((int) cle + 0.5) * taille();
    }

    /** Capacité du secteur qui contient (x, z) (lue au centre du secteur). */
    static int capacite(long cle) {
        double cx = centreX(cle), cz = centreZ(cle);
        int valeur = SpawnZombiesServeur.valeurVille(cx, cz);
        double c = valeur > 0 ? ZombiesConfig.CAP_VILLE.get() + ZombiesConfig.CAP_PAR_VALEUR.get() * valeur : ZombiesConfig.CAP_NATURE.get();
        ZonesZombies.Zone z = ZonesZombies.ici(cx, cz);
        if (z != null) {
            if (z.type() == ZonesZombies.Type.VILLAGE && valeur == 0) c = Math.max(c, 3);
            c *= z.type().capacite;
        }
        return Math.max(0, (int) Math.round(c));
    }

    private static long regenMs(long cle) {
        double cx = centreX(cle), cz = centreZ(cle);
        double m = SpawnZombiesServeur.valeurVille(cx, cz) > 0 ? ZombiesConfig.REGEN_VILLE.get() : ZombiesConfig.REGEN_NATURE.get();
        ZonesZombies.Zone z = ZonesZombies.ici(cx, cz);
        if (z != null) m *= z.type().regen;
        return Math.max(1000L, (long) (m * 60_000L));
    }

    /** Places disponibles maintenant (recharge comptée au passage). */
    static int disponible(long cle, long now) {
        int cap = capacite(cle);
        if (!RESERVE.containsKey(cle)) return cap;
        int r = RESERVE.get(cle);
        long depuis = DEPUIS.get(cle), regen = regenMs(cle);
        if (now > depuis) {
            long n = (now - depuis) / regen;
            if (n > 0) {
                r = (int) Math.min(cap, r + n);
                depuis += n * regen;
            }
        }
        if (r >= cap) {
            RESERVE.remove(cle);
            DEPUIS.remove(cle);
            return cap;
        }
        RESERVE.put(cle, r);
        DEPUIS.put(cle, depuis);
        return r;
    }

    /** Prend une place (le zombie est posé) ; faux s'il n'y en a plus. */
    static boolean prendre(long cle, long now) {
        int r = disponible(cle, now);
        if (r <= 0) return false;
        if (!RESERVE.containsKey(cle)) DEPUIS.put(cle, now);
        RESERVE.put(cle, r - 1);
        return true;
    }

    /** Rend une place (zombie retiré sans avoir été tué). */
    static void rendre(double x, double z, long now) {
        long cle = cle(x, z);
        if (!RESERVE.containsKey(cle)) return;
        int r = disponible(cle, now);
        if (RESERVE.containsKey(cle)) RESERVE.put(cle, Math.min(capacite(cle), r + 1));
    }

    /** Secteurs entamés (pour /zombies stats). */
    static int entames() {
        return RESERVE.size();
    }
}
