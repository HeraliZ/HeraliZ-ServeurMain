package fr.heraliz.crac.placable;

import java.util.Collection;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.build.world.StructureBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Chutes des placables qui ont perdu leur support (corps d'origine de PlacableSupport, côté serveur). */
public final class PlacableChutes {
    private PlacableChutes() {
    }

    private static final int MAX_CASCADE = 16;

    private static boolean enabled() {
        return PlacableSupport.enabled();
    }

    private static boolean isSupported(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
        return PlacableSupport.isSupported(level, pos, state);
    }

    /** After the block at {@code pos} went away: placables stacked on it that lost their support fall (drops kept). */
    public static void dropUnsupportedAbove(ServerLevel level, BlockPos pos) {
        if (!enabled()) {
            return;
        }
        BlockPos cur = pos.above();
        for (int i = 0; i < MAX_CASCADE && level.isLoaded(cur); i++) {
            BlockState state = level.getBlockState(cur);
            if (!PlacableHpManager.isTracked(state) || isSupported(level, cur, state)) {
                return;
            }
            fall(level, cur, state);
            cur = cur.above();
        }
    }

    /**
     * The block at {@code pos} lost what held it and falls: it drops as an item and what it held spills, except the
     * Claim Block, which never drops. A damaged placable, or one of two blocks (bed, door), drops the item a pickup gives
     * ({@link PlacableHpManager#pickupItem}), so losing its support never heals it.
     */
    public static void fall(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ClaimBlock) {
            PlacableHpManager.forget(level, state, pos);
            level.destroyBlock(pos, false);
        } else if (PlacableHpManager.isDamaged(level, state, pos) || PlacableHpManager.isTracked(state) && MultiBlocks.partner(state, pos) != null) {
            // read before forget(): the HP goes into the item
            ItemStack item = PlacableHpManager.pickupItem(level, state, pos);
            PlacableHpManager.forget(level, state, pos);
            PlacableHpManager.spillShulker(level, pos);
            // both halves go without drops (containers still spill): the item is the only one handed out
            MultiBlocks.remove(level, pos, true);
            Block.popResource(level, pos, item);
        } else {
            PlacableHpManager.forget(level, state, pos);
            level.destroyBlock(pos, true);
        }
    }
}
