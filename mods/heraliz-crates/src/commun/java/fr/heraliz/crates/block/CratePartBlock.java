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
 * Bloc invisible qui occupe l'espace autour du coffre (le coffre fait 2x2x1.5 blocs).
 * Il transmet les clics au bloc principal et donne la bonne collision.
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

    /** Caisse a laquelle appartient cette partie (null si le bloc principal a disparu). */
    @javax.annotation.Nullable
    private static fr.heraliz.crates.crate.CrateType owner(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState ms = level.getBlockState(masterPos(pos, state));
        return ms.getBlock() instanceof CrateBlock cb ? cb.type : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        fr.heraliz.crates.crate.CrateType type = owner(state, level, pos);
        // partie orpheline : un bloc plein qu'on peut viser et casser
        return type == null ? net.minecraft.world.phys.shapes.Shapes.block() : CrateShapes.outline(type, state.getValue(PART));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        fr.heraliz.crates.crate.CrateType type = owner(state, level, pos);
        return type == null ? net.minecraft.world.phys.shapes.Shapes.empty() : CrateShapes.collision(type, state.getValue(PART));
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
