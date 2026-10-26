package fr.heralisz.utils.zombies;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
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
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class CheminsZombies {
    private CheminsZombies() {
    }

    /** Réponse « pas de chemin ce tick » (jamais rendue à Minecraft : le mixin rend null à la place). */
    public static final Path REFUS = new Path(List.of(), BlockPos.ZERO, false);

    private record Entree(Path chemin, int tick, double x, double y, double z, ResourceKey<Level> monde) {
    }

    private static final Map<Long, Entree> CACHE = new HashMap<>();
    private static int restant = Integer.MAX_VALUE, tick;
    private static long calcules, partages, refuses;

    @SubscribeEvent
    public static void tickServeur(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        tick = e.getServer().getTickCount();
        int budget = ZombiesConfig.CHEMINS_BUDGET.get();
        restant = budget <= 0 ? Integer.MAX_VALUE : budget;
        if (tick % 40 == 0) CACHE.values().removeIf(en -> tick - en.tick > 20);
    }

    /** null : laisser Minecraft calculer ; {@link #REFUS} : rien ce tick ; sinon le chemin repris d'un autre zombie. */
    public static Path avant(Mob mob, Set<BlockPos> cibles) {
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
        CACHE.put(cibles.iterator().next().asLong(), new Entree(p, tick, mob.getX(), mob.getY(), mob.getZ(), mob.level().dimension()));
    }

    private static Path copie(Path p) {
        List<Node> noeuds = new ArrayList<>(p.getNodeCount());
        for (int i = 0; i < p.getNodeCount(); i++) noeuds.add(p.getNode(i));
        return new Path(noeuds, p.getTarget(), p.canReach());
    }

    /** Compteurs depuis le démarrage : {calculés, repris d'un autre, refusés faute de budget}. */
    public static long[] compteurs() {
        return new long[]{calcules, partages, refuses};
    }
}
