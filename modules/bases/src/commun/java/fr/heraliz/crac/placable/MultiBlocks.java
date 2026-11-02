package fr.heraliz.crac.placable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Two-block placables (beds and sleeping bags, doors, tall plants, the storage chests of CoffreBlock, the TaCZ gun smith
 * table). Both halves share one HP entry and one position entry, keyed by the {@link #canonical} half, and are always
 * removed together.
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
        BedPart part = partieTacz(state);
        if (part != null) {
            Direction facing = (Direction) state.getValue(state.getBlock().getStateDefinition().getProperty("facing"));
            return part == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite());
        }
        return null;
    }

    /**
     * HeraliZ (31/10) : la table d'armurier de TaCZ ({@code tacz:gun_smith_table}, à poser dans sa base) tient deux cases
     * comme un lit, sans en être un : propriétés « part » (pied, là où on l'a posée, ou tête) et « facing » (la tête est
     * du côté de facing), mêmes règles que le lit (GunSmithTableBlockB de TaCZ). Lues par leur nom (CRac ne dépend pas de
     * TaCZ). Partie de la case, ou null pour tout autre bloc.
     */
    @Nullable
    private static BedPart partieTacz(BlockState state) {
        Block b = state.getBlock();
        return estTableTacz(b) ? (BedPart) state.getValue(b.getStateDefinition().getProperty("part")) : null;
    }

    /** Le bloc est une table de TaCZ de deux cases (propriétés « part » et « facing », lues par leur nom). */
    public static boolean estTableTacz(Block b) {
        net.minecraft.world.level.block.state.properties.Property<?> part = b.getStateDefinition().getProperty("part");
        net.minecraft.world.level.block.state.properties.Property<?> facing = b.getStateDefinition().getProperty("facing");
        if (part == null || facing == null || part.getValueClass() != BedPart.class || facing.getValueClass() != Direction.class) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
        return id != null && "tacz".equals(id.getNamespace());
    }

    /** La tête d'une table de TaCZ dont {@code pied} est l'état du pied (pour le fantôme de pose), sinon null. */
    @Nullable
    public static BlockState teteTacz(BlockState pied) {
        if (!estTableTacz(pied.getBlock())) {
            return null;
        }
        @SuppressWarnings("unchecked")
        net.minecraft.world.level.block.state.properties.Property<BedPart> part =
            (net.minecraft.world.level.block.state.properties.Property<BedPart>) pied.getBlock().getStateDefinition().getProperty("part");
        return pied.setValue(part, BedPart.HEAD);
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
        if (partieTacz(state) == BedPart.HEAD) {   // table d'armurier de TaCZ : le pied, comme un lit
            BlockPos pied = partner(state, pos);
            return pied == null ? pos.immutable() : pied;
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
