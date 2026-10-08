package fr.heralisz.matchs.zombies.contenu.bloc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Neige de The Giant (D-053), posee par la carte : plaques de 1/16 en tuiles raccordables ({@link Tache}) et bourrelets au bord des toits
 * ({@link Rebord}). Un quad cuit (decoupe), sans collision, traversable, non opaque, jamais une cible : zero cout en jeu.
 */
public abstract class BlocNeige extends Block {
    protected BlocNeige(Properties p) {
        super(p);
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
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) {
        return 1.0F;
    }

    @Override
    public boolean isPathfindable(BlockState s, BlockGetter l, BlockPos p, PathComputationType t) {
        return true;
    }

    /**
     * Plaque en tuiles raccordables (0.2.3, contrat §2) : {@code forme} = masque des 4 coins couverts (bit0 nord-ouest,
     * bit1 nord-est, bit2 sud-est, bit3 sud-ouest), comme le marching squares ; 15 = case pleine, interieur de plaque.
     * {@code variante} 0..1 = deux habillages de la meme forme (texture calee sur le monde, sans couture). Modeles et
     * etats de bloc : assets generes par {@code decor_giant_neige.py}.
     */
    public static class Tache extends BlocNeige {
        public static final IntegerProperty FORME = IntegerProperty.create("forme", 0, 15);
        public static final IntegerProperty VARIANTE = IntegerProperty.create("variante", 0, 1);

        public Tache(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FORME, 15).setValue(VARIANTE, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FORME, VARIANTE);
        }
    }

    /** Bourrelet de 3/16 sur un rebord, debordant vers {@code facing}. */
    public static class Rebord extends BlocNeige {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        public static final IntegerProperty VARIANTE = IntegerProperty.create("variante", 0, 2);

        public Rebord(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(VARIANTE, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, VARIANTE);
        }
    }
}
