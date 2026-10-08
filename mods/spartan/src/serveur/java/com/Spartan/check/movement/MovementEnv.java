package com.Spartan.check.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;

/**
 * Environnement du joueur à une position DONNÉE (celle que le client affirme
 * occuper), pour les détections de mouvement et la correction.
 *
 * On ne se sert jamais de getX/getY/getZ pendant le tick : le serveur y simule
 * sa propre physique du joueur (gravité cumulée comprise) avant de le remettre à
 * la position du client. Toutes les mesures se font sur des positions revendiquées.
 */
public final class MovementEnv {
    private MovementEnv() {
    }

    /** Hitbox du joueur (posture actuelle) placée à une position. */
    public static AABB boxAt(ServerPlayer p, Vec3 pos) {
        return p.getBoundingBox().move(pos.x - p.getX(), pos.y - p.getY(), pos.z - p.getZ());
    }

    /**
     * Appui EXACT (moins de 0,03 bloc sous la hitbox) : le client était au sol au début
     * du tick et y a appliqué le frottement du sol. Plus strict que groundUnder.
     */
    public static boolean onSurface(ServerPlayer p, AABB box) {
        return !p.level().noCollision(p, box.move(0, -0.03, 0));
    }

    /**
     * Entités qui peuvent pousser le joueur (mobs, joueurs, bateaux…) collées à lui :
     * vanilla les écarte d'au plus ~0,07 bloc/tick chacune.
     */
    public static int pushers(ServerPlayer p, AABB box) {
        return p.level().getEntities(p, box.inflate(0.25), e -> e.isPushable() && !e.isPassengerOfSameVehicle(p)).size();
    }

    /** Collision dans les 0,2 bloc sous la hitbox : bloc, entité solide (bateau, shulker…). */
    public static boolean groundUnder(ServerPlayer p, AABB box) {
        return !p.level().noCollision(p, box.move(0, -0.2, 0));
    }

    /** N'importe quel fluide (y compris de mods) touchant la hitbox. */
    public static boolean fluidIn(Level level, AABB box) {
        return level.containsAnyLiquid(box.inflate(0.001));
    }

    /** Échelle, liane, échafaudage, échelles de mods (règles Forge) aux pieds. */
    public static boolean climbableAt(ServerPlayer p, Vec3 pos) {
        BlockPos feet = BlockPos.containing(pos);
        return ForgeHooks.isLivingOnLadder(p.level().getBlockState(feet), p.level(), feet, p).isPresent();
    }

    /**
     * Bloc qui freine ou retient le mouvement touchant la hitbox : toile, buisson à
     * baies, échafaudage, neige poudreuse, miel (glissade le long d'un mur).
     */
    public static boolean slowingIn(Level level, AABB box) {
        return level.getBlockStates(box.inflate(0.001)).anyMatch(MovementEnv::isSlowing);
    }

    private static boolean isSlowing(BlockState s) {
        return s.is(Blocks.COBWEB) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.SCAFFOLDING)
                || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.HONEY_BLOCK);
    }

    /**
     * Plateforme sous les pieds : bateau, wagonnet, contraption Create, navire de
     * mod, shulker… mais PAS les mobs ordinaires (courir dans une horde ne doit pas
     * exempter) : un vivant ne compte que s'il est réellement solide.
     */
    public static boolean platformUnder(ServerPlayer p, AABB box) {
        return !p.level().getEntities(p, box.move(0, -0.5, 0).inflate(0.3, 0, 0.3),
                e -> e.canBeCollidedWith() || (!(e instanceof LivingEntity) && e.isPushable())).isEmpty();
    }

    /** Plafond à moins de {@code gap} bloc au-dessus de la tête. */
    public static boolean headBlocked(ServerPlayer p, AABB box, double gap) {
        return !p.level().noCollision(p, new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + gap, box.maxZ));
    }

    /** Piston en mouvement à proximité : il pousse ou projette le joueur. */
    public static boolean pistonNear(Level level, AABB box) {
        return level.getBlockStates(box.inflate(1.0))
                .anyMatch(s -> s.is(Blocks.MOVING_PISTON) || s.is(Blocks.PISTON_HEAD));
    }

    /** Bloc rebondissant sous les pieds (slime, lit) : il renvoie le joueur vers le haut. */
    public static boolean bouncyUnder(ServerPlayer p, Vec3 pos) {
        BlockPos below = BlockPos.containing(pos.x, pos.y - 0.2, pos.z);
        BlockState s = p.level().getBlockState(below);
        return s.isSlimeBlock() || s.getBlock() instanceof BedBlock || s.is(BlockTags.BEDS);
    }

    /** Bloc glissant sous les pieds (glace, y compris de mods). */
    public static boolean slipperyUnder(ServerPlayer p, Vec3 pos) {
        BlockPos below = BlockPos.containing(pos.x, pos.y - 0.5, pos.z);
        return p.level().getBlockState(below).getFriction(p.level(), below, p) > 0.8F;
    }

    /** Frottement du bloc sous les pieds (0,6 pour un bloc normal). */
    public static float frictionUnder(ServerPlayer p, Vec3 pos) {
        BlockPos below = BlockPos.containing(pos.x, pos.y - 0.5, pos.z);
        return p.level().getBlockState(below).getFriction(p.level(), below, p);
    }

    public static boolean loaded(ServerPlayer p, Vec3 pos) {
        return p.level().hasChunkAt(BlockPos.containing(pos));
    }
}
