package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PorteGrille extends DoorBlock {
    private static final VoxelShape AXE_X = Block.box(6.5, 0.0, 0.0, 9.5, 16.0, 16.0);
    private static final VoxelShape AXE_Z = Block.box(0.0, 0.0, 6.5, 16.0, 16.0, 9.5);

    public PorteGrille(Properties p, BlockSetType t) {
        super(p, t);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        if (s.getValue(OPEN)) {
            return super.getShape(s, l, p, c);
        } else {
            return s.getValue(FACING).getAxis() == Axis.X ? AXE_X : AXE_Z;
        }
    }
}
