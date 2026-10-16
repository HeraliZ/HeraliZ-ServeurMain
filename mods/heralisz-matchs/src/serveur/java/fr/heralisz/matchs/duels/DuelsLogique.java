package fr.heralisz.matchs.duels;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.matchs.duels.armes.Armurerie;
import fr.heralisz.matchs.duels.armes.Catalogue;
import fr.heralisz.matchs.duels.armes.TirsTacz;
import fr.heralisz.matchs.duels.classes.ClassePerso;
import fr.heralisz.matchs.duels.donnees.DuelsStore;
import fr.heralisz.matchs.duels.net.PaquetsDuels;
import fr.heralisz.matchs.duels.net.Vues;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Carte;
import fr.heralisz.matchs.moteur.api.Groupe;
import fr.heralisz.matchs.moteur.api.Groupes;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Partie serveur (docs/METHODE_CLIENT_SERVEUR.md) du module d'entree des duels (appele par {@code Modules}) : registres, paquets, commandes, ecouteurs.
 * <p>Commandes : {@code /duel} (ecran DUEL), {@code /duel <joueur>} (ecran avec la cible choisie), {@code /duel
 * accepter|refuser [id]} (dernier defi recu, ou celui d'id donne : boutons du chat), {@code /duel classes} (ecran CLASSES), {@code /duel quitter} (abandon),
 * {@code /duel stats [joueur]}, {@code /duel classement} ; op : {@code /duel entrainement <rd|mam>} (contre la cible
 * immobile), {@code /duel etat}, {@code /duel annuler}.
 */
public final class DuelsLogique {
    private DuelsLogique() {
    }

    /** Version du catalogue deja envoyee a chaque joueur. */
    private static final Map<UUID, Integer> CATALOGUE_ENVOYE = new HashMap<>();

    public static void construire(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(DuelsLogique::tick);
        MinecraftForge.EVENT_BUS.addListener(DuelsLogique::rechargement);
        MinecraftForge.EVENT_BUS.addListener(DuelsLogique::deconnexion);
        MinecraftForge.EVENT_BUS.addListener(DuelsLogique::demarrage);
        MinecraftForge.EVENT_BUS.addListener(DuelsLogique::arret);
        // bombe : balles arretees, joueur qui amorce ou pirate immobilise (sans tir)
        fr.heralisz.matchs.duels.bombe.ReglesBombe.construire();
    }


    // ------------------------------------------------------------------ ecouteurs
    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (e.getServer().getTickCount() % 20 == 0) {
            Defis.tick(e.getServer());
            DuelsStore.sauverSiBesoin(false);
        }
    }

    private static void rechargement(AddReloadListenerEvent e) {
        e.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager rm, ProfilerFiller pf) {
                return null;
            }

            @Override
            protected void apply(Void v, ResourceManager rm, ProfilerFiller pf) {
                // les index TaCZ / tactical sont relus au rechargement : le catalogue sera reconstruit a la demande
                Armurerie.invalider();
                CATALOGUE_ENVOYE.clear();
            }
        });
    }

    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Defis.oublier(p.server, p.getUUID());
        CATALOGUE_ENVOYE.remove(p.getUUID());
        fr.heralisz.matchs.duels.net.ReceptionPaquetsDuelsServeur.oublier(p.getUUID());
    }

    private static void demarrage(ServerStartedEvent e) {
        TirsTacz.brancher();
        fr.heralisz.matchs.duels.bombe.ReglesBombe.brancherTacz();
    }

    private static void arret(ServerStoppedEvent e) {
        DuelsStore.fermer();
        Armurerie.invalider();
        TirsTacz.vider();
        fr.heralisz.matchs.duels.bombe.ReglesBombe.vider();
        Defis.vider();
        CATALOGUE_ENVOYE.clear();
    }

    // ------------------------------------------------------------------ ecrans
    /** Envoie le catalogue au joueur s'il ne l'a pas deja dans cette version. */
    public static void assurerCatalogue(ServerPlayer p) {
        Catalogue c = Armurerie.catalogue();
        Integer v = CATALOGUE_ENVOYE.get(p.getUUID());
        if (v != null && v == c.version()) return;
        CATALOGUE_ENVOYE.put(p.getUUID(), c.version());
        MatchsNet.versJoueur(p, new PaquetsDuels.CatalogueS2C(c));
    }

    /** Ouvre l'ecran CLASSES. */
    public static void ouvrirClasses(ServerPlayer p) {
        assurerCatalogue(p);
        MatchsNet.versJoueur(p, new PaquetsDuels.ClassesS2C(DuelsStore.classes(p.getUUID(), Armurerie.catalogue()), DuelsStore.derniere(p.getUUID()), true));
    }

    /** Enregistre une classe recue du client (assainie contre le catalogue), puis renvoie la liste a jour. */
    public static void enregistrerClasse(ServerPlayer p, int n, ClassePerso c) {
        if (n < 0 || n >= ClassePerso.EMPLACEMENTS) return;
        Catalogue cat = Armurerie.catalogue();
        DuelsStore.definir(p.getUUID(), n, c.assainir(cat));
        MatchsNet.versJoueur(p, new PaquetsDuels.ClassesS2C(DuelsStore.classes(p.getUUID(), cat), DuelsStore.derniere(p.getUUID()), false));
    }

    /** Ouvre (ou met a jour) l'ecran DUEL. */
    public static void ouvrirEcranDuel(ServerPlayer p, @Nullable UUID preselection) {
        MatchsNet.versJoueur(p, new PaquetsDuels.EcranDuelS2C(ecran(p, preselection)));
    }

    /** Met a jour l'ecran DUEL du joueur s'il est ouvert (le client ignore sinon). */
    public static void rafraichirEcran(ServerPlayer p) {
        MatchsNet.versJoueur(p, new PaquetsDuels.EcranDuelS2C(ecran(p, null)));
    }

    private static Vues.EcranDuel ecran(ServerPlayer p, @Nullable UUID preselection) {
        MinecraftServer s = p.server;
        List<Vues.Candidat> l = new ArrayList<>();
        for (ServerPlayer q : s.getPlayerList().getPlayers()) {
            if (q == p) continue;
            Groupe g = Groupes.de(q.getUUID());
            l.add(new Vues.Candidat(q.getUUID(), q.getGameProfile().getName(), g != null && g.taille() == 2, MatchApi.enMatch(q),
                    Groupes.ensemble(p.getUUID(), q.getUUID())));
        }
        l.sort((x, y) -> x.nom().compareToIgnoreCase(y.nom()));
        Groupe moi = Groupes.de(p.getUUID());
        List<Vues.CarteVue> cartes = new ArrayList<>();
        for (Carte c : Defis.cartes()) {
            cartes.add(new Vues.CarteVue(c.id(), c.nomFr(), c.nomEn(), Defis.liste(c, "modes", List.of(Vues.MODE_RD, Vues.MODE_MAM)),
                    Defis.liste(c, "formats", List.of(Vues.FORMAT_1V1, Vues.FORMAT_2V2))));
        }
        DuelsStore.Stats st = DuelsStore.statsSiConnues(p.getUUID());
        Vues.StatsVue sv = st == null ? Vues.StatsVue.ZERO : new Vues.StatsVue(st.victoires, st.defaites, st.eliminations, st.morts, st.tetes, st.parties);
        Defis.Defi in = Defis.entrant(p.getUUID()), out = Defis.sortant(p.getUUID());
        return new Vues.EcranDuel(l, moi == null ? 1 : moi.taille(), cartes, sv, in == null ? null : Defis.vue(s, in), out == null ? null : Defis.vue(s, out),
                preselection);
    }

    // ------------------------------------------------------------------ commandes
    public static void commandes(CommandDispatcher<CommandSourceStack> d, CommandBuildContext c) {
        d.register(Commands.literal("duel")
                .executes(ctx -> {
                    ouvrirEcranDuel(ctx.getSource().getPlayerOrException(), null);
                    return 1;
                })
                // sans id : le dernier defi recu ; avec id : ce defi-la (boutons du chat, plusieurs defis en attente)
                .then(Commands.literal("accepter").executes(ctx -> {
                    Defis.repondreDernier(ctx.getSource().getPlayerOrException(), true);
                    return 1;
                }).then(Commands.argument("defi", IntegerArgumentType.integer(1)).executes(ctx -> {
                    Defis.repondreCommande(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "defi"), true);
                    return 1;
                })))
                .then(Commands.literal("refuser").executes(ctx -> {
                    Defis.repondreDernier(ctx.getSource().getPlayerOrException(), false);
                    return 1;
                }).then(Commands.argument("defi", IntegerArgumentType.integer(1)).executes(ctx -> {
                    Defis.repondreCommande(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "defi"), false);
                    return 1;
                })))
                .then(Commands.literal("classes").executes(ctx -> {
                    ouvrirClasses(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("quitter").executes(DuelsLogique::quitter))
                .then(Commands.literal("stats")
                        .executes(ctx -> stats(ctx, ctx.getSource().getPlayerOrException().getGameProfile().getName()))
                        .then(Commands.argument("joueur", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), b))
                                .executes(ctx -> stats(ctx, StringArgumentType.getString(ctx, "joueur")))))
                .then(Commands.literal("classement").executes(DuelsLogique::classement))
                .then(Commands.literal("entrainement").requires(s -> s.hasPermission(2))
                        .executes(ctx -> entrainement(ctx, Vues.MODE_MAM))
                        .then(Commands.literal("rd").executes(ctx -> entrainement(ctx, Vues.MODE_RD)))
                        .then(Commands.literal("mam").executes(ctx -> entrainement(ctx, Vues.MODE_MAM))))
                .then(Commands.literal("etat").requires(s -> s.hasPermission(2)).executes(DuelsLogique::etat))
                .then(Commands.literal("annuler").requires(s -> s.hasPermission(2)).executes(DuelsLogique::annulerOp))
                .then(Commands.argument("joueur", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ServerPlayer cible = EntityArgument.getPlayer(ctx, "joueur");
                    ouvrirEcranDuel(p, cible.getUUID());
                    return 1;
                })));
        // banc d'essai (client de dev) : joueurs simules pour les defis et les vraies eliminations
        if (!net.minecraftforge.fml.loading.FMLEnvironment.production) fr.heralisz.matchs.duels.dev.BotsDuel.commandes(d);
    }

    private static int quitter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Partie partie = MatchApi.partieDe(p);
        if (!(partie instanceof DuelPartie d)) {
            Annonces.chat(p, Component.translatable("duel.erreur.pas_en_duel"));
            return 0;
        }
        d.abandonner(p);
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> ctx, String nom) {
        Map.Entry<UUID, DuelsStore.Stats> e = DuelsStore.statsParNom(nom);
        if (e == null) {
            Annonces.retour(ctx.getSource(), Component.translatable("duel.stats.aucune", nom).withStyle(ChatFormatting.GRAY));
            return 0;
        }
        DuelsStore.Stats s = e.getValue();
        Annonces.retour(ctx.getSource(), Component.translatable("duel.stats.ligne", s.nom, s.victoires, s.defaites, s.eliminations, s.morts,
                String.format(Locale.ROOT, "%.2f", s.ratio()), s.tetes));
        return 1;
    }

    private static int classement(CommandContext<CommandSourceStack> ctx) {
        List<Map.Entry<UUID, DuelsStore.Stats>> l = DuelsStore.meilleurs(10);
        Annonces.retour(ctx.getSource(), Component.translatable("duel.classement.titre").withStyle(ChatFormatting.BOLD));
        int rang = 1;
        for (Map.Entry<UUID, DuelsStore.Stats> e : l) {
            DuelsStore.Stats s = e.getValue();
            int r = rang++;
            Annonces.retour(ctx.getSource(), Component.translatable("duel.classement.ligne", r, s.nom, s.victoires, s.defaites,
                    String.format(Locale.ROOT, "%.2f", s.ratio())));
        }
        return l.size();
    }

    private static int entrainement(CommandContext<CommandSourceStack> ctx, String mode) throws CommandSyntaxException {
        Defis.entrainement(ctx.getSource().getPlayerOrException(), mode);
        return 1;
    }

    private static int etat(CommandContext<CommandSourceStack> ctx) {
        int n = 0;
        for (Partie p : MatchApi.parties()) {
            if (!(p instanceof DuelPartie d)) continue;
            n++;
            Annonces.retour(ctx.getSource(), Component.translatableWithFallback("heralisz_matchs.commande.duels.instance", "%s instance %s",
                    String.valueOf(d), d.instance() == null ? "-" : d.instance().id()));
        }
        int total = n;
        Annonces.retour(ctx.getSource(), Component.translatableWithFallback("heralisz_matchs.commande.duels.etat",
                "[duels] %s duel(s), catalogue v%s (%s armes)", String.valueOf(total), String.valueOf(Armurerie.catalogue().version()),
                String.valueOf(Armurerie.catalogue().armes().size())));
        return n;
    }

    private static int annulerOp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Partie partie = MatchApi.partieDe(p);
        if (partie instanceof DuelPartie d) {
            d.annuler(Component.translatable("duel.annule.op"));
            return 1;
        }
        return 0;
    }
}
