package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Case d'un element d'ouverture payant (porte barree, grille cadenassee, tas de debris) : toutes les cases du volume
 * de la porte portent ce bloc (pose par le decor a l'installation) ; seule la case {@link #MAITRE} (coin minimal) a un
 * bloc-entite {@link ObstacleBE} qui dessine tout l'element et sa pancarte de prix. Pleine pour tout le monde (joueurs,
 * creatures, projectiles) ; le jeu retire les blocs a l'achat. Clic droit = interaction du jeu (acheter).
 */
public class BlocObstacle extends BaseEntityBlock {
    public static final BooleanProperty MAITRE = BooleanProperty.create("maitre");

    public BlocObstacle(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(MAITRE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(MAITRE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(MAITRE) ? new ObstacleBE(pos, state) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(MAITRE) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
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
