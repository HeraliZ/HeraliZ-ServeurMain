package fr.heraliz.grades.serveur;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Hôtel des ventes, pour les autres mods (lu PAR RÉFLEXION : ne jamais renommer cette classe ni ses méthodes) :
 * HeraliZ Quêtes ouvre l'écran depuis le PNJ commissaire-priseur (marchand de type « hdv ») et vide les ventes d'un
 * joueur remis à zéro ({@code /wipe}). Fil serveur seulement.
 */
public final class HdvApi {
    private HdvApi() {
    }

    /** Ouvre l'hôtel des ventes à ce joueur (mêmes refus que {@code /hdv}) ; vrai s'il s'ouvre. */
    public static boolean ouvrir(ServerPlayer p) {
        return HdvServeur.ouvrir(p) > 0;
    }

    /** /wipe : ventes et objets en attente du joueur retirés ; rend leurs objets (l'appelant ne garde que les objets à vie). */
    public static List<ItemStack> wipe(UUID joueur) {
        return HdvServeur.wipe(joueur);
    }
}
