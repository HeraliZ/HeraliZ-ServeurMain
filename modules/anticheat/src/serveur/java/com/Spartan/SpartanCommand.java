package com.Spartan;

import com.Spartan.alert.DiscordWebhook;
import com.Spartan.check.Check;
import com.Spartan.check.CheckManager;
import com.Spartan.check.Mitigation;
import com.Spartan.check.movement.MovementEngine;
import com.Spartan.esp.EspBenchmark;
import com.Spartan.esp.VisibilityEngine;
import com.Spartan.integrity.ChallengeManager;
import com.Spartan.integrity.LauncherGate;
import com.Spartan.scan.ScanManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commandes d'administration (op niveau 2+) :
 *   /spartan checks          liste des détections actives
 *   /spartan vl <joueur>     niveaux de violation actuels d'un joueur
 *   /spartan status <joueur> intégrité (build, défis, jeton du launcher) et suivi du mouvement
 *   /spartan setback <joueur> renvoie le joueur à sa dernière position sûre au sol
 *   /spartan scan <joueur>   contrôle d'intégrité immédiat (invisible pour le joueur)
 *   /spartan capture <joueur> capture de l'écran du joueur (s'il l'a autorisée dans le launcher)
 *   /spartan report <joueur> indices du dernier contrôle d'intégrité
 *   /spartan suspects        joueurs en ligne dont le dernier contrôle est suspect
 *   /spartan reference learn|add <joueur> | info | clear   client de référence (op 3)
 *   /spartan officiel        relit config/spartan/officiel.json (modpack officiel, op 3)
 *   /spartan selfscan        contrôle du serveur lui-même (diagnostic, op 3)
 *   /spartan discordtest     un message d'essai par webhook Discord, code HTTP de chacun (op 3, console aussi)
 *   /spartan esp [joueur]    anti-ESP : coût et activité, ou ce que ce joueur reçoit des autres
 *   /spartan esp bench [n] [rayon]  coût de l'anti-ESP sur la carte avec n joueurs fictifs (op 3)
 */
