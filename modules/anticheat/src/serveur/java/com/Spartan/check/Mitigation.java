package com.Spartan.check;

import com.Spartan.check.movement.MovementEngine;
import com.Spartan.check.movement.MovementEnv;
import com.Spartan.config.SpartanConfig;
import com.Spartan.util.ServerNet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Correction instantanée : quand une détection se déclenche, le tricheur ne
 * profite pas de sa triche.
 *
 * - mouvement (Fly, Speed, Vertical, NoFall, Timer, BadPackets) : retour en
 *   arrière d'environ 1 s, sur une position au sol RÉELLEMENT occupée par le
 *   joueur (historique de ses propres positions revendiquées), donc avant le
 *   début de la triche ; à défaut, dépôt au sol juste en dessous. Jamais dans un
 *   fluide, un bloc dangereux, une zone non chargée ou une place occupée ;
 * - combat (Reach, KillAura, AutoClicker) : coup annulé, puis coups bloqués
 *   quelques secondes ;
 * - minage (Nuker, FastBreak) : casse annulée, puis minage bloqué quelques
 *   secondes.
 *
 * Indépendant de alertOnly : la correction ne bannit personne, elle annule
 * seulement l'avantage. Chaque correction est configurable.
 */
public final class Mitigation {
    private static final Map<UUID, Deque<GroundPos>> HISTORY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COMBAT_LOCK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> BLOCK_LOCK = new ConcurrentHashMap<>();
    private static final long REWIND_NANOS = 1_000_000_000L;   // retour ~1 s en arrière
    private static final long HISTORY_NANOS = 10_000_000_000L; // positions au sol conservées 10 s
    private static final int HISTORY_MAX = 400;
    private static final double MAX_DROP = 64.0;               // dépôt au sol : profondeur maximale

    private Mitigation() {
    }

    private record GroundPos(ResourceKey<Level> dimension, Vec3 pos, long nanos) {
    }

    /** Position au sol réellement occupée par le joueur, sans détection (moteur de mouvement). */
    public static void recordGround(ServerPlayer p, Vec3 pos, long nanos) {
        Deque<GroundPos> h = HISTORY.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        GroundPos last = h.peekLast();
        if (last != null && last.pos().equals(pos) && last.dimension().equals(p.level().dimension())) {
            return; // immobile : inutile d'empiler
        }
        h.addLast(new GroundPos(p.level().dimension(), pos, nanos));
        while (h.size() > HISTORY_MAX || (!h.isEmpty() && nanos - h.peekFirst().nanos() > HISTORY_NANOS)) {
            h.removeFirst();
        }
    }

