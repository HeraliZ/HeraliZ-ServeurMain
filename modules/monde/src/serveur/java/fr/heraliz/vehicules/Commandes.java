package fr.heraliz.vehicules;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@code /vehicules liste} : les véhicules et les objets du mod. {@code /vehicules donner <joueurs> <véhicule>} (op 2) :
 * donne l'objet qui pose le véhicule (noms courts buggy / moto / camion / fourgon, ou l'id Limitless Vehicle), par
 * exemple pour remplir une caisse ({@code /heraliz loot add <caisse> <poids> <rareté>} avec l'objet en main).
 * Réponses en {@code sendSystemMessage} : {@code sendCommandFeedback} est coupé sur le serveur.
 */
public final class Commandes {
    private Commandes() {
    }

    static void onRegister(RegisterCommandsEvent e) {
        register(e.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        List<String> choix = new ArrayList<>(Contenu.NOMS);
        Contenu.VEHICULES.forEach(v -> choix.add(v.substring(v.indexOf(':') + 1)));
        d.register(Commands.literal("vehicules")
                .then(Commands.literal("liste").executes(Commandes::liste))
                .then(Commands.literal("donner").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("joueurs", EntityArgument.players())
                                .then(Commands.argument("vehicule", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(choix, b))
                                        .executes(Commandes::donner)))));
    }

    private static int liste(CommandContext<CommandSourceStack> c) {
        repondre(c.getSource(), Component.translatable("heraliz_vehicules.cmd.liste").withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < Contenu.VEHICULES.size(); i++) {
            String id = Contenu.VEHICULES.get(i);
            repondre(c.getSource(), Component.literal(" - " + Contenu.NOMS.get(i) + " : ")
                    .append(Component.translatable(id.replace(':', '.'))).withStyle(ChatFormatting.GRAY));
        }
        for (ItemStack s : Contenu.outils()) {
            repondre(c.getSource(), Component.literal(" - ").append(s.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int donner(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String nom = StringArgumentType.getString(c, "vehicule");
        String id = Contenu.resoudre(nom);
        if (id == null) {
            repondre(c.getSource(), Component.translatable("heraliz_vehicules.cmd.inconnu", nom).withStyle(ChatFormatting.RED));
            return 0;
        }
        Collection<ServerPlayer> joueurs = EntityArgument.getPlayers(c, "joueurs");
        for (ServerPlayer p : joueurs) {
            ItemStack s = Contenu.vehicule(id);
            if (!p.getInventory().add(s)) p.drop(s, false);
        }
        repondre(c.getSource(), Component.translatable("heraliz_vehicules.cmd.donne", Component.translatable(id.replace(':', '.')), joueurs.size())
                .withStyle(ChatFormatting.GREEN));
        return joueurs.size();
    }

    /** Joueur : message système (toujours affiché) ; console / bloc de commande : retour classique. */
    static void repondre(CommandSourceStack src, Component msg) {
        if (src.getEntity() instanceof ServerPlayer p) p.sendSystemMessage(msg);
        else src.sendSuccess(() -> msg, false);
    }
}
