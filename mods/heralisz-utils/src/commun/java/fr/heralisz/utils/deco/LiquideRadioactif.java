package fr.heralisz.utils.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Liquide radioactif des bassins de l'usine nucléaire : bloc translucide lumineux (texture animée), sans écoulement
 * ni collision ; on s'y enfonce et il empoisonne. Les faces entre deux blocs de liquide ne sont pas dessinées.
 * Jar fusionné : l'enlisement (des deux côtés) ici ; le poison dans LiquideRadioactifServeur (DecoBlocks.LIQUIDE_USINE).
 */
public class LiquideRadioactif extends Block {
    private static final VoxelShape SEL = Block.box(0, 0, 0, 16, 14, 16);

    public LiquideRadioactif(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SEL;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public boolean skipRendering(BlockState s, BlockState adjacent, Direction d) {
        return adjacent.is(this) || super.skipRendering(s, adjacent, d);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState s, Level level, BlockPos p, Entity e) {
        e.makeStuckInBlock(s, new net.minecraft.world.phys.Vec3(0.6, 0.4, 0.6));
        empoisonner(level, e);
    }

    /** Poison (règle du jeu, serveur) : neutre ici ; LiquideRadioactifServeur (partie serveur) empoisonne. */
    void empoisonner(Level level, Entity e) {}
}
