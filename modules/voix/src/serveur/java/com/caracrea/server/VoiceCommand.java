package com.caracrea.server;

import com.caracrea.network.CaraCreaNetwork;
import com.caracrea.voice.net.UdpVoiceServer;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * {@code /voix}: opens your voice settings; operators mute and unmute players (with a duration, online or not), list
 * the mutes, look at one player's voice connection and at the server's voice figures.
 *
 * <p>Replies go straight to the player with {@code sendSystemMessage}: on HeraliZ the world has
 * {@code sendCommandFeedback=false}, which silences {@code sendSuccess} / {@code sendFailure} for players.
 */
public final class VoiceCommand {
    private static final SuggestionProvider<CommandSourceStack> DURATIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(List.of("10m", "30m", "1h", "1j", "7j", "perm"), builder);
    private static UdpVoiceServer.Stats lastStats;
    private static long lastStatsTime;

    private VoiceCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("voix")
                .executes(context -> open(context.getSource()))
                .then(Commands.literal("mute")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("joueurs", GameProfileArgument.gameProfile())
                                .executes(context -> mute(context, null, null))
                                .then(Commands.argument("duree", StringArgumentType.word()).suggests(DURATIONS)
                                        .executes(context -> mute(context, StringArgumentType.getString(context, "duree"), null))
                                        .then(Commands.argument("raison", StringArgumentType.greedyString())
                                                .executes(context -> mute(context, StringArgumentType.getString(context, "duree"),
                                                        StringArgumentType.getString(context, "raison")))))))
                .then(Commands.literal("unmute")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("joueurs", GameProfileArgument.gameProfile())
                                .executes(VoiceCommand::unmute)))
                .then(Commands.literal("mutes")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("info")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .executes(context -> info(context.getSource(), EntityArgument.getPlayer(context, "joueur")))))
                .then(Commands.literal("stats")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> stats(context.getSource()))));
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        // The screen itself says when the voices are handled elsewhere (Simple Voice Chat) or off.
        CaraCreaNetwork.sendTo(player, new CaraCreaNetwork.OpenVoiceSettings());
        return 1;
    }

    private static int mute(CommandContext<CommandSourceStack> context, String durationText, String reason) throws CommandSyntaxException {
        Duration duration = null;
        if (durationText != null && !durationText.equalsIgnoreCase("perm") && !durationText.equalsIgnoreCase("definitif")) {
            duration = VoiceMutes.parse(durationText);
            if (duration == null) {
                reply(context.getSource(), Component.translatable("caracrea.voice.command.bad_duration", durationText).withStyle(ChatFormatting.RED));
                return 0;
            }
        }
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "joueurs");
        for (GameProfile profile : profiles) {
            VoiceMutes.mute(profile.getId(), profile.getName(), duration, reason, context.getSource().getTextName());
        }
        MutableComponent message = duration == null
                ? Component.translatable("caracrea.voice.command.muted", profiles.size())
                : Component.translatable("caracrea.voice.command.muted_for", profiles.size(), VoiceMutes.remainingText(duration.toMillis()));
        reply(context.getSource(), message.withStyle(ChatFormatting.GREEN));
        return profiles.size();
    }

    private static int unmute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = 0;
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "joueurs");
        for (GameProfile profile : profiles) {
            if (VoiceMutes.unmute(profile.getId())) {
                count++;
            }
        }
        reply(context.getSource(), Component.translatable("caracrea.voice.command.unmuted", count).withStyle(ChatFormatting.GREEN));
        return count;
    }

    private static int list(CommandSourceStack source) {
        List<VoiceMutes.Mute> mutes = VoiceMutes.sorted();
        reply(source, Component.translatable("caracrea.voice.command.mutes", mutes.size()).withStyle(ChatFormatting.GOLD));
        long now = System.currentTimeMillis();
        for (VoiceMutes.Mute mute : mutes) {
            MutableComponent line = Component.literal(" • " + mute.name() + " — ").withStyle(ChatFormatting.WHITE);
            line.append(mute.until() == 0 ? Component.translatable("caracrea.voice.command.forever")
                    : Component.translatable("caracrea.voice.command.left", VoiceMutes.remainingText(mute.until() - now)));
            if (!mute.reason().isBlank()) {
                line.append(Component.literal(" — " + mute.reason()).withStyle(ChatFormatting.GRAY));
            }
            if (!mute.by().isBlank()) {
                line.append(Component.translatable("caracrea.voice.command.by", mute.by()).withStyle(ChatFormatting.DARK_GRAY));
            }
            reply(source, line);
        }
        return mutes.size();
    }

    private static int info(CommandSourceStack source, ServerPlayer target) {
        VoiceServer.Info info = VoiceServer.info(target.getUUID());
        if (info == null) {
            reply(source, Component.translatable("caracrea.voice.command.no_session", target.getGameProfile().getName()).withStyle(ChatFormatting.GRAY));
            return 0;
        }
        reply(source, Component.translatable("caracrea.voice.command.info", info.name(), info.transport(),
                info.rttMs() < 0 ? "—" : info.rttMs() + " ms", percent(info.lossPermille()), info.bufferMs() + " ms").withStyle(ChatFormatting.GOLD));
        MutableComponent state = Component.literal(" ");
        state.append(Component.translatable(info.talking() ? "caracrea.voice.command.talking" : "caracrea.voice.command.silent"));
        if (!info.group().isEmpty()) {
            state.append(" · ").append(Component.translatable("caracrea.voice.command.group", info.group()));
        }
        if (info.disabled()) {
            state.append(" · ").append(Component.translatable("caracrea.voice.command.disabled"));
        }
        if (info.selfMuted()) {
            state.append(" · ").append(Component.translatable("caracrea.voice.command.self_muted"));
        }
        if (info.adminMuted()) {
            VoiceMutes.Mute mute = VoiceMutes.get(target.getUUID());
            state.append(" · ").append(Component.translatable("caracrea.voice.command.admin_muted").withStyle(ChatFormatting.RED));
            if (mute != null && mute.until() > 0) {
                state.append(" (").append(Component.translatable("caracrea.voice.command.left",
                        VoiceMutes.remainingText(mute.until() - System.currentTimeMillis()))).append(")");
            }
        }
        state.append(Component.literal(String.format(Locale.ROOT, " · UDP %d ↑ %d ↓", info.framesIn(), info.framesOut()))
                .withStyle(ChatFormatting.DARK_GRAY));
        reply(source, state);
        return 1;
    }

    private static int stats(CommandSourceStack source) {
        VoiceServer.Stats stats = VoiceServer.stats();
        if (!stats.active()) {
            reply(source, Component.translatable("caracrea.voice.command.off").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        reply(source, Component.translatable("caracrea.voice.command.stats", stats.udpPort() == 0 ? "—" : String.valueOf(stats.udpPort()),
                stats.sessions(), stats.udpBound(), stats.sessions() - stats.udpBound(), stats.groups()).withStyle(ChatFormatting.GOLD));
        UdpVoiceServer.Stats udp = stats.udp();
        long now = System.nanoTime();
        if (udp != null) {
            String rate = "—";
            if (lastStats != null && now > lastStatsTime) {
                double seconds = (now - lastStatsTime) / 1e9;
                rate = String.format(Locale.ROOT, "%.0f / %.0f p/s, %.1f / %.1f kbit/s", (udp.packetsIn() - lastStats.packetsIn()) / seconds,
                        (udp.packetsOut() - lastStats.packetsOut()) / seconds, (udp.bytesIn() - lastStats.bytesIn()) * 8 / seconds / 1000,
                        (udp.bytesOut() - lastStats.bytesOut()) * 8 / seconds / 1000);
            }
            lastStats = udp;
            lastStatsTime = now;
            reply(source, Component.translatable("caracrea.voice.command.stats_udp", udp.packetsIn(), udp.packetsOut(), udp.invalid(),
                    udp.replayed(), udp.limited(), rate));
            reply(source, Component.translatable("caracrea.voice.command.stats_cost",
                    String.format(Locale.ROOT, "%.1f", udp.relayed() == 0 ? 0 : udp.relayNanos() / 1000.0 / udp.relayed()),
                    String.format(Locale.ROOT, "%.1f", stats.tickRecentMicros())));
        }
        reply(source, Component.translatable("caracrea.voice.command.stats_frames", stats.relayed(), stats.tcpIn(), stats.tcpOut(), stats.udpOut())
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static String percent(int permille) {
        return String.format(Locale.FRANCE, "%.1f %%", permille / 10.0);
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
