package fr.heralisz.clans.server;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.ClanLigne;
import fr.heralisz.clans.model.LienJoueur;
import fr.heralisz.clans.model.Membre;
import fr.heralisz.clans.model.Niveaux;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.model.Stats;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.territory.Territoires;
import fr.heralisz.economie.api.EconomieApi;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Commandes /clan, /cc et /clan admin (§1.5). Les commandes joueur passent par {@link ClanService} (memes regles que
 * l'interface) ; les commandes admin demandent le niveau 2. Suggestions : joueurs en ligne, membres du clan, tags.
 * <p>Reponses : toujours par {@link Reponses} (directement au joueur, visibles meme avec la regle
 * {@code sendCommandFeedback} a false, et journalisees) ; jamais {@code sendSuccess} directement.
 */
public final class ClanCommands {
    private ClanCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        register(e.getDispatcher());
    }

    // ------------------------------------------------------------------ aides
    private interface ActionJoueur {
        void run(ServerPlayer p, CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    private static int joueur(CommandContext<CommandSourceStack> ctx, ActionJoueur action) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        action.run(p, ctx);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lit(String s) {
        return Commands.literal(s);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> mot(String nom, SuggestionProvider<CommandSourceStack> sugg) {
        return Commands.argument(nom, StringArgumentType.word()).suggests(sugg);
    }

    private static final SuggestionProvider<CommandSourceStack> SUGG_EN_LIGNE = (ctx, b) ->
            SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), b);

    /** Noms des membres du clan de l'appelant. */
    private static final SuggestionProvider<CommandSourceStack> SUGG_MEMBRES = (ctx, b) -> {
        List<String> l = new ArrayList<>();
        ClanStore s = ClanStore.get();
        if (s != null && ctx.getSource().getEntity() instanceof ServerPlayer p) {
            Clan c = s.clanDe(p.getUUID());
            if (c != null) for (Membre m : c.membres.values()) if (!m.uuid.equals(p.getUUID())) l.add(m.dernierNom);
        }
        return SharedSuggestionProvider.suggest(l, b);
    };

    /** Tags des clans qui m'invitent. */
    private static final SuggestionProvider<CommandSourceStack> SUGG_INVITATIONS = (ctx, b) -> {
        List<String> l = new ArrayList<>();
        ClanStore s = ClanStore.get();
        if (s != null && ctx.getSource().getEntity() instanceof ServerPlayer p) {
            long now = System.currentTimeMillis();
            for (Clan c : s.tous()) {
                var i = c.invitationDe(p.getUUID());
                if (i != null && !i.expiree(now)) l.add(c.tag);
            }
        }
        return SharedSuggestionProvider.suggest(l, b);
    };

    /** Noms des permissions (minuscules, ordre de l'onglet). */
    private static final SuggestionProvider<CommandSourceStack> SUGG_PERMS = (ctx, b) -> {
        List<String> l = new ArrayList<>();
        for (Perm p : Perm.ORDRE) l.add(p.name().toLowerCase(Locale.ROOT));
        return SharedSuggestionProvider.suggest(l, b);
    };

    /** Etats d'un reglage individuel. */
    private static final SuggestionProvider<CommandSourceStack> SUGG_ETATS = (ctx, b) ->
            SharedSuggestionProvider.suggest(List.of("herite", "autorise", "refuse"), b);

    /** Tous les tags. */
    private static final SuggestionProvider<CommandSourceStack> SUGG_TAGS = (ctx, b) -> {
        List<String> l = new ArrayList<>();
        ClanStore s = ClanStore.get();
        if (s != null) for (Clan c : s.tous()) l.add(c.tag);
        return SharedSuggestionProvider.suggest(l, b);
    };

    /** Uuid d'un membre de mon clan par son nom (dernier nom connu, insensible a la casse), sinon joueur en ligne. */
    @Nullable
    private static UUID membreParNom(Clan c, String nom, MinecraftServer server) {
        for (Membre m : c.membres.values()) if (m.dernierNom.equalsIgnoreCase(nom)) return m.uuid;
        ServerPlayer p = server.getPlayerList().getPlayerByName(nom);
        return p == null ? null : p.getUUID();
    }

    /** Profil d'un joueur par son nom : en ligne, lien connu, cache des profils du serveur. */
    @Nullable
    private static GameProfile profil(MinecraftServer server, String nom) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(nom);
        if (p != null) return p.getGameProfile();
        ClanStore s = ClanStore.get();
        if (s != null) for (LienJoueur l : s.liens()) if (l.dernierNom.equalsIgnoreCase(nom)) return new GameProfile(l.joueur, l.dernierNom);
        if (server.getProfileCache() != null) {
            Optional<GameProfile> o = server.getProfileCache().get(nom);
            if (o.isPresent()) return o.get();
        }
        return null;
    }

    @Nullable
    private static Clan clanParTag(CommandContext<CommandSourceStack> ctx, String tag) {
        ClanStore s = ClanStore.get();
        Clan c = s == null ? null : s.parTag(tag);
        if (c == null) Reponses.echec(ctx.getSource(), Component.translatable("msg.clan_introuvable", tag));
        return c;
    }

    // ------------------------------------------------------------------ enregistrement
    static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> clan = lit("clan")
                // /clan : ouvre l'interface (sans clan : creation / invitations)
                .executes(ctx -> joueur(ctx, (p, c) -> ouvrir(p, "clan")))
                .then(lit("creer").executes(ctx -> joueur(ctx, (p, c) -> ouvrir(p, "creation"))))
                .then(lit("inviter").then(mot("joueur", SUGG_EN_LIGNE)
                        .executes(ctx -> joueur(ctx, (p, c) -> ClanService.inviter(p, StringArgumentType.getString(c, "joueur"))))))
                .then(lit("accepter").then(mot("tag", SUGG_INVITATIONS).executes(ctx -> reponse(ctx, true))))
                .then(lit("refuser").then(mot("tag", SUGG_INVITATIONS).executes(ctx -> reponse(ctx, false))))
                .then(lit("quitter").executes(ctx -> joueur(ctx, (p, c) -> ClanService.quitter(p))))
                .then(lit("exclure").then(mot("joueur", SUGG_MEMBRES).executes(ctx -> membre(ctx, Packets.ActionMembreC2S.Genre.EXCLURE))))
                .then(lit("promouvoir").then(mot("joueur", SUGG_MEMBRES).executes(ctx -> membre(ctx, Packets.ActionMembreC2S.Genre.PROMOUVOIR))))
                .then(lit("retrograder").then(mot("joueur", SUGG_MEMBRES).executes(ctx -> membre(ctx, Packets.ActionMembreC2S.Genre.RETROGRADER))))
                .then(lit("transferer").then(mot("joueur", SUGG_MEMBRES).executes(ctx -> membre(ctx, Packets.ActionMembreC2S.Genre.TRANSFERER))))
                // /clan perm <joueur> <perm> <herite|autorise|refuse> : reglage individuel (memes regles que l'editeur)
                .then(lit("perm").then(mot("joueur", SUGG_MEMBRES).then(mot("perm", SUGG_PERMS)
                        .then(mot("etat", SUGG_ETATS).executes(ClanCommands::permJoueur)))))
                .then(lit("dissoudre").executes(ctx -> joueur(ctx, (p, c) -> ClanService.dissoudre(p))))
                .then(lit("banque")
                        .then(lit("deposer").then(Commands.argument("montant", LongArgumentType.longArg(1))
                                .executes(ctx -> joueur(ctx, (p, c) -> ClanService.banque(p, true, LongArgumentType.getLong(c, "montant"))))))
                        .then(lit("retirer").then(Commands.argument("montant", LongArgumentType.longArg(1))
                                .executes(ctx -> joueur(ctx, (p, c) -> ClanService.banque(p, false, LongArgumentType.getLong(c, "montant")))))))
                .then(lit("coffre").executes(ctx -> joueur(ctx, (p, c) -> CoffreClan.ouvrir(p))))
                .then(lit("info").executes(ctx -> info(ctx, null))
                        .then(mot("tag", SUGG_TAGS).executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "tag")))))
                .then(lit("top").executes(ClanCommands::top))
                .then(admin());
        d.register(clan);

        // /cc [message] : chat de clan ; sans message = bascule du mode
        d.register(lit("cc")
                .executes(ctx -> joueur(ctx, (p, c) -> ClanService.chatBascule(p)))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> joueur(ctx, (p, c) -> ClanService.chat(p, StringArgumentType.getString(c, "message"))))));
    }

    private static int reponse(CommandContext<CommandSourceStack> ctx, boolean accepte) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String tag = StringArgumentType.getString(ctx, "tag");
        Clan c = clanParTag(ctx, tag);
        if (c == null) return 0;
        return ClanService.repondre(p, c.id, accepte) ? 1 : 0;
    }

    /** exclure / promouvoir / retrograder / transferer <joueur>. */
    private static int membre(CommandContext<CommandSourceStack> ctx, Packets.ActionMembreC2S.Genre genre) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ClanStore s = ClanStore.get();
        Clan c = s == null ? null : s.clanDe(p.getUUID());
        if (c == null) {
            ServerHandlers.erreur(p, "msg.pas_de_clan");
            return 0;
        }
        String nom = StringArgumentType.getString(ctx, "joueur");
        UUID cible = membreParNom(c, nom, ctx.getSource().getServer());
        if (cible == null || !c.estMembre(cible)) {
            ServerHandlers.erreur(p, "msg.pas_membre", nom);
            return 0;
        }
        boolean ok = switch (genre) {
            case EXCLURE -> ClanService.exclure(p, cible);
            case PROMOUVOIR -> ClanService.promouvoir(p, cible);
            case RETROGRADER -> ClanService.retrograder(p, cible);
            case TRANSFERER -> ClanService.transferer(p, cible);
        };
        return ok ? 1 : 0;
    }

    /** Message de reussite d'un reglage individuel : « Construire dans la base » : autorise pour Nyx. */
    private static Component permOk(Perm perm, Perm.Etat etat, String nom) {
        return Component.translatable("msg.perm_joueur_ok", Component.translatable(perm.libelle()),
                Component.translatable("msg.perm_etat." + etat.name().toLowerCase(Locale.ROOT)), nom);
    }

    /** /clan perm &lt;joueur&gt; &lt;perm&gt; &lt;etat&gt; : reglage individuel par un joueur (regles de ClanService.refusPermJoueur). */
    private static int permJoueur(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ClanStore s = ClanStore.get();
        Clan c = s == null ? null : s.clanDe(p.getUUID());
        if (c == null) {
            ServerHandlers.erreur(p, "msg.pas_de_clan");
            return 0;
        }
        String nom = StringArgumentType.getString(ctx, "joueur");
        UUID cible = membreParNom(c, nom, ctx.getSource().getServer());
        if (cible == null || !c.estMembre(cible)) {
            ServerHandlers.erreur(p, "msg.pas_membre", nom);
            return 0;
        }
        Perm perm = Perm.parNom(StringArgumentType.getString(ctx, "perm"));
        Perm.Etat etat = Perm.Etat.parNom(StringArgumentType.getString(ctx, "etat"));
        if (perm == null || etat == null) {
            ServerHandlers.erreur(p, "msg.perm_inconnue", StringArgumentType.getString(ctx, "perm") + " " + StringArgumentType.getString(ctx, "etat"));
            return 0;
        }
        String refus = ClanService.refusPermJoueur(c, p.getUUID(), cible, perm, etat);
        if (refus != null) {
            if (refus.equals("msg.perm_joueur_accorder")) ServerHandlers.erreur(p, refus, Component.translatable(perm.libelle()));
            else if (refus.equals("msg.pas_membre")) ServerHandlers.erreur(p, refus, nom);
            else ServerHandlers.erreur(p, refus);
            return 0;
        }
        ClanService.definirPermJoueur(p.getUUID(), c, cible, perm, etat);
        Reponses.ok(ctx.getSource(), permOk(perm, etat, c.membre(cible).dernierNom));
        return 1;
    }

    /** /clan admin perm &lt;tag&gt; &lt;joueur&gt; &lt;perm&gt; &lt;etat&gt; : reglage par le systeme (le chef reste non reglable). */
    private static int adminPerm(CommandContext<CommandSourceStack> ctx) {
        Clan c = clanParTag(ctx, StringArgumentType.getString(ctx, "tag"));
        if (c == null) return 0;
        String nom = StringArgumentType.getString(ctx, "joueur");
        UUID cible = membreParNom(c, nom, ctx.getSource().getServer());
        Perm perm = Perm.parNom(StringArgumentType.getString(ctx, "perm"));
        Perm.Etat etat = Perm.Etat.parNom(StringArgumentType.getString(ctx, "etat"));
        if (perm == null || etat == null) {
            return Reponses.echec(ctx.getSource(), Component.translatable("msg.perm_inconnue",
                    StringArgumentType.getString(ctx, "perm") + " " + StringArgumentType.getString(ctx, "etat")));
        }
        if (cible == null || !c.estMembre(cible)) {
            return Reponses.echec(ctx.getSource(), Component.translatable("msg.admin.pas_membre", nom, c.tag));
        }
        String refus = ClanService.refusPermJoueur(c, null, cible, perm, etat);
        if (refus != null) return Reponses.echec(ctx.getSource(), Component.translatable(refus, nom));
        ClanService.definirPermJoueur(null, c, cible, perm, etat);
        Reponses.ok(ctx.getSource(), ClanService.prefixe(c).append(permOk(perm, etat, c.membre(cible).dernierNom)));
        return 1;
    }

    /** /clan info [tag] : fiche d'un clan dans le chat. */
    private static int info(CommandContext<CommandSourceStack> ctx, @Nullable String tag) {
        ClanStore s = ClanStore.get();
        if (s == null) return 0;
        Clan c;
        if (tag == null) {
            c = ctx.getSource().getEntity() instanceof ServerPlayer p ? s.clanDe(p.getUUID()) : null;
            if (c == null) return Reponses.echec(ctx.getSource(), Component.translatable("msg.pas_de_clan"));
        } else {
            c = clanParTag(ctx, tag);
            if (c == null) return 0;
        }
        CommandSourceStack src = ctx.getSource();
        Stats t = c.totaux();
        int enLigne = ClanService.membresEnLigne(c).size();
        long now = System.currentTimeMillis();
        Reponses.ok(src, ClanService.prefixe(c).append(Component.translatable("msg.info.titre", c.nom, c.niveau, c.xp, Niveaux.xpPour(c.niveau))));
        Reponses.ligne(src, Component.translatable("msg.info.membres", ClanService.nomDe(c.chef), c.membres.size(), Niveaux.membresMax(c.niveau), enLigne));
        Reponses.ligne(src, Component.translatable("msg.info.stats", t.kills, String.format(Locale.FRANCE, "%.2f", t.kd()),
                Territoires.compterTenus(c.id), Niveaux.territoiresMax(c.niveau), ClanService.duree(now - c.creeLe)));
        if (!c.description.isEmpty()) {
            Reponses.ligne(src, Component.literal(c.description.replace('\n', ' ')).withStyle(st -> st.withColor(Reponses.ATTENUE)));
        }
        return 1;
    }

    /** /clan top : envoie le classement au client et ecrit le top 10 dans le chat. */
    private static int top(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<ClanLigne> l = ClanService.classement(20);
        if (src.getEntity() instanceof ServerPlayer p) ClansNet.toPlayer(p, new Packets.ClassementS2C(l));
        Reponses.ok(src, Component.translatable("msg.top.titre"));
        int n = 0;
        for (ClanLigne li : l) {
            if (n++ >= 10) break;
            Reponses.ligne(src, Component.translatable("msg.top.ligne", li.rang(), li.tag(), li.nom(), li.niveau(), li.membres(), li.kills(), li.territoires()));
        }
        if (l.isEmpty()) Reponses.ligne(src, Component.translatable("msg.top.vide").withStyle(st -> st.withColor(Reponses.ATTENUE)));
        return 1;
    }

    // ------------------------------------------------------------------ administration
    private static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return lit("admin").requires(s -> s.hasPermission(2))
                .then(lit("info").executes(ClanCommands::adminInfo))
                .then(lit("niveau").then(mot("tag", SUGG_TAGS)
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, Niveaux.MAX)).executes(ClanCommands::adminNiveau))))
                .then(lit("xp").then(mot("tag", SUGG_TAGS)
                        .then(Commands.argument("n", LongArgumentType.longArg(0)).executes(ClanCommands::adminXp))))
                .then(lit("supprimer").then(mot("tag", SUGG_TAGS).executes(ClanCommands::adminSupprimer)))
                .then(lit("forcer").then(mot("joueur", SUGG_EN_LIGNE)
                        .then(mot("tag", SUGG_TAGS).executes(ClanCommands::adminForcer))))
                .then(lit("argent").then(mot("joueur", SUGG_EN_LIGNE)
                        .then(Commands.argument("montant", LongArgumentType.longArg()).executes(ClanCommands::argent))))
                .then(lit("quetes").then(mot("tag", SUGG_TAGS).executes(ClanCommands::adminQuetes)))
                .then(lit("banniere").then(mot("tag", SUGG_TAGS).then(lit("reset").executes(ClanCommands::adminBanniereReset))))
                .then(lit("perm").then(mot("tag", SUGG_TAGS).then(Commands.argument("joueur", StringArgumentType.word())
                        .then(mot("perm", SUGG_PERMS).then(mot("etat", SUGG_ETATS).executes(ClanCommands::adminPerm))))))
                .then(lit("recharger").executes(ClanCommands::adminRecharger));
    }

    private static int adminInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ClanStore store = ClanStore.get();
        Economie eco = Economie.active();
        int n = store == null ? 0 : store.tous().size();
        Reponses.ok(src, Component.translatable("msg.admin.info", n, eco.nom(), eco.symbole()));
        Reponses.ligne(src, Component.translatable("msg.admin.info_quetes", Quetes.tailleJour(), Quetes.tailleSemaine(), Quetes.fichier().toString()));
        if (store != null) {
            for (Clan c : store.classement()) {
                Reponses.ligne(src, Component.translatableWithFallback("heralisz_clans.cmd.admin_ligne",
                        "[%s] %s · niv. %s (%s/%s XP) · %s membres · banque %s · zones %s", c.tag, c.nom, String.valueOf(c.niveau),
                        String.valueOf(c.xp), String.valueOf(Niveaux.xpPour(c.niveau)), String.valueOf(c.membres.size()), eco.format(c.banque),
                        String.valueOf(Territoires.compterTenus(c.id))));
            }
        }
        return 1;
    }

    private static int adminNiveau(CommandContext<CommandSourceStack> ctx) {
        Clan c = clanParTag(ctx, StringArgumentType.getString(ctx, "tag"));
        if (c == null) return 0;
        int n = IntegerArgumentType.getInteger(ctx, "n");
        ClanService.adminNiveau(c, n);
        Reponses.ok(ctx.getSource(), Component.translatable("msg.admin.niveau", c.tag, c.niveau));
        return 1;
    }

    private static int adminXp(CommandContext<CommandSourceStack> ctx) {
        Clan c = clanParTag(ctx, StringArgumentType.getString(ctx, "tag"));
        if (c == null) return 0;
        long n = LongArgumentType.getLong(ctx, "n");
        ClanService.ajouterXp(c, n, null);
        Sync.marquer(c);
        Reponses.ok(ctx.getSource(), Component.translatable("msg.admin.xp", n, c.tag, c.niveau, c.xp));
        return 1;
    }

    private static int adminSupprimer(CommandContext<CommandSourceStack> ctx) {
        Clan c = clanParTag(ctx, StringArgumentType.getString(ctx, "tag"));
        if (c == null) return 0;
        String tag = c.tag;
        long perdus = ClanService.dissoudre(c, "admin");
        Reponses.ok(ctx.getSource(), Component.translatable("msg.dissous", tag));
        // la dissolution a eu lieu : les jetons qui n'ont tenu sur aucun compte sont un avertissement, pas un echec
        if (perdus > 0) Reponses.avert(ctx.getSource(), Component.translatable("msg.admin.supprimer_perdus", Economie.active().format(perdus)));
        return 1;
    }

    private static int adminForcer(CommandContext<CommandSourceStack> ctx) {
        String nom = StringArgumentType.getString(ctx, "joueur");
        Clan c = clanParTag(ctx, StringArgumentType.getString(ctx, "tag"));
        if (c == null) return 0;
        GameProfile gp = profil(ctx.getSource().getServer(), nom);
        if (gp == null) return Reponses.echec(ctx.getSource(), Component.translatable("msg.joueur_introuvable", nom));
        if (!ClanService.adminForcer(gp.getId(), gp.getName() == null ? nom : gp.getName(), c)) {
            return Reponses.echec(ctx.getSource(), Component.translatable("msg.admin.forcer_chef", nom));
        }
        Reponses.ok(ctx.getSource(), Component.translatable("msg.admin.forcer", nom, c.tag));
        return 1;
    }

    /** /clan admin argent <joueur> <±n> : economie (tests). */
    private static int argent(CommandContext<CommandSourceStack> ctx) {
        String nom = StringArgumentType.getString(ctx, "joueur");
        long montant = LongArgumentType.getLong(ctx, "montant");
        GameProfile gp = profil(ctx.getSource().getServer(), nom);
        if (gp == null) return Reponses.echec(ctx.getSource(), Component.translatable("msg.joueur_introuvable", nom));
        Economie eco = Economie.active();
        if (!eco.disponible()) return Reponses.echec(ctx.getSource(), Component.translatable("msg.economie_indisponible"));
        boolean ok;
        if (montant >= 0) {
            eco.deposer(gp.getId(), montant, "clan:admin");
            ok = true;
        } else {
            ok = eco.retirer(gp.getId(), -montant, "clan:admin");
        }
        if (!ok) return Reponses.echec(ctx.getSource(), Component.translatable("msg.argent_insuffisant"));
        EconomieApi.sauvegarderMaintenant(); // operation d'argent : ecrite tout de suite
        String solde = eco.format(eco.solde(gp.getId()));
        Reponses.ok(ctx.getSource(), Component.translatable("msg.admin.argent", nom, solde, ""));
        return 1;
    }

    private static int adminQuetes(CommandContext<CommandSourceStack> ctx) {
        Clan c = clanParTag(ctx, StringArgumentType.getString(ctx, "tag"));
        if (c == null) return 0;
        Quetes.retirer(c, System.currentTimeMillis());
        ClanStore s = ClanStore.get();
        if (s != null) s.marquerDirty();
        Sync.marquer(c);
        Reponses.ok(ctx.getSource(), Component.translatable("msg.admin.quetes", c.tag, c.quetesJour.size(), c.quetesSemaine.size()));
        return 1;
    }

    /** /clan admin banniere &lt;tag&gt; reset : banniere par defaut (dessin offensant), delai du clan intact. */
    private static int adminBanniereReset(CommandContext<CommandSourceStack> ctx) {
        Clan c = clanParTag(ctx, StringArgumentType.getString(ctx, "tag"));
        if (c == null) return 0;
        if (!ClanService.adminBanniereReset(c, ctx.getSource().getTextName())) {
            return Reponses.echec(ctx.getSource(), Component.translatable("msg.admin.banniere_deja", c.tag));
        }
        Reponses.ok(ctx.getSource(), Component.translatable("msg.admin.banniere_reset", c.tag));
        return 1;
    }

    private static int adminRecharger(CommandContext<CommandSourceStack> ctx) {
        Niveaux.charger(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("heralisz_clans"));
        Quetes.recharger();
        ClanStore s = ClanStore.get();
        if (s != null) for (Clan c : s.tous()) Sync.marquer(c);
        Reponses.ok(ctx.getSource(), Component.translatable("msg.admin.recharge", Quetes.tailleJour(), Quetes.tailleSemaine()));
        return 1;
    }

    /** Ouvre un ecran chez le joueur (apres lui avoir renvoye son etat). */
    private static void ouvrir(ServerPlayer p, String ecran) {
        Sync.envoyerEtat(p);
        ClansNet.toPlayer(p, new Packets.OuvrirS2C(ecran));
    }
}
