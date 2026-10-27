package fr.heraliz.casino.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.economy.CompteCapsules;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Compte de capsules (27/10) : {@code /capsules} pour les joueurs (solde ; depot et retrait a cote d'une caisse du
 * casino), {@code /casino compte} pour le staff. Le serveur verifie tout ({@link CompteCapsules}). Sur la carte, en combat
 * et a terre, /capsules est refusee par le noyau (commande absente de ses listes).
 */
final class CommandeCapsules {
    private CommandeCapsules() {
    }

    private static final long MAX = 1_000_000_000L;

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("capsules")
                .executes(c -> {
                    CompteCapsules.montrer(c.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("deposer")
                        .executes(c -> {
                            CompteCapsules.deposer(c.getSource().getPlayerOrException(), 0, false);
                            return 1;
                        })
                        .then(Commands.argument("nombre", LongArgumentType.longArg(1, MAX))
                                .executes(c -> {
                                    CompteCapsules.deposer(c.getSource().getPlayerOrException(), LongArgumentType.getLong(c, "nombre"), false);
                                    return 1;
                                })))
                .then(Commands.literal("retirer")
                        .then(Commands.argument("nombre", LongArgumentType.longArg(1, MAX))
                                .executes(c -> {
                                    CompteCapsules.retirer(c.getSource().getPlayerOrException(), LongArgumentType.getLong(c, "nombre"), false);
                                    return 1;
                                }))));
    }

    /** /casino compte voir (staff, niveau 2) | donner | retirer (operateur, niveau 4) : joueur connecte. */
    static LiteralArgumentBuilder<CommandSourceStack> staff() {
        return Commands.literal("compte").requires(s -> s.hasPermission(2))
                .then(Commands.literal("voir").then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> voir(c, EntityArgument.getPlayer(c, "joueur")))))
                .then(Commands.literal("donner").requires(s -> s.hasPermission(4)).then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("nombre", LongArgumentType.longArg(1, MAX))
                                .executes(c -> changer(c, EntityArgument.getPlayer(c, "joueur"), LongArgumentType.getLong(c, "nombre"))))))
                .then(Commands.literal("retirer").requires(s -> s.hasPermission(4)).then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("nombre", LongArgumentType.longArg(1, MAX))
                                .executes(c -> changer(c, EntityArgument.getPlayer(c, "joueur"), -LongArgumentType.getLong(c, "nombre"))))));
    }

    private static int voir(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        String nom = p.getGameProfile().getName();
        if (!CompteCapsules.ouvert(p.getUUID())) {
            Reponses.erreur(c.getSource(), Component.translatableWithFallback("command.heralizcasino.compte.bloque",
                    "Compte de %s bloqué (fichier illisible, mis de côté) : à réparer à la main.", nom));
            return 0;
        }
        Reponses.ok(c.getSource(), Component.translatableWithFallback("command.heralizcasino.compte.voir", "%s : %s capsules sur son compte, %s sur lui.",
                nom, Chips.format(CompteCapsules.solde(p.getUUID())), Chips.format(Chips.count(p))).withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int changer(CommandContext<CommandSourceStack> c, ServerPlayer p, long delta) {
        String nom = p.getGameProfile().getName(), source = "staff " + c.getSource().getTextName();
        boolean ok = delta > 0 ? CompteCapsules.crediter(p.getUUID(), nom, delta, source) : CompteCapsules.debiter(p.getUUID(), nom, -delta, source);
        if (!ok) {
            Reponses.erreur(c.getSource(), Component.translatableWithFallback("command.heralizcasino.compte.refuse",
                    "Impossible : compte bloqué, plafond dépassé, solde insuffisant ou écriture ratée."));
            return 0;
        }
        Reponses.ok(c.getSource(), Component.translatableWithFallback(delta > 0 ? "command.heralizcasino.compte.donne" : "command.heralizcasino.compte.retire",
                delta > 0 ? "%s capsules ajoutées au compte de %s (solde : %s)." : "%s capsules retirées du compte de %s (solde : %s).",
                Chips.format(Math.abs(delta)), nom, Chips.format(CompteCapsules.solde(p.getUUID()))).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
