package com.caracrea.server;

import com.caracrea.CaraCrea;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * {@code /perso} opens your own creator; operators can open, randomize or reset other players' characters
 * ({@code /perso open|random|reset <players>}).
 *
 * <p>02/11 (audit, point 36): {@code /caracrea} was the same command under another name ({@code /perso} only redirected
 * to it) and is gone; its {@code mute|unmute} (without end) are {@code /voix mute <players>} (no duration: without end)
 * and {@code /voix unmute <players>}.
 *
 * <p>Replies go straight to the player with {@code sendSystemMessage}: on HeraliZ the world has
 * {@code sendCommandFeedback=false}, which silences {@code sendSuccess} / {@code sendFailure} for players.
 */
public final class CharacterCommand {
    private CharacterCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("perso")
                .executes(context -> openOwn(context.getSource()))
                .then(Commands.literal("open")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(context -> forEach(context, player -> CharacterService.openWhenFree(player, false), "caracrea.command.opened"))))
                .then(Commands.literal("random")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(context -> forEach(context, player -> {
                                    CharacterService.randomize(player);
                                    return true;
                                }, "caracrea.command.randomized"))))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(context -> forEach(context, player -> {
                                    CharacterService.reset(player);
                                    return !CharacterService.busy(player);
                                }, "caracrea.command.reset"))));
        dispatcher.register(root);
    }

    private static int openOwn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!com.caracrea.CaraCreaConfig.SERVER.freeEdit.get() && !source.hasPermission(2)) {
            player.sendSystemMessage(Component.translatable("caracrea.message.not_allowed").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (CharacterService.busy(player)) {
            player.sendSystemMessage(Component.translatable("caracrea.message.busy").withStyle(ChatFormatting.RED));
            return 0;
        }
        Component refus = EditionLibre.refus(player);
        if (refus != null) {
            player.sendSystemMessage(refus.copy().withStyle(ChatFormatting.RED));
            return 0;
        }
        CharacterService.openSelf(player);
        return 1;
    }

    /** What the command does to one player; false when it is postponed (the player is busy). */
    private interface PlayerAction {
        boolean run(ServerPlayer player);
    }

    private static int forEach(CommandContext<CommandSourceStack> context, PlayerAction action, String message) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        int later = 0;
        for (ServerPlayer player : players) {
            if (!action.run(player)) {
                later++;
            }
        }
        MutableComponent reply = Component.translatable(message, players.size());
        if (later > 0) {
            reply.append(" ").append(Component.translatable("caracrea.command.later", later).withStyle(ChatFormatting.GRAY));
        }
        reply(context.getSource(), reply);
        CaraCrea.LOGGER.info("{}: /perso {} on {} player(s)", context.getSource().getTextName(), message, players.size());
        return players.size();
    }

    /** To the player who ran the command (whatever sendCommandFeedback says), or to the console. */
    private static void reply(CommandSourceStack source, Component message) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            player.sendSystemMessage(message);
        } else {
            source.sendSuccess(() -> message, false);
        }
    }
}
