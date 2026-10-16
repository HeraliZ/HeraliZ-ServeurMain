package fr.heralisz.clans.territory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.EtatZone;
import fr.heralisz.clans.registry.ClansRegistry;
import fr.heralisz.clans.server.ClanStore;
import fr.heralisz.clans.territory.supply.CaisseParachuteEntity;
import fr.heralisz.clans.territory.supply.Ravitaillement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Commandes op (niveau 2) : /territoire creer &lt;id&gt; &lt;rayon&gt; &lt;nom...&gt; (centre = ma position), supprimer &lt;id&gt;,
 * drapeau &lt;id&gt; (sur le bloc vise, sinon a mes pieds ; le centre de la zone suit le drapeau), deplacer &lt;id&gt; [rayon]
 * (centre = ma position, etat garde : reposer une zone de l'ancienne carte), liste (signale les zones hors de la bordure
 * du monde), info [id],
 * reset &lt;id&gt;, capturer &lt;id&gt; &lt;tag&gt;, ravitailler &lt;id&gt; (largage immediat), valeur &lt;id&gt; &lt;1-7&gt; (niveau de loot de la
 * ville : XP et points de territoire du clan qui la tient), recharger (zones.json + table de butin).
 * <p>Reponses : chaque commande repond, et directement au joueur ({@link #ok}) : un {@code sendSuccess} ne s'affiche
 * pas quand la regle {@code sendCommandFeedback} est a false (cas du monde de test de l'utilisateur : creer et drapeau
 * semblaient muets). Les erreurs passent par {@code sendFailure}, toujours affiche.
 * <p>Position : les zones n'existent que dans le monde principal ; un passager de vehicule est d'abord descendu
 * ({@link Territoires#descendre}) car sa position serveur est celle du vehicule, peut-etre figee loin de lui.
 */
@Mod.EventBusSubscriber(modid = HeraliszClans.MODID)
public final class TerritoireCommands {
    private TerritoireCommands() {
    }

    /** Couleurs des reponses : accent neutre (prefixe, liens), avertissement ambre, texte attenue. */
    private static final int ACCENT = 0x6FA8FF, AVERT = 0xE8B34A, ATTENUE = 0x8F95A0;

    private static final SuggestionProvider<CommandSourceStack> ZONES = (c, b) -> SharedSuggestionProvider.suggest(Territoires.ids(), b);
    private static final SuggestionProvider<CommandSourceStack> TAGS = (c, b) -> {
        ClanStore store = ClanStore.get();
        List<String> tags = new ArrayList<>();
        if (store != null) for (Clan cl : store.tous()) tags.add(cl.tag);
        return SharedSuggestionProvider.suggest(tags, b);
    };

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        register(e.getDispatcher());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lit(String s) {
        return Commands.literal(s);
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(lit("territoire").requires(s -> s.hasPermission(2))
                .then(lit("creer").then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("rayon", IntegerArgumentType.integer(5, 2000))
                                .then(Commands.argument("nom", StringArgumentType.greedyString()).executes(TerritoireCommands::creer)))))
                .then(lit("supprimer").then(Commands.argument("id", StringArgumentType.word()).suggests(ZONES).executes(TerritoireCommands::supprimer)))
                .then(lit("drapeau").then(Commands.argument("id", StringArgumentType.word()).suggests(ZONES).executes(TerritoireCommands::drapeau)))
                .then(lit("deplacer").then(Commands.argument("id", StringArgumentType.word()).suggests(ZONES).executes(c -> deplacer(c, false))
                        .then(Commands.argument("rayon", IntegerArgumentType.integer(5, 2000)).executes(c -> deplacer(c, true)))))
                .then(lit("liste").executes(TerritoireCommands::liste))
                .then(lit("info").executes(c -> info(c, false))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(ZONES).executes(c -> info(c, true))))
                .then(lit("reset").then(Commands.argument("id", StringArgumentType.word()).suggests(ZONES).executes(TerritoireCommands::reset)))
                .then(lit("capturer").then(Commands.argument("id", StringArgumentType.word()).suggests(ZONES)
                        .then(Commands.argument("tag", StringArgumentType.word()).suggests(TAGS).executes(TerritoireCommands::capturer))))
                .then(lit("ravitailler").then(Commands.argument("id", StringArgumentType.word()).suggests(ZONES).executes(TerritoireCommands::ravitailler)))
                .then(lit("valeur").then(Commands.argument("id", StringArgumentType.word()).suggests(ZONES)
                        .then(Commands.argument("valeur", IntegerArgumentType.integer(Zone.VALEUR_MIN, Zone.VALEUR_MAX)).executes(TerritoireCommands::valeur))))
                .then(lit("recharger").executes(TerritoireCommands::recharger)));
    }

    // ------------------------------------------------------------------ reponses
    private static MutableComponent tr(String cle, Object... args) {
        return Component.translatable(cle, args);
    }

    /** Message traduit chez le joueur, en francais a la console (cles ajoutees en 2.6.0). */
    private static MutableComponent trf(String cle, String francais, Object... args) {
        return Component.translatableWithFallback(cle, francais, args);
    }

    /**
     * « Territoires · » en couleur d'accent suivi du message. La racine est vide : le message n'herite pas de la
     * couleur du prefixe (les freres heritent du style de leur parent).
     */
    private static MutableComponent prefixe(Component c) {
        return Component.empty().append(tr("zone.cmd.prefixe").withStyle(s -> s.withColor(ACCENT))).append(c);
    }

    /**
     * Reponse de reussite : envoyee directement au joueur (visible meme avec sendCommandFeedback a false), a la source
     * sinon (console : sendSuccess journalise et previent les ops) ; ecrite dans le journal du serveur dans tous les cas.
     */
    private static void ok(CommandSourceStack src, Component c) {
        envoyer(src, prefixe(c));
    }

    /** Avertissement (ambre) : la commande a reussi mais quelque chose merite l'attention de l'op. */
    private static void avert(CommandSourceStack src, Component c) {
        envoyer(src, prefixe(c.copy().withStyle(s -> s.withColor(AVERT))));
    }

    /** Ligne de detail (liste, info) : sans prefixe, en retrait. */
    private static void ligne(CommandSourceStack src, Component c) {
        envoyer(src, Component.literal("  ").append(c));
    }

    private static void envoyer(CommandSourceStack src, Component c) {
        if (src.getEntity() instanceof ServerPlayer p) {
            p.sendSystemMessage(c);
            HeraliszClans.LOG.info("[clans] /territoire ({}) : {}", p.getGameProfile().getName(), c.getString());
        } else {
            src.sendSuccess(() -> c, true);
        }
    }

    /** Erreur (rouge, toujours affichee) ; renvoie 0 pour la commande. */
    private static int echec(CommandSourceStack src, Component c) {
        src.sendFailure(prefixe(c));
        return 0;
    }

    /** Commande cliquable (proposee dans la barre de chat) en couleur d'accent, soulignee. */
    private static MutableComponent lien(String cmd) {
        return Component.literal(cmd).withStyle(s -> s.withColor(ACCENT).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tr("zone.cmd.cliquer"))));
    }

    private static Territoires moteur(CommandContext<CommandSourceStack> ctx) {
        Territoires t = Territoires.get();
        if (t == null) echec(ctx.getSource(), tr("zone.cmd.indisponible"));
        return t;
    }

    private static Zone zone(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        Zone z = Territoires.zone(id);
        if (z == null) {
            echec(ctx.getSource(), Territoires.zones().isEmpty() ? tr("zone.cmd.aucune_zone")
                    : tr("zone.cmd.inconnue", id, String.join(", ", Territoires.ids())));
        }
        return z;
    }

    /** Les zones n'existent que dans le monde principal : refus explicite ailleurs (base, arene, Nether...). */
    private static boolean memeMonde(CommandSourceStack src, Territoires t, ServerPlayer p) {
        if (p.level() == t.monde()) return true;
        echec(src, tr("zone.cmd.mauvais_monde", p.level().dimension().location().toString()));
        return false;
    }

    /**
     * Passager d'un vehicule : sa position serveur est celle du vehicule, peut-etre figee loin de lui (train dont la
     * descente a ete refusee, puis /tp). On le fait descendre avant d'agir ; refus si le vehicule le retient.
     */
    private static boolean aPied(CommandSourceStack src, ServerPlayer p) {
        if (!p.isPassenger()) return true;
        Territoires.Descente d = Territoires.descendre(p);
        if (d.bloque()) {
            echec(src, tr("zone.cmd.vehicule_bloque", d.vehicule()));
            return false;
        }
        avert(src, tr("zone.cmd.descendu", d.vehicule(), p.getBlockX(), p.getBlockY(), p.getBlockZ()));
        return true;
    }

    // ------------------------------------------------------------------ creer
    private static int creer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Territoires t = moteur(ctx);
        if (t == null) return 0;
        String id = Zone.normaliserId(StringArgumentType.getString(ctx, "id"));
        if (!Zone.idValide(id)) return echec(src, tr("zone.cmd.id_invalide", id));
        Zone existante = Territoires.zone(id);
        if (existante != null) {
            return echec(src, tr("zone.cmd.existe", id, existante.nom, existante.x, existante.z, existante.rayon, id));
        }
        String nom = StringArgumentType.getString(ctx, "nom");
        if (!Zone.nomValide(nom)) return echec(src, tr("zone.cmd.nom_invalide", Zone.NOM_MAX));
        ServerPlayer p = src.getPlayerOrException();
        if (!memeMonde(src, t, p) || !aPied(src, p)) return 0;
        int rayon = IntegerArgumentType.getInteger(ctx, "rayon");
        Zone z = t.creer(id, rayon, nom, p.getBlockX(), p.getBlockZ());
        if (z == null) return echec(src, tr("zone.cmd.id_invalide", id));
        HeraliszClans.LOG.info("[clans] zone {} creee par {} : centre {}, {}, rayon {}", z.id, p.getGameProfile().getName(), z.x, z.z, z.rayon);
        ok(src, tr("zone.cmd.creee", z.nom, z.id, z.x, z.z, z.rayon));
        ligne(src, tr("zone.cmd.creee_suite", lien("/territoire drapeau " + z.id)).withStyle(s -> s.withColor(ATTENUE)));
        chevauchements(src, z);
        return 1;
    }

    /** Un avertissement par zone que celle-ci chevauche (permis : la zone au centre le plus proche l'emporte). */
    private static void chevauchements(CommandSourceStack src, Zone z) {
        for (Zone o : Territoires.zones()) {
            if (o == z) continue;
            double d = o.distance(z.x + 0.5, z.z + 0.5);
            if (d < o.rayon + z.rayon) avert(src, tr("zone.cmd.chevauche", o.nom, o.id, Math.round(d), o.rayon, z.rayon));
        }
    }

    // ------------------------------------------------------------------ drapeau
    /**
     * Pose le drapeau : sur le bloc vise, sinon a mes pieds ; FACING = regard du joueur = direction vers laquelle le
     * tissu part. Le centre de la zone suit le drapeau (voir {@link Territoires#poserDrapeau}) et la reponse le dit.
     */
    private static int drapeau(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Territoires t = moteur(ctx);
        Zone z = t == null ? null : zone(ctx);
        if (z == null) return 0;
        ServerPlayer p = src.getPlayerOrException();
        if (!memeMonde(src, t, p) || !aPied(src, p)) return 0;
        ServerLevel level = t.monde();
        Emplacement em = emplacement(t, level, p, z);
        if (em.erreur() != null) return echec(src, em.erreur());
        BlockPos pos = em.pos();
        Territoires.Pose pose = t.poserDrapeau(z, pos, p.getDirection());
        ok(src, tr("zone.cmd.drapeau_pose", z.nom, pos.getX(), pos.getY(), pos.getZ(), tr("zone.cmd.drapeau_source." + em.source())));
        if (pose.centreDeplace(z)) {
            long d = Math.round(Math.hypot(pose.ancienX() - z.x, pose.ancienZ() - z.z));
            ligne(src, tr("zone.cmd.centre_suit", z.x, z.z, pose.ancienX(), pose.ancienZ(), d, z.rayon));
            chevauchements(src, z);
        } else if (pose.centreSuit()) {
            ligne(src, tr("zone.cmd.zone_centree", z.rayon, z.demiHauteur()));
        } else {
            avert(src, tr("zone.cmd.centre_garde", z.x, z.z, Math.round(z.distance(pos.getX() + 0.5, pos.getZ() + 0.5)), z.rayon));
            if (!z.contientHorizontal(pos.getX() + 0.5, pos.getZ() + 0.5)) {
                avert(src, tr("zone.cmd.drapeau_hors_zone", Math.round(z.distance(pos.getX() + 0.5, pos.getZ() + 0.5)), z.rayon));
            }
        }
        BlockPos gene = obstacleMat(level, pos);
        if (gene != null) {
            avert(src, tr("zone.cmd.mat_gene", level.getBlockState(gene).getBlock().getName(), gene.getX(), gene.getY(), gene.getZ()));
        }
        return 1;
    }

    /** Emplacement retenu pour le drapeau (pos + source « vise » / « pieds » / « meme ») ou erreur a afficher. */
    private record Emplacement(@Nullable BlockPos pos, String source, @Nullable Component erreur) {
        static Emplacement ok(BlockPos pos, String source) {
            return new Emplacement(pos, source, null);
        }

        static Emplacement non(Component erreur) {
            return new Emplacement(null, "", erreur);
        }
    }

    /**
     * Ou poser le drapeau. Bloc vise a portee de main (celle du contour de bloc que le joueur voit) : le drapeau va
     * au-dessus de sa face superieure, quelle que soit la face visee (un bloc remplacable vise, herbe haute ou neige,
     * est remplace). Rien de vise : au sol sous les pieds du joueur (s'il vole, on descend jusqu'au sol, 64 blocs au
     * plus). Le drapeau actuel de la zone vise, ou sous les pieds : meme emplacement, nouvelle orientation.
     */
    private static Emplacement emplacement(Territoires t, ServerLevel level, ServerPlayer p, Zone zone) {
        HitResult hit = p.pick(p.getBlockReach(), 1f, false);
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos b = bh.getBlockPos();
            BlockState s = level.getBlockState(b);
            if (s.is(ClansRegistry.DRAPEAU.get())) return memeDrapeau(t, zone, b);
            return verifier(t, level, zone, s.canBeReplaced() ? b : b.above(), "vise");
        }
        BlockPos pos = p.blockPosition();
        if (level.getBlockState(pos).is(ClansRegistry.DRAPEAU.get())) return memeDrapeau(t, zone, pos); // debout sur le socle
        if (!level.getBlockState(pos).canBeReplaced()) pos = pos.above(); // pieds dans une dalle, un chemin...
        for (int i = 0; i < 64 && pos.getY() > level.getMinBuildHeight() && level.getBlockState(pos.below()).canBeReplaced(); i++) {
            pos = pos.below();
        }
        if (level.getBlockState(pos.below()).canBeReplaced()) return Emplacement.non(tr("zone.cmd.drapeau_sans_sol"));
        return verifier(t, level, zone, pos, "pieds");
    }

    /** Le bloc est un drapeau : celui de cette zone (ou orphelin) = reutilise, celui d'une autre zone = refus. */
    private static Emplacement memeDrapeau(Territoires t, Zone zone, BlockPos b) {
        Zone autre = t.zoneDuDrapeau(b);
        if (autre != null && autre != zone) return Emplacement.non(tr("zone.cmd.drapeau_autre_zone", autre.nom, autre.id));
        return Emplacement.ok(b, b.equals(zone.drapeau) ? "meme" : "vise");
    }

    /** La place doit etre libre (air, herbe, neige...) et posee sur quelque chose de solide. */
    private static Emplacement verifier(Territoires t, ServerLevel level, Zone zone, BlockPos pos, String source) {
        if (!level.isInWorldBounds(pos)) return Emplacement.non(tr("zone.cmd.drapeau_limites", pos.getX(), pos.getY(), pos.getZ()));
        BlockState ici = level.getBlockState(pos);
        if (ici.is(ClansRegistry.DRAPEAU.get())) return memeDrapeau(t, zone, pos);
        if (!ici.canBeReplaced()) {
            return Emplacement.non(tr("zone.cmd.drapeau_occupe", pos.getX(), pos.getY(), pos.getZ(), ici.getBlock().getName()));
        }
        BlockState sous = level.getBlockState(pos.below());
        if (sous.is(ClansRegistry.DRAPEAU.get())) return memeDrapeau(t, zone, pos.below());
        if (sous.canBeReplaced()) return Emplacement.non(tr("zone.cmd.drapeau_sans_appui", pos.getX(), pos.getY(), pos.getZ()));
        return Emplacement.ok(pos, source);
    }

    /** Premier bloc solide traverse par le mat (5 blocs : le socle et les 4 au-dessus), ou null. */
    @Nullable
    private static BlockPos obstacleMat(ServerLevel level, BlockPos pos) {
        for (int i = 1; i <= 4; i++) {
            BlockPos q = pos.above(i);
            if (!level.isInWorldBounds(q)) return null;
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) return q;
        }
        return null;
    }

    // ------------------------------------------------------------------ deplacer
    /**
     * Reposer une zone (de l'ancienne carte) sur la nouvelle : son centre vient a ma position, son rayon change si
     * donne ; proprietaire, protection et statistiques sont gardes. Un drapeau reste hors du nouveau cercle est retire.
     */
    private static int deplacer(CommandContext<CommandSourceStack> ctx, boolean avecRayon) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Territoires t = moteur(ctx);
        Zone z = t == null ? null : zone(ctx);
        if (z == null) return 0;
        ServerPlayer p = src.getPlayerOrException();
        if (!memeMonde(src, t, p) || !aPied(src, p)) return 0;
        int ax = z.x, az = z.z;
        boolean retire = t.deplacer(z, p.getBlockX(), p.getBlockZ(), avecRayon ? IntegerArgumentType.getInteger(ctx, "rayon") : 0);
        ok(src, trf("zone.cmd.deplacee", "Zone « %s » (%s) déplacée ici : centre %s, %s (avant : %s, %s), rayon %s blocs.",
                z.nom, z.id, z.x, z.z, ax, az, z.rayon));
        if (retire) {
            avert(src, trf("zone.cmd.deplacee_drapeau", "Son drapeau était hors du nouveau cercle : il est retiré. Reposez-le avec %s.",
                    lien("/territoire drapeau " + z.id)));
        } else if (z.drapeau == null) {
            ligne(src, tr("zone.cmd.creee_suite", lien("/territoire drapeau " + z.id)).withStyle(s -> s.withColor(ATTENUE)));
        }
        chevauchements(src, z);
        return 1;
    }

    // ------------------------------------------------------------------ valeur
    /** Valeur de la zone (niveau de loot de la ville, 1 a 7) : XP et points de territoire du clan qui la tient. */
    private static int valeur(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Territoires t = moteur(ctx);
        Zone z = t == null ? null : zone(ctx);
        if (z == null) return 0;
        z.valeur = IntegerArgumentType.getInteger(ctx, "valeur");
        t.sauvegarderZones();
        ok(src, tr("zone.cmd.valeur", z.nom, z.id, z.valeur));
        return 1;
    }

    // ------------------------------------------------------------------ liste et info
    private static int liste(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Territoires t = moteur(ctx);
        if (t == null) return 0;
        if (Territoires.zones().isEmpty()) {
            ok(src, tr("zone.cmd.aucune_zone"));
            return 1;
        }
        ServerPlayer p = src.getEntity() instanceof ServerPlayer sp ? sp : null;
        long now = System.currentTimeMillis();
        ok(src, tr("zone.cmd.liste_titre", Territoires.zones().size()));
        for (Zone z : Territoires.zones()) {
            EtatTerritoire e = t.etat(z);
            MutableComponent l = tr("zone.cmd.liste_ligne", z.id, z.nom, z.x, z.z, z.rayon, drapeauTexte(z), proprioTexte(e), etatTexte(t, z, e, now));
            if (!t.monde().getWorldBorder().isWithinBounds(z.x + 0.5, z.z + 0.5)) {
                // zone de l'ancienne carte : la carte du monde (touche M) ne la montre pas
                l.append(trf("zone.cmd.liste_hors_bordure", " · HORS DE LA BORDURE (à reposer : /territoire deplacer %s)", z.id)
                        .withStyle(s -> s.withColor(AVERT)));
            }
            if (p != null && p.level() == t.monde()) {
                boolean dedans = t.localisable(p) && t.zoneA(p.getX(), p.getY(), p.getZ()) == z;
                l.append(tr("zone.cmd.liste_vous", Math.round(z.distance(p.getX(), p.getZ())), tr(dedans ? "zone.cmd.dedans" : "zone.cmd.dehors")));
            }
            String cmd = "/territoire info " + z.id;
            ligne(src, l.withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(cmd)))));
        }
        return 1;
    }

    /**
     * Fiche d'une zone : geometrie, drapeau, proprietaire et etat, delais, et ou se trouve l'op par rapport a elle
     * (distance au centre, dy, dedans ou dehors et pourquoi). Sans id : la zone ou je suis, sinon la plus proche.
     */
    private static int info(CommandContext<CommandSourceStack> ctx, boolean avecId) {
        CommandSourceStack src = ctx.getSource();
        Territoires t = moteur(ctx);
        if (t == null) return 0;
        ServerPlayer p = src.getEntity() instanceof ServerPlayer sp ? sp : null;
        Zone z;
        if (avecId) {
            z = zone(ctx);
            if (z == null) return 0;
        } else {
            if (Territoires.zones().isEmpty()) return echec(src, tr("zone.cmd.aucune_zone"));
            if (p == null || p.level() != t.monde()) return echec(src, tr("zone.cmd.info_sans_id"));
            z = t.zoneA(p.getX(), p.getY(), p.getZ());
            if (z == null) {
                double best = Double.MAX_VALUE;
                for (Zone o : Territoires.zones()) {
                    double d = o.distance2(p.getX(), p.getZ());
                    if (d < best) {
                        best = d;
                        z = o;
                    }
                }
            }
        }
        long now = System.currentTimeMillis();
        EtatTerritoire e = t.etat(z);
        Integer ref = z.hauteurRef();
        if (ref == null) {
            ok(src, tr("zone.cmd.info_titre_sans_ref", z.nom, z.id, z.x, z.z, z.rayon));
        } else {
            ok(src, tr("zone.cmd.info_titre", z.nom, z.id, z.x, z.z, z.rayon, z.demiHauteur(), ref,
                    tr(z.drapeau != null ? "zone.cmd.info_ref.drapeau" : "zone.cmd.info_ref.surface")));
        }
        if (z.drapeau != null) {
            ligne(src, tr("zone.cmd.info_drapeau", z.drapeau.getX(), z.drapeau.getY(), z.drapeau.getZ()));
            if (z.centreFixe) {
                ligne(src, tr("zone.cmd.info_centre_fixe", Math.round(z.distance(z.drapeau.getX() + 0.5, z.drapeau.getZ() + 0.5))));
            }
        } else {
            ligne(src, tr("zone.cmd.info_sans_drapeau", lien("/territoire drapeau " + z.id)));
        }
        ligne(src, tr("zone.cmd.info_etat", proprioTexte(e), etatTexte(t, z, e, now), Math.round(e.progression * 100)));
        ligne(src, tr("zone.cmd.info_valeur", z.valeur));
        if (e.proprio != null) {
            Ravitaillement r = Territoires.ravitaillement();
            Component signal = r != null && r.enCours(z.id) ? tr("zone.cmd.info_signal.en_cours")
                    : e.prochainRavitaillement > now ? tr("zone.cmd.info_signal.dans", Territoires.duree(e.prochainRavitaillement - now))
                    : tr("zone.cmd.info_signal.possible");
            Component protection = e.protegeeJusque > now ? tr("zone.cmd.info_protection.encore", Territoires.duree(e.protegeeJusque - now))
                    : tr("zone.cmd.info_protection.aucune");
            ligne(src, tr("zone.cmd.info_delais", protection, signal));
        }
        if (p != null) {
            if (p.level() != t.monde()) {
                ligne(src, tr("zone.cmd.info_autre_monde", p.level().dimension().location().toString()));
            } else {
                long dist = Math.round(z.distance(p.getX(), p.getZ()));
                String dy = ref == null ? "?" : String.format(java.util.Locale.ROOT, "%+d", Math.round(p.getY() - ref));
                Component verdict;
                if (p.isPassenger()) verdict = tr("zone.cmd.verdict.vehicule");
                else if (!z.contientHorizontal(p.getX(), p.getZ())) verdict = tr("zone.cmd.verdict.trop_loin");
                else if (!z.contient(p.getX(), p.getY(), p.getZ())) verdict = tr("zone.cmd.verdict.hauteur");
                else if (t.zoneA(p.getX(), p.getY(), p.getZ()) != z) verdict = tr("zone.cmd.verdict.autre", t.zoneA(p.getX(), p.getY(), p.getZ()).nom);
                else verdict = tr("zone.cmd.verdict.dedans");
                ligne(src, tr("zone.cmd.info_vous", dist, z.rayon, dy, z.demiHauteur(), verdict));
            }
        }
        return 1;
    }

    private static String drapeauTexte(Zone z) {
        return z.drapeau == null ? "-" : z.drapeau.getX() + " " + z.drapeau.getY() + " " + z.drapeau.getZ();
    }

    private static Component proprioTexte(EtatTerritoire e) {
        ClanStore store = ClanStore.get();
        Clan c = store == null || e.proprio == null ? null : store.get(e.proprio);
        return c == null ? tr("zone.cmd.proprio_aucun") : Component.literal("[" + c.tag + "] " + c.nom);
    }

    /** Etat lisible pour un op (pas les libelles du HUD, ecrits pour le joueur present : « Votre zone »...). */
    private static Component etatTexte(Territoires t, Zone z, EtatTerritoire e, long now) {
        EtatZone ez = t.etatAffiche(z);
        ClanStore store = ClanStore.get();
        Clan capteur = store == null || e.capteur == null ? null : store.get(e.capteur);
        return switch (ez) {
            case CAPTURE -> tr("zone.cmd.etat.capture", Math.round(e.progression * 100), capteur == null ? "?" : capteur.tag);
            case CONTESTE -> tr("zone.cmd.etat.conteste");
            case LIMITE -> tr("zone.cmd.etat.limite");
            default -> e.protegeeJusque > now ? tr("zone.cmd.etat.protegee", Territoires.duree(e.protegeeJusque - now))
                    : e.proprio == null ? tr("zone.cmd.etat.libre") : tr("zone.cmd.etat.tenue");
        };
    }

    // ------------------------------------------------------------------ administration
    private static int supprimer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Territoires t = moteur(ctx);
        Zone z = t == null ? null : zone(ctx);
        if (z == null) return 0;
        String drapeau = drapeauTexte(z);
        boolean avecDrapeau = z.drapeau != null;
        t.supprimer(z);
        ok(src, avecDrapeau ? tr("zone.cmd.supprimee", z.nom, z.id, drapeau) : tr("zone.cmd.supprimee_sans_drapeau", z.nom, z.id));
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Territoires t = moteur(ctx);
        Zone z = t == null ? null : zone(ctx);
        if (z == null) return 0;
        t.reset(z);
        ok(src, tr("zone.cmd.reset", z.nom, z.id));
        return 1;
    }

    private static int capturer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Territoires t = moteur(ctx);
        Zone z = t == null ? null : zone(ctx);
        if (z == null) return 0;
        String tag = StringArgumentType.getString(ctx, "tag");
        ClanStore store = ClanStore.get();
        Clan c = store == null ? null : store.parTag(tag);
        if (c == null) return echec(src, tr("msg.clan_introuvable", tag));
        if (!t.capturerAdmin(z, c)) return echec(src, tr("zone.cmd.deja_tenue", z.nom, c.tag));
        EtatTerritoire e = t.etat(z);
        ok(src, tr("zone.cmd.capturee", z.nom, c.tag, Territoires.duree(e.protegeeJusque - System.currentTimeMillis())));
        return 1;
    }

    private static int ravitailler(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Territoires t = moteur(ctx);
        Zone z = t == null ? null : zone(ctx);
        if (z == null) return 0;
        BlockPos d = z.drapeau;
        if (d == null || !t.ravitaillerAdmin(z)) return echec(src, tr("zone.cmd.sans_drapeau", z.nom, z.id));
        // l'avion part a DISTANCE_APPROCHE du drapeau et largue a la verticale ; la caisse tombe de ALTITUDE - 3
        long largage = Math.round(Ravitaillement.DISTANCE_APPROCHE / Ravitaillement.VITESSE / 20);
        long sol = largage + Math.round(Math.max(0, Ravitaillement.ALTITUDE - 3 - d.getY()) / CaisseParachuteEntity.CHUTE / 20);
        ok(src, tr("zone.cmd.ravitaille", z.nom, d.getX(), d.getY(), d.getZ(), largage, sol));
        return 1;
    }

    private static int recharger(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Territoires t = moteur(ctx);
        if (t == null) return 0;
        int n = t.recharger();
        if (n < 0) return echec(src, tr("zone.cmd.recharge_echec"));
        ok(src, tr("zone.cmd.rechargee", n));
        return 1;
    }
}