    /** Réaction à une détection, selon sa catégorie. */
    public static void onFlag(Check check, ServerPlayer p) {
        long now = System.currentTimeMillis();
        switch (check.getCategory()) {
            case "movement" -> {
                if (SpartanConfig.MITIGATE_MOVEMENT.get()) {
                    setback(p);
                }
            }
            case "combat" -> {
                if (SpartanConfig.MITIGATE_COMBAT.get()) {
                    COMBAT_LOCK.put(p.getUUID(), now + SpartanConfig.COMBAT_LOCK_SECONDS.get() * 1000L);
                }
            }
            case "block" -> {
                if (SpartanConfig.MITIGATE_BLOCKS.get()) {
                    BLOCK_LOCK.put(p.getUUID(), now + SpartanConfig.BLOCK_LOCK_SECONDS.get() * 1000L);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Renvoie le joueur en arrière : d'abord la position au sol la plus récente
     * datant d'au moins 1 s, puis une plus récente, puis le sol sous le joueur.
     * Rien si une téléportation est déjà en cours ou si aucune cible n'est sûre.
     *
     * @return true si le joueur a été renvoyé
     */
    public static boolean setback(ServerPlayer p) {
        if (ServerNet.pendingTeleport(p) != null) {
            return false; // déjà en cours de repositionnement (renvoi ou correction vanilla)
        }
        Vec3 target = fromHistory(p, true);
        if (target == null) {
            target = fromHistory(p, false);
        }
        if (target == null) {
            target = groundBelow(p);
        }
        if (target == null) {
            return false;
        }
        UUID uuid = p.getUUID();
        p.connection.teleport(target.x, target.y, target.z, p.getYRot(), p.getXRot());
        // Sans cela, la fin du tick réseau remettrait le joueur à sa position de
        // triche côté serveur jusqu'à ce que le client confirme le renvoi.
        p.connection.resetPosition();
        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
        // Le renvoi est un saut de position : les détections repartent d'un état propre,
        // et le moteur ignore les positions envoyées avant que le client l'ait reçu.
        CheckManager.resetMovementState(uuid);
        MovementEngine.onOwnSetback(uuid);
        return true;
    }

    private static Vec3 fromHistory(ServerPlayer p, boolean rewind) {
        Deque<GroundPos> h = HISTORY.get(p.getUUID());
        if (h == null) {
            return null;
        }
        long now = System.nanoTime();
        Iterator<GroundPos> it = h.descendingIterator();
        while (it.hasNext()) {
            GroundPos g = it.next();
            if (rewind && now - g.nanos() < REWIND_NANOS) {
                continue;
            }
            if (g.dimension().equals(p.level().dimension()) && isSafe(p, g.pos())) {
                return g.pos();
            }
        }
        return null;
    }

    /** Premier sol solide sous le client (64 blocs max) ; jamais à travers un fluide. */
    private static Vec3 groundBelow(ServerPlayer p) {
        Vec3 from = MovementEngine.lastClaim(p.getUUID());
        if (from == null) {
            from = new Vec3(p.xo, p.yo, p.zo);
        }
        BlockHitResult hit = p.level().clip(new ClipContext(from, from.subtract(0, MAX_DROP, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        if (hit.getType() != HitResult.Type.BLOCK || !p.level().getFluidState(hit.getBlockPos()).isEmpty()) {
            return null; // vide ou fluide sous le joueur
        }
        Vec3 ground = new Vec3(from.x, hit.getLocation().y, from.z);
        return isSafe(p, ground) ? ground : null;
    }

    /** Zone chargée, place libre, ni fluide ni bloc dangereux dans la hitbox ou sous les pieds. */
    private static boolean isSafe(ServerPlayer p, Vec3 pos) {
        Level level = p.level();
        if (!level.hasChunkAt(BlockPos.containing(pos))) {
            return false;
        }
        AABB box = MovementEnv.boxAt(p, pos).deflate(1.0E-7);
        if (!level.noCollision(p, box) || level.containsAnyLiquid(box)) {
            return false;
        }
        AABB around = new AABB(box.minX, box.minY - 0.6, box.minZ, box.maxX, box.maxY, box.maxZ);
        return level.getBlockStates(around).noneMatch(Mitigation::isHazard);
    }

    private static boolean isHazard(BlockState s) {
        return s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.LAVA)
                || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH)
                || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.WITHER_ROSE) || s.is(Blocks.POINTED_DRIPSTONE)
                || s.is(Blocks.COBWEB);
    }

    /** Oublie l'historique de positions (téléportation lointaine, respawn, changement de dimension…). */
    public static void invalidate(UUID uuid) {
        HISTORY.remove(uuid);
    }

    public static boolean isCombatLocked(UUID uuid) {
        return isLocked(COMBAT_LOCK, uuid);
    }

    public static boolean isBlockLocked(UUID uuid) {
        return isLocked(BLOCK_LOCK, uuid);
    }

    private static boolean isLocked(Map<UUID, Long> map, UUID uuid) {
        Long until = map.get(uuid);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            map.remove(uuid);
            return false;
        }
        return true;
    }

    public static void clear(UUID uuid) {
        HISTORY.remove(uuid);
        COMBAT_LOCK.remove(uuid);
        BLOCK_LOCK.remove(uuid);
    }
}
