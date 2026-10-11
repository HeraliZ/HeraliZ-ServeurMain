package fr.heraliz.wonder.client;

import com.mojang.brigadier.Command;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.client.fx.Fx;
import fr.heraliz.wonder.client.fx.FxVent;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/**
 * Cote client : effets (projectiles, foudre, impacts, electrocution) dessines par {@link Fx}, et branchements TaCZ
 * ({@link TaczClient}) si TaCZ est la. Commande client {@code /wonderfx} : occupation des reserves et cout du rendu.
 */
public final class ClientWonder {
    private ClientWonder() {
    }

    /** TaCZ charge (lu une fois). */
    public static boolean tacz;

    public static void init(IEventBus mod) {
        tacz = ModList.get().isLoaded("tacz");
        IEventBus f = MinecraftForge.EVENT_BUS;
        f.addListener(Fx::tick);
        f.addListener(Fx::rendu);
        f.addListener(EventPriority.LOWEST, Fx::vivantAvant);
        f.addListener(EventPriority.LOWEST, Fx::vivantApres);
        f.addListener(Fx::dechargement);
        f.addListener(Fx::ecran);
        // Thundergun (1.0.2) : onde de choc, poussiere, culbutes, secousse de la camera
        f.addListener(FxVent::tick);
        f.addListener(FxVent::rendu);
        f.addListener(EventPriority.LOWEST, FxVent::vivantAvant);
        f.addListener(EventPriority.LOWEST, FxVent::vivantApres);
        f.addListener(FxVent::dechargement);
        f.addListener(FxVent::camera);
        f.addListener(FxVent::ecran);
        f.addListener(ClientWonder::commandes);
        if (tacz) TaczClient.brancher(f);
    }

    private static void commandes(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("wonderfx").executes(c -> {
            String r = Fx.stats() + " | " + FxVent.stats();
            HeralizWonder.LOG.info("[wonder] fx {}", r);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("heraliz_wonder.journal.effets"), true);
            return Command.SINGLE_SUCCESS;
        }).then(Commands.literal("inspecter").executes(c -> {
            // banc d'essai : inspection TaCZ de l'arme tenue (comme la touche d'inspection)
            if (tacz) TaczClient.inspecter();
            return Command.SINGLE_SUCCESS;
        })));
    }
}
