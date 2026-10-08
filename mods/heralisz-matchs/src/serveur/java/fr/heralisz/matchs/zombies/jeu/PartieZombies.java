package fr.heralisz.matchs.zombies.jeu;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Carte;
import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.Equipe;
import fr.heralisz.matchs.moteur.api.Equipement;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.api.Pose;
import fr.heralisz.matchs.moteur.api.Spectateur;
import fr.heralisz.matchs.moteur.arenes.ArenesService;
import fr.heralisz.matchs.moteur.arenes.Structure;
import fr.heralisz.matchs.reseau.MatchsNet;
import fr.heralisz.matchs.zombies.jeu.api.Allure;
import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import fr.heralisz.matchs.zombies.jeu.api.MomentMusique;
import fr.heralisz.matchs.zombies.jeu.api.MotifPoints;
import fr.heralisz.matchs.zombies.jeu.api.PlanZombies;
import fr.heralisz.matchs.zombies.jeu.api.Rarete;
import fr.heralisz.matchs.zombies.jeu.api.TypeAtout;
import fr.heralisz.matchs.zombies.jeu.api.TypeBonus;
import fr.heralisz.matchs.zombies.jeu.api.TypeZombie;
import fr.heralisz.matchs.zombies.jeu.api.ZombieFabrique;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor;
import fr.heralisz.matchs.zombies.jeu.net.PaquetsZombies;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Partie de zombies (MATCHS_SPEC §4, ZOMBIES_V2_SPEC §2) : groupe de 1 a 4 joueurs dans une instance d'une carte de
 * type zombies.
 * <p>Deroule : {@link Phase#ATTENTE} (pose de l'instance, Coffre deja fait) puis {@link Phase#COMBAT} pour toute la
 * partie (sous-etapes : depart 5 s, manche, pause de 10 s entre manches), puis {@link Phase#FIN} (ecran de fin 10 s,
 * records), Coffre rendu et {@link #fermer()}.
 * <p>Regles tenues ici, toutes cote serveur :
 * <ul>
 *     <li>points : +10 par touche (une fois par balle, jamais pour le coup mortel), kill 60, tete 100, couteau 130
 *     (toute melee : couteau, crosse), +10 par planche reparee (plafond par manche), doubles avec le bonus ;</li>
 *     <li>fenetres : le zombie marche jusqu'au point exterieur de la fenetre, s'y colle, arrache une planche toutes les
 *     ~1,2 s (evenement d'animation et son), griffe a travers un joueur qui TOUCHE la fenetre de l'autre cote une fois 4
 *     planches arrachees sur 6 ({@link #PLANCHES_GRIFFE}, {@link #PORTEE_GRIFFE_FENETRE}), puis enjambe la fenetre
 *     vide (deplacement interpole de {@link ZombiesApi#ENJAMBEMENT_TICKS} ticks, sans teleportation) ; un seul zombie par
 *     fenetre, les autres attendent derriere ; les joueurs a l'interieur le touchent au couteau pendant ce temps (coup
 *     applique par le serveur, {@link #coup}) ;</li>
 *     <li>apparitions : fenetres et sorties de terre des zones ouvertes ({@code points_apparition},
 *     {@code apparitions_salle}), ponderees par la proximite des joueurs ;</li>
 *     <li>garde-fou : 120 s sans kill = la manche passe (zombies restants retires proprement) ; un zombie a plus de 48
 *     blocs des joueurs, sans ligne de vue depuis 20 s ou bloque est replace a un point d'apparition actif proche des
 *     joueurs ; les derniers zombies d'une manche sprintent et se rapprochent apres 60 s sans kill ;</li>
 *     <li>portes et zones, armes au mur (achat a 1,6 bloc du dessin en le regardant), boite mystere a raretes
 *     ({@link Boite}, {@link TableBoite}), courant, atouts (5), Pack-a-Punch ({@link PackAPunch}), etablis et materiaux
 *     de recuperation ({@link Recettes}), objectifs ({@link Objectifs}) et lore, easter egg a etapes, bonus, a terre
 *     (30 s, pistolet seul, kit d'auto-reanimation) et reanimation, musique (evenements vers le client), fin de partie
 *     et records ({@link Records}). Malus ArkaniaZ, cartes de Xaero et tag de combat : moteur ({@code MatchApi.rejoindre},
 *     {@code RestrictionsMatch}).</li>
 * </ul>
 * Le rendu (blocs-entites, creatures, sons) passe par {@link ZombiesApi#decor()} et {@link ZombiesApi#fabrique()}.
 * <p>Fil serveur. Pas d'allocation par tick dans les chemins chauds (listes reutilisees).
 */
public final class PartieZombies extends Partie {
    /** Sous-etape de la phase COMBAT. */
    enum Etape { DEPART, MANCHE, PAUSE }

    static final String PISTOLET = "tacz:m1911";
    /** Contenus heralisz_tactical (porteur generique « melee » / « throwable » + id en NBT, voir ModItems du mod tactique). */
    static final String COUTEAU = "heralisz_melee:tac_dagger";
    static final String GRENADE = "heralisz_grenade:m67";
    static final int SLOT_COUTEAU = 2, SLOT_GRENADES = 3, SLOT_TACTIQUE = 4, SLOT_PISTOLET_SECOURS = 8;
    /** Case de la 3e arme (atout Coup de mule / Mule Kick). */
    static final int SLOT_MULE = 5;
    static final int GRENADES_MAX = 4;
    static final int A_TERRE_TICKS = 600;
    static final int REANIMATION_TICKS = 80;
    static final int PLANCHES_MAX = 6;
    /** Le zombie a la fenetre ne peut griffer a travers qu'a partir de 2 planches restantes (4 arrachees sur 6). */
    static final int PLANCHES_GRIFFE = 2;
    /** Distance a la face interieure de la fenetre (centre du joueur) en deca de laquelle il est griffe : il doit la toucher. */
    static final double PORTEE_GRIFFE_FENETRE = 0.65;
    static final int REPARATION_TICKS = 15;
    static final int BONUS_SOL_TICKS = 600;
    static final int FIN_TICKS = 200;
    static final double PORTEE = 2.6;
    /** Points d'absorption d'une plaque d'armure. */
    static final float PV_PLAQUE = 6f;
    private static final boolean BANC = System.getProperty("heralisz.matchs.harness") != null;
    private static final UUID MOD_BLINDAGE = UUID.fromString("5b7c2f0e-8a1d-4e6b-9c3f-0d2a7e1b4c01");
    private static final UUID MOD_VIGUEUR = UUID.fromString("5b7c2f0e-8a1d-4e6b-9c3f-0d2a7e1b4c02");
    private static final UUID MOD_A_TERRE = UUID.fromString("5b7c2f0e-8a1d-4e6b-9c3f-0d2a7e1b4c03");

    private final Carte carte;
    private final Equipe equipe;
    private final RandomSource alea = RandomSource.create();
    private final Map<UUID, JoueurZombies> joueurs = new LinkedHashMap<>();
    /** Joueurs connectes de la partie, rempli a chaque tick (reutilise). */
    private final List<ServerPlayer> enLigne = new ArrayList<>(4);
    private final List<ZombieSuivi> zombies = new ArrayList<>();
    private final Int2ObjectOpenHashMap<ZombieSuivi> parId = new Int2ObjectOpenHashMap<>();
    /** Etats d'origine des cases des portes (lus une fois par partie, {@link #restaurerStructure()}). */
    @Nullable
    private Long2ObjectOpenHashMap<BlockState> etatsPortes;
    private final List<BonusSol> bonusSol = new ArrayList<>();
    private final List<MateriauSol> materiauxSol = new ArrayList<>();
    private final int[] bonusTicks = new int[TypeBonus.values().length];
    private final List<PointApparition> points = new ArrayList<>();
    private final List<PointApparition> candidats = new ArrayList<>();
    private final double[] poidsCandidats = new double[256];
    private final Map<String, Component> nomsArmes = new HashMap<>();
    private final Map<TableBoite.Tirage, ItemStack> apercus = new HashMap<>();
    /** Bloc (BlockPos.asLong) -> index de fenetre, pour la collision des fenetres (appel tres frequent). */
    private final Long2IntOpenHashMap fenetreParBloc = new Long2IntOpenHashMap();
    /** Cases ou une balle incendiaire vient de toucher : feu eteint au tick suivant (la carte ne brule pas). */
    private final LongArrayList feux = new LongArrayList();
    /** Parties dont l'instance est prete (recherche par position sans allocation). */
    private static final List<PartieZombies> ACTIVES = new ArrayList<>();

    @Nullable
    private PlanZombies plan;
    @Nullable
    private ServerLevel niveau;
    @Nullable
    private TableBoite table;
    @Nullable
    private Boite boite;
    @Nullable
    private PackAPunch pap;
    @Nullable
    private Objectifs objectifs;
    /** Etablis de la carte (+ etablis de banc poses par {@code /zombies dev atelier}). */
    private final List<PlanZombies.Atelier> ateliers = new ArrayList<>();
    /** Easter egg : etapes (carte, ou banc), trouvees, prochaine attendue, fini. */
    private final List<PlanZombies.EtapeEasterEgg> eeEtapes = new ArrayList<>();
    private boolean[] eeTrouvees = new boolean[0];
    private int eeProchaine;
    private boolean eeFini;
    private String eeRecompense = "";
    /** Mort instantanee de l'easter egg : manches restantes (decomptees a chaque fin de manche). */
    private int instaKillManches;
    private boolean[] zonesOuvertes = new boolean[0];
    private final List<String> idsZones = new ArrayList<>();
    private boolean[] portesOuvertes = new boolean[0];
    private int[] planches = new int[0];
    /** Zombie colle a chaque fenetre (un seul a la fois), null = libre. */
    private ZombieSuivi[] occupants = new ZombieSuivi[0];
    private boolean courant;
    /** v3 : regles et quete de The Giant (null sur une ancienne carte). */
    @Nullable
    private QueteGiant giant;
    /** Atouts de la partie (v3 : types melanges sur les places normales ; machine secrete cachee jusqu'a sa revelation). */
    private final List<PlanZombies.Atout> atouts = new ArrayList<>();
    private boolean[] atoutVisible = new boolean[0];
    /** Manches de chiens deja jouees (apres la 3e, quelques chiens se melent aux zombies). */
    private int manchesChiens;
    /** Quete de The Giant terminee (boss N-07 vaincu, tous remontes) : la partie continue, la fin sera une victoire. */
    private boolean victoireQuete;
    /** Manche ou la quete a ete terminee (0 = pas terminee). */
    private int mancheQuete;
    /** v3 : acte IV de The Giant (boss N-07), null sur une autre carte. */
    @Nullable
    private ActeQuatre acte;

    private Etape etape = Etape.DEPART;
    private int ticksEtape;
    private int manche;
    private boolean speciale;
    private int prochaineSpeciale;
    private int aFaire;
    private int ticksAvantApparition;
    private int bonusManche;
    private boolean sprintForce;
    private long dernierKill;
    private double dernierKillX, dernierKillY, dernierKillZ;
    private boolean atomiqueEnCours;
    private boolean hudSale = true;
    private long finMs;
    private MomentMusique ambiance = MomentMusique.PARTIE_DEBUT;
    /** Banc d'essai : apparitions naturelles et garde-fou de manche suspendus. */
    private boolean devPause;
    /** Plus bas sol jouable (bas des volumes des zones, absolu) : un zombie 4 blocs plus bas est tombe sous la carte. */
    private double ySolMin = -1e9;

    /** Point d'apparition actif potentiel (sortie de terre si fenetre = -1, {@code genre} du point de salle). */
    private record PointApparition(int zone, Pose pose, int fenetre, String genre) {
    }

    /** Bonus pose au sol. */
    private static final class BonusSol {
        final TypeBonus type;
        final double x, y, z;
        @Nullable
        final Entity entite;
        int ticks = BONUS_SOL_TICKS;

        BonusSol(TypeBonus type, double x, double y, double z, @Nullable Entity entite) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.entite = entite;
        }
    }

    /** Tas de materiaux de recuperation au sol (ramasse en marchant dessus). */
    private static final class MateriauSol {
        final int quantite;
        final double x, y, z;
        @Nullable
        final Entity entite;
        int ticks;

        MateriauSol(int quantite, double x, double y, double z, @Nullable Entity entite, int ticks) {
            this.quantite = quantite;
            this.x = x;
            this.y = y;
            this.z = z;
            this.entite = entite;
            this.ticks = ticks;
        }
    }

    PartieZombies(String id, MinecraftServer serveur, Carte carte, List<ServerPlayer> membres) {
        super(id, "zombies", serveur);
        this.carte = carte;
        this.equipe = new Equipe("groupe", "HeraliZ", 0xE0473C);
        ajouterEquipe(equipe);
        int rang = 0;
        for (ServerPlayer p : membres) {
            equipe.ajouter(p.getUUID());
            JoueurZombies j = new JoueurZombies(p.getUUID(), p.getGameProfile().getName(), rang++);
            j.points = Manches.POINTS_DEPART;
            joueurs.put(p.getUUID(), j);
        }
    }

    // ------------------------------------------------------------------ acces
    public Carte carte() {
        return carte;
    }

    PlanZombies plan() {
        return plan;
    }

    ServerLevel niveau() {
        return niveau;
    }

    RandomSource alea() {
        return alea;
    }

    boolean courant() {
        return courant;
    }

    public int manche() {
        return manche;
    }

    /** Partie de zombies dont l'instance contient ce bloc, ou null. */
    @Nullable
    public static PartieZombies a(Level niveau, BlockPos pos) {
        for (int i = 0; i < ACTIVES.size(); i++) {
            PartieZombies z = ACTIVES.get(i);
            Instance inst = z.instance();
            if (inst != null && z.niveau == niveau && inst.contient(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) return z;
        }
        return null;
    }

    /** Planches de la fenetre contenant ce bloc (0..6), ou -1. Sans allocation. */
    public int planchesA(BlockPos pos) {
        int f = fenetreParBloc.get(pos.asLong());
        return f < 0 || f >= planches.length ? -1 : planches[f];
    }

    /** Oublie les parties actives (arret du serveur). */
    static void viderActives() {
        ACTIVES.clear();
    }

    @Override
    public void fermer() {
        ACTIVES.remove(this);
        super.fermer();
    }

    // ------------------------------------------------------------------ demarrage
    /** Associe l'instance et demarre quand elle est prete (appele par {@link ZombiesJeu#lancer}). */
    void demarrer(Instance inst) {
        instance(inst);
        inst.quandPrete(this::instancePrete);
    }

    private void instancePrete() {
        Instance inst = instance();
        if (inst == null || phase() != Phase.ATTENTE) return;
        niveau = inst.niveau();
        long t0 = System.nanoTime();
        plan = PlanZombies.lire(carte, inst.origine());
        giant = plan.giant() == null ? null : new QueteGiant(this, plan.giant());
        HeraliszMatchs.LOG.debug("[zombies] plan de {} lu en {} us", carte.id(), (System.nanoTime() - t0) / 1000);
        table = TableBoite.pour(carte.meta(), carte.id(), plan.armeCarte());
        purgerEntites();
        restaurerStructure();
        preparerAtouts();
        idsZones.clear();
        idsZones.addAll(plan.zones().keySet());
        zonesOuvertes = new boolean[idsZones.size()];
        portesOuvertes = new boolean[plan.portes().size()];
        planches = new int[plan.fenetres().size()];
        occupants = new ZombieSuivi[plan.fenetres().size()];
        java.util.Arrays.fill(planches, PLANCHES_MAX);
        fenetreParBloc.clear();
        fenetreParBloc.defaultReturnValue(-1);
        for (PlanZombies.Fenetre f : plan.fenetres()) {
            for (BlockPos bp : BlockPos.betweenClosed(f.boite().minX(), f.boite().minY(), f.boite().minZ(),
                    f.boite().maxX(), f.boite().maxY(), f.boite().maxZ())) fenetreParBloc.put(bp.asLong(), f.index());
        }
        if (!ACTIVES.contains(this)) ACTIVES.add(this);
        ySolMin = Double.MAX_VALUE;
        for (PlanZombies.Zone z : plan.zones().values()) {
            for (AABB v : z.volumes()) ySolMin = Math.min(ySolMin, v.minY);
            for (Pose p : z.apparitionsSalle()) ySolMin = Math.min(ySolMin, p.y() - 1);
        }
        for (PlanZombies.Fenetre f : plan.fenetres()) ySolMin = Math.min(ySolMin, f.apparition().y() - 1);
        if (ySolMin == Double.MAX_VALUE) ySolMin = inst.bornes().minY;
        points.clear();
        for (int zi = 0; zi < idsZones.size(); zi++) {
            PlanZombies.Zone z = plan.zone(idsZones.get(zi));
            for (Pose p : z.apparitions()) points.add(new PointApparition(zi, p, -1, "sol"));
            for (int k = 0; k < z.apparitionsSalle().size(); k++) {
                points.add(new PointApparition(zi, z.apparitionsSalle().get(k), -1, k < z.genresSalle().size() ? z.genresSalle().get(k) : "sol"));
            }
            for (int fi : z.fenetres()) points.add(new PointApparition(zi, plan.fenetres().get(fi).apparition(), fi, ""));
        }
        boite = null;
        ouvrirZone(plan.zoneDepart());
        if (zonesOuvertes.length > 0 && !auMoinsUneZone()) zonesOuvertes[0] = true;
        courant = plan.courant() == null && (giant == null || !giant.aArmoire());
        manchesChiens = 0;
        victoireQuete = false;
        mancheQuete = 0;
        boite = new Boite(this, table, emplacementBoiteDepart());
        pap = plan.packAPunch() == null ? null : new PackAPunch(this, plan.packAPunch());
        ateliers.clear();
        ateliers.addAll(plan.ateliers());
        eeEtapes.clear();
        if (plan.easterEgg() != null) {
            eeEtapes.addAll(plan.easterEgg().etapes());
            eeRecompense = plan.easterEgg().recompense();
        }
        eeTrouvees = new boolean[eeEtapes.size()];
        eeProchaine = 0;
        eeFini = false;
        objectifs = new Objectifs(plan, courant, giant);
        objectifs.demarrer();

        ZombiesDecor d = ZombiesApi.decor();
        try {
            d.installer(inst, plan);
            for (PlanZombies.Fenetre f : plan.fenetres()) d.fenetre(inst, f, PLANCHES_MAX, ZombiesDecor.EvenementFenetre.INSTALLEE);
            for (int i = 0; i < plan.boites().size(); i++) d.boite(inst, i, i == boite.emplacement ? ZombiesDecor.EtatBoite.FERMEE : ZombiesDecor.EtatBoite.ABSENTE);
            d.courant(inst, courant);
            for (int k = 0; k < atouts.size(); k++) if (atoutVisible[k]) d.atout(inst, atouts.get(k), courant);
            for (PlanZombies.Atelier a : ateliers) d.atelier(inst, a, null);
            for (PlanZombies.EtapeEasterEgg e : eeEtapes) d.easterEgg(inst, e, ZombiesDecor.EtatEtape.CACHEE);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.installer en erreur", ex);
        }
        if (pap != null) pap.installer(courant);
        if (giant != null) giant.installer(courant);
        if (acte != null) acte.nettoyer();
        acte = giant == null ? null : new ActeQuatre(this, giant.plan);
        if (acte != null) acte.preparer();

        List<Pose> spawns = plan.spawnJoueurs();
        int i = 0;
        for (JoueurZombies j : joueurs.values()) {
            ServerPlayer p = joueur(j.id);
            if (p == null) {
                j.connecte = false;
                equipe.eliminer(j.id);
                continue;
            }
            Pose s = spawns.isEmpty() ? new Pose(inst.bornes().getCenter().x, inst.bornes().minY + 1, inst.bornes().getCenter().z, 0)
                    : spawns.get(i++ % spawns.size());
            preparerJoueur(p);
            s.teleporter(p, niveau);
            donnerDepart(p, true);
        }
        prochaineSpeciale = Manches.premiereSpeciale(alea);
        phase(Phase.COMBAT);
        etape = Etape.DEPART;
        ticksEtape = 0;
        hudSale = true;
        remplirEnLigne();
        Annonces.grande(enLigneListe(), new Annonces.Annonce(Component.literal(carte.nom(true)).withStyle(s -> s.withBold(true)),
                Component.translatable("zombies.annonce.bienvenue"), 0xE0473C, "", 80, Annonces.Style.NORMALE));
        MatchsNet.versJoueurs(enLigne, new PaquetsZombies.IntroS2C(plan.introFr(), plan.introEn()));
        envoyerObjectif(false);
        musique(MomentMusique.PARTIE_DEBUT);
        HeraliszMatchs.LOG.info("[zombies] partie {} demarree sur {} ({} joueurs) : {} fenetres, {} points d'apparition, Pack-a-Punch {}, {} etablis, easter egg {} etapes",
                id(), inst, joueurs.size(), plan.fenetres().size(), points.size(), pap != null, ateliers.size(), eeEtapes.size());
    }

    /**
     * Atouts de la partie : ceux de la carte ; si la carte les melange ({@code atouts_aleatoires}, The Giant), les types des
     * places normales sont tires au hasard a chaque partie et le prix suit l'atout (prix de CoD) ; la machine secrete
     * ({@code secret}) n'est posee qu'a sa revelation, avec un atout que les autres places n'ont pas.
     */
    private void preparerAtouts() {
        atouts.clear();
        List<PlanZombies.Atout> l = plan.atouts();
        boolean melange = giant != null && giant.plan.atoutsMelanges;
        List<TypeAtout> types = new ArrayList<>();
        for (PlanZombies.Atout a : l) if (!a.secret()) types.add(a.type());
        if (melange) java.util.Collections.shuffle(types, new java.util.Random(alea.nextLong()));
        int k = 0;
        atoutVisible = new boolean[l.size()];
        for (int i = 0; i < l.size(); i++) {
            PlanZombies.Atout a = l.get(i);
            if (a.secret()) {
                TypeAtout t = giant != null ? giant.atoutSecret(types) : a.type();
                atouts.add(new PlanZombies.Atout(i, a.pos(), a.face(), t, melange ? t.prixCod : a.cout(), a.zone(), true));
                atoutVisible[i] = false;
            } else {
                TypeAtout t = types.get(k++);
                atouts.add(new PlanZombies.Atout(i, a.pos(), a.face(), t, melange ? t.prixCod : a.cout(), a.zone(), false));
                atoutVisible[i] = true;
            }
        }
        if (melange) {
            StringBuilder b = new StringBuilder();
            for (PlanZombies.Atout a : atouts) b.append(a.type().id()).append(a.secret() ? "*" : "").append(' ');
            HeraliszMatchs.LOG.info("[zombies] atouts melanges : {}", b.toString().trim());
        }
    }

    /** v3 : la machine secrete apparait (la neige a fondu) et s'allume si le courant est la. */
    void revelerAtoutSecret() {
        for (int i = 0; i < atouts.size(); i++) {
            if (atoutVisible[i] || !atouts.get(i).secret()) continue;
            atoutVisible[i] = true;
            try {
                ZombiesApi.decor().atout(instance(), atouts.get(i), courant);
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[zombies] decor.atout", ex);
            }
            HeraliszMatchs.LOG.info("[zombies] machine secrete revelee : {}", atouts.get(i).type().id());
        }
        annonceChat(Component.translatable("zombies.giant.machine_secrete").withStyle(st -> st.withColor(0x8FFFD8)));
    }

    /**
     * Premier emplacement de la boite (regle CoD) : au hasard parmi ceux de la zone de depart, sinon parmi ceux des zones
     * ouvertes ; -1 si aucun (la boite reste cachee jusqu'a l'ouverture d'une zone qui en a un).
     */
    private int emplacementBoiteDepart() {
        int n = plan.boites().size();
        List<Integer> depart = new ArrayList<>(), ouverts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!emplacementBoiteOuvert(i)) continue;
            ouverts.add(i);
            if (plan.zoneBoite(i).equals(plan.zoneDepart())) depart.add(i);
        }
        List<Integer> l = depart.isEmpty() ? ouverts : depart;
        return l.isEmpty() ? -1 : l.get(alea.nextInt(l.size()));
    }

    /** Vrai si l'emplacement de boite {@code i} est dans une zone ouverte (ou sans zone connue). */
    boolean emplacementBoiteOuvert(int i) {
        return zoneOuverte(plan.zoneBoite(i));
    }

    private boolean auMoinsUneZone() {
        for (boolean b : zonesOuvertes) if (b) return true;
        return false;
    }

    /** Retire les restes d'une partie precedente (creatures, affichages) dans l'instance. */
    private void purgerEntites() {
        Instance inst = instance();
        if (inst == null || niveau == null) return;
        for (Entity e : niveau.getEntities((Entity) null, inst.bornes().inflate(8),
                e -> e.getPersistentData().contains(ZombiesApi.TAG_PARTIE) || e.getPersistentData().contains(ZombiesApi.TAG_DECOR))) {
            e.discard();
        }
    }

    /**
     * Remet les blocs des portes tels que dans la structure d'origine (robuste a un crash en pleine partie). Les etats
     * d'origine des cases des portes sont lus une fois dans la structure deja chargee par les arenes, puis seules les
     * cases differentes sont reecrites. Avant : 17 poses vanilla de la structure entiere (218 000 entrees chacune), soit
     * 1 a 2 s de gel au debut et a la fin de chaque partie.
     */
    private void restaurerStructure() {
        if (niveau == null || plan == null || volumesARestaurer().isEmpty()) return;
        long t0 = System.nanoTime();
        if (etatsPortes == null) etatsPortes = lireEtatsPortes();
        if (etatsPortes == null) return;
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        int remis = 0;
        for (BoundingBox b : volumesARestaurer()) {
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        c.set(x, y, z);
                        BlockState voulu = etatsPortes.getOrDefault(c.asLong(), air);
                        if (niveau.getBlockState(c) != voulu) {
                            niveau.setBlock(c, voulu, Block.UPDATE_CLIENTS);
                            remis++;
                        }
                    }
                }
            }
        }
        if (remis > 0) HeraliszMatchs.LOG.info("[zombies] {} bloc(s) de portes remis comme a l'origine", remis);
        HeraliszMatchs.LOG.debug("[zombies] restauration des portes et volumes : {} us", (System.nanoTime() - t0) / 1000);
    }

    /** Volumes remis comme la carte au debut et a la fin : portes, et v3 passerelle, rideau du Pack-a-Punch, tas de neige. */
    private List<BoundingBox> volumesARestaurer() {
        List<BoundingBox> l = new ArrayList<>();
        for (PlanZombies.Porte po : plan.portes()) l.add(po.boite());
        if (plan.giant() != null) l.addAll(plan.giant().volumesARestaurer());
        return l;
    }

    /** Etats d'origine (cases absolues) des blocs non vides de la structure situes dans une porte, ou null. */
    @Nullable
    private Long2ObjectOpenHashMap<BlockState> lireEtatsPortes() {
        Structure st = ArenesService.structure(niveau.getServer(), carte.id());
        if (st == null) {
            HeraliszMatchs.LOG.warn("[zombies] structure {} introuvable : portes non restaurees", carte.id());
            return null;
        }
        BlockPos o = plan.origine();
        // enveloppe des portes (calculee a part : BoundingBox.encapsulate modifie la boite)
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        List<BoundingBox> vols = volumesARestaurer();
        for (BoundingBox b : vols) {
            x0 = Math.min(x0, b.minX());
            y0 = Math.min(y0, b.minY());
            z0 = Math.min(z0, b.minZ());
            x1 = Math.max(x1, b.maxX());
            y1 = Math.max(y1, b.maxY());
            z1 = Math.max(z1, b.maxZ());
        }
        Long2ObjectOpenHashMap<BlockState> etats = new Long2ObjectOpenHashMap<>();
        for (Structure.Bloc bl : st.blocs) {
            int x = o.getX() + bl.x(), y = o.getY() + bl.y(), z = o.getZ() + bl.z();
            if (x < x0 || x > x1 || y < y0 || y > y1 || z < z0 || z > z1) continue;
            for (BoundingBox vb : vols) {
                if (vb.isInside(x, y, z)) {
                    etats.put(BlockPos.asLong(x, y, z), bl.etat());
                    break;
                }
            }
        }
        return etats;
    }

    private void preparerJoueur(ServerPlayer p) {
        p.setGameMode(GameType.ADVENTURE);
        p.setForcedPose(null);
        p.setCamera(p);
        retirerModificateur(p, Attributes.MAX_HEALTH, MOD_BLINDAGE);
        retirerModificateur(p, Attributes.MOVEMENT_SPEED, MOD_VIGUEUR);
        retirerModificateur(p, Attributes.MOVEMENT_SPEED, MOD_A_TERRE);
        p.setHealth(p.getMaxHealth());
        p.setAbsorptionAmount(0f);
        p.getFoodData().setFoodLevel(17);
        p.getFoodData().setSaturation(0f);
        p.removeAllEffects();
        p.clearFire();
    }

    /** Equipement de depart (ou de retour apres une mort) : pistolet, couteau, 2 grenades. */
    private void donnerDepart(ServerPlayer p, boolean vider) {
        Inventory inv = p.getInventory();
        if (vider) Equipement.purger(inv);
        ItemStack pistolet = Tacz.arme(PISTOLET, 4);
        if (!pistolet.isEmpty()) inv.setItem(0, Equipement.marquer(pistolet, id()));
        ItemStack c = objet(COUTEAU, 1);
        if (!c.isEmpty()) inv.setItem(SLOT_COUTEAU, Equipement.marquer(c, id()));
        ItemStack g = objet(GRENADE, 2);
        if (!g.isEmpty()) inv.setItem(SLOT_GRENADES, Equipement.marquer(g, id()));
        inv.selected = 0;
        p.connection.send(new ClientboundSetCarriedItemPacket(0));
        inv.setChanged();
    }

    /**
     * Objet de depart : les contenus heralisz_tactical ({@code heralisz_melee:<id>}, {@code heralisz_grenade:<id>}) sont
     * des porteurs generiques avec un id en NBT, construits par le moteur ; tout autre id est un objet du registre.
     */
    private static ItemStack objet(String id, int n) {
        return fr.heralisz.matchs.moteur.compat.Tacz.objet(id, n);
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        switch (phase()) {
            case ATTENTE -> {
                if (ticksPhase() > 20 * 90) {
                    HeraliszMatchs.LOG.warn("[zombies] {} : instance jamais prete, partie annulee", id());
                    annonceTous("zombies.refus.instance");
                    rendreTous();
                    fermer();
                }
            }
            case COMBAT -> tickJeu();
            case FIN -> {
                if (ticksPhase() >= FIN_TICKS) {
                    rendreTous();
                    nettoyer();
                    fermer();
                }
            }
            default -> {
            }
        }
    }

    private void tickJeu() {
        if (plan == null || niveau == null) return;
        long t = ticks();
        eteindreFeux();
        remplirEnLigne();
        for (int i = 0; i < enLigne.size(); i++) tickJoueur(enLigne.get(i), t);
        // acte IV de The Giant (boss N-07) : manches suspendues (ni apparition, ni garde-fou, ni pause qui avance)
        if (acte == null || !acte.bloqueManches()) {
            ticksEtape++;
            switch (etape) {
                case DEPART -> {
                    if (ticksEtape >= Manches.DEPART_TICKS) commencerManche();
                }
                case PAUSE -> {
                    if (ticksEtape >= Manches.PAUSE_TICKS) commencerManche();
                }
                case MANCHE -> tickManche(t);
            }
        }
        tickZombies(t);
        tickBonus(t);
        tickMateriaux(t);
        if (boite != null) boite.tick();
        if (pap != null) pap.tick();
        if (giant != null) giant.tick(t);
        if (acte != null) acte.tick(t);
        if (finDePartie()) {
            terminer(null);
            return;
        }
        if (hudSale && t % 2 == 0) envoyerEtat();
        if (t % 10 == 0) envoyerLieux(t % 200 == 0);
    }

    /**
     * Nom du lieu sous la boussole du HUD (D-049) : zone de la carte ou se trouve chaque joueur, envoyee a lui seul
     * quand elle change (entre deux zones, la derniere reste affichee) ; {@code forcer} = renvoi periodique
     * (reconnexion).
     */
    private void envoyerLieux(boolean forcer) {
        for (int i = 0; i < enLigne.size(); i++) {
            ServerPlayer p = enLigne.get(i);
            JoueurZombies j = joueurs.get(p.getUUID());
            if (j == null) continue;
            PlanZombies.Zone z = plan.zoneA(p.getX(), p.getY() + 0.5, p.getZ());
            // v3 : un joueur tombe dans une zone de saut encore fermee (la cour) : elle s'ouvre aux apparitions
            if (z != null && j.debout() && giant != null && giant.plan.sautsVers.containsKey(z.id()) && !zoneOuverte(z.id())) {
                ouvrirZone(z.id());
                HeraliszMatchs.LOG.info("[zombies] zone {} ouverte : {} y est arrive (saut)", z.id(), j.nom);
            }
            if (z == null && forcer && !j.zoneVue.isEmpty()) z = plan.zone(j.zoneVue);
            if (z == null || (!forcer && z.id().equals(j.zoneVue))) continue;
            j.zoneVue = z.id();
            MatchsNet.versJoueur(p, new PaquetsZombies.LieuS2C(z.nom(), z.nomEn()));
        }
    }

    private void remplirEnLigne() {
        enLigne.clear();
        for (JoueurZombies j : joueurs.values()) {
            if (!j.connecte) continue;
            ServerPlayer p = serveur().getPlayerList().getPlayer(j.id);
            if (p == null || p.hasDisconnected()) continue;
            enLigne.add(p);
        }
    }

    /** Copie de la liste des joueurs en ligne (pour les API qui gardent la collection). */
    private List<ServerPlayer> enLigneListe() {
        remplirEnLigne();
        return new ArrayList<>(enLigne);
    }

    /** Vrai si plus personne n'est debout (ni sur le point de se relever seul) parmi les joueurs encore la. */
    private boolean finDePartie() {
        for (JoueurZombies j : joueurs.values()) {
            if (!j.connecte) continue;
            if (j.debout() || (j.etat == HudZombies.A_TERRE && j.ticksAutoReleve > 0)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ joueurs
    private void tickJoueur(ServerPlayer p, long t) {
        JoueurZombies j = joueurs.get(p.getUUID());
        if (j == null) return;
        if (t % 40 == 0) {
            p.getFoodData().setFoodLevel(17);
            p.getFoodData().setSaturation(0f);
        }
        if (t % 20 == 0) {
            garderDansLesBornes(p, j);
            if (j.debout() && j.aAtout(TypeAtout.RECHARGEMENT)) rechargementEclair(p);
            int pl = plaques(p);
            if (pl != j.plaquesVues) {
                j.plaquesVues = pl;
                hudSale = true;
            }
        }
        switch (j.etat) {
            case HudZombies.DEBOUT -> {
                // regeneration facon CoD : rien pendant 2,5 s apres un coup, puis 8 PV/s (pleine vie en ~2,5 s, ~6 s avec Blindage)
                if (t % 5 == 0 && t - j.dernierDegat > 50 && p.getHealth() < p.getMaxHealth()) p.heal(2f);
                boolean appui = j.appui;
                j.appui = false;
                if ((t + j.couleur) % 4 == 0 || appui) calculerCible(p, j);
                tickTouche(p, j, appui);
            }
            case HudZombies.A_TERRE -> tickATerre(p, j);
            default -> {
            }
        }
    }

    /** Atout Rechargement eclair : les armes rangees (cases 0 et 1) se rechargent depuis la reserve. */
    private void rechargementEclair(ServerPlayer p) {
        Inventory inv = p.getInventory();
        boolean change = false;
        for (int s : new int[]{0, 1, SLOT_MULE}) if (s != inv.selected && Tacz.rechargerDepuisReserve(inv.getItem(s))) change = true;
        if (change) inv.setChanged();
    }

    /** Plaques d'armure restantes (absorption / {@link #PV_PLAQUE}, arrondi au-dessus). */
    private static int plaques(ServerPlayer p) {
        return (int) Math.ceil(p.getAbsorptionAmount() / PV_PLAQUE - 0.01);
    }

    private void garderDansLesBornes(ServerPlayer p, JoueurZombies j) {
        Instance inst = instance();
        if (inst == null) return;
        // les morts sont geres par le spectateur borne du moteur (camera sur un coequipier, bornes imposees)
        if (j.etat != HudZombies.MORT && (p.level() != niveau || !inst.contient(p.getX(), p.getY(), p.getZ()))) surSortieBornes(p);
    }

    @Nullable
    private ServerPlayer allieDebout(UUID sauf) {
        for (int i = 0; i < enLigne.size(); i++) {
            ServerPlayer o = enLigne.get(i);
            JoueurZombies jo = joueurs.get(o.getUUID());
            if (jo != null && jo.debout() && !o.getUUID().equals(sauf)) return o;
        }
        return null;
    }

    @Override
    public void surSortieBornes(ServerPlayer joueur) {
        if (plan == null || niveau == null) return;
        ServerPlayer allie = allieDebout(joueur.getUUID());
        if (allie != null && allie.level() == niveau) {
            joueur.teleportTo(niveau, allie.getX(), allie.getY(), allie.getZ(), joueur.getYRot(), 0f);
        } else if (!plan.spawnJoueurs().isEmpty()) {
            plan.spawnJoueurs().get(0).teleporter(joueur, niveau);
        }
    }

    @Override
    public float surDegats(ServerPlayer victime, @Nullable LivingEntity attaquant, DamageSource source, float montant) {
        if (phase() != Phase.COMBAT) return 0f;
        JoueurZombies j = joueurs.get(victime.getUUID());
        if (j == null || !j.debout()) return 0f;
        if (attaquant instanceof ServerPlayer && attaquant != victime) return 0f;
        if (acte != null) {
            // acte IV : personne n'est blesse pendant l'intro, les cinematiques du boss et l'outro
            if (acte.protege()) return 0f;
            // coup du boss N-07 (ou irradiation, sans attaquant) : montant remappe aux 20 / 50 PV, exact (le reglage de
            // difficulte du serveur l'a modifie avant cet evenement) ; un coup mortel met a terre comme toujours
            if (j.degatsBossTick == serveur().getTickCount() && (attaquant == null || attaquant.getPersistentData().getBoolean(ZombiesApi.TAG_BOSS))) {
                montant = j.degatsBoss;
                j.degatsBossTick = -1;
            }
        }
        // ses propres explosifs (lance-roquettes, grenade) : egratignure seulement, jamais de suicide
        if (attaquant == victime && source.is(DamageTypeTags.IS_EXPLOSION)) montant = Math.min(montant, 3f);
        // coup d'une creature de la partie : montant de la manche (Manches.degats, 4 -> 10 au plafond de la manche 20),
        // sans le reglage de difficulte du serveur (en facile, vanilla le divise par 2 avant cet evenement)
        if (attaquant != null) {
            ZombieSuivi s = parId.get(attaquant.getId());
            if (s != null) montant = Manches.degats(manche, s.type == TypeZombie.RODEUR);
        }
        j.dernierDegat = ticks();
        return montant;
    }

    @Override
    public void surElimination(ServerPlayer victime, @Nullable LivingEntity tueur, DamageSource source) {
        JoueurZombies j = joueurs.get(victime.getUUID());
        if (j == null || phase() != Phase.COMBAT) {
            victime.setHealth(victime.getMaxHealth());
            return;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && niveau != null) surSortieBornes(victime);
        if (j.debout()) mettreATerre(victime, j);
        else victime.setHealth(Math.max(1f, victime.getHealth()));
    }

    @Override
    public void surDeconnexion(ServerPlayer joueur) {
        JoueurZombies j = joueurs.get(joueur.getUUID());
        if (j == null) return;
        j.connecte = false;
        if (j.etat != HudZombies.MORT) j.etat = HudZombies.MORT;
        equipe.eliminer(j.id);
        if (acte != null) acte.deconnexion(j.id);
        MatchApi.quitter(joueur.getUUID());
        hudSale = true;
    }

    /** {@code /zombies quitter} : le joueur abandonne, son Coffre lui est rendu tout de suite. */
    void abandonner(ServerPlayer p) {
        JoueurZombies j = joueurs.get(p.getUUID());
        if (j == null) return;
        j.connecte = false;
        j.etat = HudZombies.MORT;
        equipe.eliminer(j.id);
        rendre(p);
        hudSale = true;
    }

    private void mettreATerre(ServerPlayer p, JoueurZombies j) {
        boolean solo = joueursConnectes() <= 1;
        boolean releveSolo = solo && j.aAtout(TypeAtout.REANIMATION) && j.relevesSolo > 0;
        boolean kit = !releveSolo && j.autoReanimations > 0;
        // Coup de mule : la 3e arme est perdue en tombant (CoD)
        if (j.aAtout(TypeAtout.MULE_KICK)) {
            ItemStack troisieme = p.getInventory().getItem(SLOT_MULE);
            if (!troisieme.isEmpty() && Tacz.gunId(troisieme) != null) p.getInventory().setItem(SLOT_MULE, ItemStack.EMPTY);
        }
        retirerAtouts(p, j);
        j.etat = HudZombies.A_TERRE;
        j.aTerreFois++;
        j.ticksATerre = A_TERRE_TICKS;
        j.ticksAutoReleve = releveSolo ? 100 : (kit ? 60 : 0);
        if (releveSolo) j.relevesSolo--;
        if (kit) j.autoReanimations--;
        j.touche = false;
        j.ticksMaintien = 0;
        j.cible = JoueurZombies.Cible.AUCUNE;
        j.cibleIndex = -1;
        envoyerInvite(p, j);
        p.setHealth(p.getMaxHealth());
        p.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
        ajouterModificateur(p, Attributes.MOVEMENT_SPEED, MOD_A_TERRE, "heralisz_zombies_a_terre", -0.65, AttributeModifier.Operation.MULTIPLY_TOTAL);
        // pistolet seulement
        Inventory inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < 9 && slot < 0; i++) {
            String g = Tacz.gunId(inv.getItem(i));
            if (g != null && "pistol".equalsIgnoreCase(Tacz.type(g))) slot = i;
        }
        if (slot < 0) {
            ItemStack pist = Tacz.arme(PISTOLET, 2);
            if (!pist.isEmpty()) {
                slot = SLOT_PISTOLET_SECOURS;
                j.objetRemplace = inv.getItem(slot).copy();
                inv.setItem(slot, Equipement.marquer(pist, id()));
                j.pistoletPrete = true;
            }
        }
        j.slotATerre = slot;
        if (slot >= 0) {
            inv.selected = slot;
            p.connection.send(new ClientboundSetCarriedItemPacket(slot));
        }
        son(ZombiesApi.Sons.A_TERRE, p.getX(), p.getY(), p.getZ(), 1f);
        evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.ETAT, j.id, 0, HudZombies.A_TERRE, j.nom));
        for (ZombieSuivi s : zombies) if (s.mob.getTarget() == p) s.mob.setTarget(null);
        hudSale = true;
    }

    private void tickATerre(ServerPlayer p, JoueurZombies j) {
        if (j.slotATerre >= 0 && p.getInventory().selected != j.slotATerre) {
            p.getInventory().selected = j.slotATerre;
            p.connection.send(new ClientboundSetCarriedItemPacket(j.slotATerre));
        }
        if (p.getForcedPose() != net.minecraft.world.entity.Pose.SWIMMING) p.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
        if (j.ticksAutoReleve > 0) {
            if (--j.ticksAutoReleve == 0) relever(p, j, null);
            return;
        }
        if (enCoursDeReanimation(j.id)) return;
        if (--j.ticksATerre <= 0) mourir(p, j);
        else if (j.ticksATerre % 20 == 0) hudSale = true;
    }

    private boolean enCoursDeReanimation(UUID cible) {
        for (JoueurZombies o : joueurs.values()) {
            if (o.connecte && o.debout() && o.touche && o.cible == JoueurZombies.Cible.REANIMER && cible.equals(o.cibleJoueur)) return true;
        }
        return false;
    }

    private void relever(ServerPlayer p, JoueurZombies j, @Nullable JoueurZombies par) {
        j.etat = HudZombies.DEBOUT;
        j.ticksATerre = 0;
        j.ticksAutoReleve = 0;
        p.setForcedPose(null);
        retirerModificateur(p, Attributes.MOVEMENT_SPEED, MOD_A_TERRE);
        restaurerSlotATerre(p, j);
        p.setHealth(p.getMaxHealth());
        j.dernierDegat = ticks();
        if (par != null) par.reanimations++;
        son(ZombiesApi.Sons.REANIME, p.getX(), p.getY(), p.getZ(), 1f);
        evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.ETAT, j.id, 0, HudZombies.DEBOUT, j.nom));
        hudSale = true;
    }

    private void restaurerSlotATerre(ServerPlayer p, JoueurZombies j) {
        if (j.pistoletPrete && j.slotATerre >= 0) {
            p.getInventory().setItem(j.slotATerre, j.objetRemplace);
            j.objetRemplace = ItemStack.EMPTY;
            j.pistoletPrete = false;
        }
        j.slotATerre = -1;
    }

    private void mourir(ServerPlayer p, JoueurZombies j) {
        j.etat = HudZombies.MORT;
        j.bowie = false;
        j.ticksATerre = 0;
        equipe.eliminer(j.id);
        p.setForcedPose(null);
        retirerModificateur(p, Attributes.MOVEMENT_SPEED, MOD_A_TERRE);
        restaurerSlotATerre(p, j);
        Equipement.purger(p.getInventory());
        p.setAbsorptionAmount(0f);
        Spectateur.suivre(p, this);
        son(ZombiesApi.Sons.MORT, p.getX(), p.getY(), p.getZ(), 1f);
        evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.ETAT, j.id, 0, HudZombies.MORT, j.nom));
        hudSale = true;
    }

    /** Les morts reviennent au debut de la manche suivante, pres d'un allie debout. */
    private void ramenerMorts() {
        for (int i = 0; i < enLigne.size(); i++) {
            ServerPlayer p = enLigne.get(i);
            JoueurZombies j = joueurs.get(p.getUUID());
            if (j == null || j.etat != HudZombies.MORT) continue;
            j.etat = HudZombies.DEBOUT;
            equipe.ranimer(j.id);
            Spectateur.arreter(p, false);
            preparerJoueur(p);
            ServerPlayer allie = allieDebout(p.getUUID());
            if (allie != null) p.teleportTo(niveau, allie.getX(), allie.getY(), allie.getZ(), allie.getYRot(), 0f);
            else if (!plan.spawnJoueurs().isEmpty()) plan.spawnJoueurs().get(0).teleporter(p, niveau);
            donnerDepart(p, true);
            j.dernierDegat = ticks();
            evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.ETAT, j.id, 0, HudZombies.DEBOUT, j.nom));
        }
    }

    int joueursConnectes() {
        int n = 0;
        for (JoueurZombies j : joueurs.values()) if (j.connecte) n++;
        return n;
    }

    // ------------------------------------------------------------------ atouts
    private void appliquerAtout(ServerPlayer p, JoueurZombies j, TypeAtout a) {
        j.atouts |= a.bit();
        switch (a) {
            case BLINDAGE -> {
                ajouterModificateur(p, Attributes.MAX_HEALTH, MOD_BLINDAGE, "heralisz_zombies_blindage", Manches.PV_BLINDAGE,
                        AttributeModifier.Operation.ADDITION);
                p.setHealth(p.getMaxHealth());
            }
            case VIGUEUR -> ajouterModificateur(p, Attributes.MOVEMENT_SPEED, MOD_VIGUEUR, "heralisz_zombies_vigueur", 0.15,
                    AttributeModifier.Operation.MULTIPLY_TOTAL);
            case RECHARGEMENT -> rechargementEclair(p);
            default -> {
            }
        }
        hudSale = true;
    }

    /**
     * Bouteille d'atout gratuite (0.2.3, grenade cachee de The Giant) : un atout de la carte que le joueur n'a pas encore,
     * bu tout de suite (meme animation qu'un achat). Rend faux s'il les a deja tous (ou s'il n'est pas debout).
     */
    boolean atoutGratuit(ServerPlayer p) {
        JoueurZombies j = joueurs.get(p.getUUID());
        if (j == null || !j.debout()) return false;
        List<TypeAtout> l = new ArrayList<>();
        for (int k = 0; k < atouts.size(); k++) {
            TypeAtout t = atouts.get(k).type();
            if (!j.aAtout(t) && !l.contains(t)) l.add(t);
        }
        if (l.isEmpty()) return false;
        TypeAtout a = l.get(alea.nextInt(l.size()));
        appliquerAtout(p, j, a);
        son(ZombiesApi.Sons.ATOUT_BU, p.getX(), p.getY(), p.getZ(), 1f);
        evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.ATOUT, j.id, 0, a.ordinal(), j.nom));
        return true;
    }

    private void retirerAtouts(ServerPlayer p, JoueurZombies j) {
        j.atouts = 0;
        retirerModificateur(p, Attributes.MAX_HEALTH, MOD_BLINDAGE);
        retirerModificateur(p, Attributes.MOVEMENT_SPEED, MOD_VIGUEUR);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        hudSale = true;
    }

    private static void ajouterModificateur(ServerPlayer p, Attribute attr, UUID id, String nom, double v, AttributeModifier.Operation op) {
        AttributeInstance ai = p.getAttribute(attr);
        if (ai == null) return;
        if (ai.getModifier(id) != null) ai.removeModifier(id);
        ai.addTransientModifier(new AttributeModifier(id, nom, v, op));
    }

    private static void retirerModificateur(ServerPlayer p, Attribute attr, UUID id) {
        AttributeInstance ai = p.getAttribute(attr);
        if (ai != null && ai.getModifier(id) != null) ai.removeModifier(id);
    }

    // ------------------------------------------------------------------ interaction
    /** Etat de la touche d'interaction (paquet C2S). */
    void touche(ServerPlayer p, boolean enfoncee) {
        JoueurZombies j = joueurs.get(p.getUUID());
        if (j == null) return;
        if (enfoncee && !j.touche) j.appui = true;
        j.touche = enfoncee;
        if (!enfoncee) j.ticksMaintien = 0;
    }

    /** Appui unique (clic droit sur un bloc du contenu, echange de main intercepte). */
    public boolean interagir(ServerPlayer p) {
        JoueurZombies j = joueurs.get(p.getUUID());
        if (j == null || phase() != Phase.COMBAT || !j.debout()) return false;
        calculerCible(p, j);
        if (BANC) HeraliszMatchs.LOG.info("[zombies] appui de {} : cible {} {}", j.nom, j.cible, j.cibleIndex);
        if (j.cible == JoueurZombies.Cible.AUCUNE) return false;
        acheter(p, j);
        return true;
    }

    private void tickTouche(ServerPlayer p, JoueurZombies j, boolean appui) {
        if (!j.touche) {
            if (j.ticksMaintien != 0) j.ticksMaintien = 0;
            // appui tres bref (enfonce puis relache dans le meme tick serveur) : l'achat compte quand meme
            if (appui && j.cible != JoueurZombies.Cible.FENETRE && j.cible != JoueurZombies.Cible.REANIMER
                    && !(j.cible == JoueurZombies.Cible.GIANT && giant != null && giant.maintien(j.cibleIndex) > 0)) acheter(p, j);
            envoyerInvite(p, j);
            return;
        }
        switch (j.cible) {
            case FENETRE -> {
                j.ticksMaintien++;
                if (j.ticksMaintien % REPARATION_TICKS == 0) reparer(p, j, j.cibleIndex);
            }
            case REANIMER -> {
                ServerPlayer cible = j.cibleJoueur == null ? null : joueur(j.cibleJoueur);
                JoueurZombies jc = j.cibleJoueur == null ? null : joueurs.get(j.cibleJoueur);
                if (cible == null || jc == null || jc.etat != HudZombies.A_TERRE || cible.distanceToSqr(p) > 2.4 * 2.4) {
                    j.ticksMaintien = 0;
                    break;
                }
                j.ticksMaintien++;
                int besoin = j.aAtout(TypeAtout.REANIMATION) ? REANIMATION_TICKS / 2 : REANIMATION_TICKS;
                if (j.ticksMaintien >= besoin) {
                    j.ticksMaintien = 0;
                    relever(cible, jc, j);
                }
            }
            case GIANT -> {
                int besoin = giant == null ? 0 : giant.maintien(j.cibleIndex);
                if (besoin <= 0) {
                    if (appui) acheter(p, j);
                } else if (++j.ticksMaintien >= besoin) {
                    j.ticksMaintien = 0;
                    acheter(p, j);
                }
            }
            default -> {
                if (appui) acheter(p, j);
            }
        }
        envoyerInvite(p, j);
    }

    /** Cible d'interaction la plus proche devant le joueur. */
    private void calculerCible(ServerPlayer p, JoueurZombies j) {
        JoueurZombies.Cible avant = j.cible;
        int avantIdx = j.cibleIndex;
        j.cible = JoueurZombies.Cible.AUCUNE;
        j.cibleIndex = -1;
        j.cibleJoueur = null;
        if (plan == null || !j.debout() || p.level() != niveau) {
            finCible(j, avant, avantIdx);
            return;
        }
        Vec3 oeil = p.getEyePosition();
        Vec3 regard = p.getLookAngle();
        // 1. reanimer un allie a terre
        for (int i = 0; i < enLigne.size(); i++) {
            ServerPlayer o = enLigne.get(i);
            JoueurZombies jo = joueurs.get(o.getUUID());
            if (o == p || jo == null || jo.etat != HudZombies.A_TERRE) continue;
            if (o.distanceToSqr(p) <= 2.2 * 2.2) {
                j.cible = JoueurZombies.Cible.REANIMER;
                j.cibleJoueur = o.getUUID();
                finCible(j, avant, avantIdx);
                return;
            }
        }
        // 2. arme offerte par la boite, arme amelioree qui attend au Pack-a-Punch
        if (boite != null && boite.offerteA(j.id)) {
            Vec3 c = boite.centre();
            if (c != null && c.distanceToSqr(oeil) <= 3.2 * 3.2) {
                j.cible = JoueurZombies.Cible.BOITE_PRENDRE;
                finCible(j, avant, avantIdx);
                return;
            }
        }
        if (pap != null && (pap.offerteA(j.id) || pap.enCoursPour(j.id)) && pap.centre().distanceToSqr(oeil) <= 3.2 * 3.2) {
            j.cible = JoueurZombies.Cible.PAP_PRENDRE;
            finCible(j, avant, avantIdx);
            return;
        }
        double meilleur = Double.MAX_VALUE;
        // portes
        for (int i = 0; i < plan.portes().size(); i++) {
            if (portesOuvertes[i]) continue;
            double d = distanceAabb(oeil, plan.portes().get(i).volume());
            if (d <= PORTEE * PORTEE && (d = penalite(d, oeil, regard, plan.portes().get(i).volume().getCenter())) < meilleur) {
                meilleur = d;
                j.cible = JoueurZombies.Cible.PORTE;
                j.cibleIndex = i;
            }
        }
        // fenetres (du cote interieur)
        for (PlanZombies.Fenetre f : plan.fenetres()) {
            if (planches[f.index()] >= PLANCHES_MAX) continue;
            if (f.cote(p.getX(), p.getZ()) > 0.2) continue;
            double d = distanceAabb(oeil, f.planches());
            if (d <= PORTEE * PORTEE && (d = penalite(d, oeil, regard, f.centre())) < meilleur) {
                meilleur = d;
                j.cible = JoueurZombies.Cible.FENETRE;
                j.cibleIndex = f.index();
            }
        }
        // armes au mur : tout pres du dessin et en le regardant
        double porteeMur = ConfigZombies.reel("achat", "arme_mur_distance", 1.6);
        for (PlanZombies.ArmeMur a : plan.armes()) {
            double d = viseeDessin(oeil, regard, a, porteeMur);
            if (d >= 0 && d < meilleur) {
                meilleur = d;
                j.cible = JoueurZombies.Cible.ARME;
                j.cibleIndex = a.index();
            }
        }
        // boite fermee
        if (boite != null && boite.achetable()) {
            Vec3 c = boite.centre();
            double d = c == null ? Double.MAX_VALUE : c.distanceToSqr(oeil);
            if (d <= PORTEE * PORTEE && (d = penalite(d, oeil, regard, c)) < meilleur) {
                meilleur = d;
                j.cible = JoueurZombies.Cible.BOITE_ACHAT;
            }
        }
        // atouts
        for (PlanZombies.Atout a : atouts) {
            if (!atoutVisible[a.index()] || j.aAtout(a.type())) continue;
            Vec3 c = Vec3.atCenterOf(a.pos());
            double d = c.distanceToSqr(oeil);
            if (d <= PORTEE * PORTEE && (d = penalite(d, oeil, regard, c)) < meilleur) {
                meilleur = d;
                j.cible = JoueurZombies.Cible.ATOUT;
                j.cibleIndex = a.index();
            }
        }
        // Pack-a-Punch (machine libre ou occupee par un autre : l'invite explique)
        if (pap != null) {
            Vec3 c = pap.centre();
            double d = c.distanceToSqr(oeil);
            if (d <= PORTEE * PORTEE && regarde(oeil, regard, c, 0.35) && (d = penalite(d, oeil, regard, c)) < meilleur) {
                meilleur = d;
                j.cible = JoueurZombies.Cible.PAP;
                // index = niveau de l'arme tenue (-1 sans arme) : l'invite change avec l'arme en main
                ItemStack tenue = p.getMainHandItem();
                j.cibleIndex = Tacz.gunId(tenue) != null ? ZombiesApi.niveauPap(tenue) : -1;
            }
        }
        // etablis
        for (int i = 0; i < ateliers.size(); i++) {
            Vec3 c = Vec3.atCenterOf(ateliers.get(i).pos());
            double d = c.distanceToSqr(oeil);
            if (d <= PORTEE * PORTEE && regarde(oeil, regard, c, 0.35) && (d = penalite(d, oeil, regard, c)) < meilleur) {
                meilleur = d;
                j.cible = JoueurZombies.Cible.ATELIER;
                j.cibleIndex = i;
            }
        }
        // easter egg : il faut regarder l'objet de pres
        if (!eeFini) {
            for (int i = 0; i < eeEtapes.size(); i++) {
                if (eeTrouvees[i]) continue;
                PlanZombies.EtapeEasterEgg e = eeEtapes.get(i);
                if (!zoneOuverte(e.zone())) continue;
                Vec3 c = Vec3.atCenterOf(e.pos());
                double d = c.distanceToSqr(oeil);
                if (d <= 2.4 * 2.4 && regarde(oeil, regard, c, 0.8) && d < meilleur) {
                    meilleur = d;
                    j.cible = JoueurZombies.Cible.EASTER_EGG;
                    j.cibleIndex = i;
                }
            }
        }
        // v3 : The Giant (fusibles, armoire, pads, mainframe, pieges, Bowie, bocaux, quete)
        if (giant != null) meilleur = giant.cible(p, j, oeil, regard, meilleur);
        // courant (levier v1)
        if (!courant && plan.courant() != null && (giant == null || !giant.aArmoire())) {
            Pose c = plan.courant();
            double d = c.distanceCarre(oeil.x, oeil.y - 0.6, oeil.z);
            if (d <= PORTEE * PORTEE && (d = penalite(d, oeil, regard, c.position())) < meilleur) {
                j.cible = JoueurZombies.Cible.COURANT;
            }
        }
        finCible(j, avant, avantIdx);
    }

    private static void finCible(JoueurZombies j, JoueurZombies.Cible avant, int avantIdx) {
        if (j.cible != avant || j.cibleIndex != avantIdx) j.ticksMaintien = 0;
    }

    private boolean zoneOuverte(String id) {
        if (id == null || id.isEmpty()) return true;
        int i = idsZones.indexOf(id);
        return i < 0 || zonesOuvertes[i];
    }

    /** Distance au carre du point au volume. */
    private static double distanceAabb(Vec3 o, AABB b) {
        double dx = Math.max(0, Math.max(b.minX - o.x, o.x - b.maxX));
        double dy = Math.max(0, Math.max(b.minY - o.y, o.y - b.maxY));
        double dz = Math.max(0, Math.max(b.minZ - o.z, o.z - b.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    /** Favorise ce qui est devant le regard (distance majoree pour ce qui est derriere). */
    private static double penalite(double d2, Vec3 oeil, Vec3 regard, Vec3 cible) {
        double vx = cible.x - oeil.x, vy = cible.y - oeil.y, vz = cible.z - oeil.z;
        double n = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (n < 1e-3) return d2;
        double dot = (vx * regard.x + vy * regard.y + vz * regard.z) / n;
        return dot >= 0.3 ? d2 : d2 + 4.0 * (1.3 - dot);
    }

    /** Vrai si le regard pointe vers la cible (cosinus de l'angle au moins {@code min}). */
    private static boolean regarde(Vec3 oeil, Vec3 regard, Vec3 cible, double min) {
        double vx = cible.x - oeil.x, vy = cible.y - oeil.y, vz = cible.z - oeil.z;
        double n = Math.sqrt(vx * vx + vy * vy + vz * vz);
        return n < 0.5 || (vx * regard.x + vy * regard.y + vz * regard.z) / n >= min;
    }

    /**
     * Arme au mur : distance au carre de l'oeil au dessin (rectangle de craie 2,4 x 1,2 sur le mur, centre sur la case de
     * l'arme), ou -1 si le joueur est a plus de {@code portee} du dessin, du mauvais cote, ou ne le regarde pas (le rayon
     * du regard doit toucher le dessin, a 35 cm pres).
     */
    static double viseeDessin(Vec3 oeil, Vec3 regard, PlanZombies.ArmeMur a, double portee) {
        Direction fc = a.face();
        double nx = fc.getStepX(), nz = fc.getStepZ();
        double px = a.pos().getX() + 0.5 - nx * 0.5, py = a.pos().getY() + 0.5, pz = a.pos().getZ() + 0.5 - nz * 0.5;
        double dn = (oeil.x - px) * nx + (oeil.z - pz) * nz;
        if (dn <= 0.05) return -1;
        double tx = -nz, tz = nx;
        double lat = (oeil.x - px) * tx + (oeil.z - pz) * tz;
        double dy = oeil.y - py;
        double cl = Math.max(-1.2, Math.min(1.2, lat)), cy = Math.max(-0.6, Math.min(0.6, dy));
        double d2 = dn * dn + (lat - cl) * (lat - cl) + (dy - cy) * (dy - cy);
        if (d2 > portee * portee) return -1;
        double rn = regard.x * nx + regard.z * nz;
        if (rn >= -0.05) return -1;
        double k = -dn / rn;
        double hl = lat + k * (regard.x * tx + regard.z * tz), hy = dy + k * regard.y;
        if (Math.abs(hl) > 1.2 + 0.35 || Math.abs(hy) > 0.6 + 0.35) return -1;
        return d2;
    }

    /** Achat ou action sur la cible courante (appui sur F). */
    private void acheter(ServerPlayer p, JoueurZombies j) {
        switch (j.cible) {
            case PORTE -> {
                if (j.cibleIndex < 0 || portesOuvertes[j.cibleIndex]) return;
                PlanZombies.Porte po = plan.portes().get(j.cibleIndex);
                if (po.courantRequis() && !courant) {
                    refus(p, "zombies.refus.courant");
                    return;
                }
                if (!payer(p, j, po.cout(), Component.translatable(po.eboulis() ? "zombies.achat.eboulis" : "zombies.achat.porte"))) return;
                ouvrirPorte(j.cibleIndex);
            }
            case ARME -> {
                PlanZombies.ArmeMur a = plan.armes().get(j.cibleIndex);
                int slot = slotArme(p, a.gunId());
                if (slot >= 0) {
                    ItemStack tenue = p.getInventory().getItem(slot);
                    int cout = ZombiesApi.estAmelioree(tenue) ? Math.max(a.coutMunitions(), 4500) : a.coutMunitions();
                    if (!payer(p, j, cout, Component.translatable("zombies.achat.munitions", nomArme(a.gunId())))) return;
                    Tacz.remplir(tenue);
                } else {
                    if (!Tacz.existe(a.gunId())) {
                        refus(p, "zombies.refus.arme");
                        return;
                    }
                    if (!payer(p, j, a.cout(), Component.translatable("zombies.achat.arme", nomArme(a.gunId())))) return;
                    donnerArme(p, a.gunId());
                    ZombiesApi.decor().armeAchetee(instance(), a);
                }
            }
            case BOITE_ACHAT -> {
                if (boite == null || !boite.achetable()) return;
                if (!payer(p, j, Boite.PRIX, Component.translatable("zombies.achat.boite"))) return;
                boite.acheter(p);
            }
            case BOITE_PRENDRE -> {
                if (boite != null) boite.prendre(p);
            }
            case ATOUT -> {
                PlanZombies.Atout a = atouts.get(j.cibleIndex);
                if (!courant) {
                    refus(p, "zombies.refus.courant");
                    return;
                }
                if (j.aAtout(a.type())) {
                    refus(p, "zombies.refus.deja");
                    return;
                }
                if (!payer(p, j, coutAtout(a), Component.translatable("zombies.achat.atout", Component.translatable(a.type().cle())))) return;
                appliquerAtout(p, j, a.type());
                son(ZombiesApi.Sons.ATOUT_BU, p.getX(), p.getY(), p.getZ(), 1f);
                evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.ATOUT, j.id, 0, a.type().ordinal(), j.nom));
            }
            case COURANT -> retablirCourant(p);
            case PAP -> acheterPap(p, j);
            case PAP_PRENDRE -> {
                if (pap != null && pap.offerteA(j.id)) pap.rendre(p);
            }
            case ATELIER -> ouvrirAtelier(p, j, j.cibleIndex);
            case EASTER_EGG -> etapeEasterEgg(p, j.cibleIndex);
            case GIANT -> {
                if (giant != null) giant.agir(p, j, j.cibleIndex);
            }
            default -> {
            }
        }
        j.inviteSale();
    }

    private int coutAtout(PlanZombies.Atout a) {
        return a.type() == TypeAtout.REANIMATION && joueursConnectes() <= 1 ? Math.max(1, a.cout() / 3) : a.cout();
    }

    private boolean payer(ServerPlayer p, JoueurZombies j, int cout, Component quoi) {
        if (j.points < cout) {
            refus(p, "zombies.refus.points");
            return false;
        }
        j.points -= cout;
        MatchsNet.versJoueur(p, PaquetsZombies.EvenementS2C.message(PaquetsZombies.EvenementS2C.ACHAT, quoi, cout));
        evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.POINTS, j.id, -cout, MotifPoints.ACHAT.ordinal(), ""));
        son(ZombiesApi.Sons.ACHAT, p.getX(), p.getY(), p.getZ(), 0.9f);
        if (BANC) HeraliszMatchs.LOG.info("[zombies] points {} -{} ACHAT (total {})", j.nom, cout, j.points);
        hudSale = true;
        return true;
    }

    private void refus(ServerPlayer p, String cle) {
        MatchsNet.versJoueur(p, PaquetsZombies.EvenementS2C.message(PaquetsZombies.EvenementS2C.REFUS, Component.translatable(cle), 0));
        SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(ZombiesApi.Sons.REFUS);
        if (ev != null) p.playNotifySound(ev, SoundSource.PLAYERS, 0.8f, 1f);
        if (BANC) HeraliszMatchs.LOG.info("[zombies] refus {} : {}", p.getGameProfile().getName(), cle);
    }

    /** Rend des points (peluche de la boite). */
    void rembourser(UUID joueur, int montant) {
        JoueurZombies j = joueurs.get(joueur);
        if (j == null) return;
        j.points += montant;
        evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.POINTS, j.id, montant, MotifPoints.AUTRE.ordinal(), ""));
        hudSale = true;
    }

    /**
     * Envoie l'invite d'achat si elle a change (cible, cout, moyens, progression). Le texte n'est construit qu'au
     * changement (aucune allocation sinon).
     */
    private void envoyerInvite(ServerPlayer p, JoueurZombies j) {
        int cout = 0;
        boolean abordable = true;
        int prog = -1;
        boolean possede = false;
        switch (j.cible) {
            case PORTE -> {
                PlanZombies.Porte po = plan.portes().get(j.cibleIndex);
                if (po.courantRequis() && !courant) abordable = false;
                else cout = po.cout();
            }
            case FENETRE -> prog = j.touche ? (j.ticksMaintien % REPARATION_TICKS) * 100 / REPARATION_TICKS : -1;
            case ARME -> {
                PlanZombies.ArmeMur a = plan.armes().get(j.cibleIndex);
                int s = slotArme(p, a.gunId());
                possede = s >= 0;
                cout = possede ? (ZombiesApi.estAmelioree(p.getInventory().getItem(s)) ? Math.max(a.coutMunitions(), 4500) : a.coutMunitions()) : a.cout();
            }
            case BOITE_ACHAT -> cout = Boite.PRIX;
            case ATOUT -> {
                if (courant) cout = coutAtout(atouts.get(j.cibleIndex));
                else abordable = false;
            }
            case GIANT -> {
                if (giant != null) {
                    QueteGiant.Invite iv = giant.invite(p, j, j.cibleIndex);
                    cout = iv.cout();
                    abordable = iv.abordable();
                    int besoin = giant.maintien(j.cibleIndex);
                    if (besoin > 0) prog = j.touche ? Math.min(100, j.ticksMaintien * 100 / besoin) : -1;
                }
            }
            case REANIMER -> {
                int besoin = j.aAtout(TypeAtout.REANIMATION) ? REANIMATION_TICKS / 2 : REANIMATION_TICKS;
                prog = j.touche ? Math.min(100, j.ticksMaintien * 100 / besoin) : -1;
            }
            case PAP -> {
                // prix du niveau suivant de l'arme tenue ; sans arme ou au niveau maximal : pas de prix
                int n = j.cibleIndex;
                if (pap != null && courant && pap.libre() && n >= 0 && n < PackAPunch.niveauMax()) cout = pap.cout(n + 1);
                else abordable = false;
            }
            case PAP_PRENDRE -> prog = pap != null ? pap.progression() : -1;
            default -> {
            }
        }
        if (cout > 0) abordable = abordable && j.points >= cout;
        if (possede) cout = -cout - 1; // distingue « munitions » d'« arme » dans la signature
        if (j.inviteCible == j.cible && j.inviteIndex == j.cibleIndex && j.inviteCout == cout && j.inviteAbordable == abordable
                && j.inviteProgression == prog) return;
        j.inviteCible = j.cible;
        j.inviteIndex = j.cibleIndex;
        j.inviteCout = cout;
        j.inviteAbordable = abordable;
        j.inviteProgression = prog;
        if (possede) cout = -cout - 1;
        Component texte = switch (j.cible) {
            case PORTE -> {
                PlanZombies.Porte po = plan.portes().get(j.cibleIndex);
                if (po.courantRequis() && !courant) yield Component.translatable("zombies.invite.courant_requis");
                yield Component.translatable(switch (po.type()) {
                    case "debris" -> "zombies.invite.eboulis";
                    case "grille" -> "zombies.jeu.invite.grille";
                    default -> po.eboulis() ? "zombies.invite.eboulis" : "zombies.invite.porte";
                });
            }
            case FENETRE -> Component.translatable("zombies.invite.fenetre");
            case ARME -> Component.translatable(possede ? "zombies.invite.munitions" : "zombies.invite.arme",
                    nomArme(plan.armes().get(j.cibleIndex).gunId()));
            case BOITE_ACHAT -> Component.translatable("zombies.invite.boite");
            case BOITE_PRENDRE -> boite != null && boite.tirage != null ? Component.translatable("zombies.invite.boite_prendre", nomTirage(boite.tirage)) : null;
            case GIANT -> giant == null ? null : giant.invite(p, j, j.cibleIndex).texte();
            case ATOUT -> courant ? Component.translatable("zombies.invite.atout", Component.translatable(atouts.get(j.cibleIndex).type().cle()))
                    : Component.translatable("zombies.invite.courant_requis");
            case COURANT -> Component.translatable("zombies.invite.courant");
            case REANIMER -> {
                JoueurZombies jc = j.cibleJoueur == null ? null : joueurs.get(j.cibleJoueur);
                yield Component.translatable("zombies.invite.reanimer", jc == null ? "" : jc.nom);
            }
            case PAP -> {
                if (!courant) yield Component.translatable("zombies.invite.courant_requis");
                if (pap == null || !pap.libre()) yield Component.translatable("zombies.jeu.invite.pap_occupe");
                // « Pack-a-Punch II · 15 000 » : niveau que prendra l'arme tenue
                if (j.cibleIndex < 0) yield Component.translatable("zombies.jeu.invite.pap");
                if (j.cibleIndex >= PackAPunch.niveauMax()) yield Component.translatable("zombies.jeu.invite.pap_max");
                yield Component.translatable("zombies.jeu.invite.pap_niveau", PackAPunch.romain(j.cibleIndex + 1));
            }
            case PAP_PRENDRE -> pap != null && pap.offerteA(j.id) ? Component.translatable("zombies.jeu.invite.pap_prendre", pap.arme.getHoverName())
                    : Component.translatable("zombies.jeu.invite.pap_attente");
            case ATELIER -> Component.translatable("zombies.jeu.invite.atelier", j.materiaux);
            case EASTER_EGG -> Component.translatable("zombies.jeu.invite.ee." + typeEtape(eeEtapes.get(j.cibleIndex).type()));
            default -> null;
        };
        MatchsNet.versJoueur(p, new PaquetsZombies.InviteS2C(texte, cout, abordable, prog));
    }

    private static String typeEtape(String t) {
        return switch (t) {
            case "radio", "interrupteur" -> t;
            default -> "objet";
        };
    }

    /** Nom affiche de l'arme (traduit cote client, mis en cache). */
    Component nomArme(String gunId) {
        Component c = nomsArmes.get(gunId);
        if (c == null) {
            try {
                ItemStack s = Tacz.arme(gunId, 0);
                c = s.isEmpty() ? null : s.getHoverName();
            } catch (RuntimeException ex) {
                c = null;
            }
            if (c == null) c = Component.literal(gunId.substring(gunId.indexOf(':') + 1).toUpperCase(Locale.ROOT));
            nomsArmes.put(gunId, c);
        }
        return c;
    }

    /** Nom d'un tirage de la boite dans la couleur de sa rarete (« AK-47 · LEGENDAIRE »). */
    Component nomTirage(TableBoite.Tirage t) {
        int c = t.rarete().couleur;
        return Component.empty().append(nomArme(t.gunId()).copy().withStyle(s -> s.withColor(c).withBold(true)))
                .append(Component.literal(" · ").withStyle(s -> s.withColor(0xB8B8B8)))
                .append(Component.translatable(t.rarete().cle()).withStyle(s -> s.withColor(c)));
    }

    /** Pile d'apercu d'un tirage (roulette), mise en cache. */
    ItemStack pileApercu(TableBoite.Tirage t) {
        ItemStack s = apercus.get(t);
        if (s == null) {
            s = Tacz.arme(t.gunId(), t.accessoires(), 0);
            apercus.put(t, s);
        }
        return s;
    }

    /** Le tirage s'arrete : l'acheteur est prevenu (rarete a part, nom de l'arme seul dans la couleur de la rarete). */
    void annonceTirage(@Nullable UUID acheteur, TableBoite.Tirage t) {
        ServerPlayer p = acheteur == null ? null : joueur(acheteur);
        int c = t.rarete().couleur;
        if (p != null) MatchsNet.versJoueur(p, PaquetsZombies.EvenementS2C.composant(PaquetsZombies.EvenementS2C.BOITE, acheteur, 0, t.rarete().ordinal(),
                nomArme(t.gunId()).copy().withStyle(st -> st.withColor(c))));
    }

    String nomJoueur(@Nullable UUID u) {
        JoueurZombies j = u == null ? null : joueurs.get(u);
        return j == null ? "?" : j.nom;
    }

    // ------------------------------------------------------------------ armes
    /** Emplacement (0, 1, ou la case de la 3e arme) qui tient cette arme, ou -1. */
    private static int slotArme(ServerPlayer p, String gunId) {
        for (int i = 0; i < 2; i++) if (gunId.equals(Tacz.gunId(p.getInventory().getItem(i)))) return i;
        if (gunId.equals(Tacz.gunId(p.getInventory().getItem(SLOT_MULE)))) return SLOT_MULE;
        return -1;
    }

    /** Vrai si la case est une case d'arme du joueur (0, 1, et la 3e avec Coup de mule). */
    private boolean caseArme(ServerPlayer p, int slot) {
        if (slot == 0 || slot == 1) return true;
        JoueurZombies j = joueurs.get(p.getUUID());
        return slot == SLOT_MULE && j != null && j.aAtout(TypeAtout.MULE_KICK);
    }

    /** Armes possedees (emplacements 0 et 1, plus celle qui attend au Pack-a-Punch), pour exclure du tirage de la boite. */
    List<String> armesDe(@Nullable UUID joueur) {
        List<String> l = new ArrayList<>(3);
        ServerPlayer p = joueur == null ? null : joueur(joueur);
        if (p == null) return l;
        for (int i : new int[]{0, 1, SLOT_MULE}) {
            String g = Tacz.gunId(p.getInventory().getItem(i));
            if (g != null) l.add(g);
        }
        if (pap != null && joueur.equals(pap.proprietaire)) {
            String g = Tacz.gunId(pap.arme);
            if (g != null) l.add(g);
        }
        return l;
    }

    /**
     * Donne l'arme (chargee, 4 chargeurs en reserve) : emplacement libre 0 ou 1, sinon remplace l'arme tenue (si c'est
     * une des deux), sinon l'emplacement 1. Deux armes au plus, comme dans CoD.
     */
    void donnerArme(ServerPlayer p, @Nullable String gunId) {
        if (gunId == null) return;
        ItemStack s = Tacz.arme(gunId, 4);
        if (s.isEmpty()) {
            refus(p, "zombies.refus.arme");
            return;
        }
        placerArme(p, Equipement.marquer(s, id()), -1);
    }

    /** Donne l'arme d'un tirage de la boite : accessoires verifies, nom et lueur de rarete, marqueur de rarete. */
    void donnerTirage(ServerPlayer p, @Nullable TableBoite.Tirage t) {
        if (t == null) return;
        ItemStack s = Tacz.arme(t.gunId(), t.accessoires(), table == null ? 4 : table.chargeursReserve());
        if (s.isEmpty()) {
            refus(p, "zombies.refus.arme");
            return;
        }
        int c = t.rarete().couleur;
        s.setHoverName(s.getHoverName().copy().withStyle(st -> st.withColor(c).withItalic(false)));
        s.getOrCreateTag().putString(ZombiesApi.NBT_RARETE, t.rarete().id());
        CompoundTag display = s.getOrCreateTagElement("display");
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable(t.rarete().cle()).withStyle(st -> st.withColor(c).withItalic(false)))));
        display.put("Lore", lore);
        placerArme(p, Equipement.marquer(s, id()), -1);
        if (BANC) HeraliszMatchs.LOG.info("[zombies] arme de boite donnee a {} : {} {} {}", p.getGameProfile().getName(), t.rarete(), t.gunId(), t.accessoires());
    }

    /** Range l'arme : emplacement prefere s'il est libre, sinon 0/1 libre, sinon a la place de l'arme tenue (0/1), sinon 1. */
    private void placerArme(ServerPlayer p, ItemStack s, int prefere) {
        Inventory inv = p.getInventory();
        boolean mule = caseArme(p, SLOT_MULE);
        int slot;
        if (prefere >= 0 && caseArme(p, prefere) && inv.getItem(prefere).isEmpty()) slot = prefere;
        else if (inv.getItem(0).isEmpty()) slot = 0;
        else if (inv.getItem(1).isEmpty()) slot = 1;
        else if (mule && (inv.getItem(SLOT_MULE).isEmpty() || Tacz.gunId(inv.getItem(SLOT_MULE)) == null)) slot = SLOT_MULE;
        else slot = caseArme(p, inv.selected) ? inv.selected : 1;
        inv.setItem(slot, s);
        inv.selected = slot;
        p.connection.send(new ClientboundSetCarriedItemPacket(slot));
        inv.setChanged();
    }

    // ------------------------------------------------------------------ Pack-a-Punch
    private void acheterPap(ServerPlayer p, JoueurZombies j) {
        if (pap == null) return;
        if (!courant && ConfigZombies.vrai("pack_a_punch", "courant_requis", true)) {
            refus(p, "zombies.refus.courant");
            return;
        }
        if (!pap.libre()) {
            refus(p, "zombies.jeu.refus.pap_occupe");
            return;
        }
        Inventory inv = p.getInventory();
        int slot = inv.selected;
        ItemStack main = inv.getItem(slot);
        if (Tacz.gunId(main) == null || !caseArme(p, slot)) {
            refus(p, "zombies.jeu.refus.pap_arme");
            return;
        }
        // niveaux 1 -> 2 -> 3 : chaque passage monte l'arme d'un niveau, au-dela refus
        int niveau = ZombiesApi.niveauPap(main);
        if (niveau >= PackAPunch.niveauMax()) {
            refus(p, "zombies.jeu.refus.pap_max");
            return;
        }
        int cout = pap.cout(niveau + 1);
        if (!payer(p, j, cout, Component.translatable("zombies.jeu.achat.pap_niveau", PackAPunch.romain(niveau + 1), main.getHoverName()))) return;
        ItemStack a = main.copy();
        inv.setItem(slot, ItemStack.EMPTY);
        int autre = slot == 0 ? 1 : 0;
        if (slot != SLOT_MULE && !inv.getItem(autre).isEmpty()) {
            inv.selected = autre;
            p.connection.send(new ClientboundSetCarriedItemPacket(autre));
        }
        inv.setChanged();
        pap.inserer(p, a, slot);
        if (BANC) HeraliszMatchs.LOG.info("[zombies] Pack-a-Punch : {} insere {} (niveau {} -> {}, {} points)", j.nom, Tacz.gunId(a), niveau, niveau + 1, cout);
    }

    /** L'arme amelioree revient a son proprietaire (appele par {@link PackAPunch}). */
    void rendreArmePap(ServerPlayer p, ItemStack arme, int slot) {
        if (arme.isEmpty()) return;
        placerArme(p, arme, slot);
        son(ZombiesApi.Sons.ARME_PRISE, p.getX(), p.getY() + 1, p.getZ(), 1f);
        int niveau = ZombiesApi.niveauPap(arme);
        evenementTous(PaquetsZombies.EvenementS2C.composant(PaquetsZombies.EvenementS2C.PAP, p.getUUID(), 0, niveau, arme.getHoverName()));
        musiqueAccent(MomentMusique.PACK_A_PUNCH);
        // « Ameliorez une arme » : rempli des le niveau 1
        objectifRempli("pack_a_punch");
        CompoundTag t = arme.getTag();
        HeraliszMatchs.LOG.info("[zombies] Pack-a-Punch : {} reprend {} (chargeur {}, ameliore {}, niveau {}, degats x{}, reserve {}, camo {}, lueur {})",
                p.getGameProfile().getName(), Tacz.gunId(arme), Tacz.chargeur(arme), ZombiesApi.estAmelioree(arme), niveau,
                PackAPunch.multiplicateur(arme), Tacz.reserveActuelle(arme), t != null && t.contains("HeraliszCamo") ? t.getString("HeraliszCamo") : "-",
                t != null && t.contains("Enchantments"));
    }

    // ------------------------------------------------------------------ etablis
    private void ouvrirAtelier(ServerPlayer p, JoueurZombies j, int index) {
        if (index < 0 || index >= ateliers.size()) return;
        MatchsNet.versJoueur(p, paquetAtelier(p, j, index));
    }

    private PaquetsZombies.AtelierS2C paquetAtelier(ServerPlayer p, JoueurZombies j, int index) {
        List<PaquetsZombies.Recette> l = new ArrayList<>();
        for (Recettes.Recette r : Recettes.toutes()) {
            l.add(new PaquetsZombies.Recette(r.id(), Component.translatable("zombies.jeu.recette." + r.id()),
                    Component.translatable("zombies.jeu.recette." + r.id() + ".desc"), r.cout(), possede(p, j, r), r.max()));
        }
        return new PaquetsZombies.AtelierS2C(index, j.materiaux, l);
    }

    /** Quantite deja detenue pour une recette (plafond). */
    private int possede(ServerPlayer p, JoueurZombies j, Recettes.Recette r) {
        return switch (r.type()) {
            case "grenade" -> compte(p.getInventory().getItem(SLOT_GRENADES), r.objet());
            case "tactique" -> compte(p.getInventory().getItem(SLOT_TACTIQUE), r.objet());
            case "plaque" -> plaques(p);
            case "auto_reanimation" -> j.autoReanimations;
            default -> 0;
        };
    }

    private static int compte(ItemStack s, String id) {
        if (s.isEmpty()) return 0;
        CompoundTag t = s.getTag();
        if (t != null) {
            for (String cle : new String[]{"ThrowableId", "MeleeWeaponId", "ConsumableId"}) {
                if (t.contains(cle)) return id.equals(t.getString(cle)) ? s.getCount() : 0;
            }
        }
        ResourceLocation r = ForgeRegistries.ITEMS.getKey(s.getItem());
        return r != null && r.toString().equals(id) ? s.getCount() : 0;
    }

    /** Fabrication a l'etabli (paquet C2S revalide : partie, etat, distance, materiaux, plafond). */
    void fabriquer(ServerPlayer p, int index, String recetteId) {
        JoueurZombies j = joueurs.get(p.getUUID());
        if (j == null || !j.debout() || phase() != Phase.COMBAT || index < 0 || index >= ateliers.size()) return;
        PlanZombies.Atelier at = ateliers.get(index);
        if (Vec3.atCenterOf(at.pos()).distanceToSqr(p.getEyePosition()) > 4.0 * 4.0) {
            refus(p, "zombies.jeu.refus.atelier_loin");
            return;
        }
        Recettes.Recette r = Recettes.par(recetteId);
        if (r == null) return;
        if (r.max() > 0 && possede(p, j, r) >= r.max()) {
            refus(p, "zombies.jeu.refus.atelier_max");
            MatchsNet.versJoueur(p, paquetAtelier(p, j, index));
            return;
        }
        if (j.materiaux < r.cout()) {
            refus(p, "zombies.jeu.refus.materiaux");
            MatchsNet.versJoueur(p, paquetAtelier(p, j, index));
            return;
        }
        if (r.type().equals("conversion") && slotConversion(p, r) < 0) {
            refus(p, "zombies.jeu.refus.conversion");
            return;
        }
        if (!appliquerRecette(p, j, r)) {
            refus(p, "zombies.jeu.refus.atelier_impossible");
            return;
        }
        j.materiaux -= r.cout();
        j.plaquesVues = plaques(p);
        evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.MATERIAUX, j.id, -r.cout(), 0, ""));
        evenementTous(PaquetsZombies.EvenementS2C.composant(PaquetsZombies.EvenementS2C.FABRICATION, j.id, 0, 0,
                Component.translatable("zombies.jeu.recette." + r.id())));
        try {
            ZombiesApi.decor().atelier(instance(), at, r.id());
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.atelier", ex);
        }
        Vec3 c = Vec3.atCenterOf(at.pos());
        son(ZombiesApi.Sons.ATELIER, c.x, c.y, c.z, 1f);
        HeraliszMatchs.LOG.info("[zombies] etabli : {} fabrique {} ({} materiaux restants)", j.nom, r.id(), j.materiaux);
        MatchsNet.versJoueur(p, paquetAtelier(p, j, index));
        hudSale = true;
    }

    private boolean appliquerRecette(ServerPlayer p, JoueurZombies j, Recettes.Recette r) {
        Inventory inv = p.getInventory();
        switch (r.type()) {
            case "grenade", "tactique" -> {
                int slot = r.type().equals("grenade") ? SLOT_GRENADES : SLOT_TACTIQUE;
                ItemStack s = inv.getItem(slot);
                if (s.isEmpty() || compte(s, r.objet()) == 0) {
                    ItemStack n = objet(r.objet(), r.nombre());
                    if (n.isEmpty()) return false;
                    if (!s.isEmpty() && !Equipement.estPrete(s)) return false;
                    inv.setItem(slot, Equipement.marquer(n, id()));
                } else {
                    int max = r.max() > 0 ? r.max() : s.getMaxStackSize();
                    s.setCount(Math.min(Math.min(max, s.getMaxStackSize()), s.getCount() + r.nombre()));
                }
                inv.setChanged();
                return true;
            }
            case "plaque" -> {
                int max = r.max() > 0 ? r.max() : 3;
                p.setAbsorptionAmount(Math.min(max * PV_PLAQUE, (plaques(p) + r.nombre()) * PV_PLAQUE));
                return true;
            }
            case "auto_reanimation" -> {
                j.autoReanimations += r.nombre();
                return true;
            }
            case "conversion" -> {
                return convertir(p, r);
            }
            default -> {
                return false;
            }
        }
    }

    /** Emplacement de l'arme a convertir (l'arme tenue d'abord, puis les cases d'armes 0 et 1), -1 si absente. */
    private static int slotConversion(ServerPlayer p, Recettes.Recette r) {
        if (r.de() == null || r.de().isEmpty()) return -1;
        Inventory inv = p.getInventory();
        int[] ordre = {inv.selected, 0, 1};
        for (int s : ordre) {
            if (s >= 0 && s < 9 && r.de().equals(Tacz.gunId(inv.getItem(s)))) return s;
        }
        return -1;
    }

    /**
     * Recette « conversion » (ex. kit Acid Gat) : l'arme {@code de} devient l'arme {@code objet}, meme pile (niveau et
     * marqueurs de Pack-a-Punch, camo, accessoires, rarete, lore) ; chargeur plein et reserve du meme nombre de chargeurs ;
     * nom refait (« ★… » si elle etait amelioree).
     */
    private boolean convertir(ServerPlayer p, Recettes.Recette r) {
        int s = slotConversion(p, r);
        if (s < 0 || r.objet() == null || r.objet().isEmpty() || !Tacz.existe(r.objet())) return false;
        Inventory inv = p.getInventory();
        ItemStack ancienne = inv.getItem(s);
        int chargeurAvant = Math.max(1, Tacz.chargeur(ancienne));
        int chargeurs = Math.max(1, Math.round(Math.max(0, Tacz.reserveMax(ancienne)) / (float) chargeurAvant));
        ItemStack n = ancienne.copy();
        CompoundTag t = n.getOrCreateTag();
        t.putString("GunId", r.objet());
        boolean nomme = n.hasCustomHoverName();
        n.resetHoverName();
        Component origine = n.getHoverName().copy();
        Tacz.reserve(n, chargeurs);
        Tacz.remplir(n);
        if (nomme || ZombiesApi.estAmelioree(n)) PackAPunch.renommer(n, origine);
        inv.setItem(s, n);
        inv.setChanged();
        HeraliszMatchs.LOG.info("[zombies] etabli : {} convertit {} en {} (Pack-a-Punch {}, chargeur {}, reserve {})", p.getGameProfile().getName(),
                r.de(), r.objet(), ZombiesApi.niveauPap(n), Tacz.chargeur(n), Tacz.reserveActuelle(n));
        return true;
    }

    // ------------------------------------------------------------------ easter egg
    private void etapeEasterEgg(ServerPlayer p, int index) {
        if (eeFini || index < 0 || index >= eeEtapes.size() || eeTrouvees[index]) return;
        PlanZombies.EtapeEasterEgg e = eeEtapes.get(index);
        Instance inst = instance();
        if (index != eeProchaine) {
            try {
                ZombiesApi.decor().easterEgg(inst, e, ZombiesDecor.EtatEtape.REFUSEE);
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[zombies] decor.easterEgg", ex);
            }
            SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(ZombiesApi.Sons.REFUS);
            if (ev != null) p.playNotifySound(ev, SoundSource.PLAYERS, 0.5f, 0.7f);
            HeraliszMatchs.LOG.info("[zombies] easter egg : etape {} activee hors ordre (attendue {})", index + 1, eeProchaine + 1);
            return;
        }
        eeTrouvees[index] = true;
        eeProchaine++;
        try {
            ZombiesApi.decor().easterEgg(inst, e, ZombiesDecor.EtatEtape.TROUVEE);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.easterEgg", ex);
        }
        Vec3 c = Vec3.atCenterOf(e.pos());
        son(ZombiesApi.Sons.EASTER_EGG_ETAPE, c.x, c.y, c.z, 1f);
        boolean fin = eeProchaine >= eeEtapes.size();
        evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.EASTER_EGG, p.getUUID(), eeProchaine,
                eeEtapes.size() | (fin ? 0x10000 : 0), ""));
        HeraliszMatchs.LOG.info("[zombies] easter egg : etape {}/{} trouvee par {}", eeProchaine, eeEtapes.size(), p.getGameProfile().getName());
        if (fin) terminerEasterEgg();
    }

    private void terminerEasterEgg() {
        eeFini = true;
        Instance inst = instance();
        for (PlanZombies.EtapeEasterEgg e : eeEtapes) {
            try {
                ZombiesApi.decor().easterEgg(inst, e, ZombiesDecor.EtatEtape.TERMINE);
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[zombies] decor.easterEgg", ex);
            }
        }
        sonTous(ZombiesApi.Sons.EASTER_EGG_FIN);
        musiqueAccent(MomentMusique.EASTER_EGG);
        // recompense : celle de la carte si elle est reconnue, sinon celle de la configuration
        String r = eeRecompense == null ? "" : eeRecompense.trim();
        String id = r.toLowerCase(Locale.ROOT);
        boolean reconnue = id.startsWith("mort_instantanee") || id.equals("arme_carte") || (id.contains(":") && Tacz.existe(r));
        if (!reconnue) id = ConfigZombies.texte("easter_egg", "recompense", "mort_instantanee_2_manches").toLowerCase(Locale.ROOT);
        Component sous;
        if (id.equals("arme_carte") || id.contains(":")) {
            String g = id.contains(":") ? r : (table == null ? null : table.armeCarte());
            if (g != null && Tacz.existe(g)) {
                for (int i = 0; i < enLigne.size(); i++) {
                    ServerPlayer p = enLigne.get(i);
                    JoueurZombies j = joueurs.get(p.getUUID());
                    if (j == null || j.etat == HudZombies.MORT) continue;
                    // deja en main ou dans l'autre emplacement : munitions pleines plutot qu'un doublon (l'arme
                    // amelioree au Pack-a-Punch n'est jamais ecrasee)
                    int deja = -1;
                    for (int k = 0; k < 2; k++) if (g.equals(Tacz.gunId(p.getInventory().getItem(k)))) deja = k;
                    if (deja >= 0) {
                        Tacz.remplir(p.getInventory().getItem(deja));
                        p.getInventory().setChanged();
                        continue;
                    }
                    Inventory inv = p.getInventory();
                    if (!inv.getItem(0).isEmpty() && !inv.getItem(1).isEmpty() && inv.selected < 2
                            && ZombiesApi.estAmelioree(inv.getItem(inv.selected)) && !ZombiesApi.estAmelioree(inv.getItem(1 - inv.selected))) {
                        inv.selected = 1 - inv.selected;
                    }
                    donnerTirage(p, new TableBoite.Tirage(g, Map.of(), Rarete.MYTHIQUE, 1));
                }
                sous = Component.translatable("zombies.jeu.ee.recompense.arme", nomArme(g));
            } else {
                instaKillManches = 2;
                sous = Component.translatable("zombies.jeu.ee.recompense.mort_instantanee", 2);
            }
        } else {
            int n = 2;
            try {
                String[] parts = id.split("_");
                for (String s : parts) if (s.matches("\\d+")) n = Math.max(1, Math.min(10, Integer.parseInt(s)));
            } catch (RuntimeException ignored) {
            }
            instaKillManches = n;
            sous = Component.translatable("zombies.jeu.ee.recompense.mort_instantanee", n);
        }
        if (!reconnue && !r.isEmpty()) sous = Component.literal(r).withStyle(s -> s.withColor(0xFFD27A));
        // la grande banniere est celle du HUD (evenement EASTER_EGG termine) : ici seulement la recompense, dans le chat
        Component titre = Component.translatable("zombies.jeu.ee.titre").withStyle(s -> s.withColor(0xFFB327).withBold(true));
        Component ligne = Component.empty().append(titre).append(Component.literal("  ")).append(sous);
        remplirEnLigne();
        for (int i = 0; i < enLigne.size(); i++) Annonces.chat(enLigne.get(i), ligne);
        objectifRempli("mystere");
        HeraliszMatchs.LOG.info("[zombies] easter egg termine : recompense {} (mort instantanee {} manches)", id, instaKillManches);
        hudSale = true;
    }

    // ------------------------------------------------------------------ objectifs, musique
    private void objectifRempli(String id) {
        if (objectifs != null && objectifs.rempli(id)) {
            envoyerObjectif(true);
            sonTous(ZombiesApi.Sons.OBJECTIF);
        }
    }

    private void envoyerObjectif(boolean nouveau) {
        if (objectifs == null) return;
        remplirEnLigne();
        MatchsNet.versJoueurs(enLigne, objectifs.paquet(nouveau, ticks()));
        HeraliszMatchs.LOG.info("[zombies] objectif : {}", objectifs.courant());
    }

    /** v3 : renvoie l'objectif courant avec sa progression ({@code (2/3)}, chrono de liaison), sans animation d'entree. */
    void envoyerObjectifProgres() {
        if (objectifs == null) return;
        remplirEnLigne();
        MatchsNet.versJoueurs(enLigne, objectifs.paquet(false, ticks()));
    }

    /** Change l'ambiance musicale durable (etat client + evenement). */
    private void musique(MomentMusique m) {
        ambiance = m;
        remplirEnLigne();
        MatchsNet.versJoueurs(enLigne, PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.MUSIQUE, null, manche, m.ordinal(), ""));
    }

    /** Accent musical ponctuel (courant, Pack-a-Punch, easter egg) : l'ambiance durable ne change pas. */
    private void musiqueAccent(MomentMusique m) {
        remplirEnLigne();
        MatchsNet.versJoueurs(enLigne, PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.MUSIQUE, null, manche, m.ordinal() | 0x100, ""));
    }

    // ------------------------------------------------------------------ portes, zones, fenetres, courant
    private void ouvrirZone(String id) {
        int i = idsZones.indexOf(id);
        if (i >= 0) {
            if (zonesOuvertes[i]) return;
            zonesOuvertes[i] = true;
        }
        // v3 : les zones ou l'on tombe depuis celle-ci (la cour depuis les etages) deviennent actives aussi
        if (giant != null) {
            for (Map.Entry<String, List<String>> e : giant.plan.sautsVers.entrySet()) if (e.getValue().contains(id)) ouvrirZone(e.getKey());
        }
        // boite encore cachee (aucun emplacement ouvert au depart) : elle arrive dans la zone qui s'ouvre
        if (boite != null && boite.cachee()) boite.apparaitre();
    }

    private void ouvrirPorte(int i) {
        if (portesOuvertes[i]) return;
        portesOuvertes[i] = true;
        PlanZombies.Porte po = plan.portes().get(i);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos bp : BlockPos.betweenClosed(po.boite().minX(), po.boite().minY(), po.boite().minZ(),
                po.boite().maxX(), po.boite().maxY(), po.boite().maxZ())) {
            if (!niveau.getBlockState(bp).isAir()) niveau.setBlock(bp, air, 2);
        }
        for (String z : po.relie()) ouvrirZone(z);
        Vec3 c = po.volume().getCenter();
        son(po.eboulis() ? ZombiesApi.Sons.EBOULIS : ZombiesApi.Sons.PORTE, c.x, c.y, c.z, 1.2f);
        try {
            ZombiesApi.decor().porte(instance(), po, true);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.porte", ex);
        }
        objectifRempli("porte");
        hudSale = true;
    }

    private void reparer(ServerPlayer p, JoueurZombies j, int f) {
        if (f < 0 || f >= planches.length || planches[f] >= PLANCHES_MAX) return;
        planches[f]++;
        j.planches++;
        PlanZombies.Fenetre fe = plan.fenetres().get(f);
        try {
            ZombiesApi.decor().fenetre(instance(), fe, planches[f], ZombiesDecor.EvenementFenetre.REPAREE);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.fenetre", ex);
        }
        son(ZombiesApi.Sons.PLANCHE_POSE, fe.centre().x, fe.centre().y, fe.centre().z, 0.9f);
        if (j.planchesManche < ConfigZombies.entier("points", "planches_max_manche", 50)) {
            j.planchesManche++;
            gagner(j, ConfigZombies.entier("points", "planche", 10), MotifPoints.PLANCHE);
        }
    }

    private void retablirCourant(@Nullable ServerPlayer p) {
        if (courant) return;
        courant = true;
        Instance inst = instance();
        // v3 : portes de la cour qui s'ouvrent seules au courant
        for (int i = 0; i < plan.portes().size(); i++) if (plan.portes().get(i).ouverteParCourant() && !portesOuvertes[i]) ouvrirPorte(i);
        if (giant != null) giant.courant();
        try {
            ZombiesApi.decor().courant(inst, true);
            for (int k = 0; k < atouts.size(); k++) if (atoutVisible[k]) ZombiesApi.decor().atout(inst, atouts.get(k), true);
            ZombiesApi.decor().ambiance(inst, ZombiesDecor.Ambiance.COURANT);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.courant", ex);
        }
        if (pap != null) pap.courant(true);
        sonTous(ZombiesApi.Sons.COURANT);
        evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.COURANT, p == null ? null : p.getUUID(), 0, 0, ""));
        musiqueAccent(MomentMusique.COURANT);
        objectifRempli("courant");
        // annonce : banniere du HUD zombies (evenement COURANT) ; pas de seconde annonce du moteur par-dessus
        hudSale = true;
    }

    // ------------------------------------------------------------------ manches
    private void commencerManche() {
        manche++;
        speciale = manche == prochaineSpeciale;
        if (speciale) {
            prochaineSpeciale = Manches.speciale(manche, alea);
            manchesChiens++;
        }
        if (giant != null) giant.nouvelleManche();
        int n = Math.max(1, joueursConnectes());
        aFaire = speciale ? Manches.nombreRodeurs(manche, n) : Manches.nombre(manche, n);
        ticksAvantApparition = 20;
        bonusManche = 0;
        sprintForce = false;
        dernierKill = ticks();
        etape = Etape.MANCHE;
        ticksEtape = 0;
        for (JoueurZombies j : joueurs.values()) j.planchesManche = 0;
        ramenerMorts();
        if (manche > 1) for (int i = 0; i < enLigne.size(); i++) rendreGrenades(enLigne.get(i));
        // grande annonce « MANCHE N » au centre (D-049) : seulement aux manches 5, 10, 15, 20, puis toutes les 10 ; le
        // numero a la craie (haut droite) marque chaque manche
        if (annonceManche(manche)) {
            Annonces.grande(enLigneListe(), new Annonces.Annonce(Component.translatable("zombies.annonce.manche", manche),
                    speciale ? Component.translatable("zombies.annonce.rodeurs") : Component.empty(),
                    0xC8102E, "", speciale ? 100 : 80, Annonces.Style.MANCHE));
        }
        sonTous(speciale ? ZombiesApi.Sons.SPECIALE_DEBUT : ZombiesApi.Sons.MANCHE_DEBUT);
        try {
            ZombiesApi.decor().ambiance(instance(), speciale ? ZombiesDecor.Ambiance.SPECIALE_DEBUT : ZombiesDecor.Ambiance.MANCHE_DEBUT);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.ambiance", ex);
        }
        evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.MANCHE, null, manche, (speciale ? 1 : 0) | 2, ""));
        musique(speciale ? MomentMusique.SPECIALE_DEBUT : MomentMusique.MANCHE_DEBUT);
        HeraliszMatchs.LOG.info("[zombies] {} : manche {} ({} zombies{})", id(), manche, aFaire, speciale ? ", Rodeurs" : "");
        hudSale = true;
    }

    /** Manches annoncees au centre de l'ecran : 5, 10, 15, 20, puis 30, 40, 50... */
    static boolean annonceManche(int m) {
        return m == 5 || m == 10 || m == 15 || m == 20 || (m > 20 && m % 10 == 0);
    }

    private void rendreGrenades(ServerPlayer p) {
        ItemStack g = p.getInventory().getItem(SLOT_GRENADES);
        if (g.isEmpty()) {
            ItemStack n = objet(GRENADE, 2);
            if (!n.isEmpty()) p.getInventory().setItem(SLOT_GRENADES, Equipement.marquer(n, id()));
        } else if (Equipement.estPrete(g)) {
            g.setCount(Math.min(GRENADES_MAX, g.getCount() + 2));
        }
    }

    private void finManche() {
        etape = Etape.PAUSE;
        ticksEtape = 0;
        boolean etaitSpeciale = speciale;
        sonTous(ZombiesApi.Sons.MANCHE_FIN);
        try {
            ZombiesApi.decor().ambiance(instance(), etaitSpeciale ? ZombiesDecor.Ambiance.SPECIALE_FIN : ZombiesDecor.Ambiance.MANCHE_FIN);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.ambiance", ex);
        }
        evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.MANCHE, null, manche, etaitSpeciale ? 1 : 0, ""));
        if (ambiance != MomentMusique.MANCHE_PASSEE) musique(etaitSpeciale ? MomentMusique.SPECIALE_FIN : MomentMusique.MANCHE_FIN);
        if (etaitSpeciale) poserBonus(TypeBonus.MUNITIONS_MAX, dernierKillX, dernierKillY, dernierKillZ);
        if (instaKillManches > 0 && --instaKillManches == 0) {
            messageTous("zombies.jeu.ee.recompense.fin");
            envoyerObjectif(false);
        }
        speciale = false;
        hudSale = true;
    }

    private void tickManche(long t) {
        if (devPause) {
            dernierKill = t;
            return;
        }
        if (aFaire > 0 && zombies.size() < Manches.VIVANTS_MAX && --ticksAvantApparition <= 0) {
            ticksAvantApparition = Manches.cadence(manche, joueursConnectes());
            if (apparaitre()) {
                aFaire--;
                hudSale = true;
            } else {
                ticksAvantApparition = 20;
            }
        }
        long sansKill = t - dernierKill;
        // garde-fou : aucun kill pendant 120 s, la manche passe
        if (sansKill >= ConfigZombies.entier("garde_fou", "sans_kill_secondes", 120) * 20L) {
            passerManche();
            return;
        }
        // derniers zombies introuvables : apres 60 s sans kill ils sprintent et ressortent pres des joueurs
        if (aFaire == 0 && !zombies.isEmpty() && zombies.size() <= 3 && sansKill >= 20L * 60 && !sprintForce) {
            sprintForce = true;
            for (int i = zombies.size() - 1; i >= 0; i--) {
                ZombieSuivi s = zombies.get(i);
                s.allure = s.type == TypeZombie.RODEUR ? Allure.RODEUR : Allure.SPRINTEUR;
                base(s.mob, Attributes.MOVEMENT_SPEED, s.allure.vitesse);
                replacer(s, "derniers zombies");
            }
        }
        if (aFaire == 0 && zombies.isEmpty()) finManche();
    }

    /** Garde-fou : la manche passe faute de kill ; les zombies restants sont retires proprement (sans points). */
    private void passerManche() {
        int n = zombies.size() + aFaire;
        for (int i = zombies.size() - 1; i >= 0; i--) {
            ZombieSuivi s = zombies.get(i);
            s.retire = true;
            liberer(s);
            s.mob.discard();
        }
        zombies.clear();
        parId.clear();
        aFaire = 0;
        HeraliszMatchs.LOG.info("[zombies] {} : garde-fou, manche {} passee sans kill depuis 120 s ({} zombies retires)", id(), manche, n);
        messageTous("zombies.jeu.garde_fou");
        musique(MomentMusique.MANCHE_PASSEE);
        finManche();
    }

    // ------------------------------------------------------------------ zombies
    private boolean apparaitre() {
        // apres la 3e manche de chiens, quelques chiens se melent aux zombies (CoD) : 6 % puis +1 % par manche (max 15 %)
        boolean chien = speciale || (manchesChiens >= 3 && alea.nextFloat() < Math.min(0.15f, 0.06f + 0.01f * Math.max(0, manche - 15)));
        // 0.2.3 : jamais en l'air ni au-dessus du vide (sol disparu) : un point sans sol est abandonne pour un autre
        PointApparition pa = null;
        Vec3 pos = null;
        int fenetre = -1;
        for (int essai = 0; essai < 5 && pos == null; essai++) {
            pa = choisirPoint(false);
            if (pa == null) return false;
            fenetre = pa.fenetre();
            pos = solOuNull(chien && fenetre >= 0 ? plan.fenetres().get(fenetre).dedans() : pa.pose().position());
            if (pos == null) HeraliszMatchs.LOG.warn("[zombies] {} : point d'apparition sans sol en {} (zone {}), autre point tire", id(),
                    fmt(pa.pose().position()), idsZones.get(pa.zone()));
        }
        if (pos == null) return false;
        if (chien) fenetre = -1;
        TypeZombie type = chien ? TypeZombie.RODEUR : TypeZombie.NORMAL;
        Allure allure = chien ? Allure.RODEUR : (sprintForce ? Allure.SPRINTEUR : Manches.allure(manche, alea));
        Mob m = creer(type, allure);
        if (m == null) return false;
        double pv = chien ? Manches.pvRodeur(manche) : Manches.pv(manche);
        float facteur = 1f;
        if (pv > Manches.PV_ATTRIBUT_MAX) {
            facteur = (float) (Manches.PV_ATTRIBUT_MAX / pv);
            pv = Manches.PV_ATTRIBUT_MAX;
        }
        base(m, Attributes.MAX_HEALTH, pv);
        base(m, Attributes.MOVEMENT_SPEED, allure.vitesse);
        base(m, Attributes.ATTACK_DAMAGE, Manches.degats(manche, chien));
        base(m, Attributes.FOLLOW_RANGE, 64);
        base(m, Attributes.KNOCKBACK_RESISTANCE, 0.4);
        m.setHealth((float) pv);
        m.moveTo(pos.x, pos.y, pos.z, pa.pose().yaw(), 0f);
        m.setPersistenceRequired();
        m.setCanPickUpLoot(false);
        m.getPersistentData().putString(ZombiesApi.TAG_PARTIE, id());
        if (!niveau.addFreshEntity(m)) return false;
        ZombieSuivi s = new ZombieSuivi(m, type, allure, fenetre);
        s.facteurDegats = facteur;
        zombies.add(s);
        parId.put(m.getId(), s);
        try {
            if (fenetre >= 0) ZombiesApi.decor().apparition(instance(), m, plan.fenetres().get(fenetre));
            else ZombiesApi.decor().apparitionSalle(instance(), m, pa.genre().isEmpty() ? "sol" : pa.genre());
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.apparition", ex);
        }
        if (s.etat == ZombieSuivi.Etat.DEDANS) cibler(s);
        if (HeraliszMatchs.LOG.isDebugEnabled()) HeraliszMatchs.LOG.debug("[zombies] apparition {} zone {} genre {} en {}",
                fenetre >= 0 ? "fenetre " + fenetre : "salle", idsZones.get(pa.zone()), fenetre >= 0 ? "fenetre" : (pa.genre().isEmpty() ? "sol" : pa.genre()), fmt(pos));
        return true;
    }

    private static void base(Mob m, Attribute a, double v) {
        AttributeInstance ai = m.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    @Nullable
    private Mob creer(TypeZombie type, Allure allure) {
        ZombieFabrique f = ZombiesApi.fabrique();
        int variante = alea.nextInt(4);
        if (f != null) {
            try {
                Mob m = f.creer(niveau, type, manche, allure, variante);
                if (m != null) return m;
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[zombies] fabrique en erreur", ex);
            }
        }
        // secours (developpement) : zombie vanilla sans ciblage automatique
        net.minecraft.world.entity.monster.Zombie z = EntityType.ZOMBIE.create(niveau);
        if (z == null) return null;
        z.targetSelector.removeAllGoals(g -> true);
        z.setBaby(false);
        return z;
    }

    /**
     * Point d'apparition actif tire au hasard, pondere par la proximite des joueurs debout : fenetres et sorties de
     * terre des zones ouvertes, entre {@code distance_min} (pas d'apparition sous le nez, sauf derriere une fenetre) et
     * {@code distance_max} blocs, poids {@code 1 / (1 + (d/14)²)} ; une fenetre deja tres demandee pese moins.
     * 0.2.3 : les points de salle pesent autant que les fenetres ({@code poids_salle} 1,0) et, quand il y en a parmi les
     * candidats, au moins {@code part_salle_min} (35 %) des apparitions en viennent : tirage d'abord de la famille (salle
     * ou fenetre), puis pondere dans la famille. Pour un replacement ({@code proche}), les points de 8 a 24 blocs sont
     * nettement favorises, et ceux a la hauteur du joueur (jamais « sous » lui : sous un plancher, une passerelle).
     * Aucun candidat : n'importe quel point actif.
     */
    @Nullable
    private PointApparition choisirPoint(boolean proche) {
        candidats.clear();
        double dMin = ConfigZombies.reel("apparitions", "distance_min", 6);
        double dMax = ConfigZombies.reel("apparitions", "distance_max", 48);
        double pFen = ConfigZombies.reel("apparitions", "poids_fenetre", 1.0);
        double pSalle = ConfigZombies.reel("apparitions", "poids_salle", 1.0);
        double partSalle = Math.max(0, Math.min(1, ConfigZombies.reel("apparitions", "part_salle_min", 0.35)));
        double totalSalle = 0, totalFen = 0;
        for (int i = 0; i < points.size() && candidats.size() < poidsCandidats.length; i++) {
            PointApparition pa = points.get(i);
            if (!zonesOuvertes[pa.zone()]) continue;
            double d = Math.sqrt(distanceJoueurDebout(pa.pose().x(), pa.pose().y(), pa.pose().z()));
            if (d > dMax) continue;
            if (pa.fenetre() < 0 && d < dMin) continue;
            if (pa.fenetre() >= 0 && d < 3) continue;
            double w = (pa.fenetre() >= 0 ? pFen : pSalle) / (1.0 + (d / 14.0) * (d / 14.0));
            if (proche) {
                w *= d >= 8 && d <= 24 ? 4.0 : 0.5;
                // a la hauteur du joueur le plus proche : un point 5 blocs plus bas (sous la passerelle) ne vaut presque rien
                double dy = ecartVerticalJoueur(pa.pose().x(), pa.pose().y(), pa.pose().z());
                w /= 1.0 + (dy / 3.0) * (dy / 3.0);
            }
            if (pa.fenetre() >= 0 && occupants.length > pa.fenetre() && occupants[pa.fenetre()] != null) w *= 0.6;
            poidsCandidats[candidats.size()] = w;
            candidats.add(pa);
            if (pa.fenetre() >= 0) totalFen += w;
            else totalSalle += w;
        }
        if (candidats.isEmpty()) {
            for (int i = 0; i < points.size(); i++) if (zonesOuvertes[points.get(i).zone()]) candidats.add(points.get(i));
            return candidats.isEmpty() ? null : candidats.get(alea.nextInt(candidats.size()));
        }
        // famille : la salle au moins part_salle_min du temps quand il y en a (sinon sa part naturelle si elle est plus forte)
        int famille = 0; // 0 = toutes, 1 = salle, 2 = fenetre
        if (totalSalle > 0 && totalFen > 0) {
            double pS = Math.max(partSalle, totalSalle / (totalSalle + totalFen));
            famille = alea.nextDouble() < pS ? 1 : 2;
        }
        double total = famille == 1 ? totalSalle : famille == 2 ? totalFen : totalSalle + totalFen;
        double x = alea.nextDouble() * total;
        PointApparition dernier = null;
        for (int i = 0; i < candidats.size(); i++) {
            PointApparition pa = candidats.get(i);
            if (famille == 1 && pa.fenetre() >= 0 || famille == 2 && pa.fenetre() < 0) continue;
            dernier = pa;
            x -= poidsCandidats[i];
            if (x < 0) return pa;
        }
        return dernier != null ? dernier : candidats.get(candidats.size() - 1);
    }

    /** Ecart vertical (blocs) entre ce point et le joueur debout le plus proche a l'horizontale ; 0 sans joueur. */
    private double ecartVerticalJoueur(double x, double y, double z) {
        double m = Double.MAX_VALUE, dy = 0;
        for (int i = 0; i < enLigne.size(); i++) {
            ServerPlayer p = enLigne.get(i);
            JoueurZombies j = joueurs.get(p.getUUID());
            if (j == null || j.etat == HudZombies.MORT || p.level() != niveau) continue;
            double dx = p.getX() - x, dz = p.getZ() - z;
            double d = dx * dx + dz * dz;
            if (d < m) {
                m = d;
                dy = Math.abs(p.getY() - y);
            }
        }
        return dy;
    }

    private double distanceJoueurDebout(double x, double y, double z) {
        double m = Double.MAX_VALUE;
        for (int i = 0; i < enLigne.size(); i++) {
            ServerPlayer p = enLigne.get(i);
            JoueurZombies j = joueurs.get(p.getUUID());
            if (j == null || j.etat == HudZombies.MORT || p.level() != niveau) continue;
            double d = p.distanceToSqr(x, y, z);
            if (d < m) m = d;
        }
        return m;
    }

    @Nullable
    private ServerPlayer plusProcheDebout(Mob m) {
        ServerPlayer best = null;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < enLigne.size(); i++) {
            ServerPlayer p = enLigne.get(i);
            JoueurZombies j = joueurs.get(p.getUUID());
            if (j == null || !j.debout() || p.level() != niveau || p.isSpectator()) continue;
            double d = p.distanceToSqr(m);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private void cibler(ZombieSuivi s) {
        ServerPlayer p = plusProcheDebout(s.mob);
        if (s.mob.getTarget() != p) s.mob.setTarget(p);
    }

    private void tickZombies(long t) {
        for (int i = zombies.size() - 1; i >= 0; i--) {
            if (i >= zombies.size()) continue;
            ZombieSuivi s = zombies.get(i);
            Mob m = s.mob;
            if (m.isRemoved() || !m.isAlive()) {
                if (m.isRemoved() && m.getRemovalReason() != null && !m.getRemovalReason().shouldDestroy()) {
                    // decharge (tronçon) : on le refait apparaitre plus tard
                    aFaire++;
                }
                liberer(s);
                zombies.remove(i);
                parId.remove(m.getId());
                hudSale = true;
                continue;
            }
            if (s.coupTick > 0 && t >= s.coupTick) {
                appliquerCoup(s, t);
                if (!m.isAlive() || m.isRemoved()) continue;
            }
            // 0.2.3 : tombe sous la carte (trou, vide) : rattrape tout de suite au lieu de le perdre (la manche ne se vide
            // plus toute seule) ; les renforts du hall du boss (sous la carte) ont leur propre garde-fou
            if (!s.salle && ((t + i) % 5) == 0 && m.getY() < ySolMin - 4) {
                HeraliszMatchs.LOG.warn("[zombies] {} : zombie tombe sous la carte en {}, replace", id(), fmt(m.position()));
                replacer(s, "sous la carte");
                if (!zombies.contains(s)) continue;
            }
            switch (s.etat) {
                case DEHORS -> tickDehors(s, t, i);
                case ARRACHE -> tickArrache(s);
                case ENJAMBE -> tickEnjambe(s);
                case DEDANS -> {
                    if (s.escalade != null) {
                        tickEscalade(s);
                        continue;
                    }
                    if (((t + i) % 20) == 0) cibler(s);
                    if (!s.salle && ((t + i) % 10) == 5) essayerEscalade(s, t);
                }
            }
            if (((t + i) % 20) == 0 && zombies.contains(s)) gardeFou(s);
        }
    }

    /** En route vers la fenetre ; colle au point exterieur s'il est libre, sinon attend en retrait. */
    private void tickDehors(ZombieSuivi s, long t, int i) {
        PlanZombies.Fenetre f = plan.fenetres().get(s.fenetre);
        Mob m = s.mob;
        Vec3 d = f.dehors();
        ZombieSuivi occ = occupant(f.index());
        double hx = m.getX() - d.x, hz = m.getZ() - d.z;
        double h2 = hx * hx + hz * hz;
        if ((occ == null || occ == s) && Math.abs(m.getY() - d.y) < 1.6) {
            // arrive contre l'embrasure ; ou approche finale : le chemin s'arrete a deux pas (precision d'un bloc du
            // pathfinder, coin de mur) sans rien entre lui et l'embrasure, tickArrache le recale en marchant
            if (h2 < 1.3 * 1.3 || (h2 < 2.4 * 2.4 && (m.getNavigation().isDone() || s.ticksImmobile >= 20) && passageLibre(m, d))) {
                occupants[f.index()] = s;
                arriverFenetre(s, f);
                return;
            }
        }
        if (((t + i) % 10) == 0) {
            if (m.getTarget() != null) m.setTarget(null);
            if (occ != null && occ != s) {
                // file d'attente : en retrait derriere celui qui arrache
                double q = 1.7;
                double qx = d.x + f.exterieur().getStepX() * q, qz = d.z + f.exterieur().getStepZ() * q;
                if (m.distanceToSqr(qx, d.y, qz) > 0.9) m.getNavigation().moveTo(qx, d.y, qz, 1.0);
                else m.getNavigation().stop();
            } else {
                m.getNavigation().moveTo(d.x, d.y, d.z, 1.0);
            }
        }
    }

    /** Vrai si rien ne bloque la marche en ligne droite du zombie jusqu'au point (aux pieds et a la poitrine). */
    private boolean passageLibre(Mob m, Vec3 d) {
        for (double h : new double[]{0.4, 1.3}) {
            net.minecraft.world.phys.BlockHitResult r = niveau.clip(new net.minecraft.world.level.ClipContext(new Vec3(m.getX(), m.getY() + h, m.getZ()),
                    new Vec3(d.x, d.y + h, d.z), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, m));
            if (r.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return false;
        }
        return true;
    }

    /** Occupant actuel de la fenetre (nettoie un occupant parti, mort ou entre). */
    @Nullable
    private ZombieSuivi occupant(int f) {
        ZombieSuivi o = occupants[f];
        if (o != null && (o.mob.isRemoved() || !o.mob.isAlive() || o.fenetre != f || o.etat == ZombieSuivi.Etat.DEDANS
                || o.etat == ZombieSuivi.Etat.DEHORS)) {
            occupants[f] = null;
            o = null;
        }
        return o;
    }

    /** Libere la fenetre que ce zombie occupait. */
    private void liberer(ZombieSuivi s) {
        if (s.fenetre >= 0 && s.fenetre < occupants.length && occupants[s.fenetre] == s) occupants[s.fenetre] = null;
    }

    private void arriverFenetre(ZombieSuivi s, PlanZombies.Fenetre f) {
        Mob m = s.mob;
        if (m.getTarget() != null) m.setTarget(null);
        immobiliser(m);
        if (planches[f.index()] <= 0) {
            commencerEnjambement(s, f);
            return;
        }
        s.etat = ZombieSuivi.Etat.ARRACHE;
        // la premiere planche part un peu plus vite (le zombie arrive deja les bras tendus)
        s.ticksArrachage = intervalleArrachage() / 3;
        s.griffeRecharge = 15;
        s.ticksImmobile = 0;
    }

    /** Ticks entre deux planches : ~1,2 s, un peu plus vite aux manches hautes (plancher 16). */
    private int intervalleArrachage() {
        return Math.max(16, ZombiesApi.ARRACHAGE_TICKS + 1 - manche / 3);
    }

    /** Colle a la fenetre : arrache les planches une a une, griffe un joueur colle de l'autre cote, puis enjambe. */
    private void tickArrache(ZombieSuivi s) {
        PlanZombies.Fenetre f = plan.fenetres().get(s.fenetre);
        Mob m = s.mob;
        if (m.getTarget() != null) m.setTarget(null);
        Vec3 d = f.dehors();
        double ex = d.x - m.getX(), ez = d.z - m.getZ();
        double e2 = ex * ex + ez * ez;
        if (e2 > 2.6 * 2.6) {
            // repousse trop loin : il revient a pied
            s.etat = ZombieSuivi.Etat.DEHORS;
            liberer(s);
            return;
        }
        // recale contre la fenetre (les coups le repoussent), sans jamais traverser
        if (e2 > 0.0025) {
            double n = Math.sqrt(e2), pas = Math.min(n, 0.12);
            m.setPos(m.getX() + ex / n * pas, m.getY(), m.getZ() + ez / n * pas);
        }
        m.getNavigation().stop();
        m.setDeltaMovement(0, Math.min(0, m.getDeltaMovement().y), 0);
        orienter(m, f.exterieur().getOpposite());
        m.getLookControl().setLookAt(f.centre().x, f.centre().y, f.centre().z);
        // encore en train de se coller a l'embrasure (approche finale, recul d'un coup) : il n'arrache qu'une fois contre
        if (e2 > 0.6 * 0.6) return;
        if (planches[f.index()] <= 0) {
            commencerEnjambement(s, f);
            return;
        }
        if (s.griffeRecharge > 0) s.griffeRecharge--;
        if (planches[f.index()] <= PLANCHES_GRIFFE && s.griffeRecharge <= 0) {
            ServerPlayer cible = joueurContreFenetre(f, m);
            if (cible != null) {
                s.griffeRecharge = 25;
                m.swing(InteractionHand.MAIN_HAND);
                float avant = cible.getHealth() + cible.getAbsorptionAmount();
                m.doHurtTarget(cible);
                if (BANC) HeraliszMatchs.LOG.info("[zombies] fenetre {} : griffe a travers les planches sur {} ({} -> {} PV)", f.index(),
                        cible.getGameProfile().getName(), String.format(Locale.ROOT, "%.1f", avant),
                        String.format(Locale.ROOT, "%.1f", cible.getHealth() + cible.getAbsorptionAmount()));
                son(ZombiesApi.Sons.GRIFFE_FENETRE, f.centre().x, f.centre().y, f.centre().z, 1f);
                return;
            }
        }
        if (++s.ticksArrachage >= intervalleArrachage()) {
            s.ticksArrachage = 0;
            planches[f.index()]--;
            m.swing(InteractionHand.MAIN_HAND);
            try {
                ZombiesApi.decor().arrachage(instance(), m, f);
                ZombiesApi.decor().fenetre(instance(), f, planches[f.index()], ZombiesDecor.EvenementFenetre.ARRACHEE);
            } catch (RuntimeException ex2) {
                HeraliszMatchs.LOG.error("[zombies] decor.fenetre", ex2);
            }
            son(ZombiesApi.Sons.PLANCHE_ARRACHE, f.centre().x, f.centre().y, f.centre().z, 1f);
            if (BANC) HeraliszMatchs.LOG.info("[zombies] fenetre {} : planche arrachee ({} restantes) par un zombie a {} bloc(s) de la face",
                    f.index(), planches[f.index()], String.format(Locale.ROOT, "%.2f", f.cote(m.getX(), m.getZ()) - f.largeurNormale() / 2.0));
        }
    }

    /**
     * Joueur debout COLLE a la face interieure de la fenetre, en face de l'ouverture, ou null ; le plus proche du zombie
     * s'il y en a plusieurs. Le zombie passe le bras par les planches arrachees : il faut toucher la fenetre pour etre
     * griffe (avant : toute une zone de ~3 blocs derriere la fenetre, d'ou des coups recus a 2 blocs).
     */
    @Nullable
    private ServerPlayer joueurContreFenetre(PlanZombies.Fenetre f, Mob zombie) {
        double faceInterieure = f.largeurNormale() / 2.0;
        double demi = (f.exterieur().getAxis() == Direction.Axis.X ? f.planches().getZsize() : f.planches().getXsize()) / 2.0;
        ServerPlayer meilleur = null;
        double meilleurD2 = Double.MAX_VALUE;
        for (int i = 0; i < enLigne.size(); i++) {
            ServerPlayer p = enLigne.get(i);
            JoueurZombies j = joueurs.get(p.getUUID());
            if (j == null || !j.debout() || p.level() != niveau) continue;
            // cote < 0 : a l'interieur ; le joueur (demi-largeur 0,3) touche la face interieure a -(faceInterieure + 0,3)
            double cote = f.cote(p.getX(), p.getZ());
            if (cote > 0 || cote < -(faceInterieure + PORTEE_GRIFFE_FENETRE)) continue;
            double lat = f.exterieur().getAxis() == Direction.Axis.X ? p.getZ() - f.centre().z : p.getX() - f.centre().x;
            if (Math.abs(lat) > demi + 0.1 || Math.abs(p.getY() - (f.planches().minY - 1)) > 1.1) continue;
            double dx = p.getX() - zombie.getX(), dz = p.getZ() - zombie.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 < meilleurD2) {
                meilleurD2 = d2;
                meilleur = p;
            }
        }
        return meilleur;
    }

    /** La fenetre est vide : le zombie l'enjambe (deplacement interpole, sans physique, pas de teleportation). */
    private void commencerEnjambement(ZombieSuivi s, PlanZombies.Fenetre f) {
        Mob m = s.mob;
        s.etat = ZombieSuivi.Etat.ENJAMBE;
        s.ticksEnjambe = 0;
        s.depart = m.position();
        s.arrivee = sol(f.dedans());
        immobiliser(m);
        m.noPhysics = true;
        m.setNoGravity(true);
        try {
            ZombiesApi.decor().franchissement(instance(), m, f);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.franchissement", ex);
        }
        if (BANC) HeraliszMatchs.LOG.info("[zombies] fenetre {} : enjambement de {} vers {}", f.index(), fmt(s.depart), fmt(s.arrivee));
    }

    private void tickEnjambe(ZombieSuivi s) {
        PlanZombies.Fenetre f = plan.fenetres().get(s.fenetre);
        Mob m = s.mob;
        s.ticksEnjambe++;
        double t = Math.min(1.0, s.ticksEnjambe / (double) ZombiesApi.ENJAMBEMENT_TICKS);
        Vec3 a = s.depart, b = s.arrivee;
        if (a == null || b == null) {
            finEnjambement(s, f);
            return;
        }
        double h = lisse(t);
        double v = lisse(Math.max(0, Math.min(1, (t - 0.35) / 0.65)));
        // petit elan au-dessus de l'appui (l'ouverture ne fait que 2 blocs : le contenu courbe le zombie), puis descente
        double y = a.y + (b.y - a.y) * v + 0.18 * Math.sin(Math.PI * Math.min(1.0, t / 0.75));
        m.setPos(a.x + (b.x - a.x) * h, y, a.z + (b.z - a.z) * h);
        m.setDeltaMovement(Vec3.ZERO);
        orienter(m, f.exterieur().getOpposite());
        // a mi-chemin, la fenetre est liberee pour le suivant
        if (t >= 0.6 && occupants[f.index()] == s) occupants[f.index()] = null;
        if (t >= 1.0) finEnjambement(s, f);
    }

    private void finEnjambement(ZombieSuivi s, PlanZombies.Fenetre f) {
        Mob m = s.mob;
        m.noPhysics = false;
        m.setNoGravity(false);
        if (s.arrivee != null) m.setPos(s.arrivee.x, s.arrivee.y, s.arrivee.z);
        s.etat = ZombieSuivi.Etat.DEDANS;
        liberer(s);
        s.reinitialiserGardeFou();
        cibler(s);
        if (BANC) HeraliszMatchs.LOG.info("[zombies] fenetre {} : zombie entre en {}", f.index(), fmt(m.position()));
    }

    private static double lisse(double t) {
        return t * t * (3 - 2 * t);
    }

    private static String fmt(@Nullable Vec3 v) {
        return v == null ? "-" : String.format(Locale.ROOT, "%.2f %.2f %.2f", v.x, v.y, v.z);
    }

    /** Arrete tout deplacement propre de la creature (navigation, controle de mouvement). */
    private static void immobiliser(Mob m) {
        m.getNavigation().stop();
        m.getMoveControl().setWantedPosition(m.getX(), m.getY(), m.getZ(), 0);
        m.setZza(0);
        m.setXxa(0);
        m.setDeltaMovement(0, Math.min(0, m.getDeltaMovement().y), 0);
    }

    private static void orienter(Mob m, Direction d) {
        float yaw = d.toYRot();
        m.setYRot(yaw);
        m.yRotO = yaw;
        m.setYBodyRot(yaw);
        m.setYHeadRot(yaw);
    }

    /**
     * Premiere position debout pres du point (centre du bloc, de y+1 a y-3) : tete libre, pieds libres ou sur une couche
     * mince (tapis, plaque, moisissure de decor : on se pose dessus, jamais dedans), sol porteur (le sommet d'une dalle
     * compte). Une creature posee DANS une forme de collision ne peut plus bouger : toutes les apparitions passent par
     * ici.
     */
    private Vec3 sol(Vec3 v) {
        Vec3 r = solOuNull(v);
        return r != null ? r : v;
    }

    /** Comme {@link #sol}, mais null s'il n'y a aucun sol porteur de y+1 a y-3 (trou, vide) : on ne pose jamais en l'air. */
    @Nullable
    private Vec3 solOuNull(Vec3 v) {
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        int x = (int) Math.floor(v.x), z = (int) Math.floor(v.z);
        int y0 = (int) Math.floor(v.y);
        for (int y = y0 + 1; y >= y0 - 3; y--) {
            bp.set(x, y + 1, z);
            if (!niveau.getBlockState(bp).getCollisionShape(niveau, bp).isEmpty()) continue;
            bp.set(x, y, z);
            net.minecraft.world.phys.shapes.VoxelShape pieds = niveau.getBlockState(bp).getCollisionShape(niveau, bp);
            if (!pieds.isEmpty()) {
                double haut = pieds.max(Direction.Axis.Y);
                if (haut > 0.5) continue;
                // couche mince aux pieds : debout dessus, la tete doit encore passer
                bp.set(x, y + 2, z);
                if (haut > 0.05 && !niveau.getBlockState(bp).getCollisionShape(niveau, bp).isEmpty()) continue;
                return new Vec3(x + 0.5, y + haut + 0.001, z + 0.5);
            }
            bp.set(x, y - 1, z);
            net.minecraft.world.phys.shapes.VoxelShape dessous = niveau.getBlockState(bp).getCollisionShape(niveau, bp);
            if (dessous.isEmpty()) continue;
            double haut = dessous.max(Direction.Axis.Y);
            return new Vec3(x + 0.5, haut < 0.999 ? y - 1 + haut + 0.001 : y, z + 0.5);
        }
        return null;
    }

    /**
     * Garde-fou par zombie (toutes les 20 ticks) : hors de l'instance, bloque 15 s, a plus de 48 blocs des joueurs
     * (chemin ou ligne droite) 5 s, ou sans ligne de vue vers un joueur depuis 20 s (une fois a l'interieur) = replace
     * a un point d'apparition actif proche des joueurs.
     */
    private void gardeFou(ZombieSuivi s) {
        if (s.salle) {
            gardeFouSalle(s);
            return;
        }
        Mob m = s.mob;
        double dx = m.getX() - s.dx, dy = m.getY() - s.dy, dz = m.getZ() - s.dz;
        boolean progresse = dx * dx + dy * dy + dz * dz > 0.36;
        s.dx = m.getX();
        s.dy = m.getY();
        s.dz = m.getZ();
        Instance inst = instance();
        if (inst != null && !inst.contient(m.getX(), m.getY(), m.getZ())) {
            replacer(s, "hors de la carte");
            return;
        }
        double distMax = ConfigZombies.reel("garde_fou", "distance_max", 48);
        double loin = Math.sqrt(distanceJoueurDebout(m.getX(), m.getY(), m.getZ()));
        switch (s.etat) {
            case ARRACHE, ENJAMBE -> {
                s.ticksImmobile = 0;
                s.ticksSansLigne = 0;
                s.ticksLoin = loin > distMax ? s.ticksLoin + 20 : 0;
            }
            case DEHORS -> {
                s.ticksImmobile = progresse ? 0 : s.ticksImmobile + 20;
                s.ticksSansLigne = 0;
                s.ticksLoin = loin > distMax ? s.ticksLoin + 20 : 0;
            }
            case DEDANS -> {
                LivingEntity c = m.getTarget();
                boolean auContact = c != null && c.distanceToSqr(m) < 2.5 * 2.5;
                s.ticksImmobile = progresse || auContact ? 0 : s.ticksImmobile + 20;
                Path chemin = m.getNavigation().getPath();
                int restant = chemin == null ? 0 : chemin.getNodeCount() - chemin.getNextNodeIndex();
                s.ticksLoin = loin > distMax || restant > distMax ? s.ticksLoin + 20 : 0;
                s.ticksSansLigne = voitUnJoueur(m) ? 0 : s.ticksSansLigne + 20;
            }
        }
        int bloque = ConfigZombies.entier("garde_fou", "bloque_secondes", 15) * 20;
        int sansLigne = ConfigZombies.entier("garde_fou", "sans_ligne_secondes", 20) * 20;
        if (s.ticksImmobile >= bloque) replacer(s, "bloque");
        else if (s.ticksLoin >= 100) replacer(s, "trop loin");
        else if (s.ticksSansLigne >= sansLigne) replacer(s, "sans ligne de vue");
    }

    /** Renfort du hall du boss : hors du hall, bloque 15 s ou sans ligne de vue 20 s = ressort d'un coin du hall. */
    private void gardeFouSalle(ZombieSuivi s) {
        Mob m = s.mob;
        if (m.isNoAi()) return;
        double dx = m.getX() - s.dx, dy = m.getY() - s.dy, dz = m.getZ() - s.dz;
        boolean progresse = dx * dx + dy * dy + dz * dz > 0.36;
        s.dx = m.getX();
        s.dy = m.getY();
        s.dz = m.getZ();
        LivingEntity c = m.getTarget();
        boolean auContact = c != null && c.distanceToSqr(m) < 2.5 * 2.5;
        s.ticksImmobile = progresse || auContact ? 0 : s.ticksImmobile + 20;
        s.ticksSansLigne = voitUnJoueur(m) ? 0 : s.ticksSansLigne + 20;
        AABB v = acte == null ? null : acte.volume();
        List<Vec3> pts = acte == null ? null : acte.renforts();
        boolean dehors = v != null && !v.inflate(2).contains(m.position());
        if (pts == null || pts.isEmpty()) return;
        if (dehors || s.ticksImmobile >= 15 * 20 || s.ticksSansLigne >= 20 * 20) {
            Vec3 p = sol(pts.get(alea.nextInt(pts.size())));
            immobiliser(m);
            m.teleportTo(p.x, p.y, p.z);
            s.reinitialiserGardeFou();
            cibler(s);
        }
    }

    // ------------------------------------------------------------------ escalade des zombies (0.2.3)
    /** Hauteur maximale d'une escalade (blocs), vitesse de montee (blocs par tick : ~1,5 bloc/s), saut d'obstacle bas. */
    private static final int ESCALADE_MAX = 6;
    private static final double ESCALADE_VITESSE = 0.075, SAUT_MAX = 1.5;

    /**
     * Zombie a l'interieur, toutes les 10 ticks : si sa cible est hors d'atteinte a pied (chemin incomplet deux fois de
     * suite, ou bloque) et qu'il est au bout de son chemin, il cherche autour de lui (3 x 3 cases) un mur, une caisse ou
     * un rebord a escalader (jusqu'a {@link #ESCALADE_MAX} blocs) vers la cible, ou un obstacle bas (≤ 1,5 bloc) a
     * sauter. Au plus ~250 lectures de cases, seulement pour un zombie coince, et une recherche ratee attend 1,5 s.
     */
    private void essayerEscalade(ZombieSuivi s, long t) {
        Mob m = s.mob;
        if (t < s.prochaineEscalade || !m.onGround() || m.isPassenger() || m.isNoAi()) return;
        LivingEntity c = m.getTarget();
        if (c == null || !c.isAlive()) {
            s.cheminsRates = 0;
            return;
        }
        double dx = c.getX() - m.getX(), dz = c.getZ() - m.getZ(), dy = c.getY() - m.getY();
        double h2 = dx * dx + dz * dz;
        if (h2 > 20 * 20 || (h2 < 1.6 * 1.6 && Math.abs(dy) < 1.5)) {
            s.cheminsRates = 0;
            return;
        }
        Path chemin = m.getNavigation().getPath();
        boolean atteint = chemin != null && chemin.canReach();
        if (atteint && s.ticksImmobile < 40) {
            s.cheminsRates = 0;
            return;
        }
        if (!atteint) s.cheminsRates++;
        // il suit d'abord son chemin incomplet jusqu'au bout (souvent le pied de la structure)
        boolean auBout = chemin == null || chemin.isDone() || chemin.getNodeCount() - chemin.getNextNodeIndex() <= 2;
        if (!(auBout || s.ticksImmobile >= 40) || s.cheminsRates < 2) return;
        ZombieSuivi.Escalade e = chercherEscalade(m, c, dx, dz, dy, s.type == TypeZombie.RODEUR);
        if (e == null) {
            s.prochaineEscalade = t + 30;
            return;
        }
        commencerEscalade(s, e);
    }

    /** Case sans collision (on peut s'y tenir ou y passer). */
    private boolean libreCase(BlockPos p) {
        return niveau.getBlockState(p).getCollisionShape(niveau, p).isEmpty();
    }

    /** Case d'une fenetre ou d'une porte encore fermee : jamais escaladee (les zombies n'entrent que par les fenetres). */
    private boolean barriere(BlockPos p) {
        if (fenetreParBloc.containsKey(p.asLong())) return true;
        List<PlanZombies.Porte> portes = plan.portes();
        for (int i = 0; i < portes.size(); i++) {
            if (i < portesOuvertes.length && portesOuvertes[i]) continue;
            BoundingBox b = portes.get(i).boite();
            if (p.getX() >= b.minX() - 1 && p.getX() <= b.maxX() + 1 && p.getY() >= b.minY() - 1 && p.getY() <= b.maxY() + 2
                    && p.getZ() >= b.minZ() - 1 && p.getZ() <= b.maxZ() + 1) return true;
        }
        return false;
    }

    /** Vrai si le point est dans une zone ouverte de la carte (jamais d'escalade vers une zone fermee ou hors carte). */
    private boolean dansZoneOuverte(double x, double y, double z) {
        for (int i = 0; i < idsZones.size(); i++) {
            if (!zonesOuvertes[i]) continue;
            PlanZombies.Zone zo = plan.zone(idsZones.get(i));
            if (zo != null && zo.contient(x, y, z)) return true;
        }
        return false;
    }

    /**
     * Meilleure escalade ou meilleur saut depuis une case voisine (3 x 3) du zombie vers sa cible, ou null : mur dans la
     * direction de la cible, couloir de montee libre (pas de plafond), rebord ou l'on tient debout, dans une zone ouverte,
     * sans fenetre ni porte fermee sur le trajet. Un chien ne grimpe pas (il saute seulement les obstacles bas).
     */
    @Nullable
    private ZombieSuivi.Escalade chercherEscalade(Mob m, LivingEntity c, double dx, double dz, double dy, boolean chien) {
        double n = Math.sqrt(dx * dx + dz * dz);
        double ux = n < 1e-3 ? 0 : dx / n, uz = n < 1e-3 ? 0 : dz / n;
        int bx = Mth.floor(m.getX()), by = Mth.floor(m.getY() + 0.05), bz = Mth.floor(m.getZ());
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        ZombieSuivi.Escalade meilleure = null;
        double meilleurScore = Double.MAX_VALUE;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int cx = bx + ox, cz = bz + oz;
                // case de depart : pieds et tete libres, sol porteur (une voisine doit aussi etre a la meme hauteur)
                if (!libreCase(q.set(cx, by, cz)) || !libreCase(q.set(cx, by + 1, cz))) continue;
                if ((ox != 0 || oz != 0) && (libreCase(q.set(cx, by - 1, cz)) || barriere(q.set(cx, by, cz)))) continue;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    double dot = d.getStepX() * ux + d.getStepZ() * uz;
                    if (n >= 2 && dot < 0.3) continue;
                    int wx = cx + d.getStepX(), wz = cz + d.getStepZ();
                    // un obstacle au niveau des pieds (sinon le zombie n'a qu'a marcher)
                    if (libreCase(q.set(wx, by, wz)) || barriere(q)) continue;
                    // hauteur du rebord : premiere case libre (avec la tete) au-dessus d'une case pleine du mur
                    int k = 1;
                    boolean ok = false;
                    for (; k <= ESCALADE_MAX; k++) {
                        // couloir de montee du zombie (sa colonne) : rien au-dessus de sa tete
                        if (!libreCase(q.set(cx, by + k + 1, cz))) break;
                        if (barriere(q.set(wx, by + k, wz))) break;
                        if (libreCase(q.set(wx, by + k, wz)) && libreCase(q.set(wx, by + k + 1, wz))) {
                            ok = true;
                            break;
                        }
                    }
                    if (!ok) continue;
                    Vec3 a = solOuNull(new Vec3(wx + 0.5, by + k, wz + 0.5));
                    if (a == null || a.y < m.getY() + 0.55) continue;
                    double haut = a.y - m.getY();
                    Vec3 pied = new Vec3(cx + 0.5 + d.getStepX() * 0.18, m.getY(), cz + 0.5 + d.getStepZ() * 0.18);
                    Vec3 depart = m.position();
                    int approche = (int) Math.ceil(Math.sqrt(depart.distanceToSqr(pied)) / 0.12);
                    ZombieSuivi.Escalade e = null;
                    if (haut <= SAUT_MAX) {
                        // obstacle bas : par-dessus (barriere mince, sol de l'autre cote a la meme hauteur), ou dessus
                        Vec3 au = null;
                        if (dy < 1.0) {
                            int fx = wx + d.getStepX(), fz = wz + d.getStepZ();
                            if (!libreCase(q.set(fx, by, fz)) || !libreCase(q.set(fx, by + 1, fz)) || barriere(q.set(fx, by, fz))) au = null;
                            else au = solOuNull(new Vec3(fx + 0.5, by, fz + 0.5));
                            if (au != null && Math.abs(au.y - m.getY()) > 0.6) au = null;
                        }
                        Vec3 fin = au != null ? au : a;
                        if (au == null && dy < 0.5) continue;
                        if (!dansZoneOuverte(fin.x, fin.y + 0.5, fin.z)) continue;
                        Vec3 sommet = new Vec3(wx + 0.5, a.y + 0.35, wz + 0.5);
                        e = new ZombieSuivi.Escalade(depart, pied, sommet, fin, d, true, approche, 0, ZombiesApi.ENJAMBEMENT_TICKS);
                    } else {
                        // vraie escalade : seulement vers une cible plus haute, sans depasser son niveau
                        if (chien || dy < 1.0 || a.y > c.getY() + 1.6) continue;
                        if (!dansZoneOuverte(a.x, a.y + 0.5, a.z)) continue;
                        Vec3 sommet = new Vec3(pied.x, a.y + 0.05, pied.z);
                        int montee = (int) Math.ceil((a.y - pied.y) / ESCALADE_VITESSE);
                        e = new ZombieSuivi.Escalade(depart, pied, sommet, a, d, false, approche, montee, 8);
                    }
                    double fx = c.getX() - e.arrivee.x, fz = c.getZ() - e.arrivee.z;
                    double score = Math.sqrt(fx * fx + fz * fz) + 2 * Math.abs(c.getY() - e.arrivee.y) + 0.5 * (Math.abs(ox) + Math.abs(oz));
                    if (score < meilleurScore) {
                        meilleurScore = score;
                        meilleure = e;
                    }
                }
            }
        }
        return meilleure;
    }

    private void commencerEscalade(ZombieSuivi s, ZombieSuivi.Escalade e) {
        Mob m = s.mob;
        s.escalade = e;
        immobiliser(m);
        if (m.getTarget() != null) m.setTarget(null);
        m.noPhysics = true;
        m.setNoGravity(true);
        if (!e.saut) decorEscalade(m, ZombiesDecor.ESCALADE_MUR);
        if (BANC) HeraliszMatchs.LOG.info("[zombies] {} de {} vers {} ({} ticks)", e.saut ? "saut d'obstacle" : "escalade", fmt(e.depart), fmt(e.arrivee), e.duree());
    }

    /** Deplacement interpole de l'escalade : approche du mur, montee le long de la face, pas sur le rebord (ou arc du saut). */
    private void tickEscalade(ZombieSuivi s) {
        ZombieSuivi.Escalade e = s.escalade;
        Mob m = s.mob;
        int t = ++e.ticks;
        double x, y, z;
        if (t <= e.ticksApproche) {
            double k = t / (double) Math.max(1, e.ticksApproche);
            x = e.depart.x + (e.pied.x - e.depart.x) * k;
            y = e.depart.y + (e.pied.y - e.depart.y) * k;
            z = e.depart.z + (e.pied.z - e.depart.z) * k;
        } else if (t <= e.ticksApproche + e.ticksMontee) {
            double k = (t - e.ticksApproche) / (double) Math.max(1, e.ticksMontee);
            x = e.pied.x;
            y = e.pied.y + (e.haut.y - e.pied.y) * k;
            z = e.pied.z;
        } else {
            int tp = t - e.ticksApproche - e.ticksMontee;
            if (tp == 1 && e.saut) decorEscalade(m, ZombiesDecor.ESCALADE_SAUT);
            double k = Math.min(1.0, tp / (double) Math.max(1, e.ticksPas)), h = lisse(k);
            Vec3 a = e.saut ? e.pied : e.haut;
            x = a.x + (e.arrivee.x - a.x) * h;
            z = a.z + (e.arrivee.z - a.z) * h;
            if (e.saut) {
                double base = a.y + (e.arrivee.y - a.y) * h;
                y = base + Math.max(0.2, e.haut.y - (a.y + e.arrivee.y) / 2) * Math.sin(Math.PI * k);
            } else {
                y = a.y + (e.arrivee.y - a.y) * h + 0.12 * Math.sin(Math.PI * k);
            }
        }
        m.setPos(x, y, z);
        m.setDeltaMovement(Vec3.ZERO);
        m.fallDistance = 0;
        orienter(m, e.face);
        if (t >= e.duree()) finirEscalade(s, true);
    }

    /** Fin de l'escalade (arrivee sur le rebord, ou interruption) : physique rendue, poursuite reprise. */
    private void finirEscalade(ZombieSuivi s, boolean arrive) {
        ZombieSuivi.Escalade e = s.escalade;
        Mob m = s.mob;
        s.escalade = null;
        m.noPhysics = false;
        m.setNoGravity(false);
        m.fallDistance = 0;
        if (arrive && e != null) m.setPos(e.arrivee.x, e.arrivee.y, e.arrivee.z);
        decorEscalade(m, ZombiesDecor.ESCALADE_FIN);
        s.cheminsRates = 0;
        s.prochaineEscalade = ticks() + 40;
        s.reinitialiserGardeFou();
        if (arrive) cibler(s);
    }

    private void decorEscalade(Mob m, int etat) {
        try {
            ZombiesApi.decor().escalade(instance(), m, etat);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.escalade", ex);
        }
    }

    private boolean voitUnJoueur(Mob m) {
        for (int i = 0; i < enLigne.size(); i++) {
            ServerPlayer p = enLigne.get(i);
            JoueurZombies j = joueurs.get(p.getUUID());
            if (j == null || j.etat == HudZombies.MORT || p.level() != niveau || p.isSpectator()) continue;
            if (m.getSensing().hasLineOfSight(p)) return true;
        }
        return false;
    }

    /**
     * Replace un zombie a un point d'apparition actif proche des joueurs (il ressort de terre, ou repart vers une
     * fenetre) en gardant ses PV ; sans point utilisable, il est retire et remis dans la file d'apparition.
     */
    private void replacer(ZombieSuivi s, String raison) {
        Mob m = s.mob;
        liberer(s);
        // 0.2.3 : un point sans sol (trou, vide) est ecarte : on en tire un autre, sinon le zombie retourne dans la file
        PointApparition pa = null;
        Vec3 dest = null;
        for (int essai = 0; essai < 5 && dest == null; essai++) {
            pa = choisirPoint(true);
            if (pa == null) break;
            boolean fen = pa.fenetre() >= 0 && s.type != TypeZombie.RODEUR;
            dest = solOuNull(fen || pa.fenetre() < 0 ? pa.pose().position() : plan.fenetres().get(pa.fenetre()).dedans());
        }
        if (pa == null || dest == null) {
            relancer(s);
            return;
        }
        if (s.escalade != null) finirEscalade(s, false);
        m.noPhysics = false;
        m.setNoGravity(false);
        m.fallDistance = 0;
        immobiliser(m);
        if (pa.fenetre() >= 0 && s.type != TypeZombie.RODEUR) {
            Vec3 p = dest;
            m.teleportTo(p.x, p.y, p.z);
            s.fenetre = pa.fenetre();
            s.etat = ZombieSuivi.Etat.DEHORS;
            if (m.getTarget() != null) m.setTarget(null);
        } else {
            Vec3 p = dest;
            m.teleportTo(p.x, p.y, p.z);
            s.fenetre = -1;
            s.etat = ZombieSuivi.Etat.DEDANS;
            try {
                ZombiesApi.decor().apparitionSalle(instance(), m, pa.genre().isEmpty() ? "sol" : pa.genre());
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[zombies] decor.apparition", ex);
            }
            cibler(s);
        }
        s.reinitialiserGardeFou();
        HeraliszMatchs.LOG.debug("[zombies] garde-fou : zombie replace ({}) vers {} (zone {})", raison, fmt(m.position()), idsZones.get(pa.zone()));
    }

    /** Retire un zombie bloque et le remet dans la file d'apparition (sans points ni bonus). */
    private void relancer(ZombieSuivi s) {
        s.retire = true;
        liberer(s);
        zombies.remove(s);
        parId.remove(s.mob.getId());
        s.mob.discard();
        aFaire++;
        ticksAvantApparition = Math.min(ticksAvantApparition, 10);
        hudSale = true;
    }

    // ------------------------------------------------------------------ couteau a travers les fenetres
    /**
     * Coup de melee signale par le client ({@code CoupC2S}) : si le joueur, a l'interieur, vise un zombie colle a une
     * fenetre (les planches le cachent au ciblage normal), le coup lui est applique 3 ticks plus tard sauf si le coup
     * normal l'a deja touche. Cadence limitee, arme a feu exclue (la crosse TaCZ a son propre coup).
     */
    void coup(ServerPlayer p) {
        JoueurZombies j = joueurs.get(p.getUUID());
        if (j == null || !j.debout() || phase() != Phase.COMBAT || plan == null || p.level() != niveau) return;
        long t = ticks();
        if (t - j.dernierCoup < 8) return;
        j.dernierCoup = t;
        if (Tacz.gunId(p.getMainHandItem()) != null || p.getMainHandItem().isEmpty()) return;
        Vec3 oeil = p.getEyePosition(), regard = p.getLookAngle();
        double portee = ConfigZombies.reel("couteau", "portee_fenetre", 2.6);
        ZombieSuivi meilleur = null;
        double md = Double.MAX_VALUE;
        for (int i = 0; i < zombies.size(); i++) {
            ZombieSuivi s = zombies.get(i);
            if (s.fenetre < 0 || (s.etat != ZombieSuivi.Etat.ARRACHE && s.etat != ZombieSuivi.Etat.ENJAMBE)) continue;
            PlanZombies.Fenetre f = plan.fenetres().get(s.fenetre);
            if (f.cote(p.getX(), p.getZ()) > 0.3) continue;
            AABB bb = s.mob.getBoundingBox().inflate(0.15);
            double d2 = distanceAabb(oeil, bb);
            if (d2 > portee * portee || !regarde(oeil, regard, bb.getCenter(), 0.55)) continue;
            if (d2 < md) {
                md = d2;
                meilleur = s;
            }
        }
        if (meilleur != null && meilleur.coupTick == 0) {
            meilleur.coupTick = t + 3;
            meilleur.coupPar = p.getUUID();
        }
    }

    private void appliquerCoup(ZombieSuivi s, long t) {
        UUID par = s.coupPar;
        s.coupTick = 0;
        s.coupPar = null;
        if (par == null || (par.equals(s.meleePar) && t - s.meleeTick <= 6)) return;
        ServerPlayer p = joueur(par);
        JoueurZombies j = joueurs.get(par);
        if (p == null || j == null || !j.debout()) return;
        float dmg = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        s.mob.invulnerableTime = 0;
        s.mob.hurt(p.damageSources().playerAttack(p), Math.max(1f, dmg));
        if (BANC) HeraliszMatchs.LOG.info("[zombies] couteau a travers la fenetre {} par {} ({} PV restants)", s.fenetre, j.nom,
                String.format(Locale.ROOT, "%.1f", s.mob.getHealth()));
    }

    // ------------------------------------------------------------------ degats et morts des zombies
    /**
     * Balle TaCZ qui va toucher une creature (avant les degats) : memorise le tir a la tete, applique l'equilibrage de
     * l'arme en zombies (0.2.3, {@link ConfigZombies#multiplicateurArme}), multiplie les degats d'une arme passee au Pack-a-Punch (celle que le tireur tient), compte le +10 de touche une seule fois par balle et seulement
     * si elle n'est pas mortelle (le kill paie a la place). Rend les nouveaux degats de base.
     */
    float surTir(Entity touchee, @Nullable LivingEntity tireur, @Nullable String gunId, boolean tete, float base, float multTete) {
        ZombieSuivi s = parId.get(touchee.getId());
        if (s == null) return touchee.getPersistentData().getBoolean(ZombiesApi.TAG_BOSS) ? surTirBoss(tireur, gunId, base) : base;
        long tick = serveur().getTickCount();
        s.tirTick = tick;
        s.tirTete = tete;
        // 0.2.3 : equilibrage des armes en zombies (DPS aligne par palier de rarete, equilibrage_armes.json)
        float b = base * ConfigZombies.multiplicateurArme(gunId);
        ServerPlayer p = tireur instanceof ServerPlayer sp ? sp : null;
        JoueurZombies j = p == null ? null : joueurs.get(p.getUUID());
        if (p != null && gunId != null) {
            ItemStack main = p.getMainHandItem();
            if (ZombiesApi.estAmelioree(main) && gunId.equals(Tacz.gunId(main))) {
                double mult = PackAPunch.multiplicateur(main);
                b *= (float) mult;
                if (BANC) HeraliszMatchs.LOG.info("[zombies] Pack-a-Punch : balle de {} niveau {} {} -> {} (x{})", gunId, ZombiesApi.niveauPap(main),
                        String.format(Locale.ROOT, "%.1f", base), String.format(Locale.ROOT, "%.1f", b), mult);
            }
        }
        if (j != null && phase() == Phase.COMBAT) {
            // atout Tir d'elite (Deadshot) : tirs a la tete plus forts
            if (tete && j.aAtout(TypeAtout.DEADSHOT)) b *= (float) ConfigZombies.reel("atouts", "deadshot_tete", 1.5);
            float prevu = b * (tete ? Math.max(1f, multTete) : 1f) * s.facteurDegats * (j.aAtout(TypeAtout.DOUBLE_IMPACT) ? 1.3f : 1f);
            boolean mortel = instaKill() || prevu >= s.mob.getHealth();
            if (!mortel) gagner(j, ConfigZombies.entier("points", "touche", 10), MotifPoints.TOUCHE);
            s.toucheTick = tick;
            s.touchePar = j.id;
        }
        return b;
    }

    /**
     * Balle TaCZ sur le boss N-07 (acte IV) : bonus du Pack-a-Punch de l'arme tenue (le boss compte ses PV avec, comme dans
     * CoD) et Double Impact ; +10 par touche (une fois par tick et par joueur). Ni mort instantanee ni Tir d'elite : le
     * point faible du boss fait deja son critique.
     */
    private float surTirBoss(@Nullable LivingEntity tireur, @Nullable String gunId, float base) {
        float b = base;
        ServerPlayer p = tireur instanceof ServerPlayer sp ? sp : null;
        JoueurZombies j = p == null ? null : joueurs.get(p.getUUID());
        if (p == null || j == null) return b;
        if (gunId != null) {
            ItemStack main = p.getMainHandItem();
            if (ZombiesApi.estAmelioree(main) && gunId.equals(Tacz.gunId(main))) b *= (float) PackAPunch.multiplicateur(main);
        }
        if (j.aAtout(TypeAtout.DOUBLE_IMPACT)) b *= 1.3f;
        long tick = serveur().getTickCount();
        if (phase() == Phase.COMBAT && acte != null && acte.combat() && j.toucheBossTick != tick) {
            j.toucheBossTick = tick;
            gagner(j, ConfigZombies.entier("points", "touche", 10), MotifPoints.TOUCHE);
        }
        return b;
    }

    private boolean instaKill() {
        return bonusTicks[TypeBonus.MORT_INSTANTANEE.ordinal()] > 0 || instaKillManches > 0;
    }

    /** Degats subis par une creature de la partie (LivingHurtEvent) : couteau, atouts, bonus, points de touche. */
    void surZombieTouche(LivingHurtEvent e) {
        ZombieSuivi s = parId.get(e.getEntity().getId());
        if (s == null) return;
        DamageSource src = e.getSource();
        Entity auteur = src.getEntity();
        if (auteur != null && ZombiesApi.estZombie(auteur)) {
            e.setCanceled(true);
            return;
        }
        // jamais de degats d'etouffement pendant l'enjambement (deplacement sans physique)
        if (s.etat == ZombieSuivi.Etat.ENJAMBE && src.getEntity() == null) {
            e.setCanceled(true);
            return;
        }
        float montant = e.getAmount() * s.facteurDegats;
        ServerPlayer p = auteur instanceof ServerPlayer sp ? sp : null;
        JoueurZombies j = p == null ? null : joueurs.get(p.getUUID());
        long tick = serveur().getTickCount();
        boolean melee = p != null && src.getDirectEntity() == p && !src.is(DamageTypeTags.IS_PROJECTILE) && !src.is(DamageTypeTags.IS_EXPLOSION);
        if (melee) {
            // couteau facon CoD : 150 points de degats CoD au minimum (un coup tue en manche 1)
            montant = Math.max(montant, (float) (ConfigZombies.reel("couteau", "degats_cod", 150) * Manches.ECHELLE_PV) * s.facteurDegats);
            // Bowie Knife (The Giant) : un coup jusqu'a la manche 10, puis 1000 degats CoD
            if (j != null && j.bowie) {
                montant = manche <= ConfigZombies.entier("the_giant", "bowie_manche_max", 10) ? s.mob.getHealth() + 1000f
                        : Math.max(montant, (float) (1000 * Manches.ECHELLE_PV) * s.facteurDegats);
            }
            // meme horloge que appliquerCoup (ticks de la partie)
            s.meleeTick = ticks();
            s.meleePar = p.getUUID();
        }
        if (p != null && src.is(DamageTypeTags.IS_EXPLOSION) && src.getDirectEntity() != p) {
            ItemStack main = p.getMainHandItem();
            if (ZombiesApi.estAmelioree(main) && Tacz.gunId(main) != null) montant *= (float) PackAPunch.multiplicateur(main);
        }
        if (j != null && j.aAtout(TypeAtout.DOUBLE_IMPACT)) montant *= 1.3f;
        if (instaKill() && j != null) montant = Math.max(montant, s.mob.getHealth() + 1000f);
        e.setAmount(montant);
        // +10 par touche non mortelle ; une balle TaCZ est deja comptee par surTir (deux parties de degats par balle)
        boolean dejaCompte = s.toucheTick == tick && j != null && j.id.equals(s.touchePar);
        if (j != null && montant > 0 && montant < s.mob.getHealth() && phase() == Phase.COMBAT && !dejaCompte) {
            gagner(j, ConfigZombies.entier("points", "touche", 10), MotifPoints.TOUCHE);
            s.toucheTick = tick;
            s.touchePar = j.id;
        }
    }

    /** Mort d'une creature de la partie : points, bonus, materiaux, compteurs. */
    void surZombieMort(LivingEntity mort, DamageSource source) {
        ZombieSuivi s = parId.remove(mort.getId());
        if (s == null) return;
        zombies.remove(s);
        liberer(s);
        if (s.etat == ZombieSuivi.Etat.ENJAMBE) {
            mort.noPhysics = false;
            mort.setNoGravity(false);
        }
        dernierKill = ticks();
        dernierKillX = mort.getX();
        dernierKillY = mort.getY();
        dernierKillZ = mort.getZ();
        hudSale = true;
        if (s.retire || atomiqueEnCours || phase() != Phase.COMBAT) return;
        if (giant != null) giant.mort(mort);
        Entity src = source.getEntity();
        ServerPlayer p = src instanceof ServerPlayer sp ? sp : null;
        JoueurZombies j = p == null ? null : joueurs.get(p.getUUID());
        if (j != null) {
            j.kills++;
            boolean couteau = source.getDirectEntity() == p && !source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION);
            boolean tete = !couteau && s.tirTete && serveur().getTickCount() - s.tirTick <= 1;
            if (couteau) {
                j.couteau++;
                gagner(j, ConfigZombies.entier("points", "couteau", 130), MotifPoints.COUTEAU);
            } else if (tete) {
                j.tetes++;
                gagner(j, ConfigZombies.entier("points", "tete", 100), MotifPoints.TETE);
            } else {
                gagner(j, ConfigZombies.entier("points", "corps", 60), MotifPoints.KILL);
            }
        }
        if (!speciale && bonusManche < 4 && alea.nextFloat() < 0.025f) {
            bonusManche++;
            TypeBonus[] v = TypeBonus.values();
            poserBonus(v[alea.nextInt(v.length)], mort.getX(), mort.getY(), mort.getZ());
        } else {
            lacherMateriaux(s, mort);
        }
    }

    /** Ajoute des points (doubles si le bonus est actif, sauf bonus et achats). */
    private void gagner(JoueurZombies j, int base, MotifPoints motif) {
        int v = base;
        if (bonusTicks[TypeBonus.POINTS_DOUBLES.ordinal()] > 0 && motif != MotifPoints.BONUS && motif != MotifPoints.ACHAT) v *= 2;
        j.points += v;
        j.pointsGagnes += v;
        evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.POINTS, j.id, v, motif.ordinal(), ""));
        if (BANC) HeraliszMatchs.LOG.info("[zombies] points {} +{} {} (total {})", j.nom, v, motif, j.points);
        hudSale = true;
    }

    // ------------------------------------------------------------------ materiaux de recuperation
    private void lacherMateriaux(ZombieSuivi s, LivingEntity mort) {
        float r = alea.nextFloat();
        int n = 0;
        if (s.type == TypeZombie.RODEUR) {
            if (r < ConfigZombies.reel("materiaux", "chance_rodeur", 0.35)) n = 2;
        } else if (r < ConfigZombies.reel("materiaux", "chance_gros", 0.04)) {
            n = ConfigZombies.entier("materiaux", "gros", 3);
        } else if (r < ConfigZombies.reel("materiaux", "chance", 0.18)) {
            n = 1;
        }
        if (n > 0) poserMateriau(n, mort.getX(), mort.getY(), mort.getZ());
    }

    private void poserMateriau(int n, double x, double y, double z) {
        Vec3 pos = new Vec3(x, y + 0.3, z);
        Entity e = null;
        try {
            e = ZombiesApi.decor().materiau(niveau, pos, n);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.materiau", ex);
        }
        if (e == null) {
            Component t = Component.literal("⚙ " + n).withStyle(s -> s.withColor(0xE8A33C).withBold(true).withFont(HeraliszMatchs.id("ui_bold")));
            e = Affichages.texte(niveau, pos.x, pos.y + 0.35, pos.z, t, 0.9f);
        }
        materiauxSol.add(new MateriauSol(n, pos.x, pos.y, pos.z, e, ConfigZombies.entier("materiaux", "duree_secondes", 60) * 20));
    }

    private void tickMateriaux(long t) {
        for (int i = materiauxSol.size() - 1; i >= 0; i--) {
            MateriauSol m = materiauxSol.get(i);
            m.ticks--;
            if (m.entite != null && m.ticks <= 200 && m.ticks % 20 == 0) {
                try {
                    ZombiesApi.decor().bonusExpire(m.entite, m.ticks / 20);
                } catch (RuntimeException ex) {
                    HeraliszMatchs.LOG.error("[zombies] decor.bonusExpire", ex);
                }
                if (m.entite instanceof net.minecraft.world.entity.Display.TextDisplay) m.entite.setInvisible((m.ticks / 10) % 2 == 0);
            }
            if (m.ticks <= 0) {
                Affichages.retirer(m.entite);
                materiauxSol.remove(i);
                continue;
            }
            if (t % 2 != 0) continue;
            for (int k = 0; k < enLigne.size(); k++) {
                ServerPlayer p = enLigne.get(k);
                JoueurZombies j = joueurs.get(p.getUUID());
                if (j == null || !j.debout() || p.level() != niveau) continue;
                if (p.distanceToSqr(m.x, m.y, m.z) <= 1.5 * 1.5) {
                    Affichages.retirer(m.entite);
                    materiauxSol.remove(i);
                    j.materiaux += m.quantite;
                    SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(ZombiesApi.Sons.MATERIAU);
                    if (ev != null) p.playNotifySound(ev, SoundSource.PLAYERS, 0.9f, 1f);
                    evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.MATERIAUX, j.id, m.quantite, 0, ""));
                    if (BANC) HeraliszMatchs.LOG.info("[zombies] materiaux : {} ramasse {} (total {})", j.nom, m.quantite, j.materiaux);
                    hudSale = true;
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ feu (balles incendiaires)
    /** Une balle a touche ce bloc : si du feu apparait sur la face touchee, il est eteint au tick suivant. */
    void impact(BlockPos pos, Direction face) {
        impact(pos, face, null);
    }

    /** Impact de balle avec son tireur (v3 : boitier et objets du Fly Trap). */
    void impact(BlockPos pos, Direction face, @Nullable Entity tireur) {
        if (feux.size() < 512) feux.add(pos.relative(face).asLong());
        if (giant != null && phase() == Phase.COMBAT) giant.impact(pos, tireur);
    }

    private void eteindreFeux() {
        if (feux.isEmpty() || niveau == null) return;
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        for (int i = 0; i < feux.size(); i++) {
            bp.set(feux.getLong(i));
            if (niveau.getBlockState(bp).getBlock() instanceof BaseFireBlock) niveau.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
        }
        feux.clear();
    }

    // ------------------------------------------------------------------ bonus
    private void poserBonus(TypeBonus type, double x, double y, double z) {
        Vec3 pos = new Vec3(x, y + 0.4, z);
        Entity e = null;
        try {
            e = ZombiesApi.decor().bonus(niveau, pos, type);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.bonus", ex);
        }
        if (e == null) {
            Component t = Component.translatable(type.cle()).withStyle(st -> st.withColor(type.couleur).withBold(true)
                    .withFont(HeraliszMatchs.id("ui_bold")));
            e = Affichages.texte(niveau, pos.x, pos.y + 0.4, pos.z, t, 1.4f);
        }
        bonusSol.add(new BonusSol(type, pos.x, pos.y, pos.z, e));
        son(ZombiesApi.Sons.BONUS_APPARITION, pos.x, pos.y, pos.z, 1f);
    }

    private void tickBonus(long t) {
        for (int i = 0; i < bonusTicks.length; i++) {
            if (bonusTicks[i] > 0 && --bonusTicks[i] == 0) {
                sonTous(ZombiesApi.Sons.BONUS_FIN);
                hudSale = true;
            }
        }
        for (int i = bonusSol.size() - 1; i >= 0; i--) {
            BonusSol b = bonusSol.get(i);
            b.ticks--;
            if (b.entite != null && b.ticks <= 100 && b.ticks % 20 == 0) {
                try {
                    ZombiesApi.decor().bonusExpire(b.entite, b.ticks / 20);
                } catch (RuntimeException ex) {
                    HeraliszMatchs.LOG.error("[zombies] decor.bonusExpire", ex);
                }
                if (b.entite instanceof net.minecraft.world.entity.Display.TextDisplay) b.entite.setInvisible((b.ticks / 10) % 2 == 0);
            }
            if (b.ticks <= 0) {
                Affichages.retirer(b.entite);
                bonusSol.remove(i);
                continue;
            }
            if (t % 2 != 0) continue;
            for (int k = 0; k < enLigne.size(); k++) {
                ServerPlayer p = enLigne.get(k);
                JoueurZombies j = joueurs.get(p.getUUID());
                if (j == null || !j.debout() || p.level() != niveau) continue;
                if (p.distanceToSqr(b.x, b.y, b.z) <= 1.6 * 1.6) {
                    Affichages.retirer(b.entite);
                    bonusSol.remove(i);
                    activerBonus(b.type, p, j);
                    break;
                }
            }
        }
    }

    private void activerBonus(TypeBonus type, @Nullable ServerPlayer par, @Nullable JoueurZombies jPar) {
        remplirEnLigne();
        switch (type) {
            case MUNITIONS_MAX -> {
                for (int i = 0; i < enLigne.size(); i++) {
                    ServerPlayer p = enLigne.get(i);
                    for (int s = 0; s < 9; s++) Tacz.remplir(p.getInventory().getItem(s));
                    ItemStack g = p.getInventory().getItem(SLOT_GRENADES);
                    if (Equipement.estPrete(g)) g.setCount(GRENADES_MAX);
                }
            }
            case MORT_INSTANTANEE, POINTS_DOUBLES -> bonusTicks[type.ordinal()] = type.secondes * 20;
            case ATOMIQUE -> {
                atomiqueEnCours = true;
                try {
                    for (int i = zombies.size() - 1; i >= 0; i--) {
                        ZombieSuivi s = zombies.get(i);
                        if (s.mob.isAlive()) s.mob.kill();
                    }
                } finally {
                    atomiqueEnCours = false;
                }
                sonTous(ZombiesApi.Sons.ATOMIQUE);
                for (JoueurZombies j : joueurs.values()) if (j.connecte) gagner(j, 400, MotifPoints.BONUS);
            }
            case CHARPENTIER -> {
                for (PlanZombies.Fenetre f : plan.fenetres()) {
                    if (planches[f.index()] == PLANCHES_MAX) continue;
                    planches[f.index()] = PLANCHES_MAX;
                    try {
                        ZombiesApi.decor().fenetre(instance(), f, PLANCHES_MAX, ZombiesDecor.EvenementFenetre.INSTALLEE);
                    } catch (RuntimeException ex) {
                        HeraliszMatchs.LOG.error("[zombies] decor.fenetre", ex);
                    }
                }
                for (JoueurZombies j : joueurs.values()) if (j.connecte) gagner(j, 200, MotifPoints.BONUS);
            }
        }
        sonTous(ZombiesApi.Sons.BONUS_PRIS);
        evenementTous(PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.BONUS, par == null ? null : par.getUUID(), 0,
                type.ordinal(), jPar == null ? "" : jPar.nom));
        // annonce : banniere du HUD zombies (evenement BONUS, avec le nom du joueur) et pastille minutee du bonus
        hudSale = true;
    }

    // ------------------------------------------------------------------ fin
    @Override
    protected void surFin(@Nullable Equipe gagnante) {
        finMs = System.currentTimeMillis();
        remplirEnLigne();
        // acte IV : combat interrompu tout de suite (camera rendue avant l'ecran de fin), salle remise a zero
        if (acte != null) acte.finPartie();
        for (int i = zombies.size() - 1; i >= 0; i--) {
            ZombieSuivi s = zombies.get(i);
            s.retire = true;
            s.mob.discard();
        }
        zombies.clear();
        parId.clear();
        java.util.Arrays.fill(occupants, null);
        for (BonusSol b : bonusSol) Affichages.retirer(b.entite);
        bonusSol.clear();
        for (MateriauSol m : materiauxSol) Affichages.retirer(m.entite);
        materiauxSol.clear();
        if (boite != null) boite.nettoyer();
        if (pap != null) pap.nettoyer();
        List<Records.Participant> parts = new ArrayList<>();
        List<PaquetsZombies.LigneFin> lignes = new ArrayList<>();
        for (JoueurZombies j : joueurs.values()) {
            parts.add(new Records.Participant(j.id, j.nom, j.kills, j.tetes));
            lignes.add(new PaquetsZombies.LigneFin(j.nom, j.couleur, j.points, j.kills, j.tetes, j.couteau, j.reanimations, j.aTerreFois));
        }
        Records.Resultat r = manche > 0 ? Records.enregistrer(carte.id(), manche, parts) : new Records.Resultat(false, false, Records.recordServeur(carte.id()));
        for (int i = 0; i < enLigne.size(); i++) enLigne.get(i).setForcedPose(null);
        try {
            ZombiesApi.decor().ambiance(instance(), ZombiesDecor.Ambiance.FIN_PARTIE);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.ambiance", ex);
        }
        sonTous(ZombiesApi.Sons.PARTIE_FIN);
        musique(MomentMusique.PARTIE_FIN);
        List<ServerPlayer> l = new ArrayList<>(enLigne);
        if (victoireQuete) {
            Annonces.grande(l, new Annonces.Annonce(Component.translatable("zombies.giant.victoire"),
                    Component.translatable("zombies.giant.victoire.fin", mancheQuete, manche), 0xFFB327, "", 160, Annonces.Style.NORMALE));
        } else {
            Annonces.grande(l, new Annonces.Annonce(Component.translatable("zombies.annonce.fin"),
                    Component.translatable("zombies.annonce.fin.sous", manche), 0xC8102E, "", 120, Annonces.Style.DEFAITE));
        }
        MatchsNet.versJoueurs(l, new PaquetsZombies.FinS2C(carte.id(), manche, finMs - debutMs(), lignes, r.groupe(), r.serveur(),
                Math.max(r.recordServeur(), r.serveur() ? manche : 0)));
        if (r.serveur() && manche >= 5) {
            Annonces.chat(serveur(), Component.translatable("zombies.chat.record", String.join(", ", noms()), manche,
                    carte.nom(true)));
        }
        HeraliszMatchs.LOG.info("[zombies] partie {} terminee a la manche {} (record groupe {}, serveur {})", id(), manche, r.groupe(), r.serveur());
    }

    private List<String> noms() {
        List<String> l = new ArrayList<>();
        for (JoueurZombies j : joueurs.values()) l.add(j.nom);
        return l;
    }

    /** Rend leur Coffre a tous les joueurs encore rattaches. */
    private void rendreTous() {
        for (UUID u : MatchApi.joueursDe(this)) {
            ServerPlayer p = joueur(u);
            if (p != null) rendre(p);
            else MatchApi.quitter(u);
        }
    }

    /** Remet le joueur dans son etat d'avant la partie (Coffre) et le detache. */
    private void rendre(ServerPlayer p) {
        p.setForcedPose(null);
        if (Spectateur.est(p.getUUID())) Spectateur.arreter(p, true);
        p.setCamera(p);
        retirerModificateur(p, Attributes.MAX_HEALTH, MOD_BLINDAGE);
        retirerModificateur(p, Attributes.MOVEMENT_SPEED, MOD_VIGUEUR);
        retirerModificateur(p, Attributes.MOVEMENT_SPEED, MOD_A_TERRE);
        p.setAbsorptionAmount(0f);
        MatchsNet.versJoueur(p, PaquetsZombies.EvenementS2C.simple(PaquetsZombies.EvenementS2C.MUSIQUE, null, manche, MomentMusique.SILENCE.ordinal(), ""));
        MatchsNet.versJoueur(p, new PaquetsZombies.ObjectifS2C("", "", "", false));
        MatchsNet.versJoueur(p, PaquetsZombies.EtatS2C.vide());
        MatchsNet.versJoueur(p, new PaquetsZombies.InviteS2C(null, 0, false, -1));
        Equipement.retirer(p);
        boolean ok = Coffre.rendre(p);
        MatchApi.quitter(p);
        if (!ok) {
            // Coffre absent ou provisoire : ne jamais laisser le joueur dans l'instance
            HeraliszMatchs.LOG.warn("[zombies] Coffre.rendre({}) a echoue : retour au point d'apparition", p.getGameProfile().getName());
            ServerLevel ow = serveur().overworld();
            BlockPos sp = ow.getSharedSpawnPos();
            p.setGameMode(serveur().getDefaultGameType());
            p.teleportTo(ow, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, p.getYRot(), 0f);
        }
    }

    /** Nettoyage du decor et des portes avant liberation de l'instance. */
    private void nettoyer() {
        Instance inst = instance();
        if (inst == null || plan == null) return;
        if (acte != null) {
            acte.nettoyer();
            acte = null;
        }
        purgerEntites();
        restaurerStructure();
        try {
            for (PlanZombies.Fenetre f : plan.fenetres()) ZombiesApi.decor().fenetre(inst, f, PLANCHES_MAX, ZombiesDecor.EvenementFenetre.INSTALLEE);
            for (PlanZombies.Porte po : plan.portes()) ZombiesApi.decor().porte(inst, po, false);
            ZombiesApi.decor().courant(inst, plan.courant() == null);
            if (plan.packAPunch() != null) ZombiesApi.decor().packAPunch(inst, plan.packAPunch(), ZombiesDecor.EtatPap.ETEINTE, ItemStack.EMPTY);
            for (PlanZombies.EtapeEasterEgg e : eeEtapes) ZombiesApi.decor().easterEgg(inst, e, ZombiesDecor.EtatEtape.CACHEE);
            ZombiesApi.decor().nettoyer(inst, plan);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.nettoyer", ex);
        }
    }

    /** Annulation immediate (commande d'administration, arret) : Coffres rendus, instance liberee. */
    void annuler() {
        if (phase() == Phase.TERMINEE) return;
        if (phase() == Phase.COMBAT) terminer(null);
        rendreTous();
        nettoyer();
        fermer();
    }

    // ------------------------------------------------------------------ outils reseau / sons
    private void envoyerEtat() {
        hudSale = false;
        remplirEnLigne();
        List<HudZombies.Joueur> l = new ArrayList<>(joueurs.size());
        for (JoueurZombies j : joueurs.values()) {
            l.add(new HudZombies.Joueur(j.id, j.nom, j.couleur, j.points, j.kills, j.tetes, j.atouts,
                    j.connecte ? j.etat : HudZombies.MORT, j.etat == HudZombies.A_TERRE ? (j.ticksATerre + 19) / 20 : 0,
                    j.materiaux, j.autoReanimations, j.plaquesVues));
        }
        int[] b = new int[bonusTicks.length];
        for (int i = 0; i < b.length; i++) b[i] = (bonusTicks[i] + 19) / 20;
        MatchsNet.versJoueurs(enLigne, new PaquetsZombies.EtatS2C(true, manche, speciale, aFaire + zombies.size(), courant, l, b));
    }

    private void evenementTous(PaquetsZombies.EvenementS2C ev) {
        MatchsNet.versJoueurs(enLigne, ev);
    }

    void messageTous(String cle) {
        for (int i = 0; i < enLigne.size(); i++) Annonces.chat(enLigne.get(i), Component.translatable(cle));
    }

    private void annonceTous(String cle) {
        for (UUID u : MatchApi.joueursDe(this)) {
            ServerPlayer p = joueur(u);
            if (p != null) Annonces.chat(p, Component.translatable(cle));
        }
    }

    /** Joue un son du jeu a une position (son absent = rien). */
    void son(ResourceLocation id, double x, double y, double z, float volume) {
        if (niveau == null) return;
        SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (ev != null) niveau.playSound(null, x, y, z, ev, SoundSource.HOSTILE, volume, 1f);
    }

    /** Joue un son a chaque joueur de la partie (jingles). */
    private void sonTous(ResourceLocation id) {
        SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (ev == null) return;
        for (int i = 0; i < enLigne.size(); i++) enLigne.get(i).playNotifySound(ev, SoundSource.MASTER, 1f, 1f);
    }

    void decorBoite(int emplacement, ZombiesDecor.EtatBoite etat) {
        try {
            ZombiesApi.decor().boite(instance(), emplacement, etat);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.boite", ex);
        }
    }

    void decorBoiteTirage(int emplacement, Rarete rarete) {
        try {
            ZombiesApi.decor().boiteTirage(instance(), emplacement, rarete);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.boiteTirage", ex);
        }
    }

    // ------------------------------------------------------------------ outils de developpement
    void devManche(int n) {
        for (int i = zombies.size() - 1; i >= 0; i--) {
            ZombieSuivi s = zombies.get(i);
            s.retire = true;
            s.mob.discard();
        }
        zombies.clear();
        parId.clear();
        java.util.Arrays.fill(occupants, null);
        aFaire = 0;
        manche = Math.max(0, n - 1);
        prochaineSpeciale = Math.max(prochaineSpeciale, n + 1);
        commencerManche();
    }

    /** Passe tout de suite a une manche speciale (Rodeurs) : la manche suivante, comme {@link #devManche}. */
    void devSpeciale() {
        for (int i = zombies.size() - 1; i >= 0; i--) {
            ZombieSuivi s = zombies.get(i);
            s.retire = true;
            s.mob.discard();
        }
        zombies.clear();
        parId.clear();
        java.util.Arrays.fill(occupants, null);
        aFaire = 0;
        prochaineSpeciale = manche + 1;
        commencerManche();
    }

    /**
     * Place le joueur a une position de la carte (coordonnees relatives a l'origine de l'instance, comme le JSON de la
     * carte) avec une orientation donnee : prises de vue du banc d'essai.
     */
    boolean devVue(ServerPlayer p, double x, double y, double z, float lacet, float tangage) {
        if (plan == null || niveau == null) return false;
        BlockPos o = plan.origine();
        p.teleportTo(niveau, o.getX() + x, o.getY() + y, o.getZ() + z, lacet, tangage);
        return true;
    }

    void devPoints(UUID joueur, int n) {
        JoueurZombies j = joueurs.get(joueur);
        if (j != null) {
            j.points = Math.max(0, n);
            hudSale = true;
        }
    }

    void devMateriaux(UUID joueur, int n) {
        JoueurZombies j = joueurs.get(joueur);
        if (j != null) {
            j.materiaux = Math.max(0, n);
            hudSale = true;
        }
    }

    void devBonus(TypeBonus type, ServerPlayer p) {
        poserBonus(type, p.getX() + p.getLookAngle().x * 2, p.getY(), p.getZ() + p.getLookAngle().z * 2);
    }

    void devMateriauSol(ServerPlayer p, int n) {
        poserMateriau(Math.max(1, n), p.getX() + p.getLookAngle().x * 2, p.getY(), p.getZ() + p.getLookAngle().z * 2);
    }

    void devCourant() {
        retablirCourant(null);
    }

    void devTuer() {
        for (int i = zombies.size() - 1; i >= 0; i--) zombies.get(i).mob.kill();
    }

    void devPortes() {
        for (int i = 0; i < portesOuvertes.length; i++) ouvrirPorte(i);
    }

    /** Met le joueur a terre (banc d'essai). */
    void devATerre(ServerPlayer p) {
        JoueurZombies j = joueurs.get(p.getUUID());
        if (j != null && j.debout() && phase() == Phase.COMBAT) mettreATerre(p, j);
    }

    /** Suspend ou reprend les apparitions naturelles et le garde-fou de manche (banc d'essai). */
    void devPause(boolean on) {
        devPause = on;
        HeraliszMatchs.LOG.info("[zombies] banc : apparitions {}", on ? "suspendues" : "reprises");
    }

    /** Effets actifs du joueur (banc : malus refuses). */
    String devEffets(ServerPlayer p) {
        StringBuilder b = new StringBuilder();
        for (net.minecraft.world.effect.MobEffectInstance i : p.getActiveEffects()) {
            if (b.length() > 0) b.append(", ");
            b.append(ForgeRegistries.MOB_EFFECTS.getKey(i.getEffect())).append(' ').append(i.getDuration() / 20).append('s');
        }
        return b.length() == 0 ? "aucun" : b.toString();
    }

    /** Garde-fou : fait comme si le dernier kill datait de {@code secondes} (banc d'essai). */
    void devSansKill(int secondes) {
        dernierKill = ticks() - secondes * 20L;
    }

    /** Impose la rarete du prochain tirage de la boite (banc d'essai). */
    void devBoite(Rarete r) {
        if (boite != null) boite.forcee = r;
    }

    /** Fixe les planches d'une fenetre (banc d'essai). */
    void devFenetre(int index, int n) {
        if (plan == null || index < 0 || index >= planches.length) return;
        planches[index] = Math.max(0, Math.min(PLANCHES_MAX, n));
        try {
            ZombiesApi.decor().fenetre(instance(), plan.fenetres().get(index), planches[index], ZombiesDecor.EvenementFenetre.INSTALLEE);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.fenetre", ex);
        }
    }

    /**
     * Fait apparaitre un zombie derriere une fenetre (banc d'essai : sequence approche, arrachage, enjambement), sans
     * toucher au compte de la manche. Il marche vite pour raccourcir le test.
     */
    boolean devZombieFenetre(int index) {
        if (plan == null || niveau == null || index < 0 || index >= plan.fenetres().size()) return false;
        PlanZombies.Fenetre f = plan.fenetres().get(index);
        Mob m = creer(TypeZombie.NORMAL, Allure.COUREUR);
        if (m == null) return false;
        base(m, Attributes.MAX_HEALTH, Manches.pv(Math.max(1, manche)));
        base(m, Attributes.MOVEMENT_SPEED, Allure.COUREUR.vitesse);
        base(m, Attributes.ATTACK_DAMAGE, Manches.degats(manche, false));
        base(m, Attributes.FOLLOW_RANGE, 64);
        m.setHealth((float) Manches.pv(Math.max(1, manche)));
        Vec3 p = sol(f.apparition().position());
        m.moveTo(p.x, p.y, p.z, f.apparition().yaw(), 0f);
        m.setPersistenceRequired();
        m.getPersistentData().putString(ZombiesApi.TAG_PARTIE, id());
        if (!niveau.addFreshEntity(m)) return false;
        ZombieSuivi s = new ZombieSuivi(m, TypeZombie.NORMAL, Allure.COUREUR, index);
        zombies.add(s);
        parId.put(m.getId(), s);
        try {
            ZombiesApi.decor().apparition(instance(), m, f);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.apparition", ex);
        }
        HeraliszMatchs.LOG.info("[zombies] banc : zombie derriere la fenetre {} (point {}, colle a {}, retombe a {})", index, fmt(p), fmt(f.dehors()), fmt(f.dedans()));
        hudSale = true;
        return true;
    }

    /** Pose un Pack-a-Punch de banc devant le joueur si la carte n'en a pas (logique seule, rendu de secours). */
    /**
     * Banc : l'arme tenue devient une arme amelioree « ancienne formule » (marqueur {@link ZombiesApi#NBT_PAP} seul, nom
     * « ★ ... », sans niveau ni nom d'origine), pour verifier qu'elle passe au niveau 2.
     */
    String devPapAncien(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        if (Tacz.gunId(s) == null) return "pas d'arme TaCZ en main";
        CompoundTag t = s.getOrCreateTag();
        t.putBoolean(ZombiesApi.NBT_PAP, true);
        t.remove(ZombiesApi.NBT_PAP_NIVEAU);
        t.remove("HeraliszZbPapNom");
        Component nom = s.getHoverName().copy();
        s.setHoverName(Component.empty().append(Component.literal("★ ").withStyle(st -> st.withFont(net.minecraft.network.chat.Style.DEFAULT_FONT)))
                .append(nom).withStyle(st -> st.withColor(0xE59BFF).withItalic(false).withBold(true)));
        p.getInventory().setChanged();
        String r = resumeArme(p);
        HeraliszMatchs.LOG.info("[zombies] banc : ancienne arme amelioree : {}", r);
        return r;
    }

    void devPap(ServerPlayer p) {
        if (pap != null) return;
        Direction face = Direction.fromYRot(p.getYRot()).getOpposite();
        BlockPos pos = BlockPos.containing(p.getX(), p.getY(), p.getZ()).relative(face.getOpposite(), 2);
        pap = new PackAPunch(this, new PlanZombies.PackAPunch(pos, face, 5000, ""));
        pap.installer(courant);
        HeraliszMatchs.LOG.info("[zombies] banc : Pack-a-Punch pose en {}", pos);
    }

    /** Pose un etabli de banc devant le joueur. */
    void devAtelier(ServerPlayer p) {
        Direction face = Direction.fromYRot(p.getYRot()).getOpposite();
        BlockPos pos = BlockPos.containing(p.getX(), p.getY(), p.getZ()).relative(face.getOpposite(), 2);
        PlanZombies.Atelier a = new PlanZombies.Atelier(ateliers.size(), pos, face, "");
        ateliers.add(a);
        try {
            ZombiesApi.decor().atelier(instance(), a, null);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.atelier", ex);
        }
        HeraliszMatchs.LOG.info("[zombies] banc : etabli {} pose en {}", a.index(), pos);
    }

    /** Pose un easter egg de banc a 3 etapes autour du joueur (si la carte n'en a pas). */
    void devEasterEgg(ServerPlayer p) {
        if (!eeEtapes.isEmpty()) return;
        BlockPos b = p.blockPosition();
        String[] types = {"radio", "interrupteur", "objet"};
        for (int i = 0; i < 3; i++) eeEtapes.add(new PlanZombies.EtapeEasterEgg(i, b.offset(2 * (i - 1), 0, 3), types[i], ""));
        eeTrouvees = new boolean[eeEtapes.size()];
        eeProchaine = 0;
        eeFini = false;
        HeraliszMatchs.LOG.info("[zombies] banc : easter egg de 3 etapes pose autour de {}", b);
    }

    /**
     * Place le joueur devant une cible et le fait regarder vers elle (banc d'essai) : {@code porte|fenetre|arme|atout|
     * atelier|ee} + index, {@code boite}, {@code courant}, {@code pap}, {@code depart}, {@code dehors} + index (point
     * exterieur d'une fenetre, vue de l'exterieur). Rend faux si la cible n'existe pas.
     */
    boolean devTp(ServerPlayer p, String type, int index) {
        if (plan == null || niveau == null) return false;
        Vec3 cible;
        Vec3 pied;
        switch (type) {
            case "porte" -> {
                if (index < 0 || index >= plan.portes().size()) return false;
                cible = plan.portes().get(index).volume().getCenter();
                pied = devant(cible, null);
            }
            case "fenetre" -> {
                if (index < 0 || index >= plan.fenetres().size()) return false;
                PlanZombies.Fenetre f = plan.fenetres().get(index);
                cible = f.centre();
                // a ~2 blocs a l'interieur, face a la fenetre (assez loin pour la voir entiere, a portee de reparation)
                double d = f.largeurNormale() / 2.0 + 1.9;
                Vec3 recul = new Vec3(f.centre().x - f.exterieur().getStepX() * d, f.planches().minY, f.centre().z - f.exterieur().getStepZ() * d);
                Vec3 s = sol(recul);
                pied = s == recul ? sol(f.dedans()) : s;
            }
            case "contre" -> {
                // colle a la fenetre, cote interieur (portee de couteau)
                if (index < 0 || index >= plan.fenetres().size()) return false;
                PlanZombies.Fenetre f = plan.fenetres().get(index);
                cible = f.centre();
                double d = f.largeurNormale() / 2.0 + 0.9;
                Vec3 c = new Vec3(f.centre().x - f.exterieur().getStepX() * d, f.planches().minY, f.centre().z - f.exterieur().getStepZ() * d);
                Vec3 s = sol(c);
                pied = new Vec3(c.x, s.y, c.z);
            }
            case "dehors" -> {
                // vue de cote, dehors, sur le point exterieur de la fenetre
                if (index < 0 || index >= plan.fenetres().size()) return false;
                PlanZombies.Fenetre f = plan.fenetres().get(index);
                cible = f.dehors().add(0, 1.0, 0);
                Direction lat = f.exterieur().getClockWise();
                pied = new Vec3(f.dehors().x + f.exterieur().getStepX() * 2.2 + lat.getStepX() * 2.2, f.dehors().y,
                        f.dehors().z + f.exterieur().getStepZ() * 2.2 + lat.getStepZ() * 2.2);
            }
            case "arme" -> {
                if (index < 0 || index >= plan.armes().size()) return false;
                PlanZombies.ArmeMur a = plan.armes().get(index);
                cible = Vec3.atCenterOf(a.pos());
                pied = devant(cible, a.face());
            }
            case "atout" -> {
                if (index < 0 || index >= atouts.size()) return false;
                PlanZombies.Atout a = atouts.get(index);
                cible = Vec3.atCenterOf(a.pos());
                pied = devant(cible, a.face());
            }
            case "boite" -> {
                if (boite == null || boite.centre() == null) return false;
                cible = boite.centre();
                pied = devant(cible, null);
            }
            case "courant" -> {
                if (plan.courant() == null) return false;
                cible = plan.courant().position().add(0, 0.5, 0);
                pied = devant(cible, null);
            }
            case "pap" -> {
                if (pap == null) return false;
                cible = pap.centre();
                pied = devant(Vec3.atCenterOf(pap.machine.pos()), pap.machine.face());
            }
            case "atelier" -> {
                if (index < 0 || index >= ateliers.size()) return false;
                cible = Vec3.atCenterOf(ateliers.get(index).pos());
                pied = devant(cible, ateliers.get(index).face());
            }
            case "ee" -> {
                if (index < 0 || index >= eeEtapes.size()) return false;
                cible = Vec3.atCenterOf(eeEtapes.get(index).pos());
                pied = devant(cible, null);
            }
            default -> {
                if (plan.spawnJoueurs().isEmpty()) return false;
                plan.spawnJoueurs().get(0).teleporter(p, niveau);
                return true;
            }
        }
        double dx = cible.x - pied.x, dy = cible.y - (pied.y + 1.62), dz = cible.z - pied.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.teleportTo(niveau, pied.x, pied.y, pied.z, yaw, pitch);
        return true;
    }

    /**
     * Place le joueur face au dessin de l'arme au mur {@code index}, l'oeil a {@code distance} blocs du mur, centre sur le
     * dessin, le regard tourne de {@code lacet} degres (0 = droit sur le dessin) : test de la distance d'achat.
     */
    boolean devTpMur(ServerPlayer p, int index, double distance, float lacet) {
        if (plan == null || niveau == null || index < 0 || index >= plan.armes().size()) return false;
        PlanZombies.ArmeMur a = plan.armes().get(index);
        Direction fc = a.face();
        double px = a.pos().getX() + 0.5 - fc.getStepX() * 0.5 + fc.getStepX() * distance;
        double pz = a.pos().getZ() + 0.5 - fc.getStepZ() * 0.5 + fc.getStepZ() * distance;
        Vec3 s = sol(new Vec3(px, a.pos().getY(), pz));
        double dy = a.pos().getY() + 0.5 - (s.y + 1.62);
        float yaw = fc.getOpposite().toYRot() + lacet;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(0.1, distance)));
        p.teleportTo(niveau, px, s.y, pz, yaw, pitch);
        HeraliszMatchs.LOG.info("[zombies] banc : devant le dessin {} ({}) a {} bloc(s), lacet {}", index, a.gunId(), distance, lacet);
        return true;
    }

    /** Position debout a ~1,6 bloc de la cible (cote de la face si connue, sinon le premier cote libre d'une zone ouverte). */
    private Vec3 devant(Vec3 c, @Nullable Direction face) {
        Direction[] dirs = face != null ? new Direction[]{face, face.getOpposite()}
                : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (double r = 1.4; r <= 3.2; r += 0.6) {
            for (Direction d : dirs) {
                Vec3 v = new Vec3(c.x + d.getStepX() * r, c.y, c.z + d.getStepZ() * r);
                Vec3 s = sol(v);
                if (s == v) continue;
                PlanZombies.Zone z = plan.zoneA(s.x, s.y + 0.5, s.z);
                if (z == null || zonesOuvertes[idsZones.indexOf(z.id())]) return s;
            }
        }
        return sol(c);
    }

    /** Resume lisible pour {@code /zombies dev etat}. */
    String resume() {
        StringBuilder etats = new StringBuilder();
        int dehors = 0, arrache = 0, enjambe = 0, dedans = 0;
        for (ZombieSuivi s : zombies) {
            switch (s.etat) {
                case DEHORS -> dehors++;
                case ARRACHE -> arrache++;
                case ENJAMBE -> enjambe++;
                case DEDANS -> dedans++;
            }
        }
        etats.append(dehors).append('/').append(arrache).append('/').append(enjambe).append('/').append(dedans);
        if (BANC && plan != null) {
            // detail par zombie dans le journal (banc) : etat, position, distance au point de l'embrasure, chemin
            for (int i = 0; i < Math.min(8, zombies.size()); i++) {
                ZombieSuivi s = zombies.get(i);
                Mob m = s.mob;
                String fen = "-";
                if (s.fenetre >= 0 && s.fenetre < plan.fenetres().size()) {
                    Vec3 d = plan.fenetres().get(s.fenetre).dehors();
                    fen = s.fenetre + String.format(Locale.ROOT, " a %.2f", Math.sqrt((m.getX() - d.x) * (m.getX() - d.x) + (m.getZ() - d.z) * (m.getZ() - d.z)));
                }
                Path ch = m.getNavigation().getPath();
                HeraliszMatchs.LOG.info("[zombies] etat   zombie {} {} pos {} fenetre {} chemin {} immobile {}", i, s.etat, fmt(m.position()), fen,
                        ch == null ? "aucun" : (ch.isDone() ? "fini" : ch.getNextNodeIndex() + "/" + ch.getNodeCount()) + (ch == null || ch.getEndNode() == null ? "" : " -> " + ch.getEndNode().asBlockPos().toShortString()),
                        s.ticksImmobile);
            }
        }
        StringBuilder pl = new StringBuilder();
        for (int i = 0; i < planches.length; i++) pl.append(planches[i]);
        return String.format(Locale.ROOT, "%s phase=%s etape=%s manche=%d speciale=%b aFaire=%d vivants=%d (dehors/arrache/enjambe/dedans %s) courant=%b zones=%s"
                        + " boite=%s pap=%s objectif=%s ee=%d/%d%s planches=%s sansKill=%ds instaKill=%d",
                id(), phase(), etape, manche, speciale, aFaire, zombies.size(), etats, courant, zonesOuvertesTexte(),
                boite == null ? "-" : boite.emplacement + ":" + boite.etat, pap == null ? "-" : pap.etat, objectifs == null ? "-" : objectifs.courant(),
                eeProchaine, eeEtapes.size(), eeFini ? " fini" : "", pl, (ticks() - dernierKill) / 20, instaKillManches)
                + (giant == null ? "" : " giant[" + giant.resume() + "] chiens=" + manchesChiens)
                + (acte == null ? "" : " " + acte.resume() + " quete=" + (victoireQuete ? "terminee manche " + mancheQuete : "non"));
    }

    private String zonesOuvertesTexte() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < zonesOuvertes.length; i++) if (zonesOuvertes[i]) b.append(b.length() == 0 ? "" : ",").append(idsZones.get(i));
        return b.toString();
    }

    // ------------------------------------------------------------------ acces pour QueteGiant (v3)
    @Nullable
    QueteGiant giant() {
        return giant;
    }

    List<ServerPlayer> enLigneCourant() {
        return enLigne;
    }

    @Nullable
    JoueurZombies joueurZ(UUID u) {
        return joueurs.get(u);
    }

    java.util.Collection<JoueurZombies> joueursZ() {
        return joueurs.values();
    }

    List<ZombieSuivi> zombiesSuivis() {
        return zombies;
    }

    void hudSale() {
        hudSale = true;
    }

    boolean payerPublic(ServerPlayer p, JoueurZombies j, int cout, Component quoi) {
        return payer(p, j, cout, quoi);
    }

    void refusPublic(ServerPlayer p, String cle) {
        refus(p, cle);
    }

    void retablirCourantPublic(@Nullable ServerPlayer p) {
        retablirCourant(p);
    }

    void objectifRempliPublic(String id) {
        objectifRempli(id);
    }

    void ouvrirZonePublic(String id) {
        ouvrirZone(id);
        hudSale = true;
    }

    void poserBonusPublic(TypeBonus type, double x, double y, double z) {
        poserBonus(type, x, y, z);
    }

    void musiqueAccentPublic(MomentMusique m) {
        musiqueAccent(m);
    }

    void sonTousPublic(ResourceLocation id) {
        sonTous(id);
    }

    double distanceJoueurDeboutPublic(double x, double y, double z) {
        return distanceJoueurDebout(x, y, z);
    }

    static boolean regardePublic(Vec3 oeil, Vec3 regard, Vec3 cible, double min) {
        return regarde(oeil, regard, cible, min);
    }

    static double penalitePublic(double d2, Vec3 oeil, Vec3 regard, Vec3 cible) {
        return penalite(d2, oeil, regard, cible);
    }

    /** Message dans le chat de tous les joueurs de la partie. */
    void annonceChat(Component c) {
        remplirEnLigne();
        for (int i = 0; i < enLigne.size(); i++) Annonces.chat(enLigne.get(i), c);
    }

    /**
     * Bowie Knife achete : l'objet de melee de la regle {@code the_giant.bowie_objet} (s'il existe) remplace le couteau ;
     * sinon le couteau actuel devient mortel (le bonus de degats est applique par {@link #surZombieTouche}).
     */
    void donnerBowie(ServerPlayer p) {
        String oid = ConfigZombies.texte("the_giant", "bowie_objet", "");
        if (oid.isEmpty()) return;
        ItemStack s = objet(oid, 1);
        if (s.isEmpty()) return;
        p.getInventory().setItem(SLOT_COUTEAU, Equipement.marquer(s, id()));
        p.getInventory().setChanged();
    }

    /** Un chien apparait dans un eclair (teleporteur apres la manche 25) ; il compte en plus de la manche. */
    void apparaitreChien(Vec3 ou) {
        if (niveau == null || zombies.size() >= Manches.VIVANTS_MAX + 6) return;
        Mob m = creer(TypeZombie.RODEUR, Allure.RODEUR);
        if (m == null) return;
        double pv = Math.min(Manches.PV_ATTRIBUT_MAX, Manches.pvRodeur(manche));
        base(m, Attributes.MAX_HEALTH, pv);
        base(m, Attributes.MOVEMENT_SPEED, Allure.RODEUR.vitesse);
        base(m, Attributes.ATTACK_DAMAGE, Manches.degats(manche, true));
        base(m, Attributes.FOLLOW_RANGE, 64);
        m.setHealth((float) pv);
        Vec3 pos = sol(ou);
        m.moveTo(pos.x, pos.y, pos.z, alea.nextFloat() * 360, 0f);
        m.setPersistenceRequired();
        m.getPersistentData().putString(ZombiesApi.TAG_PARTIE, id());
        if (!niveau.addFreshEntity(m)) return;
        ZombieSuivi s = new ZombieSuivi(m, TypeZombie.RODEUR, Allure.RODEUR, -1);
        zombies.add(s);
        parId.put(m.getId(), s);
        try {
            ZombiesApi.decor().apparitionSalle(instance(), m, "sol");
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[zombies] decor.apparition", ex);
        }
        cibler(s);
        hudSale = true;
    }

    /**
     * Quete de The Giant terminee (N-07 vaincu et tous remontes, ou porte de confinement sans le mod du boss) : records
     * (« quete terminee en X manches »), annonce ; la partie CONTINUE (facon CoD), objectif « Survivez ». Sans recompense
     * pour l'instant.
     */
    void victoireQuete() {
        if (phase() != Phase.COMBAT || victoireQuete) return;
        victoireQuete = true;
        mancheQuete = Math.max(1, manche);
        objectifRempli("n07");
        objectifRempli("n07_boss");
        objectifRempli("n07_retour");
        List<Records.Participant> parts = new ArrayList<>();
        for (JoueurZombies j : joueurs.values()) parts.add(new Records.Participant(j.id, j.nom, j.kills, j.tetes));
        boolean record = Records.enregistrerQuete(carte.id(), mancheQuete, parts);
        remplirEnLigne();
        Annonces.grande(enLigneListe(), new Annonces.Annonce(Component.translatable("zombies.giant.victoire"),
                Component.translatable("zombies.giant.victoire.sous", mancheQuete), 0xFFB327, "", 140, Annonces.Style.NORMALE));
        sonTous(ZombiesApi.Sons.EASTER_EGG_FIN);
        annonceChat(Component.translatable(record ? "zombies.giant.quete.record" : "zombies.giant.quete", mancheQuete)
                .withStyle(st -> st.withColor(0xFFB327).withBold(true)));
        if (record) Annonces.chat(serveur(), Component.translatable("zombies.chat.quete", String.join(", ", noms()), carte.nom(true), mancheQuete));
        HeraliszMatchs.LOG.info("[zombies] {} : quete de The Giant terminee a la manche {} (record du serveur {})", id(), mancheQuete, record);
        hudSale = true;
    }

    // ------------------------------------------------------------------ acte IV (ActeQuatre)
    @Nullable
    ActeQuatre acte() {
        return acte;
    }

    /** Joueurs de l'acte IV : connectes, ni morts ni spectateurs, dans l'instance (relu a chaque usage, liste neuve). */
    List<ServerPlayer> joueursFinale() {
        remplirEnLigne();
        List<ServerPlayer> l = new ArrayList<>(enLigne.size());
        for (int i = 0; i < enLigne.size(); i++) {
            ServerPlayer p = enLigne.get(i);
            JoueurZombies j = joueurs.get(p.getUUID());
            if (j != null && j.etat != HudZombies.MORT && p.level() == niveau && !p.isSpectator()) l.add(p);
        }
        return l;
    }

    /** Le boss peut viser / blesser ce joueur : debout (jamais un joueur a terre). */
    boolean cibleBoss(ServerPlayer p) {
        JoueurZombies j = joueurs.get(p.getUUID());
        return j != null && j.connecte && j.debout() && phase() == Phase.COMBAT;
    }

    /** Montant exact d'un coup du boss qui va arriver sur ce joueur (applique par surDegats dans le meme tick). */
    void degatsBossPrevus(ServerPlayer p, float montant) {
        JoueurZombies j = joueurs.get(p.getUUID());
        if (j == null) return;
        j.degatsBoss = montant;
        j.degatsBossTick = serveur().getTickCount();
    }

    /**
     * Debut de l'acte IV : joueurs a terre releves, morts ramenes (tout le monde descend), zombies de la manche retires et
     * rendus a la file de la manche (elle reprendra au retour), musique du jeu coupee.
     */
    void finaleDebut() {
        remplirEnLigne();
        int rendus = 0;
        for (int i = zombies.size() - 1; i >= 0; i--) {
            ZombieSuivi s = zombies.get(i);
            s.retire = true;
            liberer(s);
            s.mob.discard();
            if (!s.salle) rendus++;
        }
        zombies.clear();
        parId.clear();
        if (etape == Etape.MANCHE) aFaire += rendus;
        java.util.Arrays.fill(occupants, null);
        ramenerMorts();
        releverTous();
        musique(MomentMusique.BOSS_INTRO);
        hudSale = true;
    }

    /** Fin de l'acte IV (retour, abandon) : les manches reprennent la ou elles etaient, musique du jeu. */
    void finaleFin() {
        retirerRenforts();
        dernierKill = ticks();
        ticksAvantApparition = 20;
        musique(MomentMusique.BOSS_FIN);
        hudSale = true;
    }

    /** Tous les joueurs a terre se relevent (debut de l'acte, victoire sur le boss). */
    void releverTous() {
        remplirEnLigne();
        for (int i = 0; i < enLigne.size(); i++) {
            ServerPlayer p = enLigne.get(i);
            JoueurZombies j = joueurs.get(p.getUUID());
            if (j != null && j.etat == HudZombies.A_TERRE) relever(p, j, null);
        }
    }

    /** Points pour tous les joueurs encore la (bonus de la victoire sur le boss). */
    void bonusTous(int points) {
        if (points <= 0) return;
        for (JoueurZombies j : joueurs.values()) if (j.connecte) gagner(j, points, MotifPoints.BONUS);
    }

    /** Renforts du hall vivants. */
    int renfortsVivants() {
        int n = 0;
        for (int i = 0; i < zombies.size(); i++) if (zombies.get(i).salle) n++;
        return n;
    }

    /** Fige / libere les renforts du hall (cinematiques du boss). */
    void gelerRenforts(boolean gele) {
        for (int i = 0; i < zombies.size(); i++) {
            ZombieSuivi s = zombies.get(i);
            if (!s.salle) continue;
            s.mob.setNoAi(gele);
            if (gele) immobiliser(s.mob);
            else s.reinitialiserGardeFou();
        }
    }

    /** Retire les renforts du hall, sans points ni bonus. */
    void retirerRenforts() {
        for (int i = zombies.size() - 1; i >= 0; i--) {
            ZombieSuivi s = zombies.get(i);
            if (!s.salle) continue;
            s.retire = true;
            liberer(s);
            zombies.remove(i);
            parId.remove(s.mob.getId());
            s.mob.discard();
        }
        hudSale = true;
    }

    /**
     * Renfort du hall du boss (hors manche) : un zombie de la manche, ou un chien qui arrive dans un eclair (fin de phase).
     * Ressort du hall s'il s'y perd ({@link #gardeFouSalle}). Rend vrai s'il est apparu.
     */
    boolean apparaitreRenfort(Vec3 ou, boolean chien, boolean eclair) {
        if (niveau == null || zombies.size() >= Manches.VIVANTS_MAX + 10) return false;
        TypeZombie type = chien ? TypeZombie.RODEUR : TypeZombie.NORMAL;
        Allure allure = chien ? Allure.RODEUR : Manches.allure(manche, alea);
        Mob m = creer(type, allure);
        if (m == null) return false;
        double pv = chien ? Manches.pvRodeur(manche) : Manches.pv(manche);
        float facteur = 1f;
        if (pv > Manches.PV_ATTRIBUT_MAX) {
            facteur = (float) (Manches.PV_ATTRIBUT_MAX / pv);
            pv = Manches.PV_ATTRIBUT_MAX;
        }
        base(m, Attributes.MAX_HEALTH, pv);
        base(m, Attributes.MOVEMENT_SPEED, allure.vitesse);
        base(m, Attributes.ATTACK_DAMAGE, Manches.degats(manche, chien));
        base(m, Attributes.FOLLOW_RANGE, 64);
        base(m, Attributes.KNOCKBACK_RESISTANCE, 0.4);
        m.setHealth((float) pv);
        Vec3 pos = sol(ou);
        m.moveTo(pos.x, pos.y, pos.z, alea.nextFloat() * 360, 0f);
        m.setPersistenceRequired();
        m.setCanPickUpLoot(false);
        m.getPersistentData().putString(ZombiesApi.TAG_PARTIE, id());
        if (!niveau.addFreshEntity(m)) return false;
        ZombieSuivi s = new ZombieSuivi(m, type, allure, -1);
        s.facteurDegats = facteur;
        s.salle = true;
        zombies.add(s);
        parId.put(m.getId(), s);
        if (eclair) {
            net.minecraft.world.entity.LightningBolt b = EntityType.LIGHTNING_BOLT.create(niveau);
            if (b != null) {
                b.moveTo(pos.x, pos.y, pos.z);
                b.setVisualOnly(true);
                niveau.addFreshEntity(b);
            }
            son(ZombiesApi.Sons.ECLAIR, pos.x, pos.y + 1, pos.z, 1.4f);
        } else {
            try {
                ZombiesApi.decor().apparitionSalle(instance(), m, "sol");
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[zombies] decor.apparition", ex);
            }
        }
        cibler(s);
        hudSale = true;
        return true;
    }

    void musiquePublic(MomentMusique m) {
        musique(m);
    }

    /** {@code /zombies dev giant <action>} (banc d'essai) : fusibles, courant, ames, lier, flytrap. */
    String devGiant(String action) {
        if (giant == null) return "pas de quete The Giant sur cette carte";
        if ("courant".equals(action)) {
            giant.dev("fusibles");
            retablirCourant(null);
        } else if ("bossvictoire".equals(action)) {
            if (acte != null) acte.devVictoire();
        } else if ("bossevacuer".equals(action)) {
            if (acte != null) acte.devEvacuer();
        } else {
            giant.dev(action);
        }
        return giant.resume();
    }


    /** Points du joueur (banc, tests). */
    int points(UUID joueur) {
        JoueurZombies j = joueurs.get(joueur);
        return j == null ? -1 : j.points;
    }

    /** Ligne de journal sur l'arme tenue (banc) : id, rarete, amelioree, chargeur. */
    String resumeArme(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        String g = Tacz.gunId(s);
        if (g == null) return "main=" + ForgeRegistries.ITEMS.getKey(s.getItem());
        Rarete r = ZombiesApi.rarete(s);
        StringBuilder acc = new StringBuilder();
        for (String emp : new String[]{"scope", "muzzle", "extended_mag", "stock", "grip", "laser"}) {
            String a = Tacz.accessoire(s, emp);
            if (a != null) acc.append(emp).append('=').append(a).append(' ');
        }
        MutableComponent n = s.getHoverName().copy();
        CompoundTag t = s.getTag();
        return String.format(Locale.ROOT, "arme=%s nom=\"%s\" rarete=%s pap=%b niveau=%d degats=x%s chargeur=%d reserve=%d camo=%s accessoires=[%s]", g,
                n.getString(), r, ZombiesApi.estAmelioree(s), ZombiesApi.niveauPap(s), PackAPunch.multiplicateur(s), Tacz.chargeur(s), Tacz.reserveActuelle(s),
                t != null && t.contains("HeraliszCamo") ? t.getString("HeraliszCamo") : "-", acc.toString().trim());
    }
}
