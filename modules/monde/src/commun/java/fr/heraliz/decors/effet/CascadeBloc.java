package fr.heraliz.decors.effet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * Grande cascade : posé dans la fissure de la roche d'où sort l'eau ; l'eau file vers FACING, déborde et tombe.
 * Largeur, hauteur de chute et profondeur de la fissure sont dans l'entité de bloc ({@link CascadeBE}).
 */
public class CascadeBloc extends EffetBloc {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CascadeBloc(Properties p) {
        super(p, CascadeBE::new);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    /** Quelques gouttes qui tombent de la lèvre de roche (le pied de la cascade a ses propres embruns). */
    @Override
    public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
        if (r.nextInt(3) != 0 || !(l.getBlockEntity(p) instanceof CascadeBE be)) {
            return;
        }
        Direction f = s.getValue(FACING);
        double travers = (r.nextDouble() - 0.5) * be.largeur;
        double x = p.getX() + 0.5 + f.getStepX() * 0.9 + (f.getAxis() == Direction.Axis.Z ? travers : 0);
        double z = p.getZ() + 0.5 + f.getStepZ() * 0.9 + (f.getAxis() == Direction.Axis.X ? travers : 0);
        l.addParticle(ParticleTypes.FALLING_WATER, x, p.getY() + 0.1, z, 0, 0, 0);
    }
}
