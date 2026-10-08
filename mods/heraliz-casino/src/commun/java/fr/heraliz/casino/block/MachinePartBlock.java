package fr.heraliz.casino.block;

import fr.heraliz.casino.pont.Crochet;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Bloc invisible qui remplit le volume d'une machine (ou d'un element de decor). Il connait le decalage vers le bloc principal
 * (proprietes dx, dy, dz), lui transmet les clics et prend la forme de la machine.
 */
public class MachinePartBlock extends Block {
    public static final int RANGE = 3, HEIGHT = 5;
    public static final IntegerProperty DX = IntegerProperty.create("dx", 0, RANGE * 2);
    public static final IntegerProperty DY = IntegerProperty.create("dy", 0, HEIGHT);
    public static final IntegerProperty DZ = IntegerProperty.create("dz", 0, RANGE * 2);

    public MachinePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(DX, RANGE).setValue(DY, 0).setValue(DZ, RANGE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DX, DY, DZ);
    }

    /** Etat d'une partie situee a (dx,dy,dz) du bloc principal (null si hors limites). */
    @Nullable
    public static BlockState forOffset(int dx, int dy, int dz) {
        if (Math.abs(dx) > RANGE || Math.abs(dz) > RANGE || dy < 0 || dy > HEIGHT) return null;
        return ModRegistry.MACHINE_PART.get().defaultBlockState().setValue(DX, dx + RANGE).setValue(DY, dy).setValue(DZ, dz + RANGE);
    }

    public static int[] offset(BlockState state) {
        return new int[]{state.getValue(DX) - RANGE, state.getValue(DY), state.getValue(DZ) - RANGE};
    }

    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        int[] o = offset(state);
        return pos.offset(-o[0], -o[1], -o[2]);
    }

    @Nullable
    private static BlockState master(BlockGetter level, BlockPos pos, BlockState state) {
        BlockState ms = level.getBlockState(masterPos(pos, state));
        return ms.getBlock() instanceof FootprintBlock ? ms : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos mp = masterPos(pos, state);
        BlockState ms = level.getBlockState(mp);
        if (ms.getBlock() instanceof FootprintBlock) {
            return ms.use(level, player, hand, hit.withPosition(mp));
        }
        return InteractionResult.PASS;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // casser une partie (en creatif) casse toute la machine
        BlockPos mp = masterPos(pos, state);
        if (!level.isClientSide && level.getBlockState(mp).getBlock() instanceof FootprintBlock) {
            level.setBlock(mp, Blocks.AIR.defaultBlockState(), 35);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        BlockState ms = master(level, pos, state);
        return ms != null ? new ItemStack(ms.getBlock()) : ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        BlockState ms = master(level, pos, state);
        if (ms == null) return Shapes.block();
        int[] o = offset(state);
        return ((FootprintBlock) ms.getBlock()).footprint(ms).outline(ms.getValue(MachineBlock.FACING), o[0], o[1], o[2]);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        BlockState ms = master(level, pos, state);
        if (ms == null) return Shapes.empty();
        int[] o = offset(state);
        return ((FootprintBlock) ms.getBlock()).footprint(ms).collision(ms.getValue(MachineBlock.FACING), o[0], o[1], o[2]);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    /** Pas de particules de coup ni de casse (bloc invisible) : branche par la partie client. */
    public static final Crochet<Consumer<IClientBlockExtensions>> EFFETS_CLIENT = new Crochet<>();

    @Override
    public void initializeClient(Consumer<IClientBlockExtensions> consumer) {
        EFFETS_CLIENT.accept(consumer);
    }
}
