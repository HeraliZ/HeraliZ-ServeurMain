package fr.heraliz.corps.client;

import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Lien souple avec HeraliZ Clans cote client : identifiant du clan du joueur local (snapshot recu du serveur). Sans le
 * mod : aucun clan, donc aucun marqueur.
 */
final class ClansClient {
    private ClansClient() {
    }

    private static Boolean present;
    private static boolean casse;

    @Nullable
    static UUID monClan() {
        if (present == null) present = ModList.get() != null && ModList.get().isLoaded("heralisz_clans");
        if (!present || casse) return null;
        try {
            return Api.monClan();
        } catch (LinkageError | RuntimeException e) {
            casse = true;
            return null;
        }
    }

    private static final class Api {
        @Nullable
        static UUID monClan() {
            var s = fr.heralisz.clans.client.ClientClans.snapshot();
            return s == null ? null : s.id();
        }
    }
}
