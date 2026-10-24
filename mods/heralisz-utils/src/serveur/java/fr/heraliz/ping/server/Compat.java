package fr.heraliz.ping.server;

import fr.heraliz.ping.HeralizPing;
import net.minecraftforge.fml.ModList;

/**
 * Presence des mods lies en douceur. La classe {@link ClansCompat} n'est chargee que si le mod clans est la ; une API
 * devenue incompatible (LinkageError) coupe le lien au lieu de planter, et le ping retombe sur soi seul.
 */
public final class Compat {
    private Compat() {
    }

    private static Boolean clans;

    public static boolean clans() {
        if (clans == null) clans = charge("heralisz_clans");
        return clans;
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
}
