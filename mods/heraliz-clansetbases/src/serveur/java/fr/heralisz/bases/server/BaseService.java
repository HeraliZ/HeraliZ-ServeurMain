package fr.heralisz.bases.server;

import fr.heralisz.bases.BasesConfig;
import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.api.BasesApi;
import fr.heralisz.bases.combat.CombatLocal;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.model.Boite;
import fr.heralisz.bases.model.Plan;
import fr.heralisz.bases.model.Retour;
import fr.heralisz.bases.monde.Chantier;
import fr.heralisz.bases.monde.Grille;
import fr.heralisz.bases.monde.Modele;
import fr.heralisz.bases.monde.ParcellesData;
import fr.heralisz.bases.network.BasesNet;
import fr.heralisz.bases.network.Packets;
import fr.heralisz.bases.registry.BasesRegistry;
import fr.heralisz.bases.registry.Sons;
import fr.heralisz.clans.api.ClanInfo;
import fr.heralisz.clans.api.ClansApi;
import fr.heralisz.clans.model.Perm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Regles du coeur des bases (BASES_SPEC §2, §3) : creation payee par la banque du clan, deblocage des salles, entree et
 * sortie, ejection, dissolution, reinitialisation, reprise au demarrage. Une methode par regle ; {@code boolean} = action
 * faite, les messages sont deja envoyes. Fil serveur uniquement.
 */
public final class BaseService {
    private BaseService() {
    }

    /** Bases dont les cloisons achetees ont ete verifiees depuis le demarrage (reprise d'une animation interrompue). */
    private static final Set<UUID> CLOISONS_VERIFIEES = new HashSet<>();
    /** Dernier refus discret par joueur (1 par seconde au plus). */
    private static final Map<UUID, Long> DERNIER_REFUS = new HashMap<>();

    // =================================================================== lecture
    @Nullable
    public static BasesStore store() {
        return BasesStore.get();
    }

    @Nullable
    public static Modele modele() {
        return Modele.get();
    }

    /** Base qui contient ce point du monde des bases (null ailleurs). */
    @Nullable
    public static Base baseA(@Nullable Level level, BlockPos pos) {
        BasesStore s = store();
        if (s == null || !Grille.estBunkers(level)) return null;
        int p = Grille.parcelle(pos.getX(), pos.getZ());
        return p < 0 ? null : s.parParcelle(p);
    }

    /** Coin (0, 0, 0) de la structure de la base. */
    public static BlockPos origine(Base b) {
        Modele m = modele();
        return m == null ? Grille.origine(b.parcelle, 0, 0) : Grille.origine(b.parcelle, m.plan.tx, m.plan.tz);
    }

    /** Ids des salles ouvertes (depart + achetees). */
    public static Set<String> ouvertes(Base b) {
        Set<String> s = new HashSet<>(b.salles);
        Modele m = modele();
        if (m != null) for (Plan.Salle x : m.plan.salles) if (x.depart()) s.add(x.id());
        return s;
    }

    /** Vrai si le point est dans le volume constructible de la base (salles ouvertes, hall, couloirs). */
    public static boolean constructible(Base b, BlockPos pos) {
        Modele m = modele();
        if (m == null) return false;
        BlockPos o = origine(b);
        return m.plan.constructible(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ(), ouvertes(b));
    }

    /**
     * Cle du refus d'une action hors du volume constructible : « salle verrouillee » si le point est dans une salle pas
     * encore achetee (ou sa cloison, un bloc au-dessus du sol compris), sinon {@code defaut} (coque, hors des salles).
     */
    public static String refusHorsVolume(Base b, BlockPos pos, String defaut) {
        Modele m = modele();
        if (m == null) return defaut;
        BlockPos o = origine(b);
        Set<String> ouv = ouvertes(b);
        int x = pos.getX() - o.getX(), y = pos.getY() - o.getY(), z = pos.getZ() - o.getZ();
        // sol d'une salle fermee : le bloc vise est juste sous son volume
        if (m.plan.salleFermee(x, y, z, ouv) != null || m.plan.salleFermee(x, y + 1, z, ouv) != null) {
            return "refus.heralisz_bases.salle_verrouillee";
        }
        return defaut;
    }

    /** Zone du point pour le HUD (id de salle, hall, couloir, sas_nord...), « » si inconnue. */
    public static String zone(Base b, BlockPos pos) {
        Modele m = modele();
        if (m == null) return "";
        BlockPos o = origine(b);
        String z = m.plan.zone(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ());
        return z == null ? "" : z;
    }

