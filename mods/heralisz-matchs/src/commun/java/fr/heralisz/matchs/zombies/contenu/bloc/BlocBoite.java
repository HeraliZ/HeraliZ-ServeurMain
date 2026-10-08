package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Case d'un emplacement de boite mystere : {@link #PARTIE} 0 = centre (bloc-entite {@link BoiteBE}), 1 = a droite
 * (sens horaire de {@link #FACING}), 2 = a gauche. Boite v2 (D-048) : la table sur parpaings est un modele de bloc cuit
 * dans les troncons ({@code zb_boite_socle} quand la boite est la, {@code zb_boite_vide} = table + 3 bidons + nounours
 * assis quand l'emplacement est vide, choisi par {@link #ALLUMEE}) ; le bloc-entite ne dessine que la caisse animee.
 * Collision : table + caisse (17 px) quand la boite occupe l'emplacement, sinon la table (5 px) et les bidons ; la case
 * centrale fait toute la largeur, les cases laterales s'arretent au bout de la table (22 px du centre).
 */
public class BlocBoite extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PARTIE = IntegerProperty.create("partie", 0, 2);
    public static final BooleanProperty ALLUMEE = BooleanProperty.create("allumee");

    /** Hauteurs (px) : caisse fermee (dessus du couvercle), table, bidons de l'emplacement vide. */
    public static final double H_BOITE = 17, H_TABLE = 5, H_BIDONS = 9.6;
    /** Formes [partie][orientation 2D] : boite presente, emplacement vide. */
    private static final VoxelShape[][] PLEINE = new VoxelShape[3][4], VIDE = new VoxelShape[3][4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int k = d.get2DDataValue();
            // emprise en profondeur de la table et de la caisse : -7,5..7,5 px autour du centre (repere tourne vers le sud)
            PLEINE[0][k] = BlocLarge.tourner(0, 16, 0.5, 15.5, H_BOITE, d);
            PLEINE[1][k] = BlocLarge.tourner(2, 16, 0.5, 15.5, H_BOITE, d);
            PLEINE[2][k] = BlocLarge.tourner(0, 14, 0.5, 15.5, H_BOITE, d);
            VIDE[0][k] = Shapes.or(BlocLarge.tourner(0, 16, 0.5, 15.5, H_TABLE, d), BlocLarge.tourner(4, 12, 6, 13.5, H_BIDONS, d));
            VIDE[1][k] = BlocLarge.tourner(2, 16, 0.5, 15.5, H_TABLE, d);
            VIDE[2][k] = BlocLarge.tourner(0, 14, 0.5, 15.5, H_TABLE, d);
        }
    }

    public BlocBoite(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PARTIE, 0).setValue(ALLUMEE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PARTIE, ALLUMEE);
    }

    /** Position de la case centrale de cette boite. */
    public static BlockPos centre(BlockPos pos, BlockState state) {
        int p = state.getValue(PARTIE);
        Direction f = state.getValue(FACING);
        return p == 1 ? pos.relative(f.getCounterClockWise()) : p == 2 ? pos.relative(f.getClockWise()) : pos;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PARTIE) == 0 ? new BoiteBE(pos, state) : null;
    }

    /** Case centrale : socle ou emplacement vide cuits (modele de bloc) ; le bloc-entite dessine en plus la caisse. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PARTIE) == 0 ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // particules de l'arrivee et de l'envol (cote client seulement, calees sur l'animation)
        if (!level.isClientSide || state.getValue(PARTIE) != 0) return null;
        return createTickerHelper(type, ZbRegistres.BOITE_BE.get(), BoiteBE::tickClient);
    }

    private static VoxelShape forme(BlockGetter level, BlockPos pos, BlockState state) {
        boolean occupe = level.getBlockEntity(centre(pos, state)) instanceof BoiteBE b && b.occupe();
        return (occupe ? PLEINE : VIDE)[state.getValue(PARTIE)][state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return forme(level, pos, state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return forme(level, pos, state);
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

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) ZombiesApi.interagir(sp);
        return InteractionResult.CONSUME;
    }
}
