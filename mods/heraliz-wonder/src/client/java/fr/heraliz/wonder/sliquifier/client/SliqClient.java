package fr.heraliz.wonder.sliquifier.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
 * Cote client du Sliquifier : effets ({@link FxSliq}), glisse du joueur local ({@link GlisseJoueur}), branchements TaCZ
 * ({@link SliqTaczClient}) si TaCZ est la. Commande client {@code /sliqfx} : occupation des reserves et cout du rendu ;
 * {@code /sliqfx glisser <ticks>} (banc) : avancer tout droit ce nombre de ticks.
 */
public final class SliqClient {
    private SliqClient() {
    }

    public static void init(IEventBus mod) {
        IEventBus f = MinecraftForge.EVENT_BUS;
        f.addListener(FxSliq::tick);
        f.addListener(FxSliq::rendu);
        f.addListener(EventPriority.LOWEST, FxSliq::vivantAvant);
        f.addListener(EventPriority.LOWEST, FxSliq::vivantApres);
        f.addListener(FxSliq::dechargement);
        f.addListener(SliqClient::commandes);
        if (ModList.get().isLoaded("tacz")) SliqTaczClient.brancher(f);
    }

    private static void commandes(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("sliqfx").executes(c -> {
            String r = FxSliq.stats();
            HeralizWonder.LOG.info("[sliquifier] fx {}", r);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("heraliz_wonder.journal.effets_sliquifier"), true);
            return Command.SINGLE_SUCCESS;
        }).then(Commands.literal("glisser").then(Commands.argument("ticks", IntegerArgumentType.integer(1, 200)).executes(c -> {
            GlisseJoueur.avancer = IntegerArgumentType.getInteger(c, "ticks");
            return Command.SINGLE_SUCCESS;
        }))));
    }
}
