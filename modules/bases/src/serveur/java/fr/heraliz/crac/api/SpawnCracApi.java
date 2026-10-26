package fr.heraliz.crac.api;

import fr.heraliz.crac.bases.Deplacements;
import net.minecraft.server.level.ServerPlayer;

/**
 * Point /spawn de CRac pour nos autres mods (côté serveur seulement, appelée par réflexion : retour d'une extraction de
 * HeraliZ Extraction). Ne jamais renommer cette classe ni changer ses signatures. Fil serveur.
 */
public final class SpawnCracApi {
    private SpawnCracApi() {
    }

    /**
     * Pose le joueur au point posé par le staff avec {@code /spawn definir} (vraie téléportation, regard compris). Rend
     * false sans rien faire si aucun point n'est défini ou si son monde est absent : l'appelant garde sa destination.
     */
    public static boolean allerAuSpawnDefini(ServerPlayer joueur) {
        return joueur != null && Deplacements.allerAuPointDefini(joueur);
    }
}
