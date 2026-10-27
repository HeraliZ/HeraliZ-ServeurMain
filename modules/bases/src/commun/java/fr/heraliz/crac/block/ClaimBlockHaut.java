package fr.heraliz.crac.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Case du haut de l'armoire à outils ({@link ClaimBlock}, CRac 1.10.0) : invisible (le bloc du bas dessine tout le
 * modèle), elle donne au casier sa forme sur 2 blocs et renvoie les clics au bloc du bas. Sans armoire dessous, elle
 * disparaît. Elle ne se casse pas : on casse l'armoire par le bas, ou n'importe où en créatif.
 */
public class ClaimBlockHaut extends Block implements Adosse.Adossable {
    /** Formes du haut, par sens (x 0,5 à 15,5, z 3 à 15,75 dans le modèle tourné au nord). */
    private static final VoxelShape[] HAUT = ClaimBlock.formes(0.5, 3, 15.5, 15.75);
    /** Formes du haut poussées contre le mur, par sens et par décalage ({@link Adosse#formesDecalees}). */
    private static final VoxelShape[][] HAUT_DECALEES =
        Adosse.formesDecalees(f -> HAUT[f.get2DDataValue()], ClaimBlock.BOITE);

    public ClaimBlockHaut(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ClaimBlock.FACING, Direction.NORTH).setValue(Adosse.ADOSSE, Adosse.AUCUN));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(ClaimBlock.FACING, Adosse.ADOSSE);
    }

    @Override
    public double[] boiteNord() {
        return ClaimBlock.BOITE;
    }

    /** L'armoire entière : 32 px, dont 16 sous cette case. */
    @Override
    public double hauteur(BlockState state) {
        return 32;
    }

    @Override
    public double dessous(BlockState state) {
        return 16;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return HAUT_DECALEES[s.getValue(ClaimBlock.FACING).get2DDataValue()][s.getValue(Adosse.ADOSSE)];
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos bas = pos.below();
        BlockState b = level.getBlockState(bas);
        if (b.getBlock() instanceof ClaimBlock cb) {
            return cb.use(b, level, bas, player, hand, hit.withPosition(bas));
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState voisin, LevelAccessor level, BlockPos pos, BlockPos posVoisin) {
        if (dir == Direction.DOWN && !(voisin.getBlock() instanceof ClaimBlock)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // créatif : casser le haut casse l'armoire entière (le bas fait le reste : claim, stock)
        BlockPos bas = pos.below();
        if (!level.isClientSide && player.getAbilities().instabuild && level.getBlockState(bas).getBlock() instanceof ClaimBlock) {
            level.destroyBlock(bas, false, player);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        fr.heraliz.crac.pont.Serveur.Moteur m = fr.heraliz.crac.pont.Serveur.MOTEUR;
        if (!state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel sl && m != null) {
            m.objetAdosseRetire(sl, pos);   // un mur reprend la moitié de case que l'armoire fermait
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(fr.heraliz.crac.registry.ModItems.CLAIM_BLOCK.get());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(ClaimBlock.FACING, rot.rotate(state.getValue(ClaimBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(ClaimBlock.FACING)));
    }
}
