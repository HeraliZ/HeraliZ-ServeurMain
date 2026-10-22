package fr.heraliz.grades.serveur;

import com.mojang.brigadier.CommandDispatcher;
import fr.heraliz.grades.Grade;
import fr.heraliz.grades.ObjetRegistre;
import fr.heraliz.grades.reseau.ReseauGrades;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Éditeur des grades (2.0.0, maquettes validées le 22/10) : ouvert par le « Registre des grades » (clic droit) ou
 * {@code /grades editeur}, réservé aux op de niveau 4 connectés sur AuthMe. Il crée, règle et supprime des grades
 * (apparence, permissions) et leurs kits ; tout est revérifié ici puis écrit dans {@code heraliz_grades/definitions.json}
 * ({@link DefinitionsGrades}) et noté dans le journal des grades. DONNER ou RETIRER un grade à un joueur reste réservé à
 * la console du serveur (choix du propriétaire) : l'onglet Joueurs ne fait que montrer qui l'a.
 */
final class EditeurServeur {
    private EditeurServeur() {
    }

    static void brancher() {
        ObjetRegistre.OUVRIR.brancher(p -> ouvrir(p, true, ""));
        ReseauGrades.RECEPTION_ACTION.brancher(EditeurServeur::action);
    }

    private static boolean droit(ServerPlayer p) {
        return p.hasPermissions(4) && PontBukkit.authentifie(p);
    }

