package fr.heraliz.emote.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heraliz.emote.ListeEmotes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Staff (permission 2) : {@code /emote debloquer <joueurs> <emote|hasard|tout>}, {@code /emote retirer <joueurs>
 * <emote|tout>}, {@code /emote voir <joueur>}. Réponses en message direct.
 */
final class CommandeEmote {
    private CommandeEmote() {
    }

    private static final SuggestionProvider<CommandSourceStack> EMOTES = (c, b) ->
            SharedSuggestionProvider.suggest(Stream.concat(Stream.of("hasard", "tout"), ListeEmotes.parId().keySet().stream()), b);
    private static final SuggestionProvider<CommandSourceStack> EMOTES_RETIRER = (c, b) ->
            SharedSuggestionProvider.suggest(Stream.concat(Stream.of("tout"), ListeEmotes.parId().keySet().stream()), b);

    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("emote").requires(s -> s.hasPermission(2))
                .then(Commands.literal("debloquer").then(Commands.argument("joueurs", EntityArgument.players())
                        .then(Commands.argument("emote", StringArgumentType.word()).suggests(EMOTES).executes(CommandeEmote::debloquer))))
                .then(Commands.literal("retirer").then(Commands.argument("joueurs", EntityArgument.players())
                        .then(Commands.argument("emote", StringArgumentType.word()).suggests(EMOTES_RETIRER).executes(CommandeEmote::retirer))))
                .then(Commands.literal("voir").then(Commands.argument("joueur", EntityArgument.player()).executes(CommandeEmote::voir))));
    }

    private static int debloquer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> joueurs = EntityArgument.getPlayers(c, "joueurs");
        String quoi = StringArgumentType.getString(c, "emote");
        ListeEmotes.Emote cible = null;
        if (!quoi.equals("hasard") && !quoi.equals("tout") && (cible = ListeEmotes.parId(quoi)) == null) return inconnue(c, quoi);
        // 27/10 : rend le nombre de joueurs qui ont VRAIMENT reçu une emote (0 : rien de donné), comme « grade acheter » :
        // la caisse du casino et la boutique en jeu remboursent quand la commande rend 0
        int faits = 0;
        for (ServerPlayer p : joueurs) {
            String nom = p.getGameProfile().getName();
            if (quoi.equals("tout")) {
                int n = Deblocages.toutDebloquer(p);
                if (n > 0) faits++;
                dire(c, Component.translatableWithFallback("heraliz_emote.cmd.tout", "%s : %s emote(s) débloquée(s)", nom, n));
            } else if (quoi.equals("hasard")) {
                ListeEmotes.Emote e = Deblocages.debloquerAuHasard(p);
                if (e != null) faits++;
                dire(c, e == null
                        ? Component.translatableWithFallback("heraliz_emote.cmd.complet", "%s a déjà toutes les emotes", nom)
                        : Component.translatableWithFallback("heraliz_emote.cmd.debloquee", "%s : emote « %s » débloquée", nom, e.nom()));
            } else {
                boolean ok = Deblocages.debloquer(p, cible.uuid());
                if (ok) faits++;
                dire(c, ok
                        ? Component.translatableWithFallback("heraliz_emote.cmd.debloquee", "%s : emote « %s » débloquée", nom, cible.nom())
                        : Component.translatableWithFallback("heraliz_emote.cmd.deja", "%s a déjà l'emote « %s »", nom, cible.nom()));
            }
        }
        return faits;
    }

    private static int retirer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> joueurs = EntityArgument.getPlayers(c, "joueurs");
        String quoi = StringArgumentType.getString(c, "emote");
        ListeEmotes.Emote cible = null;
        if (!quoi.equals("tout") && (cible = ListeEmotes.parId(quoi)) == null) return inconnue(c, quoi);
        for (ServerPlayer p : joueurs) {
            String nom = p.getGameProfile().getName();
            if (cible == null) {
                int n = Deblocages.toutRetirer(p);
                dire(c, Component.translatableWithFallback("heraliz_emote.cmd.tout_retire", "%s : %s emote(s) retirée(s)", nom, n));
            } else {
                boolean ok = Deblocages.retirer(p, cible.uuid());
                dire(c, ok
                        ? Component.translatableWithFallback("heraliz_emote.cmd.retiree", "%s : emote « %s » retirée", nom, cible.nom())
                        : Component.translatableWithFallback("heraliz_emote.cmd.pas", "%s n'a pas l'emote « %s »", nom, cible.nom()));
            }
        }
        return joueurs.size();
    }

    private static int voir(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "joueur");
        List<String> noms = new ArrayList<>();
        for (UUID u : Deblocages.de(p.getUUID())) {
            ListeEmotes.Emote e = ListeEmotes.de(u);
            if (e != null) noms.add(e.nom());
        }
        dire(c, Component.translatableWithFallback("heraliz_emote.cmd.voir", "%s : %s / %s emotes : %s", p.getGameProfile().getName(),
                noms.size(), ListeEmotes.toutes().size(), noms.isEmpty() ? "-" : String.join(", ", noms)));
        return noms.size();
    }

    private static int inconnue(CommandContext<CommandSourceStack> c, String id) {
        c.getSource().sendFailure(Component.translatableWithFallback("heraliz_emote.cmd.inconnue", "Emote inconnue : %s", id));
        return 0;
    }

    private static void dire(CommandContext<CommandSourceStack> c, Component m) {
        c.getSource().sendSuccess(() -> m, false);
    }
}
