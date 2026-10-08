package fr.heraliz.crac.raid;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.event.level.BlockEvent.FluidPlaceBlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Keeps {@link PlacedTerrainData} in line with the world: terrain-type blocks placed by players (or made by fluids, as
 * in a cobble generator) are recorded, and so is any terrain-type block a piston moves or that falls (sand, gravel)
 * where it ends up: natural terrain is only what never moved. Runs last, after the claim rules had their say.
 */
@EventBusSubscriber(
    modid = "crac"
)
public final class PlacedTerrainTracker {
    /**
     * Persistent tags on a falling terrain-type block: whether it left a recorded position (then where it lands is
     * recorded wherever that is), and where it started (a natural block that lands anywhere else is recorded too).
     */
    private static final String FALLING_TAG = "crac_placed_terrain";
    private static final String FALLING_START = "crac_terrain_start";

    private PlacedTerrainTracker() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PlacedTerrainData data = PlacedTerrainData.get(level);
        if (event.getEntity() instanceof Player && event.getPlacedBlock().is(RaidBlocks.TERRAIN)) {
            data.add(event.getPos());
        } else {
            data.remove(event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PlacedTerrainData.get(level).remove(event.getPos());
        }
    }

    /** Stone, cobblestone or basalt made by flowing lava: player-made in practice (generators, lava casts). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFluidPlace(FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getNewState().is(RaidBlocks.TERRAIN)) {
            PlacedTerrainData.get(level).add(event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!event.getPistonMoveType().isExtend && !level.getBlockState(event.getPos()).is(Blocks.STICKY_PISTON)) {
            // a plain piston pulls nothing back
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        PlacedTerrainData data = PlacedTerrainData.get(level);
        for (BlockPos p : resolver.getToDestroy()) {
            data.remove(p);
        }
        Direction dir = resolver.getPushDirection();
        List<BlockPos> moved = new ArrayList<>();
        for (BlockPos p : resolver.getToPush()) {
            // a natural block a piston moved is player-made where it lands, like one a player placed there
            if (data.remove(p) || level.getBlockState(p).is(RaidBlocks.TERRAIN)) {
                moved.add(p.relative(dir));
            }
        }
        moved.forEach(data::add);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof FallingBlockEntity falling) || event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos start = falling.getStartPos();
        boolean placed = PlacedTerrainData.get(level).remove(start);
        if (placed || falling.getBlockState().is(RaidBlocks.TERRAIN)) {
            CompoundTag tag = falling.getPersistentData();
            tag.putBoolean(FALLING_TAG, placed);
            // the entity does not save its start: keep it for a fall that spans a restart
            tag.putLong(FALLING_START, start.asLong());
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof FallingBlockEntity falling) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag tag = falling.getPersistentData();
        // landed: the block was set where the entity stands, then the entity discarded
        if (falling.getRemovalReason() != Entity.RemovalReason.DISCARDED || !tag.contains(FALLING_TAG)) {
            return;
        }
        BlockPos pos = falling.blockPosition();
        boolean moved = !tag.contains(FALLING_START) || tag.getLong(FALLING_START) != pos.asLong();
        if ((tag.getBoolean(FALLING_TAG) || moved) && level.getBlockState(pos).is(falling.getBlockState().getBlock())) {
            PlacedTerrainData.get(level).add(pos);
        }
    }
}
