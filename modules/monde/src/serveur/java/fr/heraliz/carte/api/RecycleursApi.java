package fr.heraliz.carte.api;

import fr.heraliz.carte.serveur.RecycleursCarte;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * API de la carte du monde pour nos autres mods (19/11 ; côté serveur seulement, appelée par réflexion par HeraliZ
 * Quêtes : {@code fr.heraliz.quetes.serveur.Ponts.repereCarte}). Ne jamais renommer cette classe ni changer ses
 * signatures. Fil serveur (un appel venu d'un autre fil y est renvoyé).
 */
public final class RecycleursApi {
    private RecycleursApi() {
    }

    /**
     * Un recycleur de la carte ({@code heraliz_quetes:recycleur_carte}) est là (present : posé, ou son tronçon vient de se
     * charger) ou n'y est plus (cassé, retiré). Son repère apparaît ou part aussitôt sur la carte de tous les joueurs ; rien
     * ne change s'il était déjà connu (ou déjà retiré). Ne lève jamais d'erreur.
     */
    public static void signaler(ServerLevel level, BlockPos pos, boolean present) {
        RecycleursCarte.signaler(level, pos, present);
    }
}
