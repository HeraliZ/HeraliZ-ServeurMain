package fr.heralisz.clans.territory;

import fr.heraliz.clansetbases.pont.Crochet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * Drapeau de territoire : socle de pierre d'un bloc (modele JSON) avec BlockEntity ; collision = socle + colonne du
 * mat (0,25 x 5 blocs). Le mat et le tissu sont dessines par territory/client/DrapeauRenderer. FACING = direction
 * vers laquelle le tissu part. Clic droit sur le socle : panneau de la zone ({@code Territoires#ouvrirPanneau}, partie
 * serveur, par {@link #PANNEAU_SERVEUR}).
 */
public class DrapeauBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape FORME = Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(6, 3, 6, 10, 80, 10));

    /** Panneau de la zone (Territoires.ouvrirPanneau) : branche par la partie serveur, neutre sans elle. */
    public static volatile BiConsumer<ServerPlayer, String> PANNEAU_SERVEUR = (p, zone) -> {
    };
    /** Drapeau retire du monde (Territoires.drapeauRetire) : branche par la partie serveur, neutre sans elle. */
    public static final Crochet<BlockPos> RETRAIT_SERVEUR = new Crochet<>();

    public DrapeauBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DrapeauBlockEntity(pos, state);
    }

    /** Le socle a un modele JSON ; le reste vient du BlockEntityRenderer. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return FORME;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof DrapeauBlockEntity be && !be.zoneId.isEmpty()) {
            PANNEAU_SERVEUR.accept(sp, be.zoneId);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            RETRAIT_SERVEUR.accept(pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
