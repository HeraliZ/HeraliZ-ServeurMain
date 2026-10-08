package fr.heraliz.crates.block;

import fr.heraliz.crates.pont.Crochet;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;

import java.util.function.Consumer;

/**
 * Bloc invisible qui occupe l'espace autour de la caisse (jusqu'a 3 x 3 x 3 blocs). Il transmet les clics au bloc
 * principal ; sa forme est celle de la caisse (CrateShapes) : contour complet a la selection, son morceau en collision.
 */
public class CratePartBlock extends Block {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, CrateShapes.COUNT - 1);

    public CratePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        int i = state.getValue(PART);
        return pos.offset(-CrateShapes.dx(i), -CrateShapes.dy(i), -CrateShapes.dz(i));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos master = masterPos(pos, state);
        BlockState ms = level.getBlockState(master);
        if (ms.getBlock() instanceof CrateBlock) {
            return ms.use(level, player, hand, hit.withPosition(master));
        }
        return InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        BlockPos master = masterPos(pos, state);
        BlockState ms = level.getBlockState(master);
        if (ms.getBlock() instanceof CrateBlock) {
            ms.attack(level, master, player);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // casser une partie (en creatif) casse tout le coffre
        BlockPos master = masterPos(pos, state);
        if (!level.isClientSide && level.getBlockState(master).getBlock() instanceof CrateBlock) {
            level.setBlock(master, Blocks.AIR.defaultBlockState(), 35);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        BlockState ms = level.getBlockState(masterPos(pos, state));
        if (ms.getBlock() instanceof CrateBlock) {
            return new ItemStack(ms.getBlock());
        }
        return ItemStack.EMPTY;
    }

    /** Etat du bloc principal de la caisse a laquelle appartient cette partie (null s'il a disparu). */
    @javax.annotation.Nullable
    private static BlockState owner(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState ms = level.getBlockState(masterPos(pos, state));
        return ms.getBlock() instanceof CrateBlock ? ms : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        BlockState ms = owner(state, level, pos);
        // partie orpheline : un bloc plein qu'on peut viser et casser
        return ms == null ? net.minecraft.world.phys.shapes.Shapes.block()
                : CrateShapes.outline(((CrateBlock) ms.getBlock()).type, ms.getValue(CrateBlock.FACING), state.getValue(PART));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        BlockState ms = owner(state, level, pos);
        return ms == null ? net.minecraft.world.phys.shapes.Shapes.empty()
                : CrateShapes.collision(((CrateBlock) ms.getBlock()).type, ms.getValue(CrateBlock.FACING), state.getValue(PART));
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

    /** Pas de particules de coup ni de casse (client) : branché par la partie client. */
    public static final Crochet<Consumer<IClientBlockExtensions>> RENDU_CLIENT = new Crochet<>();

    @Override
    public void initializeClient(Consumer<IClientBlockExtensions> consumer) {
        RENDU_CLIENT.accept(consumer);
    }
}
