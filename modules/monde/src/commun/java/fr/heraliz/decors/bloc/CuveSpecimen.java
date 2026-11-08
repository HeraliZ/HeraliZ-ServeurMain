package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CuveSpecimen extends Deco.Oriente {
    public static final BooleanProperty CASSEE = BooleanProperty.create("cassee");

    public CuveSpecimen(Properties p, Function<BlockState, VoxelShape> forme) {
        super(p, forme, false, Effet.AUCUN);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CASSEE, false));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(CASSEE);
    }
}
