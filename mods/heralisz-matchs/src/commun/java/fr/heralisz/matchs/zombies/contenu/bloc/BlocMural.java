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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Base des blocs plaques contre un mur (arme a la craie, levier) : case d'air devant le mur, {@link #FACING} = vers la
 * salle (le mur est derriere). Aucune collision, contour de visee = plaque d'epaisseur {@code epaisseur} contre le mur,
 * rendu par le bloc-entite, clic droit = interaction du jeu ({@link ZombiesApi#interagir}).
 */
public abstract class BlocMural extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final VoxelShape[] visee = new VoxelShape[4];

    protected BlocMural(Properties p, double epaisseur, double y0, double y1, double demiLargeur) {
        super(p);
        double e = epaisseur * 16, a = 8 - demiLargeur * 16, b = 8 + demiLargeur * 16;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // mur derriere = cote oppose a FACING
            VoxelShape s = switch (d) {
                case SOUTH -> Block.box(a, y0 * 16, 0, b, y1 * 16, e);
                case NORTH -> Block.box(a, y0 * 16, 16 - e, b, y1 * 16, 16);
                case EAST -> Block.box(0, y0 * 16, a, e, y1 * 16, b);
                default -> Block.box(16 - e, y0 * 16, a, 16, y1 * 16, b);
            };
            visee[d.get2DDataValue()] = s;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return visee[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
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
