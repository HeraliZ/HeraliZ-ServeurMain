package fr.heraliz.corps.commande;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.heraliz.corps.entite.CorpsEntityServeur;
import fr.heraliz.corps.mort.CaptureMort;
import fr.heraliz.corps.registre.Registre;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Locale;

/**
 * Commandes d'administration (niveau 2) :
 * <ul>
 *     <li>{@code /corps liste [joueur]} : corps du serveur, du plus recent au plus ancien (numero, mort, lieu, objets,
 *     temps restant, [TP] cliquable) ;</li>
 *     <li>{@code /corps tp <numero>} : se teleporter au corps ;</li>
 *     <li>{@code /corps vider <numero>} : retire tout ce qu'il contient (il disparait ensuite comme un corps fouille) ;</li>
 *     <li>{@code /corps supprimer <numero>} : retire le corps et son contenu.</li>
 * </ul>
 * Un corps dont le chunk n'est pas charge est vide ou retire a son prochain chargement. Le monde a
 * {@code sendCommandFeedback=false} : un joueur recoit les reponses en message direct ({@code sendSystemMessage}).
 */
public final class CommandeCorps {
    private CommandeCorps() {
    }

    private static final int MAX_LIGNES = 30;

    public static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("corps").requires(s -> s.hasPermission(2))
                .then(Commands.literal("liste")
                        .executes(c -> liste(c, null))
                        .then(Commands.argument("joueur", StringArgumentType.word()).executes(c -> liste(c, StringArgumentType.getString(c, "joueur")))))
                .then(Commands.literal("tp").then(Commands.argument("numero", IntegerArgumentType.integer(1)).executes(CommandeCorps::tp)))
                .then(Commands.literal("vider").then(Commands.argument("numero", IntegerArgumentType.integer(1)).executes(c -> agir(c, false))))
                .then(Commands.literal("supprimer").then(Commands.argument("numero", IntegerArgumentType.integer(1)).executes(c -> agir(c, true)))));
    }

    /** Reponse : message direct a un joueur (le retour des commandes est coupe sur le serveur), retour classique sinon. */
    private static void dire(CommandSourceStack s, Component m) {
        if (s.getEntity() instanceof ServerPlayer p) p.sendSystemMessage(m);
        else s.sendSuccess(() -> m, false);
    }

    private static int liste(CommandContext<CommandSourceStack> c, String joueur) {
        CommandSourceStack s = c.getSource();
        MinecraftServer serveur = s.getServer();
        Registre r = Registre.de(serveur);
        long maintenant = serveur.overworld().getGameTime();
        r.purger(maintenant);
        List<Registre.Fiche> l = r.toutes();
        if (joueur != null) l.removeIf(f -> !f.nom.equalsIgnoreCase(joueur));
        if (l.isEmpty()) {
            dire(s, Component.translatable("commands.heraliz_corps.liste.vide").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        dire(s, Component.translatable("commands.heraliz_corps.liste.titre", l.size()).withStyle(ChatFormatting.GOLD));
        int n = 0;
        for (Registre.Fiche f : l) {
            if (n++ >= MAX_LIGNES) {
                dire(s, Component.translatable("commands.heraliz_corps.liste.suite", l.size() - MAX_LIGNES).withStyle(ChatFormatting.GRAY));
                break;
            }
            long reste = Math.max(0, f.expire - maintenant) / 20;
            String dim = f.dimension.startsWith("minecraft:") ? f.dimension.substring(10) : f.dimension;
            MutableComponent ligne = Component.literal("#" + f.numero + " ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(f.nom + " ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(dim + " " + f.x + " " + f.y + " " + f.z + " · ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("commands.heraliz_corps.liste.objets", f.objets).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" · " + duree(reste)).withStyle(ChatFormatting.GRAY));
            if (f.action != Registre.Action.AUCUNE) {
                ligne.append(Component.literal(" ").append(Component.translatable("commands.heraliz_corps.liste.en_attente."
                        + f.action.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.RED));
            }
            ligne.append(Component.literal(" [TP]").withStyle(st -> st.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/corps tp " + f.numero))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("commands.heraliz_corps.liste.tp")))));
            dire(s, ligne);
        }
        return l.size();
    }

    private static String duree(long secondes) {
        long m = secondes / 60, h = m / 60;
        if (h > 0) return String.format(Locale.ROOT, "%dh%02d", h, m % 60);
        return String.format(Locale.ROOT, "%d:%02d", m, secondes % 60);
    }

    private static int tp(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        Registre.Fiche f = Registre.de(s.getServer()).parNumero(IntegerArgumentType.getInteger(c, "numero"));
        if (f == null) {
            dire(s, Component.translatable("commands.heraliz_corps.introuvable").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!(s.getEntity() instanceof ServerPlayer p)) {
            dire(s, Component.translatable("commands.heraliz_corps.tp.joueur").withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerLevel ou = CaptureMort.niveau(s.getServer(), f.dimension);
        p.teleportTo(ou, f.x + 0.5, f.y + 0.2, f.z + 0.5, p.getYRot(), p.getXRot());
        dire(s, Component.translatable("commands.heraliz_corps.tp.ok", f.numero, f.nom).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int agir(CommandContext<CommandSourceStack> c, boolean supprimer) {
        CommandSourceStack s = c.getSource();
        Registre r = Registre.de(s.getServer());
        Registre.Fiche f = r.parNumero(IntegerArgumentType.getInteger(c, "numero"));
        if (f == null) {
            dire(s, Component.translatable("commands.heraliz_corps.introuvable").withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerLevel ou = CaptureMort.niveau(s.getServer(), f.dimension);
        Entity e = ou.getEntity(f.entite);
        String qui = s.getTextName();
        int numero = f.numero;
        String nom = f.nom;
        if (e instanceof CorpsEntityServeur corps) {
            if (supprimer) corps.disparaitre("commande de " + qui);
            else corps.vider();
            dire(s, Component.translatable(supprimer ? "commands.heraliz_corps.supprimer.ok" : "commands.heraliz_corps.vider.ok", numero, nom)
                    .withStyle(ChatFormatting.GREEN));
        } else {
            r.programmer(f, supprimer ? Registre.Action.SUPPRIMER : Registre.Action.VIDER);
            dire(s, Component.translatable("commands.heraliz_corps.programme", numero, nom).withStyle(ChatFormatting.YELLOW));
        }
        fr.heraliz.corps.HeralizCorps.LOG.info("[corps] {} : /corps {} {} ({})", qui, supprimer ? "supprimer" : "vider", numero, nom);
        return 1;
    }
}
