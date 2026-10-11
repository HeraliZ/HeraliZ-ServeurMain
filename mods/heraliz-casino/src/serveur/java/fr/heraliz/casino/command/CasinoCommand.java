package fr.heraliz.casino.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.data.CasinoStats;
import fr.heraliz.casino.data.Jackpot;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.economy.ChipsServeur;
import fr.heraliz.casino.event.CommonEventsServeur;
import fr.heraliz.casino.game.slot.SlotGame;
import fr.heraliz.casino.machine.MachineType;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

/**
 * /casino : solde, jetons (admin), machines (admin), statistiques, jackpot, rechargement des reglages. Les reponses
 * passent par {@link Reponses} (visibles meme avec la regle sendCommandFeedback=false).
 */
public final class CasinoCommand {
    private CasinoCommand() {
    }

    private static final SuggestionProvider<CommandSourceStack> MACHINES = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(MachineType.values()).map(t -> t.id), b);
    /** Machines de jeu (celles qu'on peut simuler). */
    private static final SuggestionProvider<CommandSourceStack> GAMES = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(MachineType.values()).filter(MachineType::game).map(t -> t.id), b);

    /** Cibles de /casino lumiere : toutes les machines, ou l'une d'elles. */
    private static final SuggestionProvider<CommandSourceStack> LIGHT_TARGETS = (ctx, b) ->
            SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("toutes"), Arrays.stream(MachineType.values()).map(t -> t.id)), b);

    /** /casino lumiere : lumiere actuelle de chaque machine. */
    private static int lights(CommandContext<CommandSourceStack> c) {
        CasinoConfig.Settings s = CasinoConfig.get();
        Reponses.ok(c.getSource(), Component.translatable("command.heralizcasino.lumiere.title").withStyle(ChatFormatting.GOLD));
        for (MachineType t : MachineType.values()) {
            int l = s.light(t);
            MutableComponent line = Component.literal("  ").append(t.displayName()).append(Component.literal(" : " + l))
                    .withStyle(l == t.light ? ChatFormatting.GRAY : ChatFormatting.YELLOW);
            if (l != t.light) line.append(Component.translatable("command.heralizcasino.lumiere.usine", t.light).withStyle(ChatFormatting.DARK_GRAY));
            Reponses.ok(c.getSource(), line);
        }
        return 1;
    }

    /** /casino lumiere &lt;machine|toutes&gt; &lt;0-15|defaut&gt; : regle la lumiere emise (les machines posees suivent). */
    private static int setLight(CommandContext<CommandSourceStack> c, String target, int level) {
        MachineType t = target.equalsIgnoreCase("toutes") ? null : MachineType.byId(target);
        if (t == null && !target.equalsIgnoreCase("toutes")) {
            Reponses.erreur(c.getSource(), Component.translatable("command.heralizcasino.unknown_machine", target));
            return 0;
        }
        try {
            fr.heraliz.casino.config.CasinoConfigServeur.setLight(t, level);
        } catch (java.io.IOException e) {
            Reponses.erreur(c.getSource(), Component.translatable("message.heralizcasino.admin_error"));
            return 0;
        }
        CommonEventsServeur.broadcastConfig();
        Component who = t == null ? Component.translatable("command.heralizcasino.lumiere.all") : t.displayName();
        Component what = level < 0 ? Component.translatable("command.heralizcasino.lumiere.defaut") : Component.literal(Integer.toString(level));
        Reponses.ok(c.getSource(), Component.translatable("command.heralizcasino.lumiere.set", who, what).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** /casino roll &lt;machine&gt; [parties] (et son raccourci /roll casino ...) : simulation avec les reglages actuels. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> rollArgs() {
        return Commands.argument("machine", StringArgumentType.word()).suggests(GAMES)
                .executes(c -> roll(c, StringArgumentType.getString(c, "machine"), 0))
                .then(Commands.argument("parties", LongArgumentType.longArg(1000, MAX_ROLL))
                        .executes(c -> roll(c, StringArgumentType.getString(c, "machine"), LongArgumentType.getLong(c, "parties"))));
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        // le droit est aussi pose sur "casino" : un autre mod peut avoir sa propre commande /roll, ouverte a tous
        d.register(Commands.literal("roll").requires(s -> s.hasPermission(2))
                .then(Commands.literal("casino").requires(s -> s.hasPermission(2)).then(rollArgs())));
        d.register(Commands.literal("casino")
                .then(ZoneCommand.build())
                .then(Commands.literal("roll").requires(s -> s.hasPermission(2)).then(rollArgs()))
                .then(Commands.literal("solde").executes(CasinoCommand::balance))
                .then(Commands.literal("jetons").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("donner").then(Commands.argument("joueurs", EntityArgument.players())
                                .then(Commands.argument("montant", LongArgumentType.longArg(1, 1_000_000_000L))
                                        .executes(c -> give(c, EntityArgument.getPlayers(c, "joueurs"), LongArgumentType.getLong(c, "montant"))))))
                        .then(Commands.literal("retirer").then(Commands.argument("joueurs", EntityArgument.players())
                                .then(Commands.argument("montant", LongArgumentType.longArg(1, 1_000_000_000L))
                                        .executes(c -> take(c, EntityArgument.getPlayers(c, "joueurs"), LongArgumentType.getLong(c, "montant"))))))
                        .then(Commands.literal("voir").then(Commands.argument("joueur", EntityArgument.player())
                                .executes(c -> see(c, EntityArgument.getPlayer(c, "joueur"))))))
                .then(Commands.literal("machine").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("joueurs", EntityArgument.players())
                                .then(Commands.argument("machine", StringArgumentType.word()).suggests(MACHINES)
                                        .executes(c -> machine(c, EntityArgument.getPlayers(c, "joueurs"), StringArgumentType.getString(c, "machine"))))))
                .then(Commands.literal("stats").requires(s -> s.hasPermission(2))
                        .executes(CasinoCommand::stats)
                        .then(Commands.literal("reset").executes(CasinoCommand::resetStats)))
                .then(Commands.literal("rtp").requires(s -> s.hasPermission(2)).executes(CasinoCommand::rtp))
                .then(Commands.literal("jackpot")
                        .executes(CasinoCommand::jackpot)
                        .then(Commands.argument("montant", LongArgumentType.longArg(0, 1_000_000_000L)).requires(s -> s.hasPermission(2))
                                .executes(c -> setJackpot(c, LongArgumentType.getLong(c, "montant")))))
                .then(Commands.literal("bot").requires(s -> s.hasPermission(2))
                        .executes(c -> bot(c, 0))
                        .then(Commands.argument("mise", LongArgumentType.longArg(1, 1_000_000_000L))
                                .executes(c -> bot(c, LongArgumentType.getLong(c, "mise")))))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(CasinoCommand::reload))
                .then(Commands.literal("lumiere").requires(s -> s.hasPermission(2))
                        .executes(CasinoCommand::lights)
                        .then(Commands.argument("machine", StringArgumentType.word()).suggests(LIGHT_TARGETS)
                                .then(Commands.argument("niveau", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 15))
                                        .executes(c -> setLight(c, StringArgumentType.getString(c, "machine"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "niveau"))))
                                .then(Commands.literal("defaut").executes(c -> setLight(c, StringArgumentType.getString(c, "machine"), -1)))))
                .then(Commands.literal("salle").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("clochard").executes(c -> salle(c, fr.heraliz.casino.world.SalleBuilder.Kind.CLOCHARD)))
                        .then(Commands.literal("vip").executes(c -> salle(c, fr.heraliz.casino.world.SalleBuilder.Kind.VIP)))));
    }

    /**
     * /casino salle &lt;clochard|vip&gt; : construit la salle devant le joueur (l'entree face a lui, au bout d'un parvis de
     * six blocs), machines et decor compris. Ecrase ce qui se trouve a cet endroit.
     */
    private static int salle(CommandContext<CommandSourceStack> c, fr.heraliz.casino.world.SalleBuilder.Kind kind) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        net.minecraft.core.BlockPos door = fr.heraliz.casino.world.SalleBuilder.build(p.serverLevel(), p.blockPosition(), p.getDirection(), kind);
        String key = "command.heralizcasino.salle." + kind.name().toLowerCase(Locale.ROOT);
        Reponses.ok(c.getSource(), Component.translatable(key, door.getX(), door.getY(), door.getZ()).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    /**
     * Partie de test contre un bot (pierre-feuille-ciseaux ou bataille navale la plus proche) : le bot releve le defi
     * ouvert, ou en ouvre un que le joueur accepte depuis la table.
     */
    private static int bot(CommandContext<CommandSourceStack> c, long amount) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        net.minecraft.server.level.ServerLevel level = p.serverLevel();
        net.minecraft.core.BlockPos at = p.blockPosition();
        fr.heraliz.casino.block.entity.DuelBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (net.minecraft.core.BlockPos q : net.minecraft.core.BlockPos.betweenClosed(at.offset(-12, -4, -12), at.offset(12, 4, 12))) {
            if (level.getBlockEntity(q) instanceof fr.heraliz.casino.block.entity.DuelBlockEntity d) {
                double dist = q.distSqr(at);
                if (dist < bestD) {
                    bestD = dist;
                    best = d;
                }
            }
        }
        if (best == null) {
            Reponses.erreur(c.getSource(), Component.translatable("command.heralizcasino.bot.none"));
            return 0;
        }
        long stake = amount > 0 ? amount : CasinoConfig.get().limits(best.type)[0];
        int r = fr.heraliz.casino.block.entity.DuelsServeur.botChallenge(best, level, stake);
        final fr.heraliz.casino.block.entity.DuelBlockEntity d = best;
        switch (r) {
            case 1 -> Reponses.ok(c.getSource(), Component.translatable("command.heralizcasino.bot.joined", d.type.displayName(), d.nameA)
                    .withStyle(ChatFormatting.GREEN));
            case 2 -> Reponses.ok(c.getSource(), Component.translatable("command.heralizcasino.bot.opened", d.type.displayName(), Chips.format(d.stake))
                    .withStyle(ChatFormatting.GREEN));
            default -> {
                Reponses.erreur(c.getSource(), Component.translatable("command.heralizcasino.bot.busy", d.type.displayName()));
                return 0;
            }
        }
        return 1;
    }

    private static int balance(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Reponses.ok(c.getSource(), Component.translatable("command.heralizcasino.balance", Chips.format(Chips.count(p))).withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, long amount) {
        for (ServerPlayer p : players) {
            ChipsServeur.give(p, amount);
            p.sendSystemMessage(Component.translatable("command.heralizcasino.received", Chips.format(amount)).withStyle(ChatFormatting.GOLD));
        }
        Reponses.ok(c.getSource(), Component.translatable("command.heralizcasino.given", Chips.format(amount), players.size()), true);
        return players.size();
    }

    private static int take(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, long amount) {
        int ok = 0;
        for (ServerPlayer p : players) {
            if (ChipsServeur.take(p, amount)) {
                ok++;
            } else {
                Reponses.erreur(c.getSource(), Component.translatable("command.heralizcasino.take_failed", p.getGameProfile().getName(), Chips.format(Chips.count(p))));
            }
        }
        final int n = ok;
        Reponses.ok(c.getSource(), Component.translatable("command.heralizcasino.taken", Chips.format(amount), n), true);
        return ok;
    }

    private static int see(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        Reponses.ok(c.getSource(), Component.translatable("command.heralizcasino.see", p.getGameProfile().getName(), Chips.format(Chips.count(p))));
        return 1;
    }

    private static int machine(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, String id) {
        MachineType t = MachineType.byId(id);
        if (t == null) {
            Reponses.erreur(c.getSource(), Component.translatable("command.heralizcasino.unknown_machine", id));
            return 0;
        }
        for (ServerPlayer p : players) ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(ModRegistry.machineItem(t)));
        Reponses.ok(c.getSource(), Component.translatable("command.heralizcasino.machine_given", t.displayName(), players.size()), true);
        return players.size();
    }

    private static int stats(CommandContext<CommandSourceStack> c) {
        CasinoStats s = CasinoStats.get(c.getSource().getServer());
        CommandSourceStack src = c.getSource();
        Reponses.ok(src, Component.translatable("command.heralizcasino.stats.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        long totalBet = 0, totalPaid = 0;
        for (MachineType t : MachineType.values()) {
            long[] a = s.machine(t);
            if (a[0] == 0) continue;
            totalBet += a[1];
            totalPaid += a[2];
            double rtp = a[1] > 0 ? 100.0 * a[2] / a[1] : 0;
            MutableComponent line = Component.literal(" • ").append(t.displayName().withStyle(ChatFormatting.YELLOW))
                    .append(Component.translatable("command.heralizcasino.stats.line", a[0], Chips.format(a[1]), Chips.format(a[2]),
                            String.format(Locale.ROOT, "%.1f", rtp), Chips.format(a[1] - a[2])).withStyle(ChatFormatting.GRAY));
            Reponses.ok(src, line);
        }
        final long b = totalBet, p = totalPaid;
        Reponses.ok(src, Component.translatable("command.heralizcasino.stats.total", Chips.format(b), Chips.format(p),
                String.format(Locale.ROOT, "%.1f", b > 0 ? 100.0 * p / b : 0), Chips.format(b - p)).withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int resetStats(CommandContext<CommandSourceStack> c) {
        CasinoStats.get(c.getSource().getServer()).reset();
        Reponses.ok(c.getSource(), Component.translatable("command.heralizcasino.stats.reset"), true);
        return 1;
    }

    /** Redistribution theorique de chaque machine selon les reglages actuels. */
    private static int rtp(CommandContext<CommandSourceStack> c) {
        CasinoConfig.Settings cfg = CasinoConfig.get();
        CommandSourceStack src = c.getSource();
        Reponses.ok(src, Component.translatable("command.heralizcasino.rtp.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        boolean refining = false;
        for (MachineType t : MachineType.values()) {
            double[] r = CasinoConfig.rtp(t, cfg);
            if (r == null) continue;
            line(src, t, r);
            if (r.length > 2 && r[2] > 0.015) refining = true;
        }
        // les machines a sous sont simulees en arriere-plan : leur chiffre s'affine pendant quelques secondes
        if (refining) Reponses.ok(src, Component.translatable("command.heralizcasino.rtp.refine").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return 1;
    }

    private static void line(CommandSourceStack src, MachineType t, double[] r) {
        MutableComponent v = Math.abs(r[0] - r[1]) < 1e-4 ? Component.literal(String.format(Locale.ROOT, "%.1f %%", r[1] * 100))
                : Component.translatableWithFallback("heralizcasino.commande.rtp_plage", "%s à %s %%",
                        String.format(Locale.ROOT, "%.1f", r[0] * 100), String.format(Locale.ROOT, "%.1f", r[1] * 100));
        if (r.length > 2 && r[2] >= 0.0005) v.append(String.format(Locale.ROOT, " (± %.1f)", r[2] * 100));
        MutableComponent l = Component.literal(" • ").append(t.displayName().withStyle(ChatFormatting.YELLOW))
                .append(Component.translatableWithFallback("heralizcasino.commande.rtp_ligne", " : %s (maison %s %%)", v,
                                String.format(Locale.ROOT, "%.1f", (1 - r[1]) * 100))
                        .withStyle(r[1] >= 1 ? ChatFormatting.RED : ChatFormatting.GRAY));
        Reponses.ok(src, l);
    }

    // =================================================================== simulation
    /** Une seule simulation a la fois (elle tourne hors du fil du serveur). */
    private static final java.util.concurrent.atomic.AtomicBoolean ROLLING = new java.util.concurrent.atomic.AtomicBoolean();
    /** Parties par simulation : au plus (une vingtaine de secondes pour la machine la plus lente), et par defaut pour une machine a sous. */
    private static final long MAX_ROLL = 20_000_000, SLOT_ROLL = 1_000_000;

    /** Machine designee par son identifiant, un morceau de son nom ("loup", "olympe"...) ou un surnom. */
    public static MachineType findGame(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        n = switch (n) {
            case "fusee", "fusée" -> "crash";
            case "demineur", "démineur" -> "mines";
            case "chevaux", "cheval" -> "course";
            case "pfc" -> "pierre_feuille_ciseaux";
            case "naval", "navale" -> "bataille_navale";
            case "bj" -> "blackjack";
            case "chien", "chiens", "dog", "doghouse" -> "la_niche";
            default -> n;
        };
        MachineType exact = MachineType.byId(n);
        if (exact != null) return exact.game() ? exact : null;
        MachineType found = null;
        for (MachineType t : MachineType.values()) {
            if (!t.game() || !t.id.contains(n)) continue;
            if (found != null) return null;
            found = t;
        }
        return found;
    }

    /** Ligne d'un rapport de simulation ("@nom" : nom a traduire). */
    public static MutableComponent rollLine(fr.heraliz.casino.game.Simulator.Line l) {
        return Lignes.rollLine(l);
    }

    /**
     * Simule la machine avec les reglages actuels et affiche ses chiffres : redistribution, frequence de chaque bonus
     * ("1 partie sur N"), gains moyens, gros gains. A refaire apres chaque changement de probabilites.
     */
    private static int roll(CommandContext<CommandSourceStack> c, String name, long rounds) {
        CommandSourceStack src = c.getSource();
        MachineType t = findGame(name);
        if (t == null) {
            Reponses.erreur(src, Component.translatable("command.heralizcasino.unknown_machine", name));
            return 0;
        }
        // copie des reglages : la simulation tourne pendant qu'on peut les modifier
        CasinoConfig.Settings cfg = CasinoConfig.copy();
        long def = CasinoConfig.rounds(t);
        if (def == 0) {
            printRoll(src, t, CasinoConfig.simulate(t, cfg, 0, 0));
            return 1;
        }
        if (!ROLLING.compareAndSet(false, true)) {
            Reponses.erreur(src, Component.translatable("roll.heralizcasino.busy"));
            return 0;
        }
        long n = rounds > 0 ? rounds : t.slot() ? SLOT_ROLL : def;
        Reponses.ok(src, Component.translatable("roll.heralizcasino.running", t.displayName(), fr.heraliz.casino.game.Simulator.num(n))
                .withStyle(ChatFormatting.GRAY));
        net.minecraft.server.MinecraftServer server = src.getServer();
        Thread th = new Thread(() -> {
            fr.heraliz.casino.game.Simulator.Report rep = null;
            try {
                rep = CasinoConfig.simulate(t, cfg, n, System.nanoTime());
            } catch (RuntimeException e) {
                fr.heraliz.casino.HeralizCasino.LOGGER.error("[HeralizCasino] Simulation de {} impossible", t.id, e);
            } finally {
                ROLLING.set(false);
            }
            final fr.heraliz.casino.game.Simulator.Report done = rep;
            server.execute(() -> printRoll(src, t, done));
        }, "HeralizCasino-Roll");
        th.setDaemon(true);
        th.start();
        return 1;
    }

    private static void printRoll(CommandSourceStack src, MachineType t, fr.heraliz.casino.game.Simulator.Report rep) {
        if (rep == null) {
            Reponses.erreur(src, Component.translatable("roll.heralizcasino.failed", t.displayName()));
            return;
        }
        MutableComponent head = rep.rounds > 0
                ? Component.translatable("roll.heralizcasino.title", t.displayName(), fr.heraliz.casino.game.Simulator.num(rep.rounds))
                : Component.translatable("roll.heralizcasino.title_exact", t.displayName());
        Reponses.ok(src, head.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (fr.heraliz.casino.game.Simulator.Line l : rep.lines) {
            boolean main = l.key().startsWith("rtp");
            ChatFormatting col = main ? (rep.rtp >= 1 ? ChatFormatting.RED : ChatFormatting.YELLOW)
                    : l.key().equals("feature") || l.key().equals("never") ? ChatFormatting.WHITE : ChatFormatting.GRAY;
            MutableComponent line = Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY).append(rollLine(l).withStyle(col));
            Reponses.ok(src, line);
        }
        // redistribution encore incertaine (gros gains rares) : proposer le nombre de parties qui ramene la marge a 1,5 point
        if (rep.margin > 0.02 && rep.rounds < MAX_ROLL) {
            double need = rep.rounds * (rep.margin / 0.015) * (rep.margin / 0.015);
            long parties = Math.min(MAX_ROLL, Math.max(rep.rounds * 2, niceAbove(need)));
            Reponses.ok(src, Component.translatable("roll.heralizcasino.precision", t.id, Long.toString(parties))
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /** Plus petit nombre "rond" (1, 2 ou 5 x 10^n) au moins egal a v, borne au maximum d'une simulation. */
    private static long niceAbove(double v) {
        for (long p = 1000; p <= MAX_ROLL; p *= 10) {
            for (long m : new long[]{1, 2, 5}) if (p * m >= v) return Math.min(MAX_ROLL, p * m);
        }
        return MAX_ROLL;
    }
    private static int jackpot(CommandContext<CommandSourceStack> c) {
        long v = Jackpot.get(c.getSource().getServer()).value();
        Reponses.ok(c.getSource(), Component.translatable("command.heralizcasino.jackpot", Chips.format(v)).withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int setJackpot(CommandContext<CommandSourceStack> c, long v) {
        Jackpot j = Jackpot.get(c.getSource().getServer());
        j.set(v);
        j.broadcast(c.getSource().getServer(), true);
        Reponses.ok(c.getSource(), Component.translatable("command.heralizcasino.jackpot_set", Chips.format(v)), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        String err = fr.heraliz.casino.config.CasinoConfigServeur.load();
        CommonEventsServeur.broadcastConfig();
        if (err != null) {
            Reponses.erreur(c.getSource(), Component.translatable("command.heralizcasino.reload_error", err));
            return 0;
        }
        Reponses.ok(c.getSource(), Component.translatable("command.heralizcasino.reloaded").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
