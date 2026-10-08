package fr.heraliz.casino.block;

import fr.heraliz.casino.machine.Footprint;
import fr.heraliz.casino.pont.Crochet;
import fr.heraliz.casino.machine.MachineType;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import javax.annotation.Nullable;

/**
 * Bloc principal d'une machine. Le rendu est entierement fait par le renderer (modele 3D procedural) ;
 * les autres cellules de la machine sont des blocs "parties" invisibles qui renvoient les clics ici.
 */
public class MachineBlock extends BaseEntityBlock implements FootprintBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Lumiere emise (0-15) : celle reglee pour ce type de machine (Tablette du Directeur, page Lumieres). */
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);

    /** Ouverture de l'ecran de jeu de la machine (clic droit) : branche par la partie client (ClientHooks.openMachine). */
    public static final Crochet<BlockPos> OUVRIR_CLIENT = new Crochet<>();

    public final MachineType type;

    @Override
    public Footprint footprint() {
        return type.footprint;
    }

    public MachineBlock(MachineType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(LIGHT, type.light));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIGHT);
    }

    /** Etat de la machine avec la lumiere reglee pour son type. */
    public BlockState withConfiguredLight(BlockState state) {
        int l = fr.heraliz.casino.config.CasinoConfig.get().light(type);
        return state.getValue(LIGHT) == l ? state : state.setValue(LIGHT, l);
    }

    // ------------------------------------------------------------------ placement multibloc
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        for (int[] c : type.footprint.cells) {
            if (c[0] == 0 && c[1] == 0 && c[2] == 0) continue;
            int[] w = Footprint.rotateCell(c[0], c[1], c[2], facing);
            BlockPos p = pos.offset(w[0], w[1], w[2]);
            if (!level.isInWorldBounds(p) || !level.getBlockState(p).canBeReplaced(ctx)) {
                if (!level.isClientSide && ctx.getPlayer() != null) {
                    ctx.getPlayer().displayClientMessage(Component.translatable("message.heralizcasino.no_space").withStyle(ChatFormatting.RED), true);
                }
                return null;
            }
        }
        return withConfiguredLight(defaultBlockState().setValue(FACING, facing));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) placeParts(level, pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !oldState.is(state.getBlock())) placeParts(level, pos, state);
    }

    private void placeParts(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (int[] c : type.footprint.cells) {
            if (c[0] == 0 && c[1] == 0 && c[2] == 0) continue;
            int[] w = Footprint.rotateCell(c[0], c[1], c[2], facing);
            BlockPos p = pos.offset(w[0], w[1], w[2]);
            if (!level.isInWorldBounds(p)) continue;
            BlockState cur = level.getBlockState(p);
            BlockState part = MachinePartBlock.forOffset(w[0], w[1], w[2]);
            if (part == null || cur == part) continue;
            if (cur.canBeReplaced()) level.setBlock(p, part, 3);
        }
    }

    private void removeParts(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (int[] c : type.footprint.cells) {
            if (c[0] == 0 && c[1] == 0 && c[2] == 0) continue;
            int[] w = Footprint.rotateCell(c[0], c[1], c[2], facing);
            BlockPos p = pos.offset(w[0], w[1], w[2]);
            BlockState cur = level.getBlockState(p);
            if (cur.getBlock() instanceof MachinePartBlock && pos.equals(MachinePartBlock.masterPos(p, cur))) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            if (level.getBlockEntity(pos) instanceof MachineBlockEntity be) be.onRemoved(sl);
            removeParts(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    // ------------------------------------------------------------------ interaction
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        // la Tablette du Directeur ouvre les reglages de la machine (voir DirectorTabletItem)
        if (player.getItemInHand(hand).getItem() instanceof fr.heraliz.casino.item.DirectorTabletItem) return InteractionResult.PASS;
        if (level.isClientSide) {
            OUVRIR_CLIENT.accept(pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ forme & rendu
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return type.footprint.outline(state.getValue(FACING), 0, 0, 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return type.footprint.collision(state.getValue(FACING), 0, 0, 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
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
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType t) {
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
        // par le type enregistre (son usine donne la version serveur quand la partie serveur est la), jamais par new
        return ModRegistry.beType(type).create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
        return level.isClientSide
                ? createTickerHelper(beType, ModRegistry.beType(type), MachineBlockEntity::clientTick)
                : createTickerHelper(beType, ModRegistry.beType(type), MachineBlockEntity::serverTick);
    }
}
