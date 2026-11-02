package fr.heraliz.casino.event;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.command.CasinoCommand;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.config.CasinoConfigServeur;
import fr.heraliz.casino.data.Jackpot;
import fr.heraliz.casino.economy.ChipsServeur;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.net.ConfigSyncPacket;
import fr.heraliz.casino.net.JackpotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Evenements du serveur seul : commandes, lecture des reglages au demarrage, connexion d'un joueur (reglages, jackpot,
 * gains en attente). Les regles appliquees des deux cotes restent dans {@link CommonEvents} (commun).
 */
@Mod.EventBusSubscriber(modid = HeralizCasino.MODID)
public final class CommonEventsServeur {
    private CommonEventsServeur() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CasinoCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        String err = fr.heraliz.casino.config.CasinoConfigServeur.load();
        HeralizCasino.LOGGER.info("[HeralizCasino] Reglages charges{}", err == null ? "" : " (erreur : " + err + ")");
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        sendConfig(player);
        ChipsServeur.convertirAnciennes(player);
        CasinoNet.sendTo(player, new JackpotPacket(Jackpot.get(player.getServer()).value()));
        fr.heraliz.casino.economy.CompteCapsules.envoyer(player);
        // gains mis de cote (anciens gains d'un joueur deconnecte, compte plein ou bloque) : sur le compte (02/11)
        fr.heraliz.casino.economy.CompteCapsules.versersEnAttente(player);
    }

    public static void sendConfig(ServerPlayer player) {
        CasinoNet.sendTo(player, new ConfigSyncPacket(jsonPour(player.getServer())));
    }

    /** Apres /casino reload : tous les joueurs recoivent les nouveaux reglages. */
    public static void broadcastConfig() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        CasinoNet.sendToAll(server, new ConfigSyncPacket(jsonPour(server)));
    }

    /**
     * Serveur dedie : la copie des joueurs, sans les commandes console de la caisse (audit du 02/11, point 11, §7). Jeu solo
     * (serveur integre) : le jeu et son serveur partagent les memes reglages en memoire, la copie complete est gardee.
     */
    private static String jsonPour(@javax.annotation.Nullable MinecraftServer server) {
        return server != null && server.isDedicatedServer() ? CasinoConfigServeur.jsonJoueurs() : CasinoConfig.json();
    }
}