    /** Cout effectif du deblocage d'une salle : config {@code couts_salles} par rang, sinon le cout du JSON. */
    public static long cout(Plan.Salle s) {
        if (s.depart()) return 0;
        Modele m = modele();
        if (m == null) return s.cout();
        int rang = m.plan.sallesAVendre().indexOf(s);
        List<? extends Long> l = BasesConfig.COUTS_SALLES.get();
        if (rang >= 0 && rang < l.size()) {
            Object v = l.get(rang);
            return v instanceof Number n ? n.longValue() : s.cout();
        }
        return s.cout();
    }

    /** Op (niveau 2) en creatif ou spectateur : ignore toutes les regles de la dimension. */
    public static boolean estAdmin(ServerPlayer p) {
        GameType g = p.gameMode.getGameModeForPlayer();
        return p.hasPermissions(2) && (g == GameType.CREATIVE || g == GameType.SPECTATOR);
    }

    /** Clan du joueur (vide sans clan). */
    public static Optional<UUID> clanDe(ServerPlayer p) {
        return ClansApi.clanDe(p.getUUID());
    }

    /** Vrai si le joueur est membre du clan proprietaire et a la permission. */
    public static boolean membreAvec(ServerPlayer p, Base b, Perm perm) {
        return ClansApi.aPerm(p.getUUID(), b.clan, perm);
    }

    public static boolean estMembre(ServerPlayer p, Base b) {
        return clanDe(p).map(b.clan::equals).orElse(false);
    }

