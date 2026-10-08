package fr.heraliz.wonder.paralyzer;

import com.mojang.brigadier.Command;
import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/** Partie serveur du Paralyzer (reprise telle quelle de Paralyseur.brancher) : rayon, commande /wonderpz, TaCZ. */
public final class ParalyseurServeur {
    private ParalyseurServeur() {
    }

    public static void brancher(IEventBus mod, IEventBus forge) {
        forge.addListener(RayonServeur::tick);
        forge.addListener(RayonServeur::joueur);
        forge.addListener(RayonServeur::rejoindre);
        forge.addListener(PerfParalyseur::tick);
        forge.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class, e -> RayonServeur.vider());
        forge.addListener(ParalyseurServeur::commandes);
        if (ModList.get().isLoaded("tacz")) TaczParalyseur.brancher(forge);
        HeralizWonder.LOG.info("[wonder] Paralyzer branche");
    }

    /** {@code /wonderpz perf} (operateurs) : cout serveur du rayon depuis le dernier releve. */
    private static void commandes(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("wonderpz").requires(s -> s.hasPermission(2))
                .then(Commands.literal("perf").executes(c -> {
                    String r = PerfParalyseur.resume();
                    c.getSource().sendSuccess(() -> Component.literal("Paralyzer : " + r), false);
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
