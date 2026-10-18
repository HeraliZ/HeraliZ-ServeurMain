package fr.heraliz.decors.bloc;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Garde-corps en métal (1.10.0, 18/10 : « poser deux model 240 sur le même bloc pour faire un angle ») : le garde-corps
 * du « Model 240 » du mod de meubles (même forme, même texture), sur 1 à 4 côtés d'un même bloc. Posé du côté où regarde
 * le joueur ; reposer un garde-corps sur un bloc qui en a déjà un ajoute le côté regardé (comme une deuxième dalle), s'il
 * n'y est pas encore. Collision qui suit les montants et les lisses de chaque côté. Modèle : outils/garde_corps.py.
 */
public class GardeCorps extends Block {
    public static final BooleanProperty NORD = BlockStateProperties.NORTH, EST = BlockStateProperties.EAST,
            SUD = BlockStateProperties.SOUTH, OUEST = BlockStateProperties.WEST;

    /** Côté nord, comme le modèle : deux montants, la main courante et deux lisses, contre le bord (z de 0 à 1 px). */
    private static final VoxelShape COTE_NORD = Shapes.or(
            Deco.boite(0, 0, 0, 0.5, 16, 1),
            Deco.boite(15.5, 0, 0, 16, 16, 1),
            Deco.boite(0.5, 15, 0, 15.5, 16, 1),
            Deco.boite(0.5, 12, 0, 15.5, 12.8, 0.8),
            Deco.boite(0.5, 5, 0, 15.5, 6, 1)).optimize();

    private final Map<BlockState, VoxelShape> formes = new HashMap<>();

    public GardeCorps(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(NORD, false).setValue(EST, false).setValue(SUD, false)
                .setValue(OUEST, false));
        for (BlockState s : stateDefinition.getPossibleStates()) {
            VoxelShape v = Shapes.empty();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (s.getValue(cote(d))) {
                    v = Shapes.or(v, Deco.tourner(COTE_NORD, d));
                }
            }
            formes.put(s, v.isEmpty() ? COTE_NORD : v.optimize());
        }
    }

    public static BooleanProperty cote(Direction d) {
        return switch (d) {
            case EAST -> EST;
            case SOUTH -> SUD;
            case WEST -> OUEST;
            default -> NORD;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(NORD, EST, SUD, OUEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BooleanProperty p = cote(ctx.getHorizontalDirection());
        BlockState ici = ctx.getLevel().getBlockState(ctx.getClickedPos());
        return ici.is(this) ? ici.setValue(p, true) : defaultBlockState().setValue(p, true);
    }

    /** Un garde-corps en main sur un bloc qui n'a pas encore ce côté : on le complète au lieu de poser à côté. */
    @Override
    public boolean canBeReplaced(BlockState s, BlockPlaceContext ctx) {
        return ctx.getItemInHand().is(this.asItem()) && !s.getValue(cote(ctx.getHorizontalDirection()));
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return formes.get(s);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }

    @Override
    public boolean isPathfindable(BlockState s, BlockGetter l, BlockPos p, PathComputationType t) {
        return false;
    }
}
