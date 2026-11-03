package fr.heraliz.crac.block;

import fr.heraliz.crac.block.entity.CoeurClanBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Cœur de clan (saison des clans) : il garde les points de saison du clan. Modèle fixe depuis le 03/11 (socle, cristal
 * cyan qui ne tourne pas, écrit par {@code modules/bases/outils/coeur_simple.py}), points écrits en direct sur sa plaque
 * ({@code client/render/CoeurClanRendu}). Incassable en survie ; pose réservée à la base de son clan, un seul par clan
 * (règles côté serveur : {@code heraliz/CoeursClans}).
 */
public class CoeurClanBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /**
     * Socle (jusqu'à 3,4 px) et colonne du cristal : on ne se met pas dans le cristal. Modèle du 03/11 (option A « simple et
     * fixe », {@code modules/bases/outils/coeur_simple.py}).
     */
    private static final VoxelShape FORME = Shapes.or(Block.box(1, 0, 1, 15, 3.4, 15), Block.box(5, 3.4, 5, 11, 14, 11));

    public CoeurClanBlock(Properties p) {
        super(p);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    /** Plaque des points tournée vers le joueur qui le pose. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoeurClanBlockEntity(pos, state);
    }
}
