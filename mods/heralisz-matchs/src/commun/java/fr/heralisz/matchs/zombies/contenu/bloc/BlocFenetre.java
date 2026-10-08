package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Case d'une fenetre barricadee. Toutes les cases de l'ouverture portent ce bloc ; seule la case {@link #MAITRE}
 * (coin minimal) a un bloc-entite et dessine les planches de toute l'ouverture. {@link #FACING} = normale vers
 * l'exterieur (cote ou apparaissent les zombies).
 * <p>Collision (verite tenue par le jeu, {@link ZombiesApi#planches}) : toujours pleine pour les joueurs ; pour les
 * creatures, pleine tant qu'il reste une planche (ou hors partie), vide a 0 ; vide pour les projectiles et les balles
 * (on tire a travers les planches) ; pleine pour le calcul de chemin (le jeu fait passer les zombies lui-meme).
 * Clic droit = interaction du jeu (reparer).
 */
public class BlocFenetre extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty MAITRE = BooleanProperty.create("maitre");

    private static final VoxelShape PLEIN = Shapes.block();
    /** Contour de visee : fine plaque sur la face interieure (ou sont clouees les planches). */
    private static final VoxelShape[] VISEE = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // face interieure = opposee a l'exterieur
            Direction in = d.getOpposite();
            double x0 = 0, z0 = 0, x1 = 16, z1 = 16;
            switch (in) {
                case NORTH -> z1 = 4;
                case SOUTH -> z0 = 12;
                case WEST -> x1 = 4;
                default -> x0 = 12;
            }
            VISEE[d.get2DDataValue()] = Block.box(x0, 0, z0, x1, 16, z1);
        }
    }

    public BlocFenetre(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MAITRE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, MAITRE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(MAITRE) ? new FenetreBE(pos, state) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(MAITRE) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return VISEE[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (ctx instanceof EntityCollisionContext ec) {
            Entity e = ec.getEntity();
            if (e != null) {
                if (e instanceof Player) return PLEIN;
                if (e instanceof Mob) {
                    if (level instanceof Level l && !l.isClientSide && ZombiesApi.planches(l, pos) == 0) return Shapes.empty();
                    return PLEIN;
                }
                return Shapes.empty();
            }
        }
        return PLEIN;
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
