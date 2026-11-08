package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.shapes.VoxelShape;

public class Applique extends Deco.Oriente {
    public Applique(Properties p, Function<BlockState, VoxelShape> forme) {
        super(p, forme, false, Effet.AUCUN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction f = c.getClickedFace();
        return this.defaultBlockState().setValue(FACING, f.getAxis().isHorizontal() ? f : c.getHorizontalDirection().getOpposite());
    }
}
