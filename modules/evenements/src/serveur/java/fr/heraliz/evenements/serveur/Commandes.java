package fr.heraliz.evenements.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * {@code /event} (tous) : événements en cours et prochains événements des 24 h. Staff (permission 4) :
 * {@code /event lancer <type> [avec_points]} (sans points par défaut : docs/EVENEMENTS.md), {@code /event annuler <type>},
 * {@code /event point ajouter|retirer <type> <nom>} (à l'endroit où l'on se tient, sur la carte), {@code /event point liste
 * <type>}, {@code /event recharger} (planning et réglages), {@code /event objet <type>} (25/10 : un objet d'événement
 * signé dans son inventaire, pour essayer la revente ; il donne des hérals, jamais de points de saison),
 * {@code /event helico info|prochain <minutes>|lancer [ici] [avec_points]} (hélicoptère, voir {@link Helico}).
 */
public final class Commandes {
    private Commandes() {
    }

    @SubscribeEvent
    public static void enregistrer(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("event")
                .executes(Commandes::agenda)
                .then(Commands.literal("lancer").requires(s -> s.hasPermission(4))
                        .then(Commands.argument("type", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Directeur.EVENEMENTS.keySet(), b))
                                .executes(c -> lancer(c, false))
                                .then(Commands.literal("avec_points").executes(c -> lancer(c, true)))))
                .then(Commands.literal("annuler").requires(s -> s.hasPermission(4))
                        .then(Commands.argument("type", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Directeur.EVENEMENTS.keySet(), b))
                                .executes(Commandes::annuler)))
                .then(Commands.literal("point").requires(s -> s.hasPermission(4))
                        .then(Commands.literal("ajouter").then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Directeur.EVENEMENTS.keySet(), b))
                                .then(Commands.argument("nom", StringArgumentType.word()).executes(Commandes::ajouter))))
                        .then(Commands.literal("retirer").then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Directeur.EVENEMENTS.keySet(), b))
                                .then(Commands.argument("nom", StringArgumentType.word()).executes(Commandes::retirer))))
                        .then(Commands.literal("liste").then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Directeur.EVENEMENTS.keySet(), b))
                                .executes(Commandes::liste))))
                .then(Commands.literal("objet").requires(s -> s.hasPermission(4))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(fr.heraliz.evenements.objets.ObjetsEvenement.IDS, b))
                                .executes(Commandes::objet)))
                // hélicoptère (25/10) : info, prochain, lancer [ici] [avec_points] (Helico.commande)
                .then(Helico.commande())
                .then(Commands.literal("recharger").requires(s -> s.hasPermission(4)).executes(c -> {
                    int n = Directeur.charger();
                    ok(c.getSource(), Component.literal("planning.json et réglages des événements relus : " + n + " créneau(x)."));
                    return 1;
                })));
    }

    private static void ok(CommandSourceStack s, Component c) {
        s.sendSuccess(() -> c, false);
    }

    private static int echec(CommandSourceStack s, String texte) {
        s.sendFailure(Component.literal(texte));
        return 0;
    }

    /** Pour tous : en cours, puis les prochains créneaux. */
    private static int agenda(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        StringBuilder enCours = new StringBuilder();
        for (Evenement e : Directeur.EVENEMENTS.values()) {
            if (!e.actif()) continue;
            if (!enCours.isEmpty()) enCours.append(", ");
            enCours.append(Outils.t("heraliz_evenements.nom." + e.id(), e.id()).getString());
        }
        if (!enCours.isEmpty()) {
            ok(s, Outils.t("heraliz_evenements.agenda.en_cours", "En cours : %s", enCours.toString()).withStyle(ChatFormatting.GOLD));
        }
        List<String[]> l = Directeur.prochains(6);
        if (l.isEmpty()) {
            ok(s, Outils.t("heraliz_evenements.agenda.aucun", "Aucun événement prévu dans les 24 prochaines heures."));
            return 1;
        }
        MutableComponent liste = Component.empty();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) liste.append(Component.literal(" · "));
            liste.append(Component.literal(l.get(i)[0] + " ")).append(Outils.t("heraliz_evenements.nom." + l.get(i)[1], l.get(i)[1]));
        }
        ok(s, Outils.t("heraliz_evenements.agenda.prochains", "Prochains événements : %s", liste).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int lancer(CommandContext<CommandSourceStack> c, boolean points) {
        String type = StringArgumentType.getString(c, "type");
        String refus = Directeur.lancer(c.getSource().getServer(), type, points, c.getSource().getTextName(), 0);
        if (refus != null) return echec(c.getSource(), "Pas lancé : " + refus + ".");
        ok(c.getSource(), Component.literal(type + " lancé" + (points ? " (avec points de saison)." : " (sans points de saison).")));
        return 1;
    }

    private static int annuler(CommandContext<CommandSourceStack> c) {
        String type = StringArgumentType.getString(c, "type");
        Evenement e = Directeur.EVENEMENTS.get(type);
        if (e == null || !e.actif()) return echec(c.getSource(), "Aucun " + type + " en cours.");
        e.annuler(c.getSource().getServer(), "annule par " + c.getSource().getTextName());
        ok(c.getSource(), Component.literal(type + " : suivi arrêté."));
        return 1;
    }

    private static int ajouter(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String type = StringArgumentType.getString(c, "type"), nom = StringArgumentType.getString(c, "nom");
        if (!Directeur.EVENEMENTS.containsKey(type)) return echec(c.getSource(), "Type inconnu : " + type + ".");
        if (p.level().dimension() != Level.OVERWORLD) return echec(c.getSource(), "Les lieux des événements sont sur la carte.");
        if (!Lieux.ajouter(type, nom, p.blockPosition())) return echec(c.getSource(), "Nom invalide (lettres, chiffres, _ et -, 32 au plus).");
        ok(c.getSource(), Component.literal("Lieu « " + nom + " » ajouté pour " + type + " (secteur "
                + Outils.secteur(p.serverLevel(), p.getX(), p.getZ()) + ") : " + Lieux.liste(type).size() + " lieu(x)."));
        Outils.journal("lieu " + type + " « " + nom + " » ajoute par " + p.getGameProfile().getName() + " en " + p.blockPosition().toShortString());
        return 1;
    }

    private static int objet(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String type = StringArgumentType.getString(c, "type");
        if (!fr.heraliz.evenements.objets.ObjetsEvenement.IDS.contains(type)) return echec(c.getSource(), "Objet inconnu : " + type + ".");
        net.minecraft.world.item.ItemStack s = ObjetsServeur.creerType(type, "staff:" + p.getGameProfile().getName());
        if (s.isEmpty()) return echec(c.getSource(), "Objets d'événement coupés (clé illisible : voir le journal).");
        if (!p.getInventory().add(s)) p.drop(s, false);
        ok(c.getSource(), Component.literal("Objet d'événement donné (essai : hérals à la revente, aucun point de saison)."));
        return 1;
    }

    private static int retirer(CommandContext<CommandSourceStack> c) {
        String type = StringArgumentType.getString(c, "type"), nom = StringArgumentType.getString(c, "nom");
        if (!Lieux.retirer(type, nom)) return echec(c.getSource(), "Aucun lieu « " + nom + " » pour " + type + ".");
        ok(c.getSource(), Component.literal("Lieu « " + nom + " » retiré."));
        return 1;
    }

    private static int liste(CommandContext<CommandSourceStack> c) {
        String type = StringArgumentType.getString(c, "type");
        List<Lieux.Lieu> l = Lieux.liste(type);
        ok(c.getSource(), Component.literal(l.size() + " lieu(x) pour " + type + " :"));
        for (Lieux.Lieu x : l) ok(c.getSource(), Component.literal("· " + x.nom() + " : " + x.x() + " " + x.y() + " " + x.z()));
        return 1;
    }
}
