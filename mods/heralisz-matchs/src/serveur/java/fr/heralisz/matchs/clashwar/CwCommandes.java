package fr.heralisz.matchs.clashwar;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.matchs.clashwar.bloc.MurTableau;
import fr.heralisz.matchs.clashwar.net.CwPaquets;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Arrays;

/**
 * Commandes du ClashWar. Joueurs : {@code /clashwar} (inscription pendant les inscriptions, sinon le tableau),
 * {@code /clashwar tableau}, {@code /clashwar retirer} (se retirer de l'inscription), {@code /clashwar quitter} (quitter
 * l'evenement et recuperer son Coffre, hors match). Operateurs (niveau 2) : {@code lancer [format] [minutes]},
 * {@code cloturer}, {@code annuler}, {@code recharger}, {@code mur <largeur>} (pose un mur-tableau sur la paroi visee),
 * {@code banc ...} (simulation du banc d'essai). Les retours passent par {@link Annonces#retour} (visibles meme quand le
 * monde a {@code sendCommandFeedback=false}, comme le serveur HeraliZ) ; les erreurs par {@code sendFailure}.
 */
public final class CwCommandes {
    private CwCommandes() {
    }

    public static void enregistrer(CommandDispatcher<CommandSourceStack> d, CommandBuildContext ctx) {
        d.register(Commands.literal("clashwar")
                .executes(c -> {
                    Tournois.ouvrirEcran(c.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("tableau").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    MatchsNet.versJoueur(p, new CwPaquets.TableauS2C(Tournois.vue()));
                    MatchsNet.versJoueur(p, new CwPaquets.OuvrirS2C("tableau"));
                    return 1;
                }))
                .then(Commands.literal("retirer").executes(c -> {
                    Tournois.retirer(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("quitter").executes(c -> {
                    Tournois.quitter(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("regarder")
                        .executes(c -> {
                            Tournois.regarder(c.getSource().getPlayerOrException(), -1, -1);
                            return 1;
                        })
                        .then(Commands.argument("tour", IntegerArgumentType.integer(0, 7))
                                .then(Commands.argument("match", IntegerArgumentType.integer(0, 31)).executes(c -> {
                                    Tournois.regarder(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "tour"),
                                            IntegerArgumentType.getInteger(c, "match"));
                                    return 1;
                                }))))
                .then(Commands.literal("salle").executes(c -> {
                    Tournois.revenirEnSalle(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("classement").executes(CwCommandes::classement))
                .then(Commands.literal("lancer").requires(s -> s.hasPermission(2))
                        .executes(c -> lancer(c, null, CwConfig.get().inscriptionMinutes))
                        .then(Commands.argument("format", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Format.values()).map(f -> f.nom), b))
                                .executes(c -> lancer(c, Format.parNom(StringArgumentType.getString(c, "format")), CwConfig.get().inscriptionMinutes))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 60))
                                        .executes(c -> lancer(c, Format.parNom(StringArgumentType.getString(c, "format")),
                                                IntegerArgumentType.getInteger(c, "minutes"))))))
                .then(Commands.literal("cloturer").requires(s -> s.hasPermission(2)).executes(c -> {
                    Tournoi t = Tournois.courant();
                    if (t == null || t.etat != Tournoi.Etat.INSCRIPTION || t.simulation) return erreur(c, "clashwar.cmd.rien");
                    t.finInscriptionMs = System.currentTimeMillis();
                    t.cloturer();
                    return 1;
                }))
                .then(Commands.literal("annuler").requires(s -> s.hasPermission(2)).executes(c -> {
                    Tournoi t = Tournois.courant();
                    if (t == null || !t.actif()) return erreur(c, "clashwar.cmd.rien");
                    t.annuler("admin");
                    return 1;
                }))
                .then(Commands.literal("recharger").requires(s -> s.hasPermission(2)).executes(c -> {
                    CwConfig.charger(c.getSource().getServer());
                    Tournois.marquerVue();
                    Annonces.retour(c.getSource(), Component.translatable("clashwar.cmd.recharge"));
                    return 1;
                }))
                .then(Commands.literal("mur").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("largeur", IntegerArgumentType.integer(2, MurTableau.LARGEUR_MAX)).executes(CwCommandes::mur)))
                .then(Commands.literal("banc").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("simuler").then(Commands.argument("equipes", IntegerArgumentType.integer(2, 32))
                                .executes(c -> simuler(c, Format.TROIS_TROIS))
                                .then(Commands.argument("format", StringArgumentType.word())
                                        .executes(c -> simuler(c, Format.parNom(StringArgumentType.getString(c, "format")))))))
                        .then(Commands.literal("avancer").executes(c -> avancer(c, 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
                                        .executes(c -> avancer(c, IntegerArgumentType.getInteger(c, "n")))))
                        .then(Commands.literal("direct").executes(c -> {
                            Tournoi t = Tournois.courant();
                            if (t == null || !t.simulation || !Simulation.direct(t)) return erreur(c, "clashwar.cmd.rien");
                            montrer(c, Component.translatable("clashwar.cmd.direct"));
                            return 1;
                        }))
                        .then(Commands.literal("mur").executes(CwCommandes::murBanc))
                        .then(Commands.literal("factices").then(Commands.argument("equipes", IntegerArgumentType.integer(1, 31))
                                .executes(CwCommandes::factices)))));
    }

    /**
     * Banc (client de dev seulement) : ajoute {@code n} equipes factices a l'edition dont les inscriptions sont ouvertes ;
     * elles entrent au tableau a la cloture ; sans vrai joueur, elles perdent leurs matchs par forfait (recherche et
     * destruction : seuls de vrais joueurs jouent).
     */
    private static int factices(CommandContext<CommandSourceStack> c) {
        if (net.minecraftforge.fml.loading.FMLEnvironment.production) return erreur(c, "clashwar.cmd.rien");
        Tournoi t = Tournois.courant();
        if (t == null || t.etat != Tournoi.Etat.INSCRIPTION || t.simulation) return erreur(c, "clashwar.cmd.rien");
        int n = IntegerArgumentType.getInteger(c, "equipes");
        t.facticesBanc.clear();
        t.mannequins.clear();
        t.facticesBanc.addAll(Simulation.equipesFactices(n, t.format, 1));
        for (Tableau.EquipeT e : t.facticesBanc) t.mannequins.addAll(e.joueurs);
        // tirage reproductible d'un banc a l'autre (tableau, cartes, resultats des rencontres factices)
        t.rnd.setSeed(20261001L + n);
        Annonces.retour(c.getSource(), Component.literal("[clashwar] banc : " + n + " equipes factices ajoutees a la cloture ("
                + t.mannequins.size() + " mannequins)"));
        return n;
    }

    /** Classement ClashWar des clans : les 10 meilleurs, et le rang du clan du joueur s'il est plus bas. */
    private static int classement(CommandContext<CommandSourceStack> c) {
        java.util.List<CwClassement.Ligne> l = CwClassement.meilleurs(10);
        if (l.isEmpty()) {
            Annonces.retour(c.getSource(), Component.translatable("clashwar.classement.vide"));
            return 0;
        }
        Annonces.retour(c.getSource(), Component.translatable("clashwar.classement.titre").withStyle(net.minecraft.ChatFormatting.BOLD));
        for (int i = 0; i < l.size(); i++) Annonces.retour(c.getSource(), ligne(i + 1, l.get(i)));
        if (c.getSource().getEntity() instanceof ServerPlayer p) {
            fr.heralisz.clans.api.ClansApi.clanDe(p.getUUID()).ifPresent(clan -> {
                java.util.Map.Entry<Integer, CwClassement.Ligne> r = CwClassement.rangDe(clan);
                if (r != null && r.getKey() > l.size()) Annonces.retour(c.getSource(), ligne(r.getKey(), r.getValue()));
            });
        }
        return l.size();
    }

    private static Component ligne(int rang, CwClassement.Ligne x) {
        Component tag = Component.literal("[" + x.tag + "]").withStyle(s -> s.withColor(x.couleur).withBold(true));
        return Component.translatable("clashwar.classement.ligne", String.valueOf(rang), tag, String.valueOf(x.titres),
                String.valueOf(x.matchsGagnes), String.valueOf(x.matchs), String.valueOf(x.eliminations), String.valueOf(x.morts),
                String.format(java.util.Locale.ROOT, "%.2f", x.kd()));
    }

    private static int lancer(CommandContext<CommandSourceStack> c, Format format, int minutes) {
        if (!Tournois.lancer(format, minutes)) return erreur(c, "clashwar.cmd.deja");
        Annonces.retour(c.getSource(), Component.translatable("clashwar.cmd.lance", minutes));
        return 1;
    }

    /**
     * Banc : tournoi simule. Corrige le 01/10 (RETOURS §5 : « ne fait rien ») : le serveur creait bien le tableau (journal
     * « [clashwar] tableau : 8 equipes... ») mais le retour passait par {@code sendSuccess}, masque quand la regle du
     * monde {@code sendCommandFeedback} est fausse (cas du monde de l'utilisateur), et rien n'ouvrait l'ecran. Le retour est
     * maintenant envoye directement au joueur et l'ecran {@code /clashwar tableau} s'ouvre avec la vue a jour.
     */
    private static int simuler(CommandContext<CommandSourceStack> c, Format format) {
        Tournoi actuel = Tournois.courant();
        if (actuel != null && actuel.actif() && !actuel.simulation) return erreur(c, "clashwar.cmd.deja");
        int n = IntegerArgumentType.getInteger(c, "equipes");
        Format f = format == null ? Format.TROIS_TROIS : format;
        Tournoi t = Simulation.creer(c.getSource().getServer(), n, f, 20261001L + n);
        Tournois.simulation(t);
        montrer(c, Component.translatable("clashwar.cmd.simulation", n, f.nom, t.tableau.places()));
        return 1;
    }

    private static int avancer(CommandContext<CommandSourceStack> c, int n) {
        Tournoi t = Tournois.courant();
        if (t == null || !t.simulation) return erreur(c, "clashwar.cmd.rien");
        int fait = Simulation.avancer(t, n);
        boolean fini = t.tableau != null && t.tableau.termine();
        montrer(c, Component.translatable(fini ? "clashwar.cmd.avance_fini" : "clashwar.cmd.avance", fait));
        return Math.max(1, fait);
    }

    /**
     * Retour d'une commande du banc : message direct au joueur ({@link Annonces#retour}, insensible a
     * {@code sendCommandFeedback}) et ecran du tableau ouvert avec la vue a jour ; depuis la console, retour ordinaire.
     */
    private static void montrer(CommandContext<CommandSourceStack> c, Component message) {
        Annonces.retour(c.getSource(), message);
        if (c.getSource().getEntity() instanceof ServerPlayer p) {
            MatchsNet.versJoueur(p, new CwPaquets.TableauS2C(Tournois.vue()));
            MatchsNet.versJoueur(p, new CwPaquets.OuvrirS2C("tableau"));
        }
    }

    /** Pose un mur sur la paroi visee (centre sur le point vise, hauteur = largeur / 2). */
    private static int mur(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int l = IntegerArgumentType.getInteger(c, "largeur"), h = Math.max(1, l / 2);
        HitResult hit = p.pick(32, 1f, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK || bh.getDirection().getAxis() == Direction.Axis.Y) {
            return erreur(c, "clashwar.cmd.mur_viser");
        }
        Direction face = bh.getDirection();
        BlockPos base = bh.getBlockPos().relative(face).relative(face.getClockWise(), l / 2);
        if (!MurTableau.poser(p.level(), base, face, l, h)) return erreur(c, "clashwar.cmd.mur_place");
        Annonces.retour(c.getSource(), Component.translatable("clashwar.cmd.mur", l, h));
        return 1;
    }

    /** Banc : mur 12 x 6 a 9 blocs devant le joueur, joueur replace face a lui. */
    private static int murBanc(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Direction regard = p.getDirection();
        Direction face = regard.getOpposite();
        BlockPos pieds = p.blockPosition();
        BlockPos base = pieds.relative(regard, 9).relative(face.getClockWise(), 6).above(1);
        if (!MurTableau.poser(p.level(), base, face, 12, 6)) return erreur(c, "clashwar.cmd.mur_place");
        p.teleportTo(p.serverLevel(), pieds.getX() + 0.5, pieds.getY(), pieds.getZ() + 0.5, regard.toYRot(), -12f);
        return 1;
    }

    private static int erreur(CommandContext<CommandSourceStack> c, String cle) {
        c.getSource().sendFailure(Component.translatable(cle));
        return 0;
    }
}