    private static Component msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades.editeur." + cle, fr, args);
    }

    // ------------------------------------------------------------------ commandes
    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("grades")
                .requires(s -> s.getEntity() instanceof ServerPlayer p && droit(p))
                .then(Commands.literal("editeur").executes(c -> {
                    ouvrir(c.getSource().getPlayerOrException(), true, "");
                    return 1;
                }))
                .then(Commands.literal("registre").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ItemStack s = new ItemStack(ObjetRegistre.REGISTRE.get());
                    if (!p.getInventory().add(s)) p.drop(s, false);
                    p.sendSystemMessage(msg("registre", "Registre des grades reçu : clic droit pour ouvrir l'éditeur."));
                    return 1;
                })));
    }

    // ------------------------------------------------------------------ envoi
    /** Envoie les définitions à tous les joueurs (après un changement, et à la connexion pour un seul). */
    static void diffuser(MinecraftServer srv) {
        ReseauGrades.CANAL.send(PacketDistributor.ALL.noArg(), new ReseauGrades.Definitions(List.of()));
    }

    static void envoyerDefinitions(ServerPlayer p) {
        ReseauGrades.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new ReseauGrades.Definitions(List.of()));
    }

    static void ouvrir(ServerPlayer p, boolean ouvrir, String message) {
        if (!droit(p)) {
            if (ouvrir) p.displayClientMessage(msg("refus", "Réservé aux op connectés.").copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        envoyerDefinitions(p);
        List<ReseauGrades.KitEdite> kits = new ArrayList<>();
        for (DefinitionsGrades.KitDef k : DefinitionsGrades.kits()) kits.add(new ReseauGrades.KitEdite(k.grade(), k.id(), k.nom(), k.rechargeH(), k.objets()));
        List<ReseauGrades.Membre> membres = new ArrayList<>();
        RegistreGrades r = ServeurGrades.registre;
        if (r != null) {
            for (Grade g : Grade.values()) {
                if (!g.attribuable()) continue;
                for (Map.Entry<UUID, String> e : r.membres(g)) {
                    membres.add(new ReseauGrades.Membre(g.id, e.getValue(), p.server.getPlayerList().getPlayer(e.getKey()) != null));
                }
            }
        }
        ReseauGrades.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new ReseauGrades.EtatEditeur(ouvrir, kits, membres, message));
    }

    // ------------------------------------------------------------------ actions
    private static void action(ReseauGrades.ActionRecue a) {
        ServerPlayer p = a.joueur();
        if (!droit(p)) return;
        if (DefinitionsGrades.illisible() || ServeurGrades.registre == null || ServeurGrades.registre.illisible()) {
            ouvrir(p, false, msg("illisible", "Fichier des grades illisible : rien n'est enregistré (voir la console du serveur).").getString());
            return;
        }
        ReseauGrades.ActionEditeur act = a.action();
        // état d'avant, pour tout remettre si l'écriture échoue
        List<Grade.Def> avantGrades = new ArrayList<>();
        for (Grade g : Grade.values()) avantGrades.add(g.def());
        List<DefinitionsGrades.KitDef> avantKits = DefinitionsGrades.kits();
        Component reponse;
        try {
            reponse = appliquer(p, act);
        } catch (IllegalArgumentException refus) {
            ouvrir(p, false, refus.getMessage());
            return;
        }
        try {
            DefinitionsGrades.sauver();
        } catch (IOException ex) {
            Grade.appliquer(avantGrades, true);
            DefinitionsGrades.remplacerKits(avantKits);
            ouvrir(p, false, msg("erreur", "Impossible d'enregistrer (voir la console du serveur) : rien n'a changé.").getString());
            return;
        }
        ServeurGrades.registre.journaliser(p.getGameProfile().getName() + " (éditeur) | " + act.genre().name().toLowerCase(Locale.ROOT) + " | " + act.grade()
                + (act.kit() != null ? "/" + act.kit().id() : ""));
        LienDiscord.evenement("🛠️ " + p.getGameProfile().getName() + " a modifié les grades (" + act.genre().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " : **" + act.grade() + "**).");
        diffuser(p.server);
        for (ServerPlayer j : p.server.getPlayerList().getPlayers()) ServeurGrades.rafraichir(j);
        TabServeur.maj(p.server, true);
        ouvrir(p, false, reponse.getString());
    }

    private static Component appliquer(ServerPlayer p, ReseauGrades.ActionEditeur act) {
        String id = act.grade() == null ? "" : act.grade().toLowerCase(Locale.ROOT);
        Grade g = Grade.parId(id);
        switch (act.genre()) {
            case CREER_GRADE -> {
                if (!Grade.idValide(id)) throw new IllegalArgumentException(msg("id_invalide", "Identifiant : 2 à 24 lettres minuscules, chiffres ou _.").getString());
                if (g != null) throw new IllegalArgumentException(msg("existe", "Ce grade existe déjà.").getString());
                if (Grade.values().size() >= 48) throw new IllegalArgumentException(msg("trop", "48 grades au plus.").getString());
                Grade n = Grade.creer(id);
                n.nomFr = n.nomEn = n.nomRu = id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1).replace('_', ' ');
                return msg("cree", "Grade %s créé.", n.nomFr);
            }
            case ENREGISTRER_GRADE -> {
                if (g == null || act.def() == null || !id.equals(act.def().id())) throw new IllegalArgumentException(msg("inconnu", "Grade inconnu.").getString());
                Grade.Type avant = g.type;
                g.appliquer(act.def());
                if (g.type != avant) ServeurGrades.registre.retirerPartout(g, p.getGameProfile().getName());
                return msg("enregistre", "Grade %s enregistré.", g.nomFr);
            }
            case SUPPRIMER_GRADE -> {
                if (g == null || g.interne) throw new IllegalArgumentException(msg("interne", "Ce grade ne se supprime pas.").getString());
                if (!ServeurGrades.registre.retirerPartout(g, p.getGameProfile().getName())) {
                    throw new IllegalArgumentException(msg("erreur", "Impossible d'enregistrer (voir la console du serveur) : rien n'a changé.").getString());
                }
                DefinitionsGrades.retirerKits(g.id);
                Grade.supprimer(g.id);
                return msg("supprime", "Grade %s supprimé.", g.nomFr);
            }
            case ENREGISTRER_KIT -> {
                ReseauGrades.KitEdite k = act.kit();
                if (g == null || k == null || !g.id.equals(k.grade())) throw new IllegalArgumentException(msg("inconnu", "Grade inconnu.").getString());
                String nom = k.nom() == null ? "" : k.nom().replace('§', ' ').strip();
                if (nom.isEmpty() || nom.length() > 32) throw new IllegalArgumentException(msg("kit_nom", "Nom du kit : 1 à 32 caractères.").getString());
                List<ItemStack> objets = new ArrayList<>();
                for (ItemStack s : k.objets()) {
                    if (s.isEmpty()) continue;
                    ItemStack c = s.copy();
                    // un objet suivi par l'anti-duplication de Spartan ne doit jamais être copié avec son numéro
                    if (c.getTag() != null) c.getTag().remove("spartan_uid");
                    objets.add(c);
                }
                if (objets.size() > DefinitionsGrades.MAX_OBJETS) throw new IllegalArgumentException(msg("kit_trop", "27 piles au plus dans un kit.").getString());
                String kid = k.id() == null ? "" : k.id();
                if (kid.isEmpty() || DefinitionsGrades.kit(g.id, kid) == null) {
                    if (DefinitionsGrades.kitsDe(g).size() >= DefinitionsGrades.MAX_KITS_PAR_GRADE) {
                        throw new IllegalArgumentException(msg("kits_trop", "8 kits au plus par grade.").getString());
                    }
                    kid = "kit";
                    for (int i = 2; DefinitionsGrades.kit(g.id, kid) != null; i++) kid = "kit" + i;
                }
                int h = Math.max(1, Math.min(DefinitionsGrades.RECHARGE_MAX_H, k.rechargeH()));
                DefinitionsGrades.poserKit(g.id, kid, new DefinitionsGrades.KitDef(g.id, kid, nom, h, List.copyOf(objets)));
                return msg("kit_enregistre", "Kit %s enregistré.", nom);
            }
            case SUPPRIMER_KIT -> {
                ReseauGrades.KitEdite k = act.kit();
                if (g == null || k == null || DefinitionsGrades.kit(g.id, k.id()) == null) throw new IllegalArgumentException(msg("kit_inconnu", "Kit inconnu.").getString());
                DefinitionsGrades.poserKit(g.id, k.id(), null);
                return msg("kit_supprime", "Kit supprimé.");
            }
        }
        throw new IllegalArgumentException("?");
    }
}
