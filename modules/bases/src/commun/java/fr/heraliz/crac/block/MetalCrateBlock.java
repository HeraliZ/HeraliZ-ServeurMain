package fr.heraliz.crac.block;

import fr.heraliz.crac.block.entity.MetalCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Caisse en métal ; elle se colle aux murs depuis CRac 1.11.0 ({@link Adosse}). */
public class MetalCrateBlock extends Block implements EntityBlock, Adosse.Adossable {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    private static final VoxelShape SHAPE_NS = box(0.0, 0.0, 1.5, 16.0, 9.0, 14.5);
    private static final VoxelShape SHAPE_EW = box(1.5, 0.0, 0.0, 14.5, 9.0, 16.0);

    /** Boîte de la caisse tournée vers le nord (px) : la même qu'outils/adossement.py. */
    private static final double[] BOITE = {0.0, 1.5, 16.0, 14.5};
    /** Formes poussées contre le mur, par sens et par décalage ({@link Adosse#formesDecalees}). */
    private static final VoxelShape[][] DECALEES = Adosse.formesDecalees(
        f -> f != Direction.EAST && f != Direction.WEST ? SHAPE_NS : SHAPE_EW, BOITE);
    /** Les mêmes, descendues d'un demi-bloc sur un plancher à mi-bloc ({@link Adosse#BAS}). */
    private static final VoxelShape[][] DESCENDUES = Adosse.descendre(DECALEES);

    private static VoxelShape shapeFor(BlockState state) {
        return (state.getValue(Adosse.BAS) ? DESCENDUES : DECALEES)[state.getValue(FACING).get2DDataValue()][state.getValue(Adosse.ADOSSE)];
    }

    @Override
    public double[] boiteNord() {
        return BOITE;
    }

    @Override
    public double hauteur(BlockState state) {
        return 9;
    }

    @Override
    public double dessous(BlockState state) {
        return Adosse.bas(state) ? Adosse.DEMI : 0;
    }

    public MetalCrateBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false)
            .setValue(Adosse.ADOSSE, Adosse.AUCUN).setValue(Adosse.BAS, false));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, Adosse.ADOSSE, Adosse.BAS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // face vers le joueur, tournée par R (OrientationObjet) ; descendue sur un plancher à mi-bloc (Adosse.BAS)
        BlockState s = this.defaultBlockState().setValue(FACING, OrientationObjet.face(ctx)).setValue(OPEN, false)
            .setValue(Adosse.BAS, Adosse.doitDescendre(ctx.getLevel(), ctx.getClickedPos()));
        return s.setValue(Adosse.ADOSSE, Adosse.choisir(ctx, s));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeFor(state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            if (level.getBlockEntity(pos) instanceof MetalCrateBlockEntity be) {
                player.openMenu(be);
            }

            return InteractionResult.CONSUME;
        }
    }

    /** Scheduled by the openers counter (like a barrel): closes the lid when nobody really has the crate open. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof MetalCrateBlockEntity be) {
            be.recheckOpen();
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MetalCrateBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MetalCrateBlockEntity be ? be : null;
    }

    /** Serveur : pose ou changement d'état ; les cases de mur voisines refont leur forme si la boîte a bougé. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        Adosse.changement(oldState, state);
    }

    /** Les deux côtés (le joueur reçoit les poses et changements d'état par le réseau) : comme {@link #onPlace}. */
    @Override
    public void onBlockStateChange(LevelReader level, BlockPos pos, BlockState oldState, BlockState newState) {
        Adosse.changement(oldState, newState);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            Adosse.changement();   // les cases de mur voisines refont leur forme (sans la caisse)
        }
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MetalCrateBlockEntity be) {
            Containers.dropContents(level, pos, be);
        }
        fr.heraliz.crac.pont.Serveur.Moteur m = fr.heraliz.crac.pont.Serveur.MOTEUR;
        if (!state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel sl && m != null) {
            m.objetAdosseRetire(sl, pos);   // un mur reprend la moitié de case que la caisse fermait
        }

        super.onRemove(state, level, pos, newState, moving);
    }
}
