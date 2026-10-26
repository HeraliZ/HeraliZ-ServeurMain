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
 * regler le prix d'entree, sortir un joueur. /casino zone vip ... : la meme chose pour la zone VIP (entree reservee aux
 * grades Rescape, Traqueur et Roi de la Zone, sans payer) ; les coins se posent comme pour le casino.
 */
public final class ZoneCommand {
    private ZoneCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("zone").requires(s -> s.hasPermission(2))
                .then(Commands.literal("a").executes(c -> corner(c, 0, CasinoZone.Kind.CASINO)))
                .then(Commands.literal("b").executes(c -> corner(c, 1, CasinoZone.Kind.CASINO)))
                .then(Commands.literal("creer").executes(c -> create(c, CasinoZone.Kind.CASINO)))
                .then(Commands.literal("entree").executes(c -> entrance(c, CasinoZone.Kind.CASINO)))
                .then(Commands.literal("garde").executes(c -> guard(c, CasinoZone.Kind.CASINO)))
                .then(Commands.literal("vip")
                        .then(Commands.literal("a").executes(c -> corner(c, 0, CasinoZone.Kind.VIP)))
                        .then(Commands.literal("b").executes(c -> corner(c, 1, CasinoZone.Kind.VIP)))
                        .then(Commands.literal("creer").executes(c -> create(c, CasinoZone.Kind.VIP)))
                        .then(Commands.literal("entree").executes(c -> entrance(c, CasinoZone.Kind.VIP)))
                        .then(Commands.literal("garde").executes(c -> guard(c, CasinoZone.Kind.VIP)))
                        .then(Commands.literal("info").executes(ZoneCommand::infoVip))
                        .then(Commands.literal("supprimer").executes(c -> delete(c, CasinoZone.Kind.VIP))))
                .then(Commands.literal("prix").then(Commands.argument("jetons", LongArgumentType.longArg(0, 1_000_000_000L))
                        .executes(c -> price(c, LongArgumentType.getLong(c, "jetons")))))
                .then(Commands.literal("duree").then(Commands.argument("minutes", IntegerArgumentType.integer(0, 100_000))
                        .executes(c -> minutes(c, IntegerArgumentType.getInteger(c, "minutes")))))
                .then(Commands.literal("info").executes(ZoneCommand::info))
                .then(Commands.literal("supprimer").executes(c -> delete(c, CasinoZone.Kind.CASINO)))
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

    /** Texte de la zone VIP (avec son texte francais de secours) ou, pour le casino, la cle d'origine. */
    private static Component key(CasinoZone.Kind kind, String k, String fr, Object... args) {
        if (kind == CasinoZone.Kind.CASINO) return key(k, args);
        return Component.translatableWithFallback("command.heralizcasino.zone.vip." + k, fr, args);
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    /** Coin A ou B : le bloc ou se tient l'operateur. */
    private static int corner(CommandContext<CommandSourceStack> c, int which, CasinoZone.Kind kind) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        corner(p, which, p.blockPosition(), kind);
        return 1;
    }

    /** Coin choisi (commande, ou Tablette du Directeur) : message et contour de la selection. */
    public static void corner(ServerPlayer p, int which, BlockPos pos) {
        corner(p, which, pos, CasinoZone.Kind.CASINO);
    }

    private static void corner(ServerPlayer p, int which, BlockPos pos, CasinoZone.Kind kind) {
        ZoneManager.Selection s = ZoneManager.select(p, which, pos);
        p.displayClientMessage(key(which == 0 ? "corner_a" : "corner_b", xyz(pos)).copy().withStyle(ChatFormatting.YELLOW), false);
        if (s.complete()) {
            p.displayClientMessage(key(kind, "ready", "Les deux coins sont posés : /casino zone vip creer pour créer la zone VIP.")
                    .copy().withStyle(ChatFormatting.GRAY), false);
        }
    }

