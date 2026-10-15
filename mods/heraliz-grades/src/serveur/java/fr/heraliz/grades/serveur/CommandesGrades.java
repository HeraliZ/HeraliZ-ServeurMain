package fr.heraliz.grades.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heraliz.grades.Grade;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * /grade :
 * <ul>
 *    <li>{@code grade donner <joueur> <grade>} et {@code grade retirer <joueur> <grade>} : CONSOLE DU SERVEUR SEULEMENT
 *    (aucun joueur, même op, même Fondateur ; ni bloc de commande, ni fonction, ni RCON) ;</li>
 *    <li>{@code grade voir <joueur>} : Helper et plus (connecté sur AuthMe), ou console ;</li>
 *    <li>{@code grade liste} : Modérateur et plus (connecté sur AuthMe), ou console.</li>
 * </ul>
 */
final class CommandesGrades {
    private static final SuggestionProvider<CommandSourceStack> JOUEURS = (c, b) -> SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerNames(), b);
    private static final SuggestionProvider<CommandSourceStack> GRADES = (c, b) -> SharedSuggestionProvider.suggest(
            Arrays.stream(Grade.values()).filter(Grade::attribuable).map(g -> g.id), b);

    private CommandesGrades() {
    }

    /**
     * Vraie console du serveur : aucune entité derrière la commande ET niveau 4. Un joueur a toujours une entité (même
     * via /execute) ; blocs de commande et fonctions n'ont que le niveau 2 ; RCON (désactivé sur HeraliZ) est refusé par
     * son nom ; le solo n'a pas de console.
     */
    static boolean console(CommandSourceStack s) {
        return s.getEntity() == null && s.hasPermission(4) && !"Rcon".equals(s.getTextName()) && s.getServer().isDedicatedServer();
    }

    /** Joueur du staff d'au moins ce grade, connecté sur AuthMe. */
    static boolean staff(CommandSourceStack s, Grade min) {
        RegistreGrades r = ServeurGrades.registre;
        return r != null && s.getEntity() instanceof ServerPlayer p && r.staffAuMoins(p.getUUID(), min) && PontBukkit.authentifie(p);
    }

    static void enregistrer(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("grade")
                .requires(s -> console(s) || staff(s, Grade.HELPER))
                .then(Commands.literal("donner").requires(CommandesGrades::console)
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(JOUEURS)
                                .then(Commands.argument("grade", StringArgumentType.word()).suggests(GRADES)
                                        .executes(c -> changer(c, true)))))
                .then(Commands.literal("retirer").requires(CommandesGrades::console)
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(JOUEURS)
                                .then(Commands.argument("grade", StringArgumentType.word()).suggests(GRADES)
                                        .executes(c -> changer(c, false)))))
                .then(Commands.literal("voir")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(JOUEURS)
                                .executes(CommandesGrades::voir)))
                .then(Commands.literal("liste").requires(s -> console(s) || staff(s, Grade.MODERATEUR))
                        .executes(CommandesGrades::liste)));
    }

    private static MutableComponent msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades." + cle, fr, args);
    }

    /** Joueur visé : connecté (pseudo exact du profil), sinon déjà gradé (dernier pseudo connu). null sinon. */
    private static Map.Entry<UUID, String> trouver(MinecraftServer srv, String nom) {
        ServerPlayer p = srv.getPlayerList().getPlayerByName(nom);
        if (p != null) return Map.entry(p.getUUID(), p.getGameProfile().getName());
        RegistreGrades r = ServeurGrades.registre;
        if (r == null) return null;
        for (Map.Entry<UUID, RegistreGrades.Fiche> e : r.tous()) {
            if (e.getValue().nom().equalsIgnoreCase(nom)) return Map.entry(e.getKey(), e.getValue().nom());
        }
        return null;
    }

    private static int changer(CommandContext<CommandSourceStack> c, boolean donner) {
        CommandSourceStack s = c.getSource();
        // double contrôle : la branche est déjà réservée à la console, on revérifie au moment d'agir
        if (!console(s)) return 0;
        RegistreGrades r = ServeurGrades.registre;
        String nom = StringArgumentType.getString(c, "joueur"), id = StringArgumentType.getString(c, "grade");
        Grade g = Grade.parId(id);
        if (g == null) {
            s.sendFailure(msg("grade_inconnu", "Grade inconnu : %s. Grades possibles : fondateur, moderateur, helper, rescape, traqueur, roi_de_la_zone.", id));
            return 0;
        }
        if (!g.attribuable()) {
            s.sendFailure(msg("survivant", "Survivant est le grade de tout le monde : il ne se donne pas et ne se retire pas."));
            return 0;
        }
        Map.Entry<UUID, String> cible = trouver(s.getServer(), nom);
        if (r == null || cible == null) {
            s.sendFailure(msg("joueur_inconnu", "Joueur introuvable : %s (il doit être connecté, ou déjà avoir un grade).", nom));
            return 0;
        }
        String pseudo = cible.getValue();
        switch (r.changer(cible.getKey(), pseudo, g, donner, "console")) {
            case OK -> {
                s.sendSuccess(() -> donner ? msg("donne", "Grade %2$s donné à %1$s.", pseudo, g.nom()) : msg("retire", "Grade %2$s retiré à %1$s.", pseudo, g.nom()), true);
                ServerPlayer p = s.getServer().getPlayerList().getPlayer(cible.getKey());
                if (p != null) {
                    ServeurGrades.rafraichir(p);
                    s.getServer().getCommands().sendCommands(p);
                    p.sendSystemMessage(donner ? msg("recu", "Tu as reçu le grade %s.", g.nom()) : msg("perdu", "Tu n'as plus le grade %s.", g.nom()));
                }
                return 1;
            }
            case DEJA -> s.sendFailure(msg("deja", "%1$s a déjà le grade %2$s.", pseudo, g.nom()));
            case ABSENT -> s.sendFailure(msg("absent", "%1$s n'a pas le grade %2$s.", pseudo, g.nom()));
            case ILLISIBLE -> s.sendFailure(msg("illisible", "Le fichier des grades est illisible : aucun changement possible (voir la console du serveur)."));
            default -> s.sendFailure(msg("erreur", "Impossible d'enregistrer le changement (voir la console du serveur)."));
        }
        return 0;
    }

    private static Component ouAucun(Grade g) {
        return g != null ? g.nom() : msg("aucun", "aucun");
    }

    private static int voir(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        RegistreGrades r = ServeurGrades.registre;
        String nom = StringArgumentType.getString(c, "joueur");
        Map.Entry<UUID, String> cible = trouver(s.getServer(), nom);
        RegistreGrades.Fiche f = r == null || cible == null ? null : r.fiche(cible.getKey());
        String pseudo = cible != null ? cible.getValue() : nom;
        s.sendSuccess(() -> msg("voir", "%1$s : staff %2$s, boutique %3$s", pseudo, ouAucun(f == null ? null : f.staff()), ouAucun(f == null ? null : f.boutique())), false);
        return 1;
    }

    private static int liste(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        RegistreGrades r = ServeurGrades.registre;
        if (r == null || r.tous().isEmpty()) {
            s.sendSuccess(() -> msg("liste_vide", "Aucun joueur gradé : tout le monde est Survivant."), false);
            return 1;
        }
        var tous = r.tous();
        s.sendSuccess(() -> msg("liste", "Joueurs gradés (%s) :", String.valueOf(tous.size())), false);
        for (Map.Entry<UUID, RegistreGrades.Fiche> e : tous) {
            RegistreGrades.Fiche f = e.getValue();
            s.sendSuccess(() -> msg("voir", "%1$s : staff %2$s, boutique %3$s", f.nom(), ouAucun(f.staff()), ouAucun(f.boutique())), false);
        }
        return 1;
    }
}
