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
 * {@code /caracrea} (or {@code /perso}) opens your own creator; operators can open, randomize or reset other
 * players' characters, and mute or unmute them in the voice chat (without end; {@code /voix mute} sets a duration).
 *
 * <p>Replies go straight to the player with {@code sendSystemMessage}: on HeraliZ the world has
 * {@code sendCommandFeedback=false}, which silences {@code sendSuccess} / {@code sendFailure} for players.
 */
public final class CharacterCommand {
    private CharacterCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("caracrea")
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
                                }, "caracrea.command.reset"))))
                .then(Commands.literal("mute")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(context -> forEach(context, player -> {
                                    VoiceMutes.mute(player.getUUID(), player.getGameProfile().getName(), null, null,
                                            context.getSource().getTextName());
                                    return true;
                                }, "caracrea.command.muted"))))
                .then(Commands.literal("unmute")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(context -> forEach(context, player -> {
                                    VoiceMutes.unmute(player.getUUID());
                                    return true;
                                }, "caracrea.command.unmuted"))));
        dispatcher.register(root);
        dispatcher.register(Commands.literal("perso").redirect(dispatcher.getRoot().getChild("caracrea"))
                .executes(context -> openOwn(context.getSource())));
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
        CaraCrea.LOGGER.info("{}: /caracrea {} on {} player(s)", context.getSource().getTextName(), message, players.size());
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
