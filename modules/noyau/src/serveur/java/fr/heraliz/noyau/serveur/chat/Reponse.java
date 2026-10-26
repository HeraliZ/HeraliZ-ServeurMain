package fr.heraliz.noyau.serveur.chat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /r <message>} : répondre au dernier message privé (choix du propriétaire, 24/10). Le noyau note qui a écrit à qui
 * avec les commandes de Minecraft {@code /msg}, {@code /tell}, {@code /w} (et {@code /r} elle-même) ; {@code /r} renvoie le
 * message par {@code /msg} au dernier joueur qui nous a écrit, sinon au dernier à qui on a écrit. Mémoire seulement
 * (rien d'écrit sur le disque), oubliée à la déconnexion. Fil serveur.
 */
public final class Reponse {
    private Reponse() {}

    private static final Set<String> COMMANDES_MP = Set.of("msg", "tell", "w");
    /** Dernier correspondant de chaque joueur (celui qui lui a écrit en dernier, ou à qui il a écrit). */
    private static final Map<UUID, UUID> DERNIER = new HashMap<>();

    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, Reponse::noter);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> DERNIER.remove(e.getEntity().getUUID()));
    }

    public static void commande(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("r")
                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(Reponse::repondre)));
    }

    private static int repondre(CommandContext<CommandSourceStack> x) {
        ServerPlayer p;
        try {
            p = x.getSource().getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }
        UUID u = DERNIER.get(p.getUUID());
        ServerPlayer cible = u == null ? null : p.server.getPlayerList().getPlayer(u);
        if (cible == null) {
            p.sendSystemMessage(Component.translatableWithFallback("noyau.reponse.personne", "Personne à qui répondre.")
                    .withStyle(s -> s.withColor(0xFF8A8A)));
            return 0;
        }
        String message = StringArgumentType.getString(x, "message");
        p.server.getCommands().performPrefixedCommand(x.getSource(), "msg " + cible.getGameProfile().getName() + " " + message);
        return 1;
    }

    /** Message privé envoyé (commande non refusée) : l'expéditeur et le destinataire se notent l'un l'autre. */
    private static void noter(CommandEvent e) {
        if (e.isCanceled()) return;
        ParseResults<CommandSourceStack> pr = e.getParseResults();
        if (!(pr.getContext().getSource().getEntity() instanceof ServerPlayer de)) return;
        String brut = pr.getReader().getString().trim();
        if (brut.startsWith("/")) brut = brut.substring(1);
        int sp = brut.indexOf(' ');
        String racine = (sp < 0 ? brut : brut.substring(0, sp)).toLowerCase(Locale.ROOT);
        int deux = racine.indexOf(':');
        if (deux >= 0) racine = racine.substring(deux + 1);
        if (!COMMANDES_MP.contains(racine)) return;
        try {
            List<ServerPlayer> cibles = List.copyOf(EntityArgument.getPlayers(pr.getContext().build(pr.getReader().getString()), "targets"));
            if (cibles.size() != 1 || cibles.get(0) == de) return;
            ServerPlayer a = cibles.get(0);
            DERNIER.put(a.getUUID(), de.getUUID());
            DERNIER.put(de.getUUID(), a.getUUID());
        } catch (Exception ignore) {
            // commande incomplète ou cible introuvable : Minecraft répond lui-même
        }
    }
}
