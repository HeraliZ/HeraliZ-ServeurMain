package fr.heraliz.crac.api;

import fr.heraliz.crac.bases.WipeBases;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/**
 * Pour HeraliZ Quêtes ({@code /wipe serveur}), appelée PAR RÉFLEXION : ne jamais renommer cette classe ni changer cette
 * signature (CLAUDE.md §5). Côté serveur seulement. À appeler AVANT la dissolution des clans (les bases sont rattachées à
 * leurs groupes).
 */
public final class WipeApi {
    private WipeApi() {
    }

    /**
     * Efface toutes les bases de tous les mondes et oublie les données de CRac liées aux joueurs (homes, tutoriels,
     * protections, cœurs, groupes). Renvoie les objets à vie ({@code garder}) trouvés dans les conteneurs des bases, par
     * joueur à qui les rendre.
     */
    public static Map<UUID, List<ItemStack>> toutEffacer(MinecraftServer server, Predicate<ItemStack> garder) {
        Map<UUID, List<ItemStack>> rendus = new HashMap<>();
        WipeBases.tout(server, garder, rendus);
        return rendus;
    }

    /**
     * {@code /wipe <joueur>} (HeraliZ Quêtes, par réflexion ; CRac 1.21.0) : efface seulement la cabane d'essai du joueur
     * dans le monde du tutoriel (armoire et sa case du haut comprises), jamais la base de son clan sur la carte. Renvoie le
     * nombre de pièces retirées.
     */
    public static int cabaneEssai(MinecraftServer server, UUID joueur) {
        return fr.heraliz.crac.bases.Tutoriel.effacerCabaneEssai(server, joueur);
    }
}