public class SpartanCommand {
    private SpartanCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spartan")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("checks").executes(ctx -> {
                    StringBuilder sb = new StringBuilder("Détections : ");
                    for (Check c : CheckManager.getChecks()) {
                        sb.append(c.getName()).append(" (").append(c.getCategory()).append(") ");
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(sb.toString().trim()), false);
                    return 1;
                }))
                .then(Commands.literal("status")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                    String text = t.getName().getString() + "\n" + ChallengeManager.describe(t.getUUID())
                                            + "\n" + LauncherGate.describe(t.getUUID())
                                            + "\n" + MovementEngine.describe(t.getUUID());
                                    ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.AQUA), false);
                                    return 1;
                                })))
                .then(Commands.literal("setback")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                    boolean done = Mitigation.setback(t);
                                    String name = t.getName().getString();
                                    if (done) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                name + " renvoyé à sa dernière position sûre."), true);
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "Aucune position sûre pour " + name + " (ou téléportation déjà en cours)."));
                                    }
                                    return done ? 1 : 0;
                                })))
                .then(Commands.literal("vl")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> showViolations(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                // Contrôle d'intégrité du client (jars, injection) : silencieux pour le joueur.
                .then(Commands.literal("scan")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                    CommandSourceStack src = ctx.getSource();
                                    ScanManager.request(t, src::sendSystemMessage, false, false);
                                    src.sendSuccess(() -> Component.literal("Contrôle d'intégrité envoyé à " + t.getName().getString()
                                            + " (invisible pour lui) : résultat dans quelques secondes.").withStyle(ChatFormatting.AQUA), false);
                                    return 1;
                                })))
                // Capture d'écran : seulement si le joueur l'a autorisée dans le launcher.
                .then(Commands.literal("capture")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                    CommandSourceStack src = ctx.getSource();
                                    String refus = com.Spartan.capture.CaptureManager.demander(t,
                                            "demandée par " + src.getTextName());
                                    if (refus != null) {
                                        src.sendFailure(Component.literal("Capture impossible : " + refus + "."));
                                        return 0;
                                    }
                                    src.sendSuccess(() -> Component.literal("Capture demandée à " + t.getName().getString()
                                            + " : elle arrive dans screenshots/ et sur Discord.").withStyle(ChatFormatting.AQUA), false);
                                    return 1;
                                })))
                .then(Commands.literal("report")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                    for (Component line : ScanManager.describe(t.getUUID(), t.getName().getString())) {
                                        ctx.getSource().sendSuccess(() -> line, false);
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("suspects").executes(ctx -> {
                    List<String> list = ScanManager.suspects(ctx.getSource().getServer());
                    String text = list.isEmpty() ? "Aucun joueur suspect en ligne." : "Suspects en ligne : " + String.join(", ", list);
                    ctx.getSource().sendSuccess(() -> Component.literal(text)
                            .withStyle(list.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.GOLD), false);
                    return list.size();
                }))
                .then(Commands.literal("officiel").requires(src -> src.hasPermission(3)).executes(ctx -> {
                    String resume = ScanManager.reloadOfficial();
                    ctx.getSource().sendSuccess(() -> Component.literal("Modpack officiel relu : " + resume)
                            .withStyle(ChatFormatting.AQUA), true);
                    return 1;
                }))
                .then(Commands.literal("reference")
                        .then(Commands.literal("info").executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal("Références : "
                                    + ScanManager.referenceSummary()).withStyle(ChatFormatting.AQUA), false);
                            return 1;
                        }))
                        .then(Commands.literal("learn").requires(src -> src.hasPermission(3))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> learn(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false))))
                        .then(Commands.literal("add").requires(src -> src.hasPermission(3))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> learn(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), true))))
                        .then(Commands.literal("clear").requires(src -> src.hasPermission(3)).executes(ctx -> {
                            ScanManager.clearReference();
                            ctx.getSource().sendSuccess(() -> Component.literal("Client de référence effacé.")
                                    .withStyle(ChatFormatting.YELLOW), true);
                            return 1;
                        })))
                .then(Commands.literal("esp")
                        .then(Commands.literal("bench").requires(src -> src.hasPermission(3))
                                .executes(ctx -> bench(ctx.getSource(), 70, 150))
                                .then(Commands.argument("count", IntegerArgumentType.integer(2, 300))
                                        .executes(ctx -> bench(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"), 150))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 512))
                                                .executes(ctx -> bench(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                        IntegerArgumentType.getInteger(ctx, "radius"))))))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(VisibilityEngine.describe())
                                    .withStyle(ChatFormatting.AQUA), false);
                            return 1;
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                    for (String line : VisibilityEngine.describeObserver(t)) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(line)
                                                .withStyle(ChatFormatting.AQUA), false);
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("selfscan").requires(src -> src.hasPermission(3)).executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ScanManager.selfScan(src.getServer(), src::sendSystemMessage);
                    src.sendSuccess(() -> Component.literal("Autocontrôle du serveur lancé…").withStyle(ChatFormatting.AQUA), false);
                    return 1;
                }))
                .then(Commands.literal("discordtest").requires(src -> src.hasPermission(3))
                        .executes(ctx -> discordTest(ctx.getSource()))));
    }

    /** Un message d'essai par webhook Discord ; chaque résultat revient au demandeur dès que Discord a répondu. */
    private static int discordTest(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        src.sendSuccess(() -> Component.translatableWithFallback("spartan.discordtest.envoi",
                "Messages d'essai envoyés à Discord : réponse dans quelques secondes.").withStyle(ChatFormatting.AQUA), false);
        DiscordWebhook.tester(
                code -> server.execute(() -> src.sendSuccess(() -> Component.translatableWithFallback(
                        "spartan.discordtest.alertes", "Webhook des alertes (fil « Informations du staff ») : %s",
                        resultatDiscord(code)).withStyle(couleurDiscord(code)), false)),
                code -> server.execute(() -> src.sendSuccess(() -> Component.translatableWithFallback(
                        "spartan.discordtest.sanctions", "Webhook des sanctions publiques : %s",
                        resultatDiscord(code)).withStyle(couleurDiscord(code)), false)));
        return 1;
    }

    private static Component resultatDiscord(int code) {
        if (code == DiscordWebhook.NON_CONFIGURE) {
            return Component.translatableWithFallback("spartan.discordtest.non_configure", "non configuré");
        }
        if (code == DiscordWebhook.DESACTIVE) {
            return Component.translatableWithFallback("spartan.discordtest.desactive",
                    "désactivé (discordEnabled = false)");
        }
        if (code == DiscordWebhook.ECHEC) {
            return Component.translatableWithFallback("spartan.discordtest.echec",
                    "pas de réponse de Discord (voir le journal du serveur)");
        }
        if (code < 300) {
            return Component.translatableWithFallback("spartan.discordtest.ok", "HTTP %s, message reçu par Discord",
                    String.valueOf(code));
        }
        return Component.translatableWithFallback("spartan.discordtest.refus",
                "HTTP %s, refusé par Discord (voir le journal du serveur)", String.valueOf(code));
    }

    private static ChatFormatting couleurDiscord(int code) {
        if (code == DiscordWebhook.NON_CONFIGURE || code == DiscordWebhook.DESACTIVE) {
            return ChatFormatting.YELLOW;
        }
        return code > 0 && code < 300 ? ChatFormatting.GREEN : ChatFormatting.RED;
    }

    private static int bench(CommandSourceStack src, int count, int radius) {
        for (String line : EspBenchmark.run(src.getLevel(), src.getPosition(), count, radius)) {
            src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    /** Apprend (ou complète) le client de référence depuis le jeu d'un joueur DE CONFIANCE. */
    private static int learn(CommandSourceStack src, ServerPlayer t, boolean merge) {
        ScanManager.request(t, src::sendSystemMessage, true, merge);
        src.sendSuccess(() -> Component.literal("Contrôle envoyé à " + t.getName().getString() + " : son jeu "
                + (merge ? "complétera" : "deviendra") + " la référence (à faire uniquement avec un client propre !).")
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int showViolations(CommandSourceStack src, ServerPlayer target) throws CommandSyntaxException {
        Map<String, Integer> vls = CheckManager.getViolations(target.getUUID());
        String name = target.getName().getString();
        if (vls.isEmpty()) {
            src.sendSuccess(() -> Component.literal(name + " : aucune violation.").withStyle(ChatFormatting.GREEN), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder(name + " : ");
        vls.forEach((check, vl) -> sb.append(check).append("=").append(vl).append("  "));
        src.sendSuccess(() -> Component.literal(sb.toString().trim()).withStyle(ChatFormatting.YELLOW), false);
        return vls.size();
    }
}
