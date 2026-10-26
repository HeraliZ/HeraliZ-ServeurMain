package fr.heraliz.corps.compat;

import fr.heraliz.corps.HeralizCorps;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Lien souple avec HeraliZ Clans (cote serveur) : clan d'un joueur par {@code ClansApi.clanDe}. Sans le mod (ou en cas
 * d'erreur), personne n'a de clan. La classe qui touche a l'API ({@link Api}) n'est chargee que si le mod est present.
 */
public final class ClansCompat {
    private ClansCompat() {
    }

    private static Boolean present;
    private static boolean casse;

    public static boolean present() {
        if (present == null) present = ModList.get() != null && ModList.get().isLoaded("heralisz_clans");
        return present && !casse;
    }

    /** Clan du joueur, ou null. */
    @Nullable
    public static UUID clanDe(UUID joueur) {
        if (joueur == null || !present()) return null;
        try {
            return Api.clanDe(joueur);
        } catch (LinkageError | RuntimeException e) {
            casse = true;
            HeralizCorps.LOG.warn("[corps] lien avec HeraliZ Clans coupe : {}", e.toString());
            return null;
        }
    }

    private static final class Api {
        @Nullable
        static UUID clanDe(UUID joueur) {
            return fr.heralisz.clans.api.ClansApi.clanDe(joueur).orElse(null);
        }
    }
}
