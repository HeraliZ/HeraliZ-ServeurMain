package fr.heraliz.decors.effet;

import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bloc invisible qui porte un grand effet (cascade, rayon de soleil) : le bloc ne se dessine pas, c'est son entité
 * de bloc qui dessine tout l'effet d'un seul tenant, côté client seulement. Aucune collision, aucun calcul serveur
 * (pas de tick) : coût nul pour le serveur.
 */
public class EffetBloc extends BaseEntityBlock {
    private final BiFunction<BlockPos, BlockState, BlockEntity> fabrique;

    public EffetBloc(Properties p, BiFunction<BlockPos, BlockState, BlockEntity> fabrique) {
        super(p);
        this.fabrique = fabrique;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return fabrique.apply(p, s);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }
}
