package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.FormesDecoGiant;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Objet de quete de The Giant (une case) : {@link #GENRE} 0 = fusible dans sa cachette, 1 = boitier du Fly Trap (pilier
 * du couloir), 2 = singe (incinerateur du A), 3 = nounours (balcon, cuve d'eau du B), 4 = arme dans le fourneau
 * (Annihilator), 5 = carte d'acces de Maxis. Etat et animation : {@link ObjetQueteBE}. Petite forme (les balles TaCZ s'y
 * arretent : on peut tirer dessus), rien quand l'objet est cache ou pris. Clic droit = interaction du jeu.
 * <p>D-053 : le fusible prend la place du decor {@code zb_deco_cachette_fusible} de sa case ; son etat de bloc dessine le
 * boitier cuit de la cachette (meme modele, meme collision), le bloc-entite ne dessine que le fusible dans ses pinces.
 */
public class BlocObjetQuete extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty GENRE = IntegerProperty.create("genre", 0, 5);
    public static final int FUSIBLE = 0, BOITIER = 1, SINGE = 2, NOUNOURS = 3, ARME = 4, CARTE = 5;
    private static final VoxelShape PETIT = Block.box(3, 0, 3, 13, 12, 13);

    public BlocObjetQuete(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(GENRE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, GENRE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ObjetQueteBE(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        return level.isClientSide ? createTickerHelper(t, ZbRegistres.OBJET_QUETE_BE.get(), ObjetQueteBE::tickClient) : null;
    }

    /** Fusible dans les pinces de la cachette (centre (0, 8.3, -5.25) px du prop), par face. */
    private static final VoxelShape[] FUSIBLE_CACHETTE = {Shapes.empty(), Shapes.empty(), Shapes.empty(), Shapes.empty()};

    static {
        FormesDecoGiant.ajouter(FUSIBLE_CACHETTE, 6, 5, 1, 10, 11.5, 4.5);
    }

    /** Modele cuit : le boitier de la cachette pour le genre fusible (etat de bloc), rien pour les autres. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        boolean visible = level.getBlockEntity(pos) instanceof ObjetQueteBE be && be.visible();
        if (state.getValue(GENRE) == FUSIBLE) {
            int f = state.getValue(FACING).get2DDataValue();
            FormesDecoGiant.Bloc c = FormesDecoGiant.bloc("zb_deco_cachette_fusible");
            VoxelShape boitier = c == null ? Shapes.empty() : c.forme(0, f);
            return visible ? Shapes.or(boitier, FUSIBLE_CACHETTE[f]) : boitier;
        }
        return visible ? PETIT : Shapes.empty();
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
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return true;
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
