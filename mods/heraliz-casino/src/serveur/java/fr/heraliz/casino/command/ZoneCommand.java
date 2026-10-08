package fr.heraliz.casino.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.event.CommonEventsServeur;
import fr.heraliz.casino.zone.CasinoZone;
import fr.heraliz.casino.zone.ZoneManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.Collection;

/**
 * /casino zone ... (operateurs) : delimiter la salle du casino entre deux coins, poser ses entrees et ses videurs,
 * regler le prix d'entree, sortir un joueur.
 */
public final class ZoneCommand {
    private ZoneCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("zone").requires(s -> s.hasPermission(2))
                .then(Commands.literal("a").executes(c -> corner(c, 0)))
                .then(Commands.literal("b").executes(c -> corner(c, 1)))
                .then(Commands.literal("creer").executes(ZoneCommand::create))
                .then(Commands.literal("entree").executes(ZoneCommand::entrance))
                .then(Commands.literal("garde").executes(ZoneCommand::guard))
                .then(Commands.literal("prix").then(Commands.argument("jetons", LongArgumentType.longArg(0, 1_000_000_000L))
                        .executes(c -> price(c, LongArgumentType.getLong(c, "jetons")))))
                .then(Commands.literal("duree").then(Commands.argument("minutes", IntegerArgumentType.integer(0, 100_000))
                        .executes(c -> minutes(c, IntegerArgumentType.getInteger(c, "minutes")))))
                .then(Commands.literal("info").executes(ZoneCommand::info))
                .then(Commands.literal("supprimer").executes(ZoneCommand::delete))
                .then(Commands.literal("ejecter").then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> eject(c, EntityArgument.getPlayer(c, "joueur")))))
                .then(Commands.literal("ticket").then(Commands.argument("joueurs", EntityArgument.players())
                        .executes(c -> ticket(c, EntityArgument.getPlayers(c, "joueurs"), 0))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 100_000))
                                .executes(c -> ticket(c, EntityArgument.getPlayers(c, "joueurs"), IntegerArgumentType.getInteger(c, "minutes"))))));
    }

    private static Component key(String k, Object... args) {
        return Component.translatable("command.heralizcasino.zone." + k, args);
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    /** Coin A ou B : le bloc ou se tient l'operateur. */
    private static int corner(CommandContext<CommandSourceStack> c, int which) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        corner(p, which, p.blockPosition());
        return 1;
    }

    /** Coin choisi (commande, ou Tablette du Directeur) : message et contour de la selection. */
    public static void corner(ServerPlayer p, int which, BlockPos pos) {
        ZoneManager.Selection s = ZoneManager.select(p, which, pos);
        p.displayClientMessage(key(which == 0 ? "corner_a" : "corner_b", xyz(pos)).copy().withStyle(ChatFormatting.YELLOW), false);
        if (s.complete()) p.displayClientMessage(key("ready").copy().withStyle(ChatFormatting.GRAY), false);
    }

    private static int create(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!ZoneManager.create(p)) {
            Reponses.erreur(c.getSource(), key("need_corners"));
            return 0;
        }
        CasinoZone z = CasinoZone.get(c.getSource().getServer());
        Reponses.ok(c.getSource(), key("created", xyz(z.min()), xyz(z.max())).copy().withStyle(ChatFormatting.GREEN), true);
        Reponses.ok(c.getSource(), key("next").copy().withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int entrance(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int guards = ZoneManager.addEntrance(p);
        if (guards < 0) {
            Reponses.erreur(c.getSource(), key("none"));
            return 0;
        }
        int n = CasinoZone.get(c.getSource().getServer()).entrances.size();
        Reponses.ok(c.getSource(), key("entrance", n, guards).copy().withStyle(ChatFormatting.GREEN), true);
        if (guards < 2) Reponses.ok(c.getSource(), key("entrance_tight").copy().withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int guard(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ZoneManager.addGuard(p);
        Reponses.ok(c.getSource(), key("guard").copy().withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int price(CommandContext<CommandSourceStack> c, long chips) {
        CasinoConfig.get().caisse.entree.prix = chips;
        save(c);
        Reponses.ok(c.getSource(), (chips > 0 ? key("price", Chips.format(chips)) : key("price_free")).copy().withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int minutes(CommandContext<CommandSourceStack> c, int minutes) {
        CasinoConfig.get().caisse.entree.validiteMinutes = minutes;
        save(c);
        Reponses.ok(c.getSource(), (minutes > 0 ? key("minutes", minutes) : key("minutes_once")).copy().withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** Les reglages changes par commande sont ecrits dans casino.json et envoyes aux joueurs. */
    private static void save(CommandContext<CommandSourceStack> c) {
        try {
            fr.heraliz.casino.config.CasinoConfigServeur.applySection(fr.heraliz.casino.machine.MachineType.CAISSE, CasinoConfig.GSON.toJson(CasinoConfig.get().caisse));
        } catch (IOException e) {
            Reponses.erreur(c.getSource(), Component.translatable("command.heralizcasino.reload_error", String.valueOf(e.getMessage())));
        }
        CommonEventsServeur.broadcastConfig();
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        CasinoZone z = CasinoZone.get(src.getServer());
        if (!z.exists()) {
            Reponses.erreur(src, key("none"));
            return 0;
        }
        CasinoConfig.Entree cfg = CasinoConfig.get().caisse.entree;
        ServerLevel level = src.getServer().getLevel(z.dimension());
        int guards = level == null ? 0 : ZoneManager.guardCount(level, z);
        Reponses.ok(src, key("info.title").copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        Reponses.ok(src, key("info.box", z.dimension().location().toString(), xyz(z.min()), xyz(z.max())).copy().withStyle(ChatFormatting.GRAY));
        Reponses.ok(src, key("info.doors", z.entrances.size(), guards).copy().withStyle(ChatFormatting.GRAY));
        Reponses.ok(src, (cfg.prix > 0 ? key("info.price", Chips.format(cfg.prix), cfg.validiteMinutes, Chips.format(z.revenue()))
                : key("info.free")).copy().withStyle(ChatFormatting.GRAY));
        Reponses.ok(src, key(cfg.expulserFauches ? "info.eject_on" : "info.eject_off", cfg.delaiExpulsion).copy().withStyle(ChatFormatting.GRAY));
        if (src.getEntity() instanceof ServerPlayer p && level == p.level()) ZoneManager.outline(p, z.box(), false);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> c) {
        if (!ZoneManager.delete(c.getSource().getServer())) {
            Reponses.erreur(c.getSource(), key("none"));
            return 0;
        }
        Reponses.ok(c.getSource(), key("deleted").copy().withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int eject(CommandContext<CommandSourceStack> c, ServerPlayer target) {
        if (!(target.level() instanceof ServerLevel level) || !ZoneManager.eject(level, target, 1)) {
            Reponses.erreur(c.getSource(), key("eject_failed", target.getGameProfile().getName()));
            return 0;
        }
        Reponses.ok(c.getSource(), key("ejecting", target.getGameProfile().getName()).copy().withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int ticket(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, int minutes) {
        int m = minutes > 0 ? minutes : Math.max(1, CasinoConfig.get().caisse.entree.validiteMinutes);
        for (ServerPlayer p : players) {
            ZoneManager.ticket(c.getSource().getServer(), p, m);
            p.displayClientMessage(key("ticket_got", m).copy().withStyle(ChatFormatting.GREEN), false);
        }
        Reponses.ok(c.getSource(), key("ticket", players.size(), m).copy().withStyle(ChatFormatting.GREEN), true);
        return players.size();
    }
}
