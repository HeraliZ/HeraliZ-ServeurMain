package fr.heralisz.utils.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Décor selon un axe (tuyaux, corps de turbine) : la boîte est donnée pour l'axe Y (vertical). */
public class AxisDecoBlock extends RotatedPillarBlock {
    private final VoxelShape x, y, z;
    private final boolean solid;

    public AxisDecoBlock(BlockBehaviour.Properties props, double[] b, boolean solid) {
        super(props);
        this.solid = solid;
        y = Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
        x = Block.box(b[1], b[0], b[2], b[4], b[3], b[5]);
        z = Block.box(b[0], b[2], b[1], b[3], b[5], b[4]);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        Direction.Axis a = s.getValue(AXIS);
        return a == Direction.Axis.X ? x : a == Direction.Axis.Z ? z : y;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return solid ? getShape(s, l, p, c) : Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) {
        return 1.0f;
    }
}
