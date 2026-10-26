package fr.heraliz.ping.server;

import fr.heraliz.ping.HeralizPing;
import net.minecraftforge.fml.ModList;

/**
 * Presence des mods lies en douceur. Les classes {@link ClansCompat} et {@link MatchsCompat} ne sont chargees que si le
 * mod correspondant est la ; une API devenue incompatible (LinkageError) coupe le lien au lieu de planter, et le
 * ping retombe sur le cas suivant (clan, soi seul).
 */
public final class Compat {
    private Compat() {
    }

    private static Boolean clans, matchs;

    public static boolean clans() {
        if (clans == null) clans = charge("heralisz_clans");
        return clans;
    }

    public static boolean matchs() {
        if (matchs == null) matchs = charge("heraliz_noyau");
        return matchs;
    }

    private static boolean charge(String modid) {
        try {
            return ModList.get() != null && ModList.get().isLoaded(modid);
        } catch (Exception ex) {
            return false;
        }
    }

    static void couperClans(Throwable t) {
        clans = false;
        HeralizPing.LOG.warn("[ping] lien avec HeraliZ Clans coupe (API incompatible) : {}", t.toString());
    }

    static void couperMatchs(Throwable t) {
        matchs = false;
        HeralizPing.LOG.warn("[ping] lien avec les matchs du noyau coupe (API incompatible) : {}", t.toString());
    }
}
