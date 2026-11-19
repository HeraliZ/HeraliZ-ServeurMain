package com.Spartan.check.interaction;

import com.Spartan.alert.AlertManager;
import com.Spartan.check.Check;
import com.Spartan.check.CheckManager;
import com.Spartan.check.Compteur;
import com.Spartan.check.movement.LatenceCheck;
import com.Spartan.config.SpartanConfig;
import com.Spartan.esp.VisibilityEngine;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Interactions impossibles pour un jeu honnête (19/11, demande du propriétaire), section {@code [interactions]}.
 *
 * <p>Minecraft ne vérifie que la DISTANCE d'un clic droit (6 blocs jusqu'au milieu du bloc), jamais ce qu'il y a entre le
 * joueur et l'objet. Un jeu modifié ouvrait donc à travers un mur, dans ces 6 blocs : les conteneurs du loot (salles à carte
 * comprises, sans la carte), les coffres T7, les caisses des largages, les joueurs à terre (fouille), etc. Le jeu honnête,
 * lui, ne peut viser que ce qu'il voit (son rayon s'arrête au premier bloc) et à sa portée (4,5 blocs pour un bloc, 3 pour
 * une entité). Ici, le serveur refait le rayon :
 * <ul>
 *   <li><b>vue</b> : un rayon des yeux vers le point visé par le jeu, puis vers le milieu, les faces et les coins de l'objet
 *   ({@link PointsDeVue}) ; un seul rayon libre suffit. Seuls les blocs qui ont une collision arrêtent le rayon (pas
 *   l'herbe ni les fleurs), le bloc où sont les yeux ne compte pas, ni les autres blocs du même objet (coffre double,
 *   conteneur de plusieurs blocs) ;</li>
 *   <li><b>portée</b> : distance des yeux au plus proche point de l'objet, contre la portée du joueur + {@code margePortee}
 *   (décalage d'un tick du réseau) ;</li>
 *   <li><b>joueur caché</b> : un coup ou un clic droit sur un joueur que l'anti-ESP cache à ce joueur depuis plus que son
 *   ping + 300 ms, et que Minecraft ne lui envoie vraiment plus : son jeu n'a pas ce joueur, le paquet est fabriqué.</li>
 * </ul>
 * Le clic refusé ne fait rien (« Tu ne vois pas ça d'ici. » dans la barre d'action) : un joueur honnête mal placé se décale
 * et reclique. Chaque refus est noté dans {@code config/spartan/interactions.log} ; alerte « Interaction » au staff à partir
 * de {@code alerteApres} refus en {@code fenetreMinutes}. Jamais de sanction automatique. Pas jugés : créatif, spectateur,
 * passager d'un véhicule (place du joueur tenue par le véhicule), juste après une téléportation, bots. Les bases de CRac
 * gardent leur propre refus (claim d'un autre clan) : un clic déjà refusé n'est pas revu.
 *
 * <p>Coût : seulement au clic droit sur un objet protégé, au coup ou au clic sur une entité (quelques rayons) ; rien par
 * tick. Fil du serveur.
 */
public final class InteractionCheck extends Check {
    private static InteractionCheck instance;
    private static final Map<UUID, Compteur> REFUS = new ConcurrentHashMap<>();
    /** Dernier refus {tick du monde, clé} par joueur : un même clic vu pour les deux mains ne compte qu'une fois. */
    private static final Map<UUID, long[]> DERNIER = new ConcurrentHashMap<>();
    /** Objets de plusieurs blocs : leurs blocs se cachent jusqu'à cette distance (blocs « partie » du loot : 3). */
    private static final int OBJET_MAX = 4;
    /** Marge de l'anti-ESP au-delà du ping avant qu'une cible cachée soit sûrement absente du jeu (ticks : 300 ms). */
    private static final int CACHE_MARGE_TICKS = 6;

    public InteractionCheck() {
        super("Interaction", "interaction"); // catégorie sans correction automatique : le clic est déjà refusé
        instance = this;
    }

    // ------------------------------------------------------------------ clic droit sur un bloc

    /** Clic droit sur un bloc, priorité basse (après CRac et les autres mods). */
    public static void clicBloc(PlayerInteractEvent.RightClickBlock e) {
        if (instance == null || !(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)
                || e.isCanceled() || e.getUseBlock() == Event.Result.DENY || !juge(p)) {
            return;
        }
        BlockPos pos = e.getPos();
        BlockState st = level.getBlockState(pos);
        if (!protege(level, pos, st)) {
            return;
        }
        AABB boite = boite(level, pos, st);
        Vec3 oeil = p.getEyePosition();
        String raison = null;
        if (SpartanConfig.INTER_PORTEE.get()) {
            double d = distance(oeil, boite);
            double max = p.getBlockReach() + SpartanConfig.INTER_MARGE.get();
            if (d > max) {
                raison = String.format("ouvre %s à %.1f blocs de ses yeux (portée %.1f, limite %.1f)", nom(st), d,
                        p.getBlockReach(), max);
            }
        }
        if (raison == null && SpartanConfig.INTER_LIGNE.get()) {
            Vec3 vise = e.getHitVec() == null ? null : e.getHitVec().getLocation();
            if (!enVue(level, oeil, boite, vise, pos, st)) {
                raison = "ouvre " + nom(st) + " à travers un mur (" + String.format("%.1f", distance(oeil, boite))
                        + " blocs, aucune ligne de vue)";
            }
        }
        if (raison != null) {
            e.setUseBlock(Event.Result.DENY);
            e.setUseItem(Event.Result.DENY);
            refuser(p, level.getGameTime(), pos.asLong(), raison, true);
        }
    }

    /** Bloc dont l'ouverture est protégée : liste du réglage, ou qui ouvre un écran (conteneur). */
    private static boolean protege(Level level, BlockPos pos, BlockState st) {
        if (st.isAir()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(st.getBlock());
        if (id != null && dansLaListe(SpartanConfig.getInteractionBlocs(), id)) {
            return true;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container || be instanceof MenuProvider) {
            return true;
        }
        return st.getMenuProvider(level, pos) != null;
    }

    /** « mod:* » (tout le mod) ou identifiant exact. */
    private static boolean dansLaListe(List<String> liste, ResourceLocation id) {
        if (liste.isEmpty()) {
            return false;
        }
        String ns = id.getNamespace();
        String complet = id.toString();
        for (String s : liste) {
            if (s.equals(complet) || (s.endsWith(":*") && s.length() == ns.length() + 2 && s.startsWith(ns))) {
                return true;
            }
        }
        return false;
    }

    /** Boîte de l'objet (forme visée du bloc), le bloc entier si elle est vide. */
    private static AABB boite(Level level, BlockPos pos, BlockState st) {
        VoxelShape forme = st.getShape(level, pos);
        return forme.isEmpty() ? new AABB(pos) : forme.bounds().move(pos);
    }

    private static String nom(BlockState st) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(st.getBlock());
        return id == null ? "un bloc" : id.toString();
    }

    // ------------------------------------------------------------------ clic droit sur une entité, coup

    /** Clic droit sur une entité (EntityInteract / EntityInteractSpecific), priorité la plus haute (avant la fouille). */
    public static void clicEntite(PlayerInteractEvent e, Entity cible) {
        if (instance == null || cible == null || !(e.getEntity() instanceof ServerPlayer p)
                || !(e.getLevel() instanceof ServerLevel level) || e.isCanceled() || !SpartanConfig.INTER_ENTITES.get()
                || !juge(p) || cible == p || cible.getRootVehicle() == p.getRootVehicle()) {
            return;
        }
        String raison = cachee(p, cible);
        Vec3 oeil = p.getEyePosition();
        AABB b = cible.getBoundingBox();
        // place du tick d'avant aussi : la cible a pu bouger depuis que le jeu du joueur l'a visée
        AABB avant = b.move(cible.xo - cible.getX(), cible.yo - cible.getY(), cible.zo - cible.getZ());
        if (raison == null && SpartanConfig.INTER_PORTEE.get()) {
            double d = Math.min(distance(oeil, b), distance(oeil, avant));
            double max = p.getEntityReach() + SpartanConfig.INTER_MARGE.get();
            if (d > max) {
                raison = String.format("clic sur %s à %.1f blocs de ses yeux (portée %.1f, limite %.1f)", nom(cible), d,
                        p.getEntityReach(), max);
            }
        }
        if (raison == null && SpartanConfig.INTER_LIGNE.get() && !b.contains(oeil)
                && !enVue(level, oeil, b, null, null, null) && !enVue(level, oeil, avant, null, null, null)) {
            raison = "clic sur " + nom(cible) + " à travers un mur (" + String.format("%.1f", distance(oeil, b))
                    + " blocs, aucune ligne de vue)";
        }
        if (raison != null) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            refuser(p, level.getGameTime(), cible.getId(), raison, true);
        }
    }

    /**
     * Coup porté (AttackEntityEvent) : refusé si la cible est un joueur que le jeu de l'attaquant n'a pas (caché par
     * l'anti-ESP). Les autres règles des coups (portée, angle, cadence) restent celles de Reach, KillAura, AutoClicker.
     *
     * @return true si le coup doit être annulé
     */
    public static boolean coup(ServerPlayer p, Entity cible) {
        if (instance == null || cible == null || !SpartanConfig.INTER_ENABLED.get() || !juge(p)) {
            return false;
        }
        String raison = cachee(p, cible);
        if (raison == null) {
            return false;
        }
        refuser(p, p.level().getGameTime(), cible.getId() ^ 0x40000000L, raison, false);
        return true;
    }

    /** Raison du refus si {@code cible} est un joueur que le jeu de {@code p} n'a pas, sinon null. */
    private static String cachee(ServerPlayer p, Entity cible) {
        if (!SpartanConfig.INTER_CIBLE_CACHEE.get() || !(cible instanceof ServerPlayer t)) {
            return null;
        }
        long ticks = CACHE_MARGE_TICKS + (long) Math.ceil(LatenceCheck.effective(p) / 50.0);
        if (!VisibilityEngine.cacheeConfirmee(p, t, ticks)) {
            return null;
        }
        return "vise " + t.getName().getString() + ", que son jeu n'a pas (caché par l'anti-ESP depuis plus de "
                + ticks * 50 + " ms, ligne de vue absente) : paquet fabriqué";
    }

    private static String nom(Entity e) {
        if (e instanceof ServerPlayer t) {
            return t.getName().getString();
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id == null ? "une entité" : id.toString();
    }

    // ------------------------------------------------------------------ vue

    /** Distance des yeux au plus proche point de la boîte (0 dedans). */
    private static double distance(Vec3 oeil, AABB b) {
        return PointsDeVue.distance(oeil.x, oeil.y, oeil.z, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    /**
     * Une ligne libre des yeux vers l'objet ? Rayons vers le point visé (s'il est donné), puis le milieu, les faces et les
     * coins de la boîte ; le premier libre suffit.
     */
    private static boolean enVue(Level level, Vec3 oeil, AABB b, Vec3 vise, BlockPos cible, BlockState etatCible) {
        if (b.contains(oeil)) {
            return true;
        }
        double[][] points = PointsDeVue.points(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ,
                vise == null ? Double.NaN : vise.x, vise == null ? Double.NaN : vise.y, vise == null ? Double.NaN : vise.z);
        BlockPos depart = BlockPos.containing(oeil);
        for (double[] q : points) {
            double[] f = PointsDeVue.finDuRayon(oeil.x, oeil.y, oeil.z, q[0], q[1], q[2]);
            if (libre(level, oeil, new Vec3(f[0], f[1], f[2]), depart, cible, etatCible)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rien de solide entre {@code a} et {@code b} : seuls les blocs qui ont une collision arrêtent le rayon, avec leur
     * forme visée ; ni le bloc des yeux, ni le bloc visé, ni un autre bloc du même objet.
     */
    private static boolean libre(Level level, Vec3 a, Vec3 b, BlockPos depart, BlockPos cible, BlockState etatCible) {
        Boolean cache = BlockGetter.traverseBlocks(a, b, level, (lv, pos) -> {
            if (pos.equals(depart) || (cible != null && pos.equals(cible))) {
                return null;
            }
            BlockState st = lv.getBlockState(pos);
            if (st.isAir() || (cible != null && memeObjet(st, pos, cible, etatCible))
                    || st.getCollisionShape(lv, pos).isEmpty()) {
                return null;
            }
            return st.getShape(lv, pos).clip(a, b, pos) != null ? Boolean.TRUE : null;
        }, lv -> Boolean.FALSE);
        return !Boolean.TRUE.equals(cache);
    }

    /** Autre bloc du même objet : même bloc (coffre double…), ou même mod de la liste multiBlocs, tout près. */
    private static boolean memeObjet(BlockState st, BlockPos pos, BlockPos cible, BlockState etatCible) {
        if (etatCible == null) {
            return false;
        }
        if (st.getBlock() == etatCible.getBlock()) {
            return true;
        }
        if (Math.abs(pos.getX() - cible.getX()) > OBJET_MAX || Math.abs(pos.getY() - cible.getY()) > OBJET_MAX
                || Math.abs(pos.getZ() - cible.getZ()) > OBJET_MAX) {
            return false;
        }
        ResourceLocation a = ForgeRegistries.BLOCKS.getKey(st.getBlock());
        ResourceLocation b = ForgeRegistries.BLOCKS.getKey(etatCible.getBlock());
        return a != null && b != null && a.getNamespace().equals(b.getNamespace())
                && SpartanConfig.getInteractionMultiBlocs().contains(a.getNamespace());
    }

    // ------------------------------------------------------------------ refus et alertes

    /** Joueur jugé : réglage actif, ni bot, ni créatif, ni spectateur, ni passager, pas juste après une téléportation. */
    private static boolean juge(ServerPlayer p) {
        return SpartanConfig.ENABLED.get() && SpartanConfig.INTER_ENABLED.get() && !(p instanceof FakePlayer)
                && !p.getTags().contains("heraliz_bot") && !p.isCreative() && !p.isSpectator() && !p.isPassenger()
                && !CheckManager.isInGrace(p.getUUID());
    }

    private static void refuser(ServerPlayer p, long tick, long cle, String raison, boolean message) {
        long[] d = DERNIER.get(p.getUUID());
        if (d != null && d[0] == tick && d[1] == cle) {
            return; // même clic vu une deuxième fois (autre main, deux paquets du même clic) : déjà compté
        }
        DERNIER.put(p.getUUID(), new long[] {tick, cle});
        if (message) {
            p.displayClientMessage(Component.translatableWithFallback("spartan.interaction.refus", "Tu ne vois pas ça d'ici."), true);
        }
        AlertManager.journal("interactions.log", p.getName().getString() + " (" + p.getUUID() + ") : " + raison);
        int minutes = SpartanConfig.INTER_FENETRE.get();
        Compteur c = REFUS.computeIfAbsent(p.getUUID(), u -> new Compteur(32));
        int n = c.ajouter(System.currentTimeMillis(), minutes * 60_000L);
        if (n >= SpartanConfig.INTER_ALERTE.get()) {
            c.vider();
            instance.flag(p, n + " interaction(s) impossible(s) refusée(s) en " + minutes + " min ; la dernière : " + raison);
        }
    }

    /** /spartan status : refus récents. */
    public static String describe(UUID uuid) {
        if (!SpartanConfig.INTER_ENABLED.get()) {
            return "interactions : contrôle coupé ([interactions] enabled)";
        }
        Compteur c = REFUS.get(uuid);
        int n = c == null ? 0 : c.compter(System.currentTimeMillis(), SpartanConfig.INTER_FENETRE.get() * 60_000L);
        return "interactions : " + n + " refus dans les " + SpartanConfig.INTER_FENETRE.get() + " dernières minutes";
    }

    @Override
    public void clear(UUID uuid) {
        REFUS.remove(uuid);
        DERNIER.remove(uuid);
    }

    /** Une téléportation ne change rien aux refus comptés. */
    @Override
    public void onGrace(UUID uuid) {
    }
}
