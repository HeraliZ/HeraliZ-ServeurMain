package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Etape d'easter egg (une case) : {@link #GENRE} 0 = radio a lampes posee, 1 = interrupteur industriel contre le mur,
 * 2 = fragment d'element lumineux sur un socle ; {@link #FACING} = vers le joueur. Petite forme (on peut passer a cote),
 * sans collision pour les creatures ; clic droit = interaction du jeu. Bloc-entite {@link EasterBE}.
 */
public class BlocEasterEgg extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty GENRE = IntegerProperty.create("genre", 0, 2);
    private static final VoxelShape[][] FORMES = new VoxelShape[3][4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int k = d.get2DDataValue();
            FORMES[0][k] = tourner(2.5, 13.5, 5, 11, 0, 8.5, d);
            FORMES[1][k] = tourner(4.5, 11.5, 0, 3.5, 5, 14.5, d);
            FORMES[2][k] = tourner(4, 12, 4, 12, 0, 9, d);
        }
    }

    private static VoxelShape tourner(double x0, double x1, double z0, double z1, double y0, double y1, Direction f) {
        double a = Math.toRadians(-f.toYRot());
        double c = Math.cos(a), s = Math.sin(a);
        double minX = 16, minZ = 16, maxX = 0, maxZ = 0;
        for (double x : new double[]{x0, x1}) {
            for (double z : new double[]{z0, z1}) {
                double cx = x - 8, cz = z - 8;
                double wx = cx * c + cz * s + 8, wz = -cx * s + cz * c + 8;
                minX = Math.min(minX, wx);
                maxX = Math.max(maxX, wx);
                minZ = Math.min(minZ, wz);
                maxZ = Math.max(maxZ, wz);
            }
        }
        return Block.box(Math.max(0, minX), y0, Math.max(0, minZ), Math.min(16, maxX), y1, Math.min(16, maxZ));
    }

    public BlocEasterEgg(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(GENRE, 0));
    }

    /** Genre depuis le type de la carte ({@code radio | interrupteur | objet}). */
    public static int genre(String type) {
        return "interrupteur".equals(type) ? 1 : "objet".equals(type) ? 2 : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, GENRE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EasterBE(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return FORMES[state.getValue(GENRE)][state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) ZombiesApi.interagir(sp);
        return InteractionResult.CONSUME;
    }
}
