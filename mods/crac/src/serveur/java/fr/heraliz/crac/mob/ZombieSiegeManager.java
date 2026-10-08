package fr.heraliz.crac.mob;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.logic.PlacementRules;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Idle hostile mobs chew on the weakest building piece in reach (slow siege, never raid damage). */
@EventBusSubscriber(
    modid = "crac"
)
public final class ZombieSiegeManager {
    private static final int SWEEP_TICKS = 10;
    private static final Map<UUID, Long> lastHit = new HashMap<>();
    /**
     * World collision boxes per piece (closed state, block by block): a rotated wall is not one big box. A piece's key
     * and blocks never change, so they are computed once.
     */
    private static final Map<Piece, List<AABB>> BOXES = new WeakHashMap<>();
    private static long nextSweep = 0L;

    private ZombieSiegeManager() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if (event.phase != Phase.END || !fr.heraliz.crac.heraliz.Heraliz.mobsDamagePieces()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        // a cursor far ahead comes from another world (integrated server): start again from now
        if (now < nextSweep && nextSweep - now <= SWEEP_TICKS) {
            return;
        }
        nextSweep = now + SWEEP_TICKS;
        if (lastHit.size() > 4096) {
            lastHit.clear();
        }
        for (ServerLevel level : server.getAllLevels()) {
            sweep(level, now);
        }
    }

    private static List<AABB> boxesOf(Piece p) {
        return BOXES.computeIfAbsent(p, q -> {
            List<AABB> boxes = PlacementRules.worldBoxes(q.key(), PieceIndex.blocksOf(q));
            return boxes.isEmpty() ? List.of(StructureService.bounds(q)) : List.copyOf(boxes);
        });
    }

    private static void sweep(ServerLevel level, long now) {
        StructureStore store = StructureStore.get(level);
        if (store.index.allPieces().isEmpty()) {
            return;
        }
        List<Monster> idle = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Monster mob && mob.isAlive() && (mob.getTarget() == null || !mob.getTarget().isAlive())) {
                idle.add(mob);
            }
        }
        if (idle.isEmpty()) {
            return;
        }
        int cooldown = Config.zombieAttackCooldown.get();
        double reach = Config.zombieReach.get();
        int search = Config.zombieSearchRadius.get();
        int damage = Config.zombieDamage.get();
        boolean claimedOnly = Config.zombiesOnlyWhenClaimed.get();
        boolean path = Config.zombiesPathToBuilds.get();

        // only the pieces in chunks around idle mobs are considered
        int chunkRange = Mth.ceil((Math.max(search, reach) + 4) / 16.0);
        LongSet near = new LongOpenHashSet();
        for (Monster mob : idle) {
            int cx = mob.getBlockX() >> 4;
            int cz = mob.getBlockZ() >> 4;
            for (int dx = -chunkRange; dx <= chunkRange; dx++) {
                for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                    near.add(ChunkPos.asLong(cx + dx, cz + dz));
                }
            }
        }
        Long2ObjectOpenHashMap<List<Piece>> byChunk = new Long2ObjectOpenHashMap<>();
        for (Piece p : store.index.allPieces()) {
            BlockPos centre = p.centreBlock();
            long ck = ChunkPos.asLong(centre.getX() >> 4, centre.getZ() >> 4);
            if (!near.contains(ck)) continue;
            if (claimedOnly) {
                Building b = store.index.building(p.buildingId);
                if (b == null || !b.hasClaim()) continue;
            }
            byChunk.computeIfAbsent(ck, k -> new ArrayList<>()).add(p);
        }
        if (byChunk.isEmpty()) {
            return;
        }

        for (Monster mob : idle) {
            Piece inReach = weakestWithin(byChunk, mob.position(), reach);
            if (inReach == null) {
                if (path && mob.getNavigation().isDone()) {
                    Piece far = weakestWithin(byChunk, mob.position(), search);
                    if (far != null) {
                        Vec3 t = closestPoint(boxesOf(far), mob.position());
                        mob.getNavigation().moveTo(t.x, t.y, t.z, 1.0);
                    }
                }
                continue;
            }
            Long last = lastHit.get(mob.getUUID());
            if ((last != null && now - last < cooldown) || !StructureService.isLoaded(level, inReach)) {
                continue;
            }
            lastHit.put(mob.getUUID(), now);
            Vec3 at = closestPoint(boxesOf(inReach), mob.getEyePosition());
            mob.getLookControl().setLookAt(at);
            mob.swing(InteractionHand.MAIN_HAND);
            StructureService.damage(level, inReach, damage, false);
            level.playSound(null, BlockPos.containing(at), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.6F, 0.9F + level.random.nextFloat() * 0.2F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, StructureService.particleState(inReach.tier)), at.x, at.y, at.z, 4, 0.25, 0.25, 0.25, 0.0);
        }
    }

    /** Weakest piece whose box is within {@code radius} of the mob's feet; floors under its feet do not count. */
    private static Piece weakestWithin(Long2ObjectOpenHashMap<List<Piece>> byChunk, Vec3 from, double radius) {
        Piece best = null;
        int bestHp = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        double r2 = radius * radius;
        int range = Mth.ceil((radius + 4) / 16.0);
        int cx = Mth.floor(from.x) >> 4;
        int cz = Mth.floor(from.z) >> 4;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                List<Piece> list = byChunk.get(ChunkPos.asLong(cx + dx, cz + dz));
                if (list == null) continue;
                for (Piece p : list) {
                    if (p.hp <= 0) continue;
                    List<AABB> boxes = boxesOf(p);
                    double top = Double.NEGATIVE_INFINITY;
                    double d = Double.MAX_VALUE;
                    for (AABB box : boxes) {
                        top = Math.max(top, box.maxY);
                        d = Math.min(d, box.distanceToSqr(from));
                    }
                    if (top <= from.y + 0.25) continue;
                    if (d <= r2 && (p.hp < bestHp || (p.hp == bestHp && d < bestDist))) {
                        best = p;
                        bestHp = p.hp;
                        bestDist = d;
                    }
                }
            }
        }
        return best;
    }

    private static Vec3 closestPoint(List<AABB> boxes, Vec3 p) {
        Vec3 best = p;
        double bestD = Double.MAX_VALUE;
        for (AABB box : boxes) {
            Vec3 c = new Vec3(Mth.clamp(p.x, box.minX, box.maxX), Mth.clamp(p.y, box.minY, box.maxY), Mth.clamp(p.z, box.minZ, box.maxZ));
            double d = c.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // an integrated server keeps these statics from the previous world
        nextSweep = 0L;
        lastHit.clear();
        BOXES.clear();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Monster m) {
            lastHit.remove(m.getUUID());
        }
    }
}
