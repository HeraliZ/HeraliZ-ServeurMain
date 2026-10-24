package fr.heraliz.grades.serveur;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Réponse aux messages privés (Grades 2.7.3, demande du propriétaire du 25/10) : {@code /r <message>} écrit au dernier
 * joueur qui nous a écrit en privé, ou à qui on a écrit, avec {@code /msg}, {@code /tell}, {@code /w} (commandes de
 * Minecraft) ou {@code /r}, comme Essentials.
 * <ul>
 *    <li>Les messages de Minecraft sont suivis par l'évènement des commandes de Forge (joueur -> destinataire et
 *    destinataire -> joueur), après les refus des autres mods (combat, match, tutoriel : commande annulée = rien noté).</li>
 *    <li>Un joueur caché par /vanish est introuvable pour qui n'est pas du staff, avec /r comme avec /msg (le /msg de
 *    Minecraft le trouvait par son pseudo) : « Aucun joueur trouvé ».</li>
 *    <li>Le message envoyé par /r a le même aspect que celui de /msg (textes de Minecraft, gris italique, traduits chez
 *    chaque joueur) et est noté dans la console du serveur.</li>
 * </ul>
 * Les sourdines de LiteBans couvrent /r (liste {@code command_blacklist}). En mémoire seulement. Fil serveur seulement.
 */
final class MessagesPrives {
    private static final Logger LOG = LogUtils.getLogger();
    /** Commandes de message privé de Minecraft (sans l'espace de noms « minecraft: »). */
    private static final Set<String> COMMANDES_MSG = Set.of("msg", "tell", "w");
    /** Dernier correspondant de chaque joueur. */
    private static final Map<UUID, UUID> DERNIER = new HashMap<>();

    private MessagesPrives() {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(MessagesPrives::commandes);
        // après les refus des autres mods (combat, match, carte...) : une commande annulée n'arrive pas ici
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, CommandEvent.class, MessagesPrives::commande);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> DERNIER.remove(e.getEntity().getUUID()));
    }

    private static void noter(ServerPlayer de, ServerPlayer a) {
        if (de == null || a == null || de == a) return;
        DERNIER.put(de.getUUID(), a.getUUID());
        DERNIER.put(a.getUUID(), de.getUUID());
    }

    // ------------------------------------------------------------------ /msg, /tell, /w de Minecraft
    private static void commande(CommandEvent e) {
        ParseResults<CommandSourceStack> pr = e.getParseResults();
        if (!(pr.getContext().getSource().getEntity() instanceof ServerPlayer p)) return;
        String brut = pr.getReader().getString().trim();
        if (brut.startsWith("/")) brut = brut.substring(1);
        int sp = brut.indexOf(' ');
        String racine = (sp < 0 ? brut : brut.substring(0, sp)).toLowerCase(Locale.ROOT);
        int deux = racine.indexOf(':');
        if (deux >= 0) racine = racine.substring(deux + 1);
        if (!COMMANDES_MSG.contains(racine) || pr.getReader().canRead() || !pr.getExceptions().isEmpty()) return;
        Collection<ServerPlayer> cibles;
        try {
            // /tell et /w renvoient vers /msg : les arguments sont dans le dernier contexte
            CommandContextBuilder<CommandSourceStack> b = pr.getContext().getLastChild();
            CommandContext<CommandSourceStack> ctx = b.build(pr.getReader().getString());
            cibles = EntityArgument.getPlayers(ctx, "targets");
        } catch (Exception ex) {
            return; // pas de destinataire lisible : Minecraft répondra lui-même
        }
        for (ServerPlayer c : cibles) {
            if (!VanishServeur.visible(p, c)) {
                e.setCanceled(true);
                p.sendSystemMessage(Component.translatable("argument.entity.notfound.player").withStyle(ChatFormatting.RED));
                return;
            }
        }
        if (cibles.size() == 1) noter(p, cibles.iterator().next());
    }

    // ------------------------------------------------------------------ /r
    private static void commandes(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("r")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(c -> repondre(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "message")))));
    }

    private static int repondre(ServerPlayer p, String texte) {
        UUID u = DERNIER.get(p.getUUID());
        if (u == null) {
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_grades.r.personne",
                    "Personne à qui répondre : écris d'abord avec /msg <joueur> <message>.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer cible = p.server.getPlayerList().getPlayer(u);
        // déconnecté, ou caché par /vanish entre-temps : introuvable (le staff voit les joueurs cachés)
        if (cible == null || !VanishServeur.visible(p, cible)) {
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_grades.r.parti",
                    "Le joueur à qui tu réponds n'est plus connecté.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String message = texte.strip();
        if (message.isEmpty()) return 0;
        Component corps = Component.literal(message);
        // mêmes textes et même aspect que le /msg de Minecraft (traduits par le jeu de chaque joueur)
        cible.sendSystemMessage(Component.translatable("commands.message.display.incoming", p.getDisplayName(), corps)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        p.sendSystemMessage(Component.translatable("commands.message.display.outgoing", cible.getDisplayName(), corps)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        LOG.info("[msg] {} -> {} : {}", p.getGameProfile().getName(), cible.getGameProfile().getName(), message);
        noter(p, cible);
        return 1;
    }
}
