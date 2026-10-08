package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.MachinesV3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Collision invisible des machines v3 (tour, plateforme et marches de 0,5 bloc, pylones des pads, armoire...) : rien a
 * dessiner, la lumiere passe ; {@link #FORME} 0 = demi-bloc du haut (pont), 1..16 = boite du bas de n px, au-dela =
 * forme fine d'une case d'un modele (0.2.3 : marches, rambardes, pont, deja tournee, construite au chargement). Les balles
 * TaCZ s'y arretent comme sur le modele visible.
 */
public class BlocCollision extends Block {
    /** 0..16 : anciennes formes ; au-dela (0.2.3) : formes fines des modeles ({@link MachinesV3#nombreFormes}). */
    public static final IntegerProperty FORME = IntegerProperty.create("forme", 0, Math.max(16, MachinesV3.nombreFormes() - 1));

    public BlocCollision(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FORME, 16));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FORME);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return MachinesV3.formeCollision(state.getValue(FORME));
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }
}