    // =================================================================== messages
    /** Erreur : chat rouge + notice ERREUR (toast). */
    public static void erreur(ServerPlayer p, String cle, Object... args) {
        p.sendSystemMessage(Component.translatable(cle, args).withStyle(s -> s.withColor(0xFF6B6B)));
        BasesNet.toPlayer(p, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.ERREUR, cle, chaines(args), Sons.REFUS.getId().getPath()));
    }

    /** Information : chat simple. */
    public static void info(ServerPlayer p, String cle, Object... args) {
        p.sendSystemMessage(Component.translatable(cle, args));
    }

    /** Refus discret d'une action dans la base (1 par seconde au plus, pas de chat). */
    public static void refus(ServerPlayer p, String cle) {
        HeraliszBases.LOG.debug("[bases] refus {} : {}", p.getGameProfile().getName(), cle);
        long now = System.currentTimeMillis();
        Long d = DERNIER_REFUS.get(p.getUUID());
        if (d != null && now - d < 1000) return;
        DERNIER_REFUS.put(p.getUUID(), now);
        BasesNet.toPlayer(p, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.REFUS, cle, List.of(), ""));
    }

    /**
     * Arguments d'une notice en texte. Un composant traduisible sans parametre (libelle de permission) part en cle : le
     * client le traduit dans la langue du joueur (le serveur dedie n'a pas les fichiers de langue).
     */
    static List<String> chaines(Object... args) {
        List<String> l = new ArrayList<>(args.length);
        for (Object o : args) {
            if (o instanceof Component c) {
                l.add(c.getContents() instanceof TranslatableContents t && t.getArgs().length == 0 && c.getSiblings().isEmpty()
                        ? t.getKey() : c.getString());
            } else {
                l.add(String.valueOf(o));
            }
        }
        return l;
    }

    /** Notice + ligne de chat aux membres en ligne d'un clan. */
    public static void aClan(UUID clan, Packets.NoticeS2C notice, @Nullable Component chat) {
        Optional<ClanInfo> ci = ClansApi.infos(clan);
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (ci.isEmpty() || server == null) return;
        for (UUID u : ci.get().enLigne()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) continue;
            BasesNet.toPlayer(p, notice);
            if (chat != null) p.sendSystemMessage(chat);
        }
    }

    // =================================================================== creation
    /** Creation de la base du clan du joueur (BASE_GERER, cout pris dans la banque du clan). */
    public static boolean creer(ServerPlayer p) {
        BasesStore s = store();
        Modele m = modele();
        ServerLevel level = Grille.niveau(p.server);
        if (s == null) return false;
        if (m == null) {
            erreur(p, "msg.heralisz_bases.modele_absent");
            return false;
        }
        if (level == null) {
            erreur(p, "msg.heralisz_bases.dimension_absente");
            return false;
        }
        Optional<UUID> clan = clanDe(p);
        if (clan.isEmpty()) {
            erreur(p, "msg.heralisz_bases.pas_de_clan");
            return false;
        }
        if (!ClansApi.aPerm(p.getUUID(), Perm.BASE_GERER)) {
            erreur(p, "msg.heralisz_bases.permission", Component.translatable("perm.base_gerer"));
            return false;
        }
        if (s.base(clan.get()) != null) {
            erreur(p, "msg.heralisz_bases.deja_base");
            return false;
        }
        long cout = BasesConfig.COUT_CREATION.get();
        if (cout > 0 && ClansApi.soldeBanque(clan.get()) < cout) {
            erreur(p, "msg.heralisz_bases.fonds", format(cout), format(ClansApi.soldeBanque(clan.get())));
            return false;
        }
        if (cout > 0 && !ClansApi.debiterBanque(clan.get(), cout, "base_creation", p.getUUID())) {
            erreur(p, "msg.heralisz_bases.fonds", format(cout), format(ClansApi.soldeBanque(clan.get())));
            return false;
        }
        String tag = ClansApi.infos(clan.get()).map(ClanInfo::tag).orElse("");
        Base b = installer(level, clan.get(), tag);
        ClansApi.journal(clan.get(), "evenement.heralisz_bases.base_creee", p.getGameProfile().getName());
        aClan(b.clan, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.INFO, "notice.heralisz_bases.pose_en_cours", List.of(), ""),
                Component.translatable("msg.heralisz_bases.creation", p.getGameProfile().getName(), format(cout)));
        HeraliszBases.LOG.info("[bases] base creee pour [{}] par {} (parcelle {}, {} hérals)", tag, p.getGameProfile().getName(), b.parcelle, cout);
        Sync.clan(b.clan);
        return true;
    }

    /** Cree l'entree de la base et lance la pose (sans paiement : admin, banc). */
    public static Base installer(ServerLevel level, UUID clan, String tag) {
        BasesStore s = store();
        ParcellesData data = ParcellesData.de(level);
        int parcelle = s.parcelleLibre(i -> data.occupee(i) || Chantier.occupe(i));
        Base b = new Base(clan, tag, parcelle, System.currentTimeMillis());
        b.etat = Base.Etat.POSE;
        s.ajouter(b);
        s.sauvegarderMaintenant();
        lancerPose(level, b);
        return b;
    }

    /** Pose (ou repose) le bunker de la base ; a la fin : PRETE, clan prevenu. */
    static void lancerPose(ServerLevel level, Base b) {
        Modele m = modele();
        if (m == null) {
            HeraliszBases.LOG.warn("[bases] pose de [{}] reportee : {}", b.tag, Modele.erreur());
            return;
        }
        if (Chantier.occupe(b.parcelle)) return;
        b.etat = Base.Etat.POSE;
        ParcellesData.de(level).poser(b.parcelle, b.clan);
        List<Plan.Salle> achetees = new ArrayList<>();
        for (Plan.Salle sa : m.plan.salles) if (!sa.depart() && b.salleAchetee(sa.id())) achetees.add(sa);
        long debut = System.currentTimeMillis();
        Chantier.ajouter(new Chantier.Pose(level, b.parcelle, m, achetees, t -> {
            BasesStore s = store();
            if (s == null || s.base(b.clan) != b) return;
            if (b.etat == Base.Etat.EFFACEMENT) {
                // dissolution ou reinitialisation demandee pendant la pose : l'effacement suit
                lancerEffacement(level, b);
                return;
            }
            b.etat = Base.Etat.PRETE;
            CLOISONS_VERIFIEES.add(b.clan);
            s.marquerDirty();
            HeraliszBases.LOG.info("[bases] bunker de [{}] pose en {} ms ({} blocs, parcelle {})", b.tag,
                    System.currentTimeMillis() - debut, m.nombre(), b.parcelle);
            aClan(b.clan, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.BASE_CREEE, "notice.heralisz_bases.base_prete", List.of(),
                    Sons.SUCCES.getId().getPath()), Component.translatable("msg.heralisz_bases.base_prete"));
            Sync.clan(b.clan);
        }));
    }

    /** Efface la parcelle ; ensuite la base disparait ({@code supprimerApresEffacement}) ou est reposee. */
    static void lancerEffacement(ServerLevel level, Base b) {
        b.etat = Base.Etat.EFFACEMENT;
        // un travail tourne deja sur la parcelle (pose) : sa fin relance l'effacement (etat EFFACEMENT)
        if (Chantier.occupe(b.parcelle)) return;
        Modele m = modele();
        int pas = Grille.pas();
        int tx = m != null ? m.plan.tx : pas - 2 * Chantier.MARGE, ty = m != null ? m.plan.ty : 64, tz = m != null ? m.plan.tz : pas - 2 * Chantier.MARGE;
        Chantier.ajouter(new Chantier.Effacement(level, b.parcelle, tx, ty, tz, t -> {
            BasesStore s = store();
            ParcellesData.de(level).liberer(b.parcelle);
            if (s == null || s.base(b.clan) != b) return;
            if (b.supprimerApresEffacement) {
                s.retirer(b);
                HeraliszBases.LOG.info("[bases] parcelle {} de [{}] effacee et liberee", b.parcelle, b.tag);
            } else {
                b.supprimerApresEffacement = false;
                HeraliszBases.LOG.info("[bases] parcelle {} de [{}] effacee : repose", b.parcelle, b.tag);
                lancerPose(level, b);
            }
            s.marquerDirty();
            Sync.clan(b.clan);
        }));
    }

    /** Efface une parcelle sans base (orphelin du monde) puis la libere. */
    static void effacerOrpheline(ServerLevel level, int parcelle) {
        if (Chantier.occupe(parcelle)) return;
        Modele m = modele();
        int pas = Grille.pas();
        int tx = m != null ? m.plan.tx : pas - 2 * Chantier.MARGE, ty = m != null ? m.plan.ty : 64, tz = m != null ? m.plan.tz : pas - 2 * Chantier.MARGE;
        Chantier.ajouter(new Chantier.Effacement(level, parcelle, tx, ty, tz, t -> {
            ParcellesData.de(level).liberer(parcelle);
            HeraliszBases.LOG.info("[bases] parcelle orpheline {} effacee et liberee", parcelle);
        }));
    }

    // =================================================================== salles
    /** Deblocage d'une salle par un joueur (BASE_GERER, cout pris dans la banque du clan). */
    public static boolean debloquer(ServerPlayer p, String salleId) {
        Optional<UUID> clan = clanDe(p);
        if (clan.isEmpty()) {
            erreur(p, "msg.heralisz_bases.pas_de_clan");
            return false;
        }
        if (!ClansApi.aPerm(p.getUUID(), Perm.BASE_GERER)) {
            erreur(p, "msg.heralisz_bases.permission", Component.translatable("perm.base_gerer"));
            return false;
        }
        BasesStore s = store();
        Base b = s == null ? null : s.base(clan.get());
        Plan.Salle salle = verifierSalle(p, b, salleId);
        if (salle == null) return false;
        long cout = cout(salle);
        if (cout > 0 && !ClansApi.debiterBanque(clan.get(), cout, "base_salle", p.getUUID())) {
            erreur(p, "msg.heralisz_bases.fonds", format(cout), format(ClansApi.soldeBanque(clan.get())));
            return false;
        }
        ouvrirSalle(b, salle, p.getGameProfile().getName());
        ClansApi.journal(clan.get(), "evenement.heralisz_bases.salle_debloquee", salle.nomFr(), p.getGameProfile().getName());
        return true;
    }

    @Nullable
    private static Plan.Salle verifierSalle(ServerPlayer p, @Nullable Base b, String salleId) {
        Modele m = modele();
        if (b == null) {
            erreur(p, "msg.heralisz_bases.pas_de_base");
            return null;
        }
        if (!b.prete()) {
            erreur(p, "msg.heralisz_bases.base_occupee");
            return null;
        }
        if (m == null) {
            erreur(p, "msg.heralisz_bases.modele_absent");
            return null;
        }
        Plan.Salle salle = m.plan.salle(salleId);
        if (salle == null) {
            erreur(p, "msg.heralisz_bases.salle_inconnue", salleId);
            return null;
        }
        if (b.salleOuverte(salle)) {
            erreur(p, "msg.heralisz_bases.salle_deja_ouverte", salle.nomFr());
            return null;
        }
        return salle;
    }

    /** Ouvre la salle (paiement deja fait) : cloison qui se desagrege, toast au clan. */
    public static void ouvrirSalle(Base b, Plan.Salle salle, String auteur) {
        BasesStore s = store();
        b.salles.add(salle.id());
        if (s != null) s.sauvegarderMaintenant();
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = Grille.niveau(server);
        Runnable fini = () -> {
            aClan(b.clan, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.SALLE_DEBLOQUEE, "notice.heralisz_bases.salle_debloquee",
                    List.of(salle.nomFr(), salle.nomEn()), Sons.SUCCES.getId().getPath()), null);
            Sync.clan(b.clan);
        };
        aClan(b.clan, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.INFO, "notice.heralisz_bases.salle_en_cours", List.of(salle.nomFr()), ""),
                Component.translatable("msg.heralisz_bases.salle_debloquee", salle.nomFr(), auteur));
        if (level == null) {
            fini.run();
            return;
        }
        Chantier.desagreger(level, origine(b), salle.cloison(), fini);
    }

    /** Deblocage admin (gratuit). */
    public static boolean debloquerAdmin(Base b, String salleId, String auteur) {
        Modele m = modele();
        Plan.Salle salle = m == null ? null : m.plan.salle(salleId);
        if (salle == null || b.salleOuverte(salle) || !b.prete()) return false;
        ouvrirSalle(b, salle, auteur);
        return true;
    }

    /**
     * Reprise d'une animation de cloison interrompue par un arret : les blocs de COQUE restant dans les cloisons des
     * salles achetees sont retires (une fois par base et par demarrage, a la premiere entree).
     */
    static void verifierCloisons(ServerLevel level, Base b) {
        if (!CLOISONS_VERIFIEES.add(b.clan)) return;
        Modele m = modele();
        if (m == null) return;
        BlockPos o = origine(b);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int n = 0;
        for (Plan.Salle s : m.plan.salles) {
            if (s.depart() || !b.salleAchetee(s.id())) continue;
            for (Boite bo : s.cloison()) {
                for (int x = bo.x0(); x <= bo.x1(); x++)
                    for (int y = bo.y0(); y <= bo.y1(); y++)
                        for (int z = bo.z0(); z <= bo.z1(); z++) {
                            p.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                            if (level.getBlockState(p).is(BasesRegistry.COQUE.get())) {
                                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                                n++;
                            }
                        }
            }
        }
        if (n > 0) HeraliszBases.LOG.info("[bases] [{}] : {} blocs de cloison restants retires (animation interrompue)", b.tag, n);
    }

    // =================================================================== entree / sortie
    /** Entree d'un membre (BASE_ENTRER) par l'entree « nord » ou « sud ». */
    public static boolean entrer(ServerPlayer p, @Nullable String entree) {
        Optional<UUID> clan = clanDe(p);
        if (clan.isEmpty()) {
            erreur(p, "msg.heralisz_bases.pas_de_clan");
            return false;
        }
        BasesStore s = store();
        Base b = s == null ? null : s.base(clan.get());
        if (b == null) {
            erreur(p, "msg.heralisz_bases.pas_de_base");
            return false;
        }
        if (!ClansApi.aPerm(p.getUUID(), Perm.BASE_ENTRER)) {
            erreur(p, "msg.heralisz_bases.permission", Component.translatable("perm.base_entrer"));
            return false;
        }
        return programmerEntree(p, b, entree, Packets.TeleportS2C.Genre.ENTREE, joueur -> ClansApi.aPerm(joueur.getUUID(), b.clan, Perm.BASE_ENTRER), null);
    }

    /**
     * Entree differee dans une base (membres et pillards), par tous les chemins (bouton de l'ecran, {@code /base entrer},
     * module pillage) : refus si tague en combat, a terre, base pas prete (« Preparation du bunker… x % ») ; le joueur
     * descend de son vehicule ; puis {@code delai_entree_s} immobile, POUR TOUT LE MONDE (admins compris : seul
     * {@code /base admin teleporter} est immediat). A l'echeance tout est reverifie ({@code encore} : droit toujours
     * valable), puis {@link #teleporterDans} et {@code apres}.
     *
     * @param entree « nord », « sud » ou null (nord par defaut, ou la premiere entree du modele)
     * @return vrai si le compte a rebours a commence
     */
    public static boolean programmerEntree(ServerPlayer p, Base b, @Nullable String entree, Packets.TeleportS2C.Genre genre,
                                           java.util.function.Predicate<ServerPlayer> encore, @Nullable Consumer<ServerPlayer> apres) {
        if (!verifierAvantEntree(p, b)) return false;
        Modele m = modele();
        Plan.Entree e = entree == null ? m.plan.entreeParDefaut() : m.plan.entree(entree);
        if (e == null) {
            erreur(p, "msg.heralisz_bases.entree_inconnue", entree == null ? "?" : entree);
            return false;
        }
        String id = e.id();
        int ticks = BasesConfig.DELAI_ENTREE_S.get() * 20;
        boolean commence = Teleports.programmer(p, genre, ticks, joueur -> {
            BasesStore s = store();
            if (s == null || s.base(b.clan) != b) {
                erreur(joueur, "msg.heralisz_bases.pas_de_base");
                return false;
            }
            if (!verifierAvantEntree(joueur, b)) return false;
            if (!encore.test(joueur)) {
                erreur(joueur, "msg.heralisz_bases.pas_autorise");
                return false;
            }
            if (!teleporterDans(joueur, b, id)) return false;
            if (apres != null) apres.accept(joueur);
            return true;
        });
        if (commence && ticks > 0) info(p, "msg.heralisz_bases.tp_attente", BasesConfig.DELAI_ENTREE_S.get());
        return commence;
    }

    /** Avancement de la pose (ou de l'effacement) de la base, 0..1 ; 1 si elle est prete. */
    public static float progression(Base b) {
        Chantier.Travail t = Chantier.travail(b.parcelle);
        if (t != null) return t.progression();
        return b.prete() ? 1f : 0f;
    }

    /** « Preparation du bunker… 42 % » (base en pose : on ne teleporte jamais dans un bunker incomplet). */
    private static void erreurPreparation(ServerPlayer p, Base b) {
        erreur(p, "msg.heralisz_bases.preparation", Math.round(progression(b) * 100));
    }

    private static boolean verifierAvantEntree(ServerPlayer p, Base b) {
        if (modele() == null) {
            erreur(p, "msg.heralisz_bases.modele_absent");
            return false;
        }
        if (b.etat == Base.Etat.POSE || (b.prete() && Chantier.occupe(b.parcelle))) {
            erreurPreparation(p, b);
            return false;
        }
        if (!b.prete()) {
            erreur(p, "msg.heralisz_bases.base_occupee");
            return false;
        }
        if (!p.isAlive()) return false;
        // en match (drapeau heralisz:en_match de HeraliZ Matchs) : jamais de teleportation vers une base
        if (ClansApi.enMatch(p)) {
            erreur(p, "msg.heralisz_bases.en_match");
            return false;
        }
        if (estAdmin(p)) return true;
        if (CombatLocal.tague(p)) {
            erreur(p, "msg.heralisz_bases.combat", (CombatLocal.restantMs(p) + 999) / 1000);
            return false;
        }
        if (Compat.aTerre(p)) {
            erreur(p, "msg.heralisz_bases.a_terre");
            return false;
        }
        return true;
    }

    /**
     * Teleportation immediate dans le sas d'une entree (sans aucune verification de droit) : le joueur descend de son
     * vehicule ; sa position de retour est gardee A CHAQUE entree depuis un autre monde (sa propre position, apres la
     * descente) ; jamais avant la fin de la pose ; l'arrivee est verifiee (sol plein sous les pieds, 2 blocs libres,
     * sinon le point sur le plus proche dans le sas) puis surveillee 10 s (chute = replace).
     */
    public static boolean teleporterDans(ServerPlayer p, Base b, String entree) {
        Modele m = modele();
        ServerLevel level = Grille.niveau(p.server);
        if (m == null || level == null) {
            erreur(p, m == null ? "msg.heralisz_bases.modele_absent" : "msg.heralisz_bases.dimension_absente");
            return false;
        }
        if (!b.prete() || Chantier.occupe(b.parcelle)) {
            erreurPreparation(p, b);
            return false;
        }
        Plan.Entree e = m.plan.entree(entree);
        if (e == null) e = m.plan.entreeParDefaut();
        if (e == null) {
            erreur(p, "msg.heralisz_bases.entree_inconnue", entree);
            return false;
        }
        verifierCloisons(level, b);
        BlockPos o = origine(b);
        net.minecraft.world.phys.Vec3 point = pointSur(level, o, e);
        if (point == null) {
            // ni le point d'arrivee ni le sas ne sont praticables : bunker absent ou abime -> repose, jamais de chute
            HeraliszBases.LOG.warn("[bases] arrivee {} de [{}] impraticable (bunker absent ?) : repose du bunker", e.id(), b.tag);
            lancerPose(level, b);
            erreurPreparation(p, b);
            Sync.clan(b.clan);
            return false;
        }
        if (!Descente.faireDescendre(p, "entree")) {
            erreur(p, "msg.heralisz_bases.vehicule");
            return false;
        }
        BasesStore s = store();
        if (s != null && !Grille.estBunkers(p.level())) {
            s.retour(p.getUUID(), new Retour(p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()));
            HeraliszBases.LOG.info("[bases] {} entre dans [{}] par {} ; retour garde en {} {} {} {}", p.getGameProfile().getName(), b.tag, e.id(),
                    p.level().dimension().location(), Math.round(p.getX()), Math.round(p.getY()), Math.round(p.getZ()));
        }
        p.fallDistance = 0;
        p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        p.teleportTo(level, point.x, point.y, point.z, e.yaw(), 0f);
        Teleports.surveillerArrivee(p, point, e.yaw(), point.y);
        level.playSound(null, point.x, point.y, point.z, Sons.TP_ARRIVEE.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
        BaseEvents.hudMaintenant(p);
        return true;
    }

    /**
     * Point d'arrivee sur (coordonnees du monde) : le point du modele s'il a un sol plein et deux blocs libres, sinon la
     * premiere case praticable du sas (puis autour du point, 4 blocs), null si rien n'est praticable (bunker absent).
     * Les chunks sont charges au besoin.
     */
    @Nullable
    static net.minecraft.world.phys.Vec3 pointSur(ServerLevel level, BlockPos o, Plan.Entree e) {
        BlockPos pied = BlockPos.containing(o.getX() + e.x(), o.getY() + e.y(), o.getZ() + e.z());
        level.getChunk(pied.getX() >> 4, pied.getZ() >> 4);
        if (praticable(level, pied)) return new net.minecraft.world.phys.Vec3(o.getX() + e.x(), pied.getY(), o.getZ() + e.z());
        Boite sas = e.sas();
        if (sas != null) {
            for (int y = sas.y0(); y <= sas.y1(); y++)
                for (int x = sas.x0(); x <= sas.x1(); x++)
                    for (int z = sas.z0(); z <= sas.z1(); z++) {
                        BlockPos q = o.offset(x, y, z);
                        if (praticable(level, q)) return net.minecraft.world.phys.Vec3.atBottomCenterOf(q);
                    }
        }
        for (int r = 1; r <= 4; r++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos q = pied.offset(dx, dy, dz);
                        if (praticable(level, q)) return net.minecraft.world.phys.Vec3.atBottomCenterOf(q);
                    }
        return null;
    }

    /** Sol plein sous {@code pied}, {@code pied} et le bloc au-dessus sans collision ni lave (l'eau est toleree). */
    private static boolean praticable(ServerLevel level, BlockPos pied) {
        BlockPos sol = pied.below();
        if (!level.getBlockState(sol).isFaceSturdy(level, sol, net.minecraft.core.Direction.UP)) return false;
        for (BlockPos q : new BlockPos[]{pied, pied.above()}) {
            var st = level.getBlockState(q);
            if (!st.getCollisionShape(level, q).isEmpty() || st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return false;
        }
        return true;
    }

    /**
     * Sortie volontaire, par tous les chemins (bouton SORTIR, {@code /base sortir}, bloc de sortie) : refusee en combat,
     * sinon le joueur descend de son vehicule puis reste immobile {@code delai_entree_s} secondes (comme a l'entree, admins
     * compris) ; retour a la position d'ou il est entre.
     */
    public static void sortir(ServerPlayer p) {
        if (!Grille.estBunkers(p.level())) {
            erreur(p, "msg.heralisz_bases.pas_dans_base");
            return;
        }
        if (CombatLocal.tague(p) && !estAdmin(p)) {
            erreur(p, "msg.heralisz_bases.combat", (CombatLocal.restantMs(p) + 999) / 1000);
            return;
        }
        if (ClansApi.enMatch(p)) {
            erreur(p, "msg.heralisz_bases.en_match");
            return;
        }
        int ticks = BasesConfig.DELAI_ENTREE_S.get() * 20;
        boolean commence = Teleports.programmer(p, Packets.TeleportS2C.Genre.SORTIE, ticks, joueur -> {
            if (!Grille.estBunkers(joueur.level())) return false;
            if (CombatLocal.tague(joueur) && !estAdmin(joueur)) {
                erreur(joueur, "msg.heralisz_bases.combat", (CombatLocal.restantMs(joueur) + 999) / 1000);
                return false;
            }
            ramener(joueur);
            return true;
        });
        if (commence && ticks > 0) info(p, "msg.heralisz_bases.tp_sortie_attente", BasesConfig.DELAI_ENTREE_S.get());
    }

    /** Ejection immediate (dissolution, exclusion, droit perdu, base indisponible) avec un message. */
    public static void ejecter(ServerPlayer p, String cle) {
        Teleports.oublier(p.getUUID());
        info(p, cle);
        ramener(p);
    }

    /**
     * Ramene le joueur a sa position de retour (celle d'ou il est entre la derniere fois ; dimension disparue ou absente :
     * point d'apparition du monde principal), apres l'avoir fait descendre de tout vehicule. Previent le module pillage.
     */
    public static void ramener(ServerPlayer p) {
        Descente.faireDescendre(p, "sortie");
        Base b = baseA(p.level(), p.blockPosition());
        try {
            BasesApi.pillage().sortie(p, b);
        } catch (RuntimeException ex) {
            HeraliszBases.LOG.error("[bases] module pillage : sortie en erreur", ex);
        }
        BasesStore s = store();
        Retour r = s == null ? null : s.retour(p.getUUID());
        MinecraftServer server = p.server;
        ServerLevel cible = null;
        if (r != null) {
            ResourceLocation rl = ResourceLocation.tryParse(r.dim());
            if (rl != null) cible = server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
            if (Grille.estBunkers(cible)) cible = null;
        }
        p.fallDistance = 0;
        if (cible != null) {
            p.teleportTo(cible, r.x(), r.y(), r.z(), r.yaw(), r.pitch());
            HeraliszBases.LOG.info("[bases] {} ramene a sa position d'entree {} {} {} {}", p.getGameProfile().getName(), r.dim(),
                    Math.round(r.x()), Math.round(r.y()), Math.round(r.z()));
        } else {
            ServerLevel ow = server.overworld();
            BlockPos sp = ow.getSharedSpawnPos();
            BlockPos top = ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp);
            p.teleportTo(ow, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, p.getYRot(), p.getXRot());
            HeraliszBases.LOG.info("[bases] {} ramene au point d'apparition (aucune position d'entree)", p.getGameProfile().getName());
        }
        if (s != null) s.retour(p.getUUID(), null);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), Sons.TP_ARRIVEE.get(), SoundSource.PLAYERS, 0.9f, 0.85f);
        BaseEvents.hudMaintenant(p);
    }

    // =================================================================== cycle de vie
    /** Joueurs en ligne presents dans la parcelle d'une base. */
    public static List<ServerPlayer> presents(MinecraftServer server, Base b) {
        ServerLevel level = Grille.niveau(server);
        List<ServerPlayer> l = new ArrayList<>();
        if (level == null) return l;
        for (ServerPlayer p : level.players()) {
            if (Grille.parcelle(p.getBlockX(), p.getBlockZ()) == b.parcelle) l.add(p);
        }
        return l;
    }

    /** Clan dissous : tout le monde sort, la parcelle est effacee par tranches puis liberee. */
    public static void dissoudre(MinecraftServer server, Base b) {
        for (ServerPlayer p : presents(server, b)) ejecter(p, "msg.heralisz_bases.clan_dissous");
        b.supprimerApresEffacement = true;
        b.etat = Base.Etat.EFFACEMENT;
        BasesStore s = store();
        if (s != null) s.sauvegarderMaintenant();
        ServerLevel level = Grille.niveau(server);
        if (level != null) lancerEffacement(level, b);
        HeraliszBases.LOG.info("[bases] clan [{}] dissous : effacement de la parcelle {}", b.tag, b.parcelle);
    }

    /** Reinitialisation admin : tout le monde sort, contenu efface, bunker repose (salles achetees gardees). */
    public static boolean reinitialiser(MinecraftServer server, Base b) {
        ServerLevel level = Grille.niveau(server);
        if (level == null || Chantier.occupe(b.parcelle)) return false;
        for (ServerPlayer p : presents(server, b)) ejecter(p, "msg.heralisz_bases.reinitialisee");
        b.supprimerApresEffacement = false;
        b.etat = Base.Etat.EFFACEMENT;
        CLOISONS_VERIFIEES.remove(b.clan);
        BasesStore s = store();
        if (s != null) s.sauvegarderMaintenant();
        lancerEffacement(level, b);
        return true;
    }

    /**
     * Demarrage : charge le modele puis aligne le monde sur bases.json — POSE / EFFACEMENT repris, base PRETE absente du
     * monde (wipe de la dimension) reposee, parcelle du monde sans base effacee, base d'un clan disparu effacee.
     */
    public static void reconcilier(MinecraftServer server) {
        Modele.charger(server);
        CLOISONS_VERIFIEES.clear();
        BasesStore s = store();
        ServerLevel level = Grille.niveau(server);
        if (s == null) return;
        if (level == null) {
            HeraliszBases.LOG.error("[bases] dimension {} introuvable : datapack du mod absent ?", Grille.BUNKERS.location());
            return;
        }
        ParcellesData data = ParcellesData.de(level);
        int reposees = 0, effacees = 0;
        for (Base b : new ArrayList<>(s.bases())) {
            if (ClansApi.disponible() && ClansApi.infos(b.clan).isEmpty() && !b.supprimerApresEffacement) {
                HeraliszBases.LOG.warn("[bases] le clan de la base [{}] n'existe plus : effacement", b.tag);
                b.supprimerApresEffacement = true;
                b.etat = Base.Etat.EFFACEMENT;
                s.marquerDirty();
            }
            switch (b.etat) {
                case POSE -> {
                    lancerPose(level, b);
                    reposees++;
                }
                case EFFACEMENT -> {
                    lancerEffacement(level, b);
                    effacees++;
                }
                case PRETE -> {
                    if (!data.estPosee(b.parcelle, b.clan)) {
                        HeraliszBases.LOG.warn("[bases] bunker de [{}] absent du monde (dimension reinitialisee ?) : repose", b.tag);
                        lancerPose(level, b);
                        reposees++;
                    }
                }
            }
            // tag a jour pour les journaux
            ClansApi.infos(b.clan).ifPresent(ci -> b.tag = ci.tag());
        }
        for (Map.Entry<Integer, UUID> e : new ArrayList<>(data.posees().entrySet())) {
            Base b = s.parParcelle(e.getKey());
            if (b == null) {
                effacerOrpheline(level, e.getKey());
                effacees++;
            }
        }
        if (reposees + effacees > 0) HeraliszBases.LOG.info("[bases] reprise : {} pose(s), {} effacement(s) en file", reposees, effacees);
    }

    /** Montant formate par l'economie (« 10 000 hérals »). */
    public static String format(long v) {
        try {
            return fr.heralisz.economie.api.EconomieApi.format(v);
        } catch (RuntimeException | LinkageError ex) {
            return Long.toString(v);
        }
    }
}
