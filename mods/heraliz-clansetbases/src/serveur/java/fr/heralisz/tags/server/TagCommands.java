package fr.heralisz.tags.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heralisz.tags.catalogue.TagCatalog;
import fr.heralisz.tags.catalogue.TagDef;
import fr.heralisz.tags.network.Packets;
import fr.heralisz.tags.network.TagsNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * /tags                              ouvre la collection
 * /tags donner <joueurs> <id|tout>   (op) debloque un tag (ou tous)
 * /tags retirer <joueurs> <id|tout>  (op) retire un tag (ou tous)
 * /tags info                         (op) catalogue, joueurs connus, tags actifs
 * <p>Les reponses vont directement au joueur qui tape la commande ({@link #repondre}) : la regle de jeu
 * sendCommandFeedback=false (monde du serveur) masque sendSuccess pour les joueurs.
 */
public final class TagCommands {
    private TagCommands() {
    }

    /**
     * Reponse d'une commande : envoyee directement au joueur qui l'a tapee (visible meme avec sendCommandFeedback=false,
     * qui masque sendSuccess pour les joueurs), avec une ligne au journal du serveur si journal n'est pas null (texte
     * en clair : le serveur dedie n'a pas les traductions du mod) ; console ou bloc de commande : sendSuccess.
     */
    private static void repondre(CommandSourceStack src, Component msg, String journal) {
        if (src.getEntity() instanceof ServerPlayer p) {
            p.sendSystemMessage(msg);
            if (journal != null) fr.heralisz.tags.HeraliszTags.LOGGER.info("[HeraliZ Tags] {} : {}", p.getGameProfile().getName(), journal);
        } else {
            src.sendSuccess(() -> msg, journal != null);
        }
    }

    private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, b) ->
            SharedSuggestionProvider.suggest(Stream.concat(Stream.of("tout"), TagCatalog.tous().stream().map(TagDef::id)), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tags")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    TagService.envoyerEtat(p);
                    TagsNet.toPlayer(p, new Packets.OuvrirIndexS2C());
                    return 1;
                })
                .then(Commands.literal("donner").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("joueurs", EntityArgument.players())
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
                                        .executes(c -> donner(c, EntityArgument.getPlayers(c, "joueurs"), StringArgumentType.getString(c, "id"))))))
                .then(Commands.literal("retirer").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("joueurs", EntityArgument.players())
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
                                        .executes(c -> retirer(c, EntityArgument.getPlayers(c, "joueurs"), StringArgumentType.getString(c, "id"))))))
                .then(Commands.literal("info").requires(s -> s.hasPermission(2))
                        .executes(c -> {
                            repondre(c.getSource(), Component.translatable("cmd.heralisz_tags.info",
                                    TagCatalog.taille(), TagCatalog.depuisFichier() ? "tags.json" : "reference",
                                    TagStore.nombreJoueurs(), TagService.actifs()), null);
                            return 1;
                        })));
    }

    private static List<String> ids(CommandContext<CommandSourceStack> c, String id) throws CommandSyntaxException {
        if (id.equals("tout")) {
            List<String> l = new ArrayList<>();
            for (TagDef d : TagCatalog.tous()) l.add(d.id());
            return l;
        }
        if (!TagCatalog.existe(id)) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.translatable("cmd.heralisz_tags.inconnu", id)).create();
        }
        return List.of(id);
    }

    private static int donner(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> joueurs, String id) throws CommandSyntaxException {
        List<String> ids = ids(c, id);
        int n = 0;
        for (ServerPlayer p : joueurs) {
            int nouveaux = 0;
            for (String s : ids) if (TagStore.debloquer(p.getUUID(), s)) nouveaux++;
            if (nouveaux > 0) {
                TagService.envoyerEtat(p);
                p.displayClientMessage(Component.translatable("msg.heralisz_tags.donnes", nouveaux), true);
            }
            n += nouveaux;
        }
        repondre(c.getSource(), Component.translatable("cmd.heralisz_tags.donnes", n, joueurs.size()),
                "/tags donner " + id + " : " + n + " tag(s) debloque(s) pour " + joueurs.size() + " joueur(s)");
        return n;
    }

    private static int retirer(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> joueurs, String id) throws CommandSyntaxException {
        List<String> ids = ids(c, id);
        int n = 0;
        for (ServerPlayer p : joueurs) {
            int retires = 0;
            for (String s : ids) if (TagStore.retirer(p.getUUID(), s)) retires++;
            if (retires > 0) TagService.envoyerEtat(p);
            n += retires;
        }
        repondre(c.getSource(), Component.translatable("cmd.heralisz_tags.retires", n, joueurs.size()),
                "/tags retirer " + id + " : " + n + " tag(s) retire(s) pour " + joueurs.size() + " joueur(s)");
        return n;
    }
}
