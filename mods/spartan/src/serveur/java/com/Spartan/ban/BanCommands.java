package com.Spartan.ban;

import com.Spartan.alert.AlertManager;
import com.Spartan.alert.DiscordWebhook;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Commandes de ban :
 *   /sban <joueur> <durée> [raison]   compte + IP + PC (durée : 30m, 12h, 7d, 2w, def)
 *   /sunban <joueur|uuid|serial>      lève tous les bans actifs (hors ligne OK)
 *   /sbaninfo <joueur|uuid|serial>    identifiants connus, comptes liés, historique
 *   /unbanplayer <joueur>             alias de /sunban (ancienne commande, réparée)
 *
 * Pseudos avec caractères spéciaux : entre guillemets ("Mon.Pseudo!").
 * Sur un serveur crack, « Steve » et « steve » sont deux comptes différents :
 * la casse exacte est prioritaire, sinon il faut lever l'ambiguïté avec l'UUID.
 */
public final class BanCommands {
    /** Marqueur renvoyé par resolve() quand plusieurs comptes diffèrent seulement par la casse. */
    private static final PlayerRegistry.Record AMBIGUOUS = new PlayerRegistry.Record();

    private BanCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sban")
                .requires(src -> src.hasPermission(3))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), b))
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("30m", "12h", "1d", "7d", "30d", "def"), b))
                                .executes(ctx -> ban(ctx, "Triche"))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> ban(ctx, StringArgumentType.getString(ctx, "reason")))))));

        LiteralCommandNode<CommandSourceStack> unban = dispatcher.register(Commands.literal("sunban")
                .requires(src -> src.hasPermission(3))
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(BanCommands::unban)));
        dispatcher.register(Commands.literal("unbanplayer").requires(src -> src.hasPermission(3)).redirect(unban));

        dispatcher.register(Commands.literal("sbaninfo")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), b))
                        .executes(BanCommands::info)));
    }

    /** Résout pseudo ou UUID vers un compte connu ; envoie l'erreur et renvoie null sinon. */
    private static PlayerRegistry.Record resolve(CommandSourceStack src, String input, boolean reportMissing) {
        try {
            PlayerRegistry.Record byUuid = PlayerRegistry.findByUuid(UUID.fromString(input));
            if (byUuid != null) {
                return byUuid;
            }
        } catch (IllegalArgumentException ignored) {
            // pas un UUID
        }
        List<PlayerRegistry.Record> all = PlayerRegistry.findAllByName(input);
        for (PlayerRegistry.Record r : all) {
            if (r.name.equals(input)) {
                return r; // casse exacte
            }
        }
        if (all.size() == 1) {
            return all.get(0);
        }
        if (all.size() > 1) {
            StringBuilder sb = new StringBuilder("Pseudo ambigu (comptes différents selon la casse) : ");
            for (PlayerRegistry.Record r : all) {
                sb.append(r.name).append(" [").append(r.uuid).append("]  ");
            }
            sb.append("→ réessayez avec la casse exacte ou l'UUID.");
            src.sendFailure(Component.literal(sb.toString()));
            return AMBIGUOUS;
        }
        if (reportMissing) {
            src.sendFailure(Component.literal("Joueur inconnu : " + input + " (il doit s'être connecté au moins une fois)."));
        }
        return null;
    }

    private static int ban(CommandContext<CommandSourceStack> ctx, String reason) {
        CommandSourceStack src = ctx.getSource();
        String input = StringArgumentType.getString(ctx, "player");
        String durationArg = StringArgumentType.getString(ctx, "duration");
        long duration = Durations.parse(durationArg);
        if (duration == Durations.INVALID) {
            src.sendFailure(Component.literal("Durée invalide : " + durationArg + " (ex : 30m, 12h, 7d, 2w, def)"));
            return 0;
        }

        PlayerRegistry.Record target = resolve(src, input, true);
        if (target == null || target == AMBIGUOUS) {
            return 0;
        }

        MinecraftServer server = src.getServer();
        // Un modérateur en jeu ne peut pas bannir un administrateur (op 3+). La console peut.
        int targetLevel = server.getProfilePermissions(new GameProfile(UUID.fromString(target.uuid), target.name));
        if (src.getEntity() != null && targetLevel >= 3) {
            src.sendFailure(Component.literal("Impossible de bannir un administrateur (op niveau " + targetLevel
                    + ") depuis le jeu. Utilisez la console du serveur si c'est voulu."));
            return 0;
        }

        BanManager.BanRecord r = BanManager.ban(server, target, duration, reason, src.getTextName());
        String ipPart = r.ips.isEmpty() ? "IP non bannie" : "IP bannie jusqu'au "
                + new SimpleDateFormat("dd/MM HH:mm").format(new Date(r.ipExpires));
        String summary = String.format("%s banni (%s) par %s — raison : %s — %s, %d PC",
                r.name, r.describeExpiry(), r.by, r.reason, ipPart, r.serials.size());
        src.sendSuccess(() -> Component.literal(summary).withStyle(ChatFormatting.GREEN), false);
        AlertManager.info(server, "Ban", summary, AlertManager.COLOR_BAN);
        DiscordWebhook.sendSanction(r.name, r.describeExpiry(), r.reason);
        return 1;
    }

    private static int unban(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String input = StringArgumentType.getString(ctx, "player");
        PlayerRegistry.Record target = resolve(src, input, false);
        if (target == AMBIGUOUS) {
            return 0;
        }
        // Compte connu → ses bans ET ceux de ses PC (ban matériel de la 1.0, PC partagé) ;
        // sinon pseudo brut ou serial (bans de la 1.0).
        String shown = target != null ? target.name : input;
        String summary;
        int lifted;
        if (target != null) {
            BanManager.Leve l = BanManager.unbanPlayer(src.getServer(), target, src.getTextName());
            lifted = l.total();
            summary = shown + " débanni par " + src.getTextName() + " (" + l.compte() + " ban(s) du compte, "
                    + l.pc() + " ban(s) de son PC levé(s)"
                    + (l.autresComptes().isEmpty() ? "" : " ; son PC retiré du ban de " + String.join(", ", l.autresComptes())
                    + ", qui reste banni") + ")";
        } else {
            lifted = BanManager.unban(src.getServer(), input, src.getTextName());
            summary = shown + " débanni par " + src.getTextName() + " (" + lifted + " ban(s) levé(s))";
        }
        if (lifted == 0) {
            src.sendFailure(Component.literal("Aucun ban actif pour " + input + "."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(summary).withStyle(ChatFormatting.GREEN), false);
        AlertManager.info(src.getServer(), "Déban", summary, AlertManager.COLOR_UNBAN);
        return lifted;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String input = StringArgumentType.getString(ctx, "player");
        PlayerRegistry.Record r = resolve(src, input, false);
        if (r == AMBIGUOUS) {
            return 0;
        }
        List<BanManager.BanRecord> history = BanManager.history(r != null ? r.uuid : input);
        if (r == null && history.isEmpty()) {
            src.sendFailure(Component.literal("Aucune donnée pour " + input + "."));
            return 0;
        }

        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("=== " + (r != null ? r.name : input) + " ===");
        if (r != null) {
            sb.append("\nIP connues : ").append(r.ips.size())
              .append(" | PC connus : ").append(r.serials.size());
            List<PlayerRegistry.Record> linked = PlayerRegistry.linkedAccounts(r);
            if (!linked.isEmpty()) {
                sb.append("\nComptes liés (même IP ou même PC) : ");
                for (PlayerRegistry.Record l : linked) {
                    sb.append(l.name).append(BanManager.isBannedUuid(l.uuid) ? " [BANNI]" : "").append(", ");
                }
                sb.setLength(sb.length() - 2);
            }
        }
        if (history.isEmpty()) {
            sb.append("\nAucun ban.");
        }
        for (BanManager.BanRecord b : history) {
            sb.append("\n- ").append(b.isActive(now) ? "[ACTIF] " : "[levé] ")
              .append(b.reason).append(" par ").append(b.by).append(", ").append(b.describeExpiry());
            if (b.isIpBanActive(now)) {
                sb.append(", IP bannie jusqu'au ").append(new SimpleDateFormat("dd/MM HH:mm").format(new Date(b.ipExpires)));
            }
            if (b.liftedAt != 0) {
                sb.append(" (levé par ").append(b.liftedBy).append(")");
            }
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
}
