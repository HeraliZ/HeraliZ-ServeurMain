package fr.heraliz.crac.placable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Two-block placables (beds and sleeping bags, doors, tall plants, the storage chests of CoffreBlock). Both halves share one HP entry and one
 * position entry, keyed by the {@link #canonical} half, and are always removed together.
 */
public final class MultiBlocks {
    /** Set a half to air without the neighbour shape pass, so the other half is not broken (with drops) by it. */
    private static final int SILENT = Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE;

    private MultiBlocks() {
    }

    /** Where the other half of a two-block placable would be, or null for a single block. */
    @Nullable
    public static BlockPos partner(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof fr.heraliz.crac.block.CoffreBlock c) {   // coffres par rareté (2 cases)
            return c.partenaire(state, pos);
        }
        if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART) && state.hasProperty(BedBlock.FACING)) {
            Direction facing = state.getValue(BedBlock.FACING);
            return state.getValue(BedBlock.PART) == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite());
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }
        return null;
    }

    /** The other half if it is really there (same block), else null. */
    @Nullable
    public static BlockPos partnerIn(Level level, BlockState state, BlockPos pos) {
        BlockPos other = partner(state, pos);
        return other != null && level.getBlockState(other).is(state.getBlock()) ? other : null;
    }

    /** The half that keys a two-block placable's data: the foot of a bed, the lower half of a door; else pos. */
    public static BlockPos canonical(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof fr.heraliz.crac.block.CoffreBlock c) {   // la case qui garde l'inventaire
            return c.principal(state, pos);
        }
        if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART) && state.hasProperty(BedBlock.FACING)) {
            return state.getValue(BedBlock.PART) == BedPart.HEAD ? pos.relative(state.getValue(BedBlock.FACING).getOpposite()) : pos.immutable();
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos.immutable();
    }

    /**
     * Removes a placable and its other half without any drop (pickup, raid destruction). With {@code effects} the
     * break particles and sound of {@code pos} are played. Neighbours of both halves get their usual shape updates.
     */
    public static void remove(Level level, BlockPos pos, boolean effects) {
        BlockState state = level.getBlockState(pos);
        BlockPos other = partnerIn(level, state, pos);
        if (other != null) {
            level.setBlock(other, level.getFluidState(other).createLegacyBlock(), SILENT);
        }
        if (effects) {
            level.destroyBlock(pos, false);
        } else {
            level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), Block.UPDATE_ALL);
        }
        if (other != null) {
            // now that both halves are gone, replay the shape pass the silent removal skipped (vanilla runs it
            // with UPDATE_CLIENTS only), so the other half's neighbours react (fences, panes, redstone...)
            level.getBlockState(other).updateNeighbourShapes(level, other, Block.UPDATE_CLIENTS);
        }
    }
}
