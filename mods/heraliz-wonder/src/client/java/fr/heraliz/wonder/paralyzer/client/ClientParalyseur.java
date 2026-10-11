package fr.heraliz.wonder.paralyzer.client;

import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/**
 * Cote client du Paralyzer : effets ({@link FxParalyseur}) et, si TaCZ est la, le branchement des tirs
 * ({@link TaczParalyseurClient}). Commande client {@code /wonderpzfx} : cout du rendu et occupation des reserves.
 */
public final class ClientParalyseur {
    private ClientParalyseur() {
    }

    public static void init(IEventBus mod) {
        IEventBus f = MinecraftForge.EVENT_BUS;
        f.addListener(FxParalyseur::tick);
        f.addListener(FxParalyseur::rendu);
        f.addListener(EventPriority.HIGH, FxParalyseur::vivantAvant);
        f.addListener(FxParalyseur::dechargement);
        f.addListener(ClientParalyseur::commandes);
        if (ModList.get().isLoaded("tacz")) TaczParalyseurClient.brancher(f);
    }

    private static void commandes(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("wonderpzfx").executes(c -> {
            String r = FxParalyseur.stats();
            HeralizWonder.LOG.info("[wonder] fx {}", r);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("heraliz_wonder.journal.effets_paralyzer"), true);
            return 1;
        }));
    }
}
