package fr.heralisz.matchs.moteur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Arenes;
import fr.heralisz.matchs.moteur.api.Carte;
import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.CombatTag;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.api.Pose;
import fr.heralisz.matchs.moteur.arenes.ArenesService;
import fr.heralisz.matchs.moteur.coffre.Coffres;
import fr.heralisz.matchs.moteur.dev.BancMoteur;
import fr.heralisz.matchs.moteur.groupes.GroupesService;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Commandes du moteur :
 * <ul>
 *     <li>{@code /groupe} (ecran), {@code /groupe inviter <joueur>}, {@code accepter [joueur]}, {@code refuser [joueur]},
 *     {@code quitter}, {@code exclure <joueur>}, {@code chef <joueur>} ;</li>
 *     <li>{@code /matchs} (operateurs, niveau 2) : {@code coffre etat|rendre <joueur>}, {@code combat etat|marquer|effacer
 *     <joueur>}, {@code parties}, (retours par {@link Annonces#retour} : visibles meme avec {@code sendCommandFeedback=false}),
 *     {@code arenes liste|reserver <carte>|liberer <instance>|tp <instance>} ; {@code test ...} (client de dev
 *     seulement, voir {@link BancMoteur}).</li>
 * </ul>
 */
public final class CommandesMoteur {
    private CommandesMoteur() {
    }

    public static void enregistrer(CommandDispatcher<CommandSourceStack> d, CommandBuildContext c) {
        d.register(Commands.literal("groupe")
                .executes(x -> {
                    GroupesService.envoyer(x.getSource().getPlayerOrException(), true);
                    return 1;
                })
                .then(Commands.literal("inviter").then(Commands.argument("joueur", EntityArgument.player()).executes(x -> {
                    GroupesService.inviter(x.getSource().getPlayerOrException(), EntityArgument.getPlayer(x, "joueur"));
                    return 1;
                })))
                .then(Commands.literal("accepter").executes(x -> {
                    GroupesService.accepter(x.getSource().getPlayerOrException(), null);
                    return 1;
                }).then(Commands.argument("joueur", StringArgumentType.word()).executes(x -> {
                    GroupesService.accepter(x.getSource().getPlayerOrException(), StringArgumentType.getString(x, "joueur"));
                    return 1;
                })))
                .then(Commands.literal("refuser").executes(x -> {
                    GroupesService.refuser(x.getSource().getPlayerOrException(), null);
                    return 1;
                }).then(Commands.argument("joueur", StringArgumentType.word()).executes(x -> {
                    GroupesService.refuser(x.getSource().getPlayerOrException(), StringArgumentType.getString(x, "joueur"));
                    return 1;
                })))
                .then(Commands.literal("quitter").executes(x -> {
                    GroupesService.quitter(x.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("exclure").then(Commands.argument("joueur", StringArgumentType.word()).executes(x -> {
                    GroupesService.exclure(x.getSource().getPlayerOrException(), StringArgumentType.getString(x, "joueur"));
                    return 1;
                })))
                .then(Commands.literal("chef").then(Commands.argument("joueur", StringArgumentType.word()).executes(x -> {
                    GroupesService.chef(x.getSource().getPlayerOrException(), StringArgumentType.getString(x, "joueur"));
                    return 1;
                }))));

        var matchs = Commands.literal("matchs").requires(s -> s.hasPermission(2))
                .then(Commands.literal("coffre")
                        .then(Commands.literal("etat").then(Commands.argument("joueur", EntityArgument.player()).executes(CommandesMoteur::etatCoffre)))
                        .then(Commands.literal("rendre").then(Commands.argument("joueur", EntityArgument.player()).executes(x -> {
                            ServerPlayer p = EntityArgument.getPlayer(x, "joueur");
                            boolean ok = Coffre.rendre(p);
                            retour(x, ok ? Component.translatableWithFallback("heralisz_matchs.commande.coffre.rendu", "Coffre de %s rendu.",
                                    p.getGameProfile().getName())
                                    : Component.translatableWithFallback("heralisz_matchs.commande.coffre.rien", "Coffre de %s : rien à rendre.",
                                    p.getGameProfile().getName()));
                            return ok ? 1 : 0;
                        }))))
                .then(Commands.literal("combat")
                        .then(Commands.literal("etat").then(Commands.argument("joueur", EntityArgument.player()).executes(x -> combat(x, 0))))
                        .then(Commands.literal("marquer").then(Commands.argument("joueur", EntityArgument.player()).executes(x -> combat(x, 1))))
                        .then(Commands.literal("effacer").then(Commands.argument("joueur", EntityArgument.player()).executes(x -> combat(x, 2)))))
                .then(Commands.literal("parties").executes(x -> {
                    if (MatchApi.parties().isEmpty()) {
                        retour(x, Component.translatableWithFallback("heralisz_matchs.commande.aucune_partie", "Aucune partie."));
                    }
                    for (Partie p : MatchApi.parties()) retour(x, Component.literal(p.toString()));
                    return MatchApi.parties().size();
                }))
                .then(Commands.literal("arenes")
                        .then(Commands.literal("liste").executes(x -> {
                            for (Carte ca : Arenes.cartes()) {
                                retour(x, Component.translatableWithFallback("heralisz_matchs.commande.arenes.carte", "carte %s (%s)", ca.id(), ca.type()));
                            }
                            for (Instance i : Arenes.instances()) {
                                retour(x, i.libre()
                                        ? Component.translatableWithFallback("heralisz_matchs.commande.arenes.libre", "%s %s libre",
                                        String.valueOf(i), ArenesService.progression(i))
                                        : Component.translatableWithFallback("heralisz_matchs.commande.arenes.occupee", "%s %s occupée par %s",
                                        String.valueOf(i), ArenesService.progression(i), String.valueOf(i.occupant())));
                            }
                            return Arenes.instances().size();
                        }))
                        .then(Commands.literal("reserver").then(Commands.argument("carte", StringArgumentType.word())
                                .suggests((x, b) -> SharedSuggestionProvider.suggest(Arenes.cartes().stream().map(Carte::id), b))
                                .executes(x -> {
                                    Instance i = Arenes.reserver(StringArgumentType.getString(x, "carte"));
                                    if (i == null) {
                                        x.getSource().sendFailure(Component.translatableWithFallback(
                                                "heralisz_matchs.commande.arenes.reservation_impossible", "Réservation impossible (voir le journal)."));
                                        return 0;
                                    }
                                    i.occuper("admin");
                                    retour(x, Component.translatableWithFallback("heralisz_matchs.commande.arenes.reservee", "Instance %s réservée (%s).",
                                            i.id(), ArenesService.progression(i)));
                                    return 1;
                                })))
                        .then(Commands.literal("liberer").then(Commands.argument("instance", StringArgumentType.string()).executes(x -> {
                            Instance i = instance(StringArgumentType.getString(x, "instance"));
                            if (i == null) return 0;
                            Arenes.liberer(i);
                            retour(x, Component.translatableWithFallback("heralisz_matchs.commande.arenes.liberee", "Instance %s libérée.", i.id()));
                            return 1;
                        })))
                        .then(Commands.literal("tp").then(Commands.argument("instance", StringArgumentType.string()).executes(x -> {
                            Instance i = instance(StringArgumentType.getString(x, "instance"));
                            ServerPlayer p = x.getSource().getPlayerOrException();
                            if (i == null || !i.prete()) return 0;
                            Pose pose = i.point("spectateur");
                            if (pose == null) pose = i.point("spawn_a");
                            if (pose == null) pose = new Pose(i.bornes().getCenter().x, i.bornes().minY + 2, i.bornes().getCenter().z, 0);
                            pose.teleporter(p, i.niveau());
                            return 1;
                        }).then(Commands.argument("point", StringArgumentType.greedyString()).executes(CommandesMoteur::tpPoint)))));
        if (!FMLEnvironment.production) matchs.then(BancMoteur.commandes());
        d.register(matchs);
    }

    /**
     * {@code /matchs arenes tp <instance> <point>} : sur un point nomme de la carte (premier de la liste, ou
     * {@code nom:rang} ; regard de la pose, horizontal), ou {@code aerien} (vue plongeante en diagonale depuis le coin
     * nord-ouest, tournee vers le centre) ou {@code dessus} (a la verticale du centre). Le joueur garde son mode de jeu
     * (passer en spectateur avant une vue aerienne).
     */
    private static int tpPoint(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        Instance i = instance(StringArgumentType.getString(x, "instance"));
        ServerPlayer p = x.getSource().getPlayerOrException();
        String nom = StringArgumentType.getString(x, "point");
        if (i == null || !i.prete()) {
            x.getSource().sendFailure(Component.translatableWithFallback("heralisz_matchs.commande.arenes.introuvable",
                    "Instance introuvable ou pas encore posée."));
            return 0;
        }
        if (nom.startsWith("rel ")) {
            // « rel x y z lacet tangage » : coordonnees relatives a l'origine de la structure (format des JSON de cartes)
            String[] v = nom.substring(4).trim().split("\\s+");
            try {
                double rx = Double.parseDouble(v[0]), ry = Double.parseDouble(v[1]), rz = Double.parseDouble(v[2]);
                float lacet = v.length > 3 ? Float.parseFloat(v[3]) : 0f, tangage = v.length > 4 ? Float.parseFloat(v[4]) : 0f;
                p.teleportTo(i.niveau(), i.origine().getX() + rx, i.origine().getY() + ry, i.origine().getZ() + rz, lacet, tangage);
                return 1;
            } catch (RuntimeException e) {
                x.getSource().sendFailure(Component.translatableWithFallback("heralisz_matchs.commande.arenes.format_rel",
                        "Format : rel x y z [lacet] [tangage]"));
                return 0;
            }
        }
        net.minecraft.world.phys.AABB b = i.bornes();
        double cx = b.getCenter().x, cz = b.getCenter().z, sol = b.minY;
        double taille = Math.max(b.getXsize(), b.getZsize());
        if (nom.equals("aerien") || nom.equals("dessus")) {
            double px, py, pz;
            if (nom.equals("dessus")) {
                px = cx;
                pz = cz;
                py = sol + taille * 0.78;
            } else {
                double d = taille * 0.48;
                px = cx - d;
                pz = cz - d;
                py = sol + taille * 0.45;
            }
            double dx = cx - px, dz = cz - pz, dy = sol + 2 - py;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = nom.equals("dessus") ? 90f : (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
            p.teleportTo(i.niveau(), px, py, pz, yaw, pitch);
            return 1;
        }
        int rang = 0;
        int deux = nom.indexOf(':');
        if (deux > 0) {
            try {
                rang = Integer.parseInt(nom.substring(deux + 1));
            } catch (NumberFormatException e) {
                rang = 0;
            }
            nom = nom.substring(0, deux);
        }
        java.util.List<Pose> l = i.points(nom);
        if (l.isEmpty()) {
            x.getSource().sendFailure(Component.translatableWithFallback("heralisz_matchs.commande.arenes.point_absent",
                    "Point « %s » absent de la carte %s.", nom, i.carte().id()));
            return 0;
        }
        l.get(Math.max(0, Math.min(rang, l.size() - 1))).teleporter(p, i.niveau());
        return 1;
    }

    /** Instance par id (« carte#index ») ou, a defaut, la derniere instance de la carte de ce nom. */
    private static Instance instance(String id) {
        Instance trouvee = null;
        for (Instance i : Arenes.instances()) {
            if (i.id().equals(id)) return i;
            if (i.carte().id().equals(id)) trouvee = i;
        }
        return trouvee;
    }

    /**
     * {@code /matchs combat etat|marquer|effacer <joueur>} (operateurs) : etat du tag, tag force (essai de l'indicateur, du
     * refus des commandes et de la mort a la deconnexion) ou tag retire. Reponse envoyee directement au joueur qui tape
     * la commande (la regle {@code sendCommandFeedback} du monde peut masquer les retours ordinaires).
     */
    private static int combat(CommandContext<CommandSourceStack> x, int action) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(x, "joueur");
        if (action == 1) CombatTag.marquer(p);
        else if (action == 2) CombatTag.effacer(p.getUUID());
        long cle = p.getPersistentData().getLong(CombatTag.CLE_JUSQUA);
        Component etat = CombatTag.estTague(p)
                ? Component.translatableWithFallback("heralisz_matchs.commande.combat.en_combat", "en combat, %s s restantes",
                String.valueOf((CombatTag.restantMs(p) + 999) / 1000))
                : MatchApi.enMatch(p) ? Component.translatableWithFallback("heralisz_matchs.commande.combat.en_match", "en match (jamais de tag)")
                : Component.translatableWithFallback("heralisz_matchs.commande.combat.libre", "libre");
        Component valeur = cle == 0 ? Component.translatableWithFallback("heralisz_matchs.commande.combat.absente", "absente")
                : Component.literal(String.valueOf(cle));
        retour(x, Component.translatableWithFallback("heralisz_matchs.commande.combat.ligne", "%s : %s ; %s = %s",
                p.getGameProfile().getName(), etat, CombatTag.CLE_JUSQUA, valeur));
        return CombatTag.estTague(p) ? 1 : 0;
    }

    /** Reponse a l'auteur de la commande ({@link Annonces#retour} : visible meme avec {@code sendCommandFeedback=false}). */
    private static void retour(CommandContext<CommandSourceStack> x, Component message) {
        Annonces.retour(x.getSource(), message);
    }

    private static int etatCoffre(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(x, "joueur");
        CompoundTag t = Coffres.lire(p.getUUID());
        if (t == null) {
            retour(x, Component.translatableWithFallback("heralisz_matchs.commande.coffre.aucun_depot", "%s : aucun dépôt.",
                    p.getGameProfile().getName()));
            return 0;
        }
        retour(x, Component.translatableWithFallback("heralisz_matchs.commande.coffre.depot", "%s : dépôt « %s » du %s, %s piles, dimension %s",
                p.getGameProfile().getName(), t.getString("raison"), String.valueOf(new java.util.Date(t.getLong("date"))),
                String.valueOf(t.getList("inventaire", 10).size()), t.getString("dim")));
        return 1;
    }
}
