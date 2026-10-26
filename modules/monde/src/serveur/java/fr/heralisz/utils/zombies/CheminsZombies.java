package fr.heralisz.utils.zombies;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Budget et partage des recherches de chemin des zombies HeraliZ (24/10, « le calcul des chemins a un budget par tick, et
 * un groupe qui suit la même cible partage le même chemin »). Appelé par le mixin {@code CheminsZombiesMixin} au début de
 * chaque recherche de chemin d'un {@link ZombieSurvie} (fil serveur) :
 * <ul>
 *     <li>un autre zombie a cherché un chemin vers la même case il y a moins d'une seconde, en partant à 4 blocs au plus :
 *     on reprend une copie de son chemin, sans rien calculer ;</li>
 *     <li>sinon, s'il reste du budget ce tick ({@code chemins_budget}), Minecraft calcule ; sinon le zombie n'a pas de
 *     nouveau chemin ce tick (son objectif réessaie un peu plus tard).</li>
 * </ul>
 * 26/10 (banc 150 / 300 / 450 zombies) : avant tout ça, un zombie qui poursuit sa cible à moins de {@value #DIRECT} blocs,
 * à la même hauteur, la ligne droite libre au sol, y va tout droit (chemin d'un seul point, rien calculé) ; et une horde
 * qui suit la même créature reprend le chemin d'un voisin même si la cible a bougé d'une case ou deux ({@value #CIBLE_TICKS}
 * ticks au plus).
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class CheminsZombies {
    private CheminsZombies() {
    }

    /** Réponse « pas de chemin ce tick » (jamais rendue à Minecraft : le mixin rend null à la place). */
    public static final Path REFUS = new Path(List.of(), BlockPos.ZERO, false);

    private record Entree(Path chemin, int tick, double x, double y, double z, ResourceKey<Level> monde) {
    }

    private record EntreeCible(Path chemin, int tick, double x, double y, double z, double cx, double cy, double cz,
                               ResourceKey<Level> monde) {
    }

    /** Chasse en ligne droite jusqu'à cette distance (blocs). */
    static final double DIRECT = 6.0;
    /** Chemin vers une créature repris d'un voisin pendant tant de ticks, si elle a bougé de 2 blocs au plus. */
    static final int CIBLE_TICKS = 10;

    private static final Map<Long, Entree> CACHE = new HashMap<>();
    private static final Map<Integer, EntreeCible> CACHE_CIBLE = new HashMap<>();
    private static int restant = Integer.MAX_VALUE, tick;
    private static long calcules, partages, refuses, directs;

    @SubscribeEvent
    public static void tickServeur(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        tick = e.getServer().getTickCount();
        int budget = ZombiesConfig.CHEMINS_BUDGET.get();
        restant = budget <= 0 ? Integer.MAX_VALUE : budget;
        if (tick % 40 == 0) {
            CACHE.values().removeIf(en -> tick - en.tick > 20);
            CACHE_CIBLE.values().removeIf(en -> tick - en.tick > CIBLE_TICKS);
        }
    }

    /** La créature visée par ce chemin : la cible du zombie si le chemin va à sa case. */
    private static LivingEntity cible(Mob mob, Set<BlockPos> cibles) {
        LivingEntity c = mob.getTarget();
        return c != null && cibles.size() == 1 && c.level() == mob.level() && cibles.contains(c.blockPosition()) ? c : null;
    }

    /**
     * Ligne droite au sol du zombie à sa cible, case par case : de quoi marcher (place libre aux pieds et à la tête, sol
     * dessous qui porte). Seulement à la même hauteur (une marche au plus) et de près : sinon le vrai calcul.
     */
    private static boolean ligneLibre(Mob mob, LivingEntity c) {
        if (!mob.onGround() || mob.isInWaterOrBubble() || Math.abs(c.getY() - mob.getY()) > 1.0) return false;
        double dx = c.getX() - mob.getX(), dz = c.getZ() - mob.getZ(), d2 = dx * dx + dz * dz;
        if (d2 > DIRECT * DIRECT) return false;
        int pas = Mth.ceil(Math.sqrt(d2) * 2);
        int y = Mth.floor(mob.getY() + 0.01);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Level lv = mob.level();
        for (int i = 1; i <= pas; i++) {
            double f = (double) i / pas;
            int x = Mth.floor(mob.getX() + dx * f), z = Mth.floor(mob.getZ() + dz * f);
            if (!libre(lv, m.set(x, y, z)) || !libre(lv, m.set(x, y + 1, z))) return false;
            BlockState sol = lv.getBlockState(m.set(x, y - 1, z));
            if (sol.getCollisionShape(lv, m).isEmpty() || !sol.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private static boolean libre(Level lv, BlockPos p) {
        BlockState s = lv.getBlockState(p);
        return s.getCollisionShape(lv, p).isEmpty() && s.getFluidState().isEmpty();
    }

    /** null : laisser Minecraft calculer ; {@link #REFUS} : rien ce tick ; sinon le chemin repris d'un autre zombie. */
    public static Path avant(Mob mob, Set<BlockPos> cibles) {
        LivingEntity c = cible(mob, cibles);
        if (c != null && ligneLibre(mob, c)) {
            directs++;
            BlockPos b = c.blockPosition();
            return new Path(new ArrayList<>(List.of(new Node(b.getX(), b.getY(), b.getZ()))), b, true);
        }
        if (c != null && ZombiesConfig.CHEMINS_PARTAGE.get()) {
            EntreeCible en = CACHE_CIBLE.get(c.getId());
            if (en != null && tick - en.tick <= CIBLE_TICKS && en.monde == mob.level().dimension()
                    && mob.distanceToSqr(en.x, en.y, en.z) <= 16 && c.distanceToSqr(en.cx, en.cy, en.cz) <= 4) {
                partages++;
                return copie(en.chemin);
            }
        }
        if (cibles.size() == 1 && ZombiesConfig.CHEMINS_PARTAGE.get()) {
            Entree en = CACHE.get(cibles.iterator().next().asLong());
            if (en != null && tick - en.tick <= 20 && en.monde == mob.level().dimension() && mob.distanceToSqr(en.x, en.y, en.z) <= 16) {
                partages++;
                return copie(en.chemin);
            }
        }
        if (restant <= 0) {
            refuses++;
            return REFUS;
        }
        restant--;
        calcules++;
        return null;
    }

    /** Chemin calculé par Minecraft : gardé une seconde pour les zombies voisins qui visent la même case. */
    public static void apres(Mob mob, Set<BlockPos> cibles, Path p) {
        if (p == null || p.getNodeCount() < 2 || cibles.size() != 1 || !ZombiesConfig.CHEMINS_PARTAGE.get()) return;
        LivingEntity c = cible(mob, cibles);
        if (c != null) CACHE_CIBLE.put(c.getId(), new EntreeCible(p, tick, mob.getX(), mob.getY(), mob.getZ(), c.getX(), c.getY(), c.getZ(),
                mob.level().dimension()));
        CACHE.put(cibles.iterator().next().asLong(), new Entree(p, tick, mob.getX(), mob.getY(), mob.getZ(), mob.level().dimension()));
    }

    private static Path copie(Path p) {
        List<Node> noeuds = new ArrayList<>(p.getNodeCount());
        for (int i = 0; i < p.getNodeCount(); i++) noeuds.add(p.getNode(i));
        return new Path(noeuds, p.getTarget(), p.canReach());
    }

    /** Compteurs depuis le démarrage : {calculés, repris d'un autre, refusés faute de budget, tout droit}. */
    public static long[] compteurs() {
        return new long[]{calcules, partages, refuses, directs};
    }
}
