package fr.heralisz.clans.serveur;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.ReglagesClans;
import fr.heralisz.clans.server.Sync;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Réglages des clans, côté serveur seulement (audit du 02/11, point 11, §4 de split) : déclarés ici en COMMON, avec le
 * même nom de fichier qu'avant (config/heralisz_clans-common.toml du serveur, valeurs gardées), ils ne sont plus écrits
 * sur le PC des joueurs (la règle anti-boost, les gains d'XP et les règles des territoires y étaient lisibles). À chaque
 * chargement du fichier, ce que lit le code commun est recopié dans {@link ReglagesClans} ; le prix de création part avec
 * l'état du clan de chaque joueur (renvoyé à tous les joueurs connectés quand le fichier change).
 */
public final class ReglagesClansServeur {
    private ReglagesClansServeur() {
    }

    static void brancher(IEventBus busMod) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ClansConfig.SPEC, "heralisz_clans-common.toml");
        busMod.addListener((ModConfigEvent.Loading e) -> {
            if (e.getConfig().getSpec() == ClansConfig.SPEC) appliquer(false);
        });
        busMod.addListener((ModConfigEvent.Reloading e) -> {
            if (e.getConfig().getSpec() == ClansConfig.SPEC) appliquer(true);
        });
    }

    private static void appliquer(boolean envoyer) {
        long avant = ReglagesClans.coutCreation();
        ReglagesClans.definirCoutCreation(ClansConfig.COUT_CREATION.get());
        ReglagesClans.definirCercle(ClansConfig.RAYON_CAPTURE.get(), ClansConfig.HAUTEUR_CAPTURE.get());
        MinecraftServer serveur = ServerLifecycleHooks.getCurrentServer();
        // prix changé pendant que le serveur tourne : renvoyé à chacun avec l'état de son clan, sur le fil du serveur
        if (envoyer && serveur != null && avant != ReglagesClans.coutCreation()) {
            serveur.execute(() -> {
                for (ServerPlayer p : serveur.getPlayerList().getPlayers()) Sync.envoyerSnapshot(p);
            });
        }
    }
}
