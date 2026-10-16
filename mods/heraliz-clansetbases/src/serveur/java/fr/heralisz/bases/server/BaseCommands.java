package fr.heralisz.bases.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.api.BasesApi;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.model.FenetreVue;
import fr.heralisz.bases.model.Plan;
import fr.heralisz.bases.monde.Chantier;
import fr.heralisz.bases.monde.Grille;
import fr.heralisz.bases.monde.Modele;
import fr.heralisz.bases.monde.ParcellesData;
import fr.heralisz.bases.network.Packets;
import fr.heralisz.clans.api.ClanInfo;
import fr.heralisz.clans.api.ClansApi;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Commandes (BASES_SPEC §3, §7) :
 * <pre>
 * /base                          ouvre l'ecran BASE
 * /base entrer [nord|sud]        entree differee (10 s immobile, descente de tout vehicule)
 * /base sortir                   retour a la position d'entree (10 s immobile, refus en combat)
 * /base creer                    cree la base (BASE_GERER, banque du clan)
 * /base debloquer &lt;salle&gt;        debloque une salle (BASE_GERER, banque du clan)
 * /base piller [tag]             ecran PILLER, ou pillage de la base [tag] (module pillage)
 * /base admin info [tag] | liste | teleporter &lt;tag&gt; [nord|sud] | creer &lt;tag&gt; (gratuit) | debloquer &lt;tag&gt; &lt;salle&gt;
 *             | reinitialiser &lt;tag&gt; | fenetre ouvrir|fermer|auto | recharger
 *             | voir &lt;tag&gt; &lt;salle|hall|sas_nord|sas_sud|cloison_&lt;salle&gt;&gt;   (cadrage du banc)
 *             | combat &lt;secondes&gt;   (banc : cle heralisz:combat_jusqua de HeraliZ Matchs, 0 = effacee)
 *             | match 0|1   (banc : drapeau heralisz:en_match de HeraliZ Matchs)                         (op niveau 2)
 * </pre>
 * Toutes les reponses passent par {@link Reponses} (visibles meme avec {@code sendCommandFeedback=false}).
 * Le module pillage ajoute ses sous-commandes par {@link fr.heralisz.bases.api.PillageHooks#commandes} (ex. coups).
 */
public final class BaseCommands {
    private BaseCommands() {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private static final SuggestionProvider<CommandSourceStack> TAGS = (ctx, b) -> {
        List<String> l = new ArrayList<>();
        for (ClanInfo c : ClansApi.tous()) l.add(c.tag());
        return SharedSuggestionProvider.suggest(l, b);
    };

    private static final SuggestionProvider<CommandSourceStack> TAGS_AVEC_BASE = (ctx, b) -> {
        List<String> l = new ArrayList<>();
        for (Base x : BasesApi.bases()) l.add(ClansApi.infos(x.clan).map(ClanInfo::tag).orElse(x.tag));
        return SharedSuggestionProvider.suggest(l, b);
    };

    private static final SuggestionProvider<CommandSourceStack> SALLES = (ctx, b) -> {
        Modele m = Modele.get();
        List<String> l = new ArrayList<>();
        if (m != null) for (Plan.Salle s : m.plan.salles) if (!s.depart()) l.add(s.id());
        return SharedSuggestionProvider.suggest(l, b);
    };

    private static final SuggestionProvider<CommandSourceStack> ZONES = (ctx, b) -> {
        Modele m = Modele.get();
        List<String> l = new ArrayList<>(List.of("hall"));
        if (m != null) {
            for (Plan.Salle s : m.plan.salles) {
                l.add(s.id());
                if (!s.cloison().isEmpty()) l.add("cloison_" + s.id());
            }
            for (String e : m.plan.entrees.keySet()) l.add("sas_" + e);
        }
        return SharedSuggestionProvider.suggest(l, b);
    };

    private static final SuggestionProvider<CommandSourceStack> ENTREES = (ctx, b) -> {
        Modele m = Modele.get();
        return SharedSuggestionProvider.suggest(m == null ? List.of("nord", "sud") : m.plan.entrees.keySet(), b);
    };

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        register(e.getDispatcher());
    }

    private static ServerPlayer joueur(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> base = Commands.literal("base")
                .executes(c -> {
                    ServerHandlers.ouvrir(joueur(c), Packets.OuvrirS2C.Ecran.BASE);
                    return 1;
                })
                .then(Commands.literal("entrer")
                        .executes(c -> BaseService.entrer(joueur(c), null) ? 1 : 0)
                        .then(Commands.argument("entree", StringArgumentType.word()).suggests(ENTREES)
                                .executes(c -> BaseService.entrer(joueur(c), StringArgumentType.getString(c, "entree")) ? 1 : 0)))
                .then(Commands.literal("sortir").executes(c -> {
                    BaseService.sortir(joueur(c));
                    return 1;
                }))
                .then(Commands.literal("creer").executes(c -> {
                    ServerPlayer p = joueur(c);
                    boolean ok = BaseService.creer(p);
                    Sync.etat(p);
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("debloquer")
                        .then(Commands.argument("salle", StringArgumentType.word()).suggests(SALLES)
                                .executes(c -> {
                                    ServerPlayer p = joueur(c);
                                    boolean ok = BaseService.debloquer(p, StringArgumentType.getString(c, "salle"));
                                    Sync.etat(p);
                                    return ok ? 1 : 0;
                                })))
                .then(Commands.literal("piller")
                        .executes(c -> {
                            ServerPlayer p = joueur(c);
                            ServerHandlers.ouvrir(p, Packets.OuvrirS2C.Ecran.PILLER);
                            BasesApi.pillage().piller(p, null);
                            return 1;
                        })
                        .then(Commands.argument("tag", StringArgumentType.word()).suggests(TAGS_AVEC_BASE)
                                .executes(c -> {
                                    BasesApi.pillage().piller(joueur(c), StringArgumentType.getString(c, "tag"));
                                    return 1;
                                })));

        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin").requires(s -> s.hasPermission(2))
                .then(Commands.literal("info")
                        .executes(c -> infoGlobale(c.getSource()))
                        .then(Commands.argument("tag", StringArgumentType.word()).suggests(TAGS_AVEC_BASE)
                                .executes(c -> info(c.getSource(), StringArgumentType.getString(c, "tag")))))
                .then(Commands.literal("liste").executes(c -> liste(c.getSource())))
                .then(Commands.literal("teleporter")
                        .then(Commands.argument("tag", StringArgumentType.word()).suggests(TAGS_AVEC_BASE)
                                .executes(c -> teleporter(c, null))
                                .then(Commands.argument("entree", StringArgumentType.word()).suggests(ENTREES)
                                        .executes(c -> teleporter(c, StringArgumentType.getString(c, "entree"))))))
                .then(Commands.literal("voir")
                        .then(Commands.argument("tag", StringArgumentType.word()).suggests(TAGS_AVEC_BASE)
                                .then(Commands.argument("zone", StringArgumentType.word()).suggests(ZONES)
                                        .executes(c -> voir(c, StringArgumentType.getString(c, "zone"))))))
                .then(Commands.literal("creer")
                        .then(Commands.argument("tag", StringArgumentType.word()).suggests(TAGS)
                                .executes(c -> creerAdmin(c.getSource(), StringArgumentType.getString(c, "tag")))))
                .then(Commands.literal("debloquer")
                        .then(Commands.argument("tag", StringArgumentType.word()).suggests(TAGS_AVEC_BASE)
                                .then(Commands.argument("salle", StringArgumentType.word()).suggests(SALLES)
                                        .executes(c -> debloquerAdmin(c.getSource(), StringArgumentType.getString(c, "tag"),
                                                StringArgumentType.getString(c, "salle"))))))
                .then(Commands.literal("reinitialiser")
                        .then(Commands.argument("tag", StringArgumentType.word()).suggests(TAGS_AVEC_BASE)
                                .executes(c -> reinitialiser(c.getSource(), StringArgumentType.getString(c, "tag")))))
                .then(Commands.literal("fenetre")
                        .then(Commands.literal("ouvrir").executes(c -> fenetre(c.getSource(), FenetreVue.Mode.OUVERTE)))
                        .then(Commands.literal("fermer").executes(c -> fenetre(c.getSource(), FenetreVue.Mode.FERMEE)))
                        .then(Commands.literal("auto").executes(c -> fenetre(c.getSource(), FenetreVue.Mode.AUTO))))
                .then(Commands.literal("recharger").executes(c -> recharger(c.getSource())))
                // tests : pose (ou efface, 0) la cle de combat de HeraliZ Matchs (heralisz:combat_jusqua) sur l'executant
                .then(Commands.literal("combat")
                        .then(Commands.argument("secondes", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3600))
                                .executes(c -> combat(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "secondes")))))
                // tests : pose (1) ou retire (0) le drapeau heralisz:en_match de HeraliZ Matchs sur l'executant
                .then(Commands.literal("match")
                        .then(Commands.argument("actif", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1))
                                .executes(c -> match(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "actif") == 1))));

        try {
            BasesApi.pillage().commandes(base, admin);
        } catch (RuntimeException ex) {
            HeraliszBases.LOG.error("[bases] module pillage : commandes en erreur", ex);
        }
        d.register(base.then(admin));
    }

    // ------------------------------------------------------------------ admin
    @Nullable
    private static Base trouver(CommandSourceStack src, String tag) {
        Optional<ClanInfo> ci = ClansApi.parTag(tag);
        Base b = ci.isPresent() ? BasesApi.base(ci.get().id()) : null;
        if (b == null) {
            BasesStore s = BasesStore.get();
            b = s == null ? null : s.parTagConnu(tag);
        }
        if (b == null) Reponses.erreur(src, Component.translatable("cmd.heralisz_bases.base_inconnue", tag));
        return b;
    }

    private static int infoGlobale(CommandSourceStack src) {
        BasesStore s = BasesStore.get();
        Modele m = Modele.get();
        Reponses.ok(src, Component.translatable("cmd.heralisz_bases.info_globale", s == null ? 0 : s.bases().size(),
                m == null ? Modele.erreurTexte() : m.plan.tx + " x " + m.plan.ty + " x " + m.plan.tz + ", " + m.nombre(),
                Fenetre.vue().mode().name(), ouiNon(Fenetre.ouverte()), Fenetre.jour()));
        return 1;
    }

    private static int info(CommandSourceStack src, String tag) {
        Base b = trouver(src, tag);
        if (b == null) return 0;
        BlockPos o = BaseService.origine(b);
        int presents = BaseService.presents(src.getServer(), b).size();
        String salles = String.join(", ", BaseService.ouvertes(b));
        String pillages = b.pillages.isEmpty() ? "-" : b.pillages.get(0).tagAttaquant + " " + DATE.format(Instant.ofEpochMilli(b.pillages.get(0).debut))
                + " " + b.pillages.get(0).issue;
        Reponses.ok(src, Component.translatable("cmd.heralisz_bases.info", b.tag, b.etat.name(), b.parcelle,
                o.getX() + " " + o.getY() + " " + o.getZ(), salles, presents, DATE.format(Instant.ofEpochMilli(b.creeLe)), pillages));
        return 1;
    }

    private static int liste(CommandSourceStack src) {
        BasesStore s = BasesStore.get();
        if (s == null || s.bases().isEmpty()) {
            Reponses.ok(src, Component.translatable("cmd.heralisz_bases.aucune"));
            return 0;
        }
        for (Base b : s.bases()) {
            String tag = ClansApi.infos(b.clan).map(ClanInfo::tag).orElse(b.tag);
            Reponses.ok(src, Component.translatableWithFallback("heralisz_bases.cmd.liste_ligne", "[%s] parcelle %s · %s · %s salle(s) achetée(s)",
                    tag, String.valueOf(b.parcelle), b.etat.name(), String.valueOf(b.salles.size())));
        }
        return s.bases().size();
    }

    private static int teleporter(CommandContext<CommandSourceStack> c, @Nullable String entree) throws CommandSyntaxException {
        ServerPlayer p = joueur(c);
        Base b = trouver(c.getSource(), StringArgumentType.getString(c, "tag"));
        if (b == null) return 0;
        if (!b.prete()) {
            Reponses.erreur(c.getSource(), Component.translatable("msg.heralisz_bases.base_occupee"));
            return 0;
        }
        Modele m = Modele.get();
        String id = entree != null ? entree : m == null || m.plan.entreeParDefaut() == null ? "nord" : m.plan.entreeParDefaut().id();
        return BaseService.teleporterDans(p, b, id) ? 1 : 0;
    }

    /**
     * Banc / admin : teleporte dans une zone (salle, hall, sas_nord...) a un coin de son volume, le regard vers le centre
     * (cadrage des captures). Position de retour gardee comme pour une entree.
     */
    private static int voir(CommandContext<CommandSourceStack> c, String zone) throws CommandSyntaxException {
        ServerPlayer p = joueur(c);
        Base b = trouver(c.getSource(), StringArgumentType.getString(c, "tag"));
        Modele m = Modele.get();
        ServerLevel level = Grille.niveau(c.getSource().getServer());
        if (b == null || m == null || level == null) return 0;
        fr.heralisz.bases.model.Boite boite = null;
        if (zone.startsWith("cloison_")) return voirCloison(c, p, b, m, level, zone.substring(8));
        Plan.Salle s = m.plan.salle(zone);
        if (s != null && !s.interieur().isEmpty()) boite = s.interieur().get(0);
        else if (zone.startsWith("sas_") && m.plan.entree(zone.substring(4)) != null) boite = m.plan.entree(zone.substring(4)).sas();
        else if (zone.equals("hall")) boite = !m.plan.hall.isEmpty() ? m.plan.hall.get(0) : m.plan.interieur.isEmpty() ? null : m.plan.interieur.get(0);
        if (boite == null) {
            Reponses.erreur(c.getSource(), Component.translatable("cmd.heralisz_bases.zone_inconnue", zone));
            return 0;
        }
        Descente.faireDescendre(p, "admin voir");
        BasesStore st = BasesStore.get();
        if (st != null && !Grille.estBunkers(p.level())) {
            st.retour(p.getUUID(), new fr.heralisz.bases.model.Retour(p.level().dimension().location().toString(), p.getX(), p.getY(),
                    p.getZ(), p.getYRot(), p.getXRot()));
        }
        BlockPos o = BaseService.origine(b);
        double x = o.getX() + boite.x0() + 0.7, y = o.getY() + boite.y0(), z = o.getZ() + boite.z0() + 0.7;
        double cx = o.getX() + (boite.x0() + boite.x1() + 1) / 2.0, cz = o.getZ() + (boite.z0() + boite.z1() + 1) / 2.0;
        float yaw = (float) Math.toDegrees(Math.atan2(-(cx - x), cz - z));
        p.teleportTo(level, x, y, z, yaw, 12f);
        return 1;
    }

    /** Face a la cloison d'une salle, 6 blocs devant elle cote parties communes (cadrage du deblocage). */
    private static int voirCloison(CommandContext<CommandSourceStack> c, ServerPlayer p, Base b, Modele m, ServerLevel level, String id) {
        Plan.Salle s = m.plan.salle(id);
        if (s == null || s.cloison().isEmpty()) {
            Reponses.erreur(c.getSource(), Component.translatable("cmd.heralisz_bases.zone_inconnue", "cloison_" + id));
            return 0;
        }
        fr.heralisz.bases.model.Boite k = s.cloison().get(0);
        double cx = (k.x0() + k.x1() + 1) / 2.0, cz = (k.z0() + k.z1() + 1) / 2.0;
        int[][] dirs = {{6, 0}, {-6, 0}, {0, 6}, {0, -6}};
        double px = cx, pz = cz;
        for (int[] d : dirs) {
            if (fr.heralisz.bases.model.Boite.dans(m.plan.interieur, (int) Math.floor(cx + d[0]), k.y0(), (int) Math.floor(cz + d[1]))) {
                px = cx + d[0];
                pz = cz + d[1];
                break;
            }
        }
        Descente.faireDescendre(p, "admin voir");
        BasesStore st = BasesStore.get();
        if (st != null && !Grille.estBunkers(p.level())) {
            st.retour(p.getUUID(), new fr.heralisz.bases.model.Retour(p.level().dimension().location().toString(), p.getX(), p.getY(),
                    p.getZ(), p.getYRot(), p.getXRot()));
        }
        BlockPos o = BaseService.origine(b);
        float yaw = (float) Math.toDegrees(Math.atan2(-(cx - px), cz - pz));
        p.teleportTo(level, o.getX() + px, o.getY() + k.y0(), o.getZ() + pz, yaw, 5f);
        return 1;
    }

    private static int creerAdmin(CommandSourceStack src, String tag) {
        if (!fr.heralisz.bases.BasesConfig.CREATION.get()) {
            Reponses.erreur(src, Component.translatableWithFallback("msg.heralisz_bases.creation_coupee_admin",
                    "Création de bunkers coupée (réglage [bases] creation de heralisz_bases-common.toml) : bases CRac à la place."));
            return 0;
        }
        Optional<ClanInfo> ci = ClansApi.parTag(tag);
        if (ci.isEmpty()) {
            Reponses.erreur(src, Component.translatable("cmd.heralisz_bases.clan_inconnu", tag));
            return 0;
        }
        if (BasesApi.base(ci.get().id()) != null) {
            Reponses.erreur(src, Component.translatable("msg.heralisz_bases.deja_base"));
            return 0;
        }
        ServerLevel level = Grille.niveau(src.getServer());
        if (Modele.get() == null || level == null) {
            Reponses.erreur(src, Component.translatable(Modele.get() == null ? "msg.heralisz_bases.modele_absent" : "msg.heralisz_bases.dimension_absente"));
            return 0;
        }
        Base b = BaseService.installer(level, ci.get().id(), ci.get().tag());
        ClansApi.journal(ci.get().id(), "evenement.heralisz_bases.base_creee", src.getTextName());
        Reponses.ok(src, Component.translatable("cmd.heralisz_bases.creee", ci.get().tag(), b.parcelle));
        Sync.clan(b.clan);
        return 1;
    }

    private static int debloquerAdmin(CommandSourceStack src, String tag, String salle) {
        Base b = trouver(src, tag);
        if (b == null) return 0;
        if (!BaseService.debloquerAdmin(b, salle, src.getTextName())) {
            Reponses.erreur(src, Component.translatable("cmd.heralisz_bases.deblocage_impossible", salle));
            return 0;
        }
        Reponses.ok(src, Component.translatable("cmd.heralisz_bases.debloquee", salle, b.tag));
        return 1;
    }

    private static int reinitialiser(CommandSourceStack src, String tag) {
        Base b = trouver(src, tag);
        if (b == null) return 0;
        if (!BaseService.reinitialiser(src.getServer(), b)) {
            Reponses.erreur(src, Component.translatable("cmd.heralisz_bases.occupee"));
            return 0;
        }
        Reponses.ok(src, Component.translatable("cmd.heralisz_bases.reinitialisee", b.tag));
        return 1;
    }

    private static int fenetre(CommandSourceStack src, FenetreVue.Mode m) {
        Fenetre.forcer(m);
        Reponses.ok(src, Component.translatable("cmd.heralisz_bases.fenetre", m.name(), ouiNon(Fenetre.ouverte())));
        return 1;
    }

    /** Banc : cle de combat de HeraliZ Matchs posee pour {@code s} secondes (0 = effacee). */
    private static int combat(CommandContext<CommandSourceStack> c, int s) throws CommandSyntaxException {
        ServerPlayer p = joueur(c);
        if (s <= 0) p.getPersistentData().remove(fr.heralisz.bases.combat.CombatLocal.CLE_COMBAT);
        else p.getPersistentData().putLong(fr.heralisz.bases.combat.CombatLocal.CLE_COMBAT, System.currentTimeMillis() + s * 1000L);
        Reponses.ok(c.getSource(), Component.literal("heralisz:combat_jusqua = " + (s <= 0 ? "-" : "+" + s + " s")));
        return 1;
    }

    /** Banc : drapeau « en match » de HeraliZ Matchs pose ou retire sur l'executant. */
    private static int match(CommandContext<CommandSourceStack> c, boolean actif) throws CommandSyntaxException {
        ServerPlayer p = joueur(c);
        if (actif) p.getPersistentData().putBoolean(ClansApi.EN_MATCH, true);
        else p.getPersistentData().remove(ClansApi.EN_MATCH);
        Reponses.ok(c.getSource(), Component.literal(ClansApi.EN_MATCH + " = ").append(actif ? ouiNon(true) : Component.literal("-")));
        return 1;
    }

    private static int recharger(CommandSourceStack src) {
        boolean ok = Modele.charger(src.getServer());
        ServerLevel level = Grille.niveau(src.getServer());
        // bases en attente du modele : on relance leur pose
        if (ok && level != null) {
            for (Base b : BasesApi.bases()) {
                if (b.etat == Base.Etat.POSE && !Chantier.occupe(b.parcelle)) BaseService.lancerPose(level, b);
            }
            ParcellesData.de(level);
        }
        if (ok) Reponses.ok(src, Component.translatable("cmd.heralisz_bases.recharge", Modele.erreurTexte()));
        else Reponses.erreur(src, Component.translatable("cmd.heralisz_bases.recharge_erreur", Modele.erreurTexte()));
        return ok ? 1 : 0;
    }

    /** « oui » / « non » dans la langue du joueur (reponses des commandes d'administration). */
    private static Component ouiNon(boolean v) {
        return v ? Component.translatableWithFallback("heralisz_bases.cmd.oui", "oui")
                : Component.translatableWithFallback("heralisz_bases.cmd.non", "non");
    }
}
