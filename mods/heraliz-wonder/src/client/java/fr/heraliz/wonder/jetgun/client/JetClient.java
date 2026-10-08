package fr.heraliz.wonder.jetgun.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.jetgun.JetGun;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Côté client du Jet Gun : effets ({@link JetFx}), jauge de chaleur ({@link JetHud}), son du réacteur ({@link JetSon}),
 * branchements TaCZ ({@link JetTaczClient}) si TaCZ est là. Commande client {@code /jetgunfx} : occupation des réserves et
 * coût du rendu ; banc d'essai : {@code tenir <ticks>} (détente tenue), {@code inspecter}, {@code purger}.
 */
public final class JetClient {
    private JetClient() {
    }

    public static void init(IEventBus mod, IEventBus forge) {
        forge.addListener(JetFx::tick);
        forge.addListener(JetFx::rendu);
        forge.addListener(EventPriority.LOWEST, JetFx::vivantAvant);
        forge.addListener(EventPriority.LOWEST, JetFx::vivantApres);
        forge.addListener(JetFx::camera);
        forge.addListener(JetFx::dechargement);
        forge.addListener(JetHud::masquerTacz);
        forge.addListener(JetClient::commandes);
        forge.addListener(JetClient::banc);
        mod.addListener(JetClient::calques);
        if (JetGun.tacz) JetTaczClient.brancher(forge);
    }

    private static void calques(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("jetgun_chaleur", JetHud::rendu);
    }

    // ------------------------------------------------------------------ banc d'essai : détente tenue
    private static int tenir;

    private static void banc(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || tenir <= 0) return;
        tenir--;
        if (JetGun.tacz) JetTaczClient.tirer();
    }

    private static void commandes(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("jetgunfx").executes(c -> {
            String r = JetFx.stats();
            HeralizWonder.LOG.info("[wonder] fx {}", r);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("Effets du Jet Gun : voir le journal"), true);
            return Command.SINGLE_SUCCESS;
        }).then(Commands.literal("tenir").then(Commands.argument("ticks", IntegerArgumentType.integer(0, 2000)).executes(c -> {
            tenir = IntegerArgumentType.getInteger(c, "ticks");
            return Command.SINGLE_SUCCESS;
        }))).then(Commands.literal("inspecter").executes(c -> {
            if (JetGun.tacz) JetTaczClient.inspecter();
            return Command.SINGLE_SUCCESS;
        })).then(Commands.literal("etat").executes(c -> {
            if (JetGun.tacz) HeralizWonder.LOG.info("[wonder] etat jet gun : {}", JetTaczClient.etat());
            return Command.SINGLE_SUCCESS;
        })).then(Commands.literal("purger").executes(c -> {
            if (JetGun.tacz) JetTaczClient.recharger();
            return Command.SINGLE_SUCCESS;
        })));
    }
}
