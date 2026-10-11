package fr.heraliz.wonder.blundergat.client;

import com.mojang.brigadier.Command;
import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/**
 * Cote client du Blundergat et de l'Acid Gat : effets ({@link FxBlunder}) et branchements TaCZ ({@link BlunderTaczClient})
 * si TaCZ est la. Commande client {@code /blundergatfx} : occupation des reserves et cout du rendu (dans le journal).
 */
public final class BlunderClient {
    private BlunderClient() {
    }

    public static void init(IEventBus mod) {
        IEventBus f = MinecraftForge.EVENT_BUS;
        f.addListener(FxBlunder::tick);
        f.addListener(FxBlunder::rendu);
        f.addListener(FxBlunder::dechargement);
        f.addListener(FxBlunder::camera);
        f.addListener(EventPriority.LOWEST, FxBlunder::vivantAvant);
        f.addListener(EventPriority.LOWEST, FxBlunder::vivantApres);
        f.addListener(BlunderClient::commandes);
        if (ModList.get().isLoaded("tacz")) BlunderTaczClient.brancher(f);
    }

    private static void commandes(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("blundergatfx").executes(c -> {
            String r = FxBlunder.stats();
            HeralizWonder.LOG.info("[wonder] fx blundergat {}", r);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("heraliz_wonder.journal.effets_blundergat"), true);
            return Command.SINGLE_SUCCESS;
        }));
    }
}
