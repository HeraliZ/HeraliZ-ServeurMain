package com.Spartan.ban;

import com.Spartan.Spartan;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commandes de sanction alignées (11/10) : un nom par action, et sa commande inverse.
 * <ul>
 *   <li>LiteBans : /kick, /ban (compte seul, {@code punish_ip: false}), /tempban, /banip, /mute, /tempmute, /unmute,
 *   /unban (lève aussi les bans temporaires et les bans d'IP) ;</li>
 *   <li>/untempban &lt;joueur&gt; [raison] : transmis à /unban de LiteBans, avec les droits de celui qui tape la commande
 *   (LiteBans vérifie ses permissions) ;</li>
 *   <li>/banpc = /sban (compte + IP + PC, Spartan) ; /unbanpc = /sunban (lève les bans du joueur et de ses PC).</li>
 * </ul>
 * /sban et /sunban restent aussi. LiteBans est appelé par l'API Bukkit du serveur hybride (Mohist), par réflexion et
 * sans dépendance : sans Bukkit ou sans LiteBans, /untempban le dit et renvoie vers /sunban.
 */
final class AliasSanctions {
    private AliasSanctions() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, LiteralCommandNode<CommandSourceStack> sban,
                         LiteralCommandNode<CommandSourceStack> sunban) {
        dispatcher.register(Commands.literal("banpc").requires(src -> src.hasPermission(3)).redirect(sban));
        dispatcher.register(Commands.literal("unbanpc").requires(src -> src.hasPermission(3)).redirect(sunban));
        dispatcher.register(Commands.literal("untempban")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), b))
                        .executes(ctx -> transmettre(ctx, "litebans:unban"))));
    }

    private static boolean resolu;
    private static Method getPlayer, getConsole, dispatch, getPluginManager, getPlugin;

    private static void resoudre() {
        resolu = true;
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Class<?> sender = Class.forName("org.bukkit.command.CommandSender");
            getPlayer = bukkit.getMethod("getPlayer", UUID.class);
            getConsole = bukkit.getMethod("getConsoleSender");
            dispatch = bukkit.getMethod("dispatchCommand", sender, String.class);
            getPluginManager = bukkit.getMethod("getPluginManager");
            getPlugin = Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin", String.class);
        } catch (ReflectiveOperationException | LinkageError ex) {
            dispatch = null;
        }
    }

    /** Fait taper « commande args » à LiteBans par le même joueur (ou la console). */
    private static int transmettre(CommandContext<CommandSourceStack> ctx, String commande) {
        CommandSourceStack src = ctx.getSource();
        String args = StringArgumentType.getString(ctx, "args");
        if (!resolu) resoudre();
        try {
            Object pm = dispatch == null ? null : getPluginManager.invoke(null);
            if (pm == null || getPlugin.invoke(pm, "LiteBans") == null) {
                src.sendFailure(Component.literal("LiteBans n'est pas chargé : pour un ban de Spartan, utilisez /unbanpc."));
                return 0;
            }
            Object qui = src.getEntity() instanceof ServerPlayer p ? getPlayer.invoke(null, p.getUUID()) : getConsole.invoke(null);
            if (qui == null) {
                src.sendFailure(Component.literal("Expéditeur inconnu de Bukkit : tapez /unban."));
                return 0;
            }
            return Boolean.TRUE.equals(dispatch.invoke(null, qui, commande + " " + args)) ? 1 : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            Spartan.LOGGER.warn("[Spartan] /untempban : LiteBans injoignable ({})", ex.toString());
            src.sendFailure(Component.literal("LiteBans injoignable : utilisez /unban."));
            return 0;
        }
    }
}
