package fr.heraliz.casino.event;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.command.CasinoCommand;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.data.Jackpot;
import fr.heraliz.casino.data.PendingPayouts;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.economy.ChipsServeur;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.net.ConfigSyncPacket;
import fr.heraliz.casino.net.JackpotPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
        CasinoNet.sendTo(player, new JackpotPacket(Jackpot.get(player.getServer()).value()));
        long pending = PendingPayouts.get(player.getServer()).take(player.getUUID());
        if (pending > 0) {
            ChipsServeur.give(player, pending);
            player.sendSystemMessage(Component.translatable("message.heralizcasino.pending", Chips.format(pending)).withStyle(ChatFormatting.GOLD));
        }
    }

    public static void sendConfig(ServerPlayer player) {
        CasinoNet.sendTo(player, new ConfigSyncPacket(CasinoConfig.json()));
    }

    /** Apres /casino reload : tous les joueurs recoivent les nouveaux reglages. */
    public static void broadcastConfig() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        CasinoNet.sendToAll(server, new ConfigSyncPacket(CasinoConfig.json()));
    }
}