    private static int create(CommandContext<CommandSourceStack> c, CasinoZone.Kind kind) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!ZoneManager.create(p, kind)) {
            Reponses.erreur(c.getSource(), key(kind, "need_corners",
                    "Pose d'abord les deux coins : /casino zone vip a, puis /casino zone vip b (ou Tablette du Directeur, accroupi : clic gauche = A, clic droit = B)."));
            return 0;
        }
        CasinoZone z = CasinoZone.get(c.getSource().getServer(), kind);
        Reponses.ok(c.getSource(), key(kind, "created", "Zone VIP créée : de %s à %s.", xyz(z.min()), xyz(z.max())).copy().withStyle(ChatFormatting.GREEN), true);
        Reponses.ok(c.getSource(), key(kind, "next",
                "Place-toi maintenant dans l'ouverture de la porte de la zone VIP et fais /casino zone vip entree : deux videurs VIP s'y posteront.")
                .copy().withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static Component none(CasinoZone.Kind kind) {
        return key(kind, "none", "Aucune zone VIP dans ce monde. Délimite-la avec /casino zone vip a, /casino zone vip b, puis /casino zone vip creer.");
    }

    private static int entrance(CommandContext<CommandSourceStack> c, CasinoZone.Kind kind) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int guards = ZoneManager.addEntrance(p, kind);
        if (guards < 0) {
            Reponses.erreur(c.getSource(), none(kind));
            return 0;
        }
        int n = CasinoZone.get(c.getSource().getServer(), kind).entrances.size();
        Reponses.ok(c.getSource(), key(kind, "entrance", "Entrée VIP n° %s enregistrée (%s videur(s) VIP en poste).", n, guards).copy().withStyle(ChatFormatting.GREEN), true);
        if (guards < 2) {
            Reponses.ok(c.getSource(), key(kind, "entrance_tight", "Pas assez de place devant la porte pour deux videurs : ajoute-en avec /casino zone vip garde.")
                    .copy().withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int guard(CommandContext<CommandSourceStack> c, CasinoZone.Kind kind) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ZoneManager.addGuard(p, kind);
        Reponses.ok(c.getSource(), key(kind, "guard", "Videur VIP posté ici.").copy().withStyle(ChatFormatting.GREEN), true);
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

    private static int delete(CommandContext<CommandSourceStack> c, CasinoZone.Kind kind) {
        if (!ZoneManager.delete(c.getSource().getServer(), kind)) {
            Reponses.erreur(c.getSource(), none(kind));
            return 0;
        }
        Reponses.ok(c.getSource(), key(kind, "deleted", "Zone VIP supprimée (videurs VIP retirés).").copy().withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int infoVip(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        CasinoZone z = CasinoZone.get(src.getServer(), CasinoZone.Kind.VIP);
        if (!z.exists()) {
            Reponses.erreur(src, none(CasinoZone.Kind.VIP));
            return 0;
        }
        CasinoConfig.Entree cfg = CasinoConfig.get().caisse.entree;
        ServerLevel level = src.getServer().getLevel(z.dimension());
        int guards = level == null ? 0 : ZoneManager.guardCount(level, z);
        Reponses.ok(src, key(CasinoZone.Kind.VIP, "info.title", "Zone VIP").copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        Reponses.ok(src, key("info.box", z.dimension().location().toString(), xyz(z.min()), xyz(z.max())).copy().withStyle(ChatFormatting.GRAY));
        Reponses.ok(src, key("info.doors", z.entrances.size(), guards).copy().withStyle(ChatFormatting.GRAY));
        Reponses.ok(src, (ZoneManager.vipDisponible()
                ? key(CasinoZone.Kind.VIP, "info.access", "Accès : grades Rescapé, Traqueur et Roi de la Zone, sans payer")
                : key(CasinoZone.Kind.VIP, "info.no_grades", "HeraliZ Grades absent : personne n'entre (sauf en créatif ou spectateur)"))
                .copy().withStyle(ChatFormatting.GRAY));
        Reponses.ok(src, key(cfg.expulserFauches ? "info.eject_on" : "info.eject_off", cfg.delaiExpulsion).copy().withStyle(ChatFormatting.GRAY));
        if (src.getEntity() instanceof ServerPlayer p && level == p.level()) ZoneManager.outline(p, z.box(), false);
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
