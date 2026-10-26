package fr.heraliz.noyau.serveur.diag;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * {@code /diag} (opérateurs niveau 4 et console) : {@code etat}, {@code start [secondes]}, {@code stop},
 * {@code mesure [secondes]} (temps par entité et entité de bloc, {@link Moniteur}), {@code fuites}, {@code client <joueur>
 * [secondes]} (analyse du jeu du joueur, {@link DiagClients}), {@code auto on|off}, {@code seuil <ms>}. Voir {@link Diag}.
 */
public final class CommandeDiag {
    private CommandeDiag() {
    }

    private static final int BLEU = 0x8FD3FF, ROUGE = 0xFF8A8A;

    public static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("diag").requires(s -> s.hasPermission(4))
                .executes(CommandeDiag::etat)
                .then(Commands.literal("etat").executes(CommandeDiag::etat))
                .then(Commands.literal("start").executes(c -> lancer(c, 60))
                        .then(Commands.argument("secondes", IntegerArgumentType.integer(10, 1800))
                                .executes(c -> lancer(c, IntegerArgumentType.getInteger(c, "secondes")))))
                .then(Commands.literal("stop").executes(CommandeDiag::stop))
                .then(Commands.literal("mesure").executes(c -> mesure(c, 15))
                        .then(Commands.argument("secondes", IntegerArgumentType.integer(5, 300))
                                .executes(c -> mesure(c, IntegerArgumentType.getInteger(c, "secondes")))))
                .then(Commands.literal("fuites").executes(CommandeDiag::fuites))
                .then(Commands.literal("client").then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> client(c, 20))
                        .then(Commands.argument("secondes", IntegerArgumentType.integer(5, 120))
                                .executes(c -> client(c, IntegerArgumentType.getInteger(c, "secondes"))))))
                .then(Commands.literal("auto")
                        .then(Commands.literal("on").executes(c -> auto(c, true)))
                        .then(Commands.literal("off").executes(c -> auto(c, false))))
                .then(Commands.literal("seuil").then(Commands.argument("ms", IntegerArgumentType.integer(60, 60000))
                        .executes(c -> seuil(c, IntegerArgumentType.getInteger(c, "ms"))))));
    }

    private static void dire(CommandContext<CommandSourceStack> c, Component t, int couleur) {
        c.getSource().sendSuccess(() -> t.copy().withStyle(s -> s.withColor(couleur)), false);
    }

    private static int etat(CommandContext<CommandSourceStack> c) {
        Diag.etatServeur(c.getSource().getServer(), l -> dire(c, Component.literal(l.stripTrailing()), BLEU));
        dire(c, Component.translatableWithFallback("noyau.diag.auto", "[Diag] Rapports automatiques : %s (seuil %s ms), dans diag/",
                NoyauConfig.DIAG_AUTO.get() ? "on" : "off", String.valueOf(NoyauConfig.DIAG_SEUIL_MS.get())), BLEU);
        return 1;
    }

    private static int lancer(CommandContext<CommandSourceStack> c, int secondes) {
        UUID qui = c.getSource().getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
        if (!Diag.lancer(secondes, qui)) {
            dire(c, Component.translatableWithFallback("noyau.diag.deja", "[Diag] Un enregistrement est déjà en cours (/diag stop)."), ROUGE);
            return 0;
        }
        dire(c, Component.translatableWithFallback("noyau.diag.debut", "[Diag] Enregistrement lancé pour %s s : résumé ici toutes les 10 s, rapport complet à la fin.",
                String.valueOf(secondes)), BLEU);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> c) {
        String nom = Diag.arreterSession(c.getSource().getServer());
        if (nom == null) {
            dire(c, Component.translatableWithFallback("noyau.diag.rien", "[Diag] Aucun enregistrement en cours."), ROUGE);
            return 0;
        }
        dire(c, Component.translatableWithFallback("noyau.diag.fin", "[Diag] Terminé : rapport diag/%s", nom), BLEU);
        return 1;
    }

    private static int mesure(CommandContext<CommandSourceStack> c, int secondes) {
        UUID qui = c.getSource().getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
        Moniteur.mesurerPuisRapport(secondes, qui);
        dire(c, Component.translatableWithFallback("noyau.diag.mesure_debut", "[Diag] Chronomètre des entités et entités de bloc allumé pour %s s : rapport à la fin.",
                String.valueOf(secondes)), BLEU);
        return 1;
    }

    private static int fuites(CommandContext<CommandSourceStack> c) {
        Moniteur.ecrireFuites(l -> dire(c, Component.literal(l), BLEU));
        return 1;
    }

    private static int client(CommandContext<CommandSourceStack> c, int secondes) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer cible = EntityArgument.getPlayer(c, "joueur");
        UUID qui = c.getSource().getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
        if (!DiagClients.demander(cible, secondes, qui)) {
            dire(c, Component.translatableWithFallback("noyau.diag.client_deja", "[Diag] Une analyse du jeu de ce joueur est déjà en cours."), ROUGE);
            return 0;
        }
        dire(c, Component.translatableWithFallback("noyau.diag.client_lance", "[Diag] Analyse du jeu de %s pendant %s s : son rapport arrivera ici.",
                cible.getGameProfile().getName(), String.valueOf(secondes)), BLEU);
        return 1;
    }

    /** Réglage changé pour la session en cours (le fichier du monde garde le sien au prochain démarrage). */
    private static int auto(CommandContext<CommandSourceStack> c, boolean oui) {
        NoyauConfig.DIAG_AUTO.set(oui);
        return etat(c);
    }

    private static int seuil(CommandContext<CommandSourceStack> c, int ms) {
        NoyauConfig.DIAG_SEUIL_MS.set(ms);
        return etat(c);
    }
}
