package fr.heraliz.crates.block;

import java.util.function.BiConsumer;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.item.CrateKeyItem;
import fr.heraliz.crates.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Bloc principal d'un coffre. Le rendu est entierement fait par CrateRenderer (modele 3D HD).
 */
public class CrateBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public final CrateType type;

    public CrateBlock(CrateType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // ------------------------------------------------------------------ placement multibloc
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        for (int i = 0; i < CrateShapes.COUNT; i++) {
            if (i == CrateShapes.MASTER || !CrateShapes.needed(type, i)) continue;
            BlockPos p = pos.offset(CrateShapes.dx(i), CrateShapes.dy(i), CrateShapes.dz(i));
            if (!level.isInWorldBounds(p) || !level.getBlockState(p).canBeReplaced(ctx)) {
                if (!level.isClientSide && ctx.getPlayer() != null) {
                    ctx.getPlayer().displayClientMessage(
                            Component.translatable("message.heralizcrates.no_space").withStyle(ChatFormatting.RED), true);
                }
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            placeParts(level, pos, type);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !oldState.is(state.getBlock())) {
            placeParts(level, pos, type);
        }
    }

    /**
     * Pose les blocs "parties" des cellules traversees par la caisse et retire ceux qui ne servent plus (caisse posee avec
     * une ancienne version, dont la taille a change).
     */
    public static void placeParts(Level level, BlockPos pos, CrateType type) {
        for (int i = 0; i < CrateShapes.COUNT; i++) {
            if (i == CrateShapes.MASTER) continue;
            BlockPos p = pos.offset(CrateShapes.dx(i), CrateShapes.dy(i), CrateShapes.dz(i));
            if (!level.isInWorldBounds(p)) continue;
            BlockState cur = level.getBlockState(p);
            boolean mine = cur.getBlock() instanceof CratePartBlock && cur.getValue(CratePartBlock.PART) == i;
            if (!CrateShapes.needed(type, i)) {
                if (mine) level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
                continue;
            }
            if (mine) continue;
            if (cur.canBeReplaced()) {
                level.setBlock(p, ModRegistry.CRATE_PART.get().defaultBlockState().setValue(CratePartBlock.PART, i), 3);
            }
        }
    }

    public static void removeParts(Level level, BlockPos pos) {
        for (int i = 0; i < CrateShapes.COUNT; i++) {
            if (i == CrateShapes.MASTER) continue;
            BlockPos p = pos.offset(CrateShapes.dx(i), CrateShapes.dy(i), CrateShapes.dz(i));
            BlockState cur = level.getBlockState(p);
            if (cur.getBlock() instanceof CratePartBlock && cur.getValue(CratePartBlock.PART) == i) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof CrateBlockEntity crate) {
                crate.onCrateRemoved();
            }
            removeParts(level, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    // ------------------------------------------------------------------ interaction
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof CrateBlockEntity crate)) {
            return InteractionResult.PASS;
        }
        // accroupi : interface d'ouverture ; sinon clé et tirage (version serveur de l'entité de bloc)
        crate.clicDroit(sp, player.isShiftKeyDown());
        return InteractionResult.CONSUME;
    }

    /** Aperçu du contenu au clic gauche (serveur : menu/PreviewMenu) : branché par la partie serveur. */
    public static volatile BiConsumer<ServerPlayer, CrateType> APERCU;

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        // clic gauche : apercu du contenu
        if (!level.isClientSide && player instanceof ServerPlayer sp && APERCU != null) {
            APERCU.accept(sp, type);
        }
    }

    // ------------------------------------------------------------------ forme & rendu
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return CrateShapes.outline(type, CrateShapes.MASTER);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return CrateShapes.collision(type, CrateShapes.MASTER);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ------------------------------------------------------------------ entite
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModRegistry.CRATE_BE.get().create(pos, state);   // usine : version serveur si la partie serveur est là
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide
                ? createTickerHelper(blockEntityType, ModRegistry.CRATE_BE.get(), CrateBlockEntity::clientTick)
                : createTickerHelper(blockEntityType, ModRegistry.CRATE_BE.get(), CrateBlockEntity::serverTick);
    }
}
