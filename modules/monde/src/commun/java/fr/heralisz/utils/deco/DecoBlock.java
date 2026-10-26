package fr.heralisz.utils.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Décor orientable (4 directions) à modèle 3D fixe ; boîte de sélection/collision donnée pour la face nord. */
public class DecoBlock extends HorizontalDirectionalBlock {
    private final VoxelShape[] shapes = new VoxelShape[4];
    private final boolean solid;

    public DecoBlock(BlockBehaviour.Properties props, double[] b, boolean solid) {
        super(props);
        this.solid = solid;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            shapes[d.get2DDataValue()] = rotated(b, d);
        }
    }

    /** Boîte (x0, y0, z0, x1, y1, z1 en pixels, bornée au bloc) tournée depuis le nord vers d. */
    static VoxelShape rotated(double[] b, Direction d) {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        double[] r = switch (d) {
            case EAST -> new double[] {16 - z1, x0, 16 - z0, x1};
            case SOUTH -> new double[] {16 - x1, 16 - z1, 16 - x0, 16 - z0};
            case WEST -> new double[] {z0, 16 - x1, z1, 16 - x0};
            default -> new double[] {x0, z0, x1, z1};
        };
        return Block.box(clamp(r[0]), clamp(b[1]), clamp(r[1]), clamp(r[2]), clamp(b[4]), clamp(r[3]));
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(16, v));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return shapes[s.getValue(FACING).get2DDataValue()];
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

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }
}
