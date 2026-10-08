package fr.heraliz.loot;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Coffre T7 : bloc orientable dessiné par son entité (animations en code), clic droit = ouvrir. Peut occuper plusieurs
 * blocs (coffre d'armes de deux mètres, largage de 2 x 2) : les autres cellules sont des blocs « partie ».
 */
public class CoffreBlock extends BaseEntityBlock implements Encombrant {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public final TypeCoffre type;
    private final Encombrement encombrement;

    public CoffreBlock(Properties p, TypeCoffre type) {
        super(p);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        float[][] b = new float[type.collision.size()][];
        for (int i = 0; i < b.length; i++) {
            double[] x = type.collision.get(i);
            b[i] = new float[]{(float) x[0], (float) x[1], (float) x[2], (float) x[3], (float) x[4], (float) x[5]};
        }
        encombrement = new Encombrement(b);
    }

    @Override
    public Encombrement encombrement() {
        return encombrement;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getHorizontalDirection().getOpposite();
        return placeLibre(ctx.getLevel(), ctx.getClickedPos(), face, ctx) ? defaultBlockState().setValue(FACING, face) : null;
    }

    @Override
    public void setPlacedBy(Level l, BlockPos pos, BlockState st, @Nullable LivingEntity qui, ItemStack it) {
        super.setPlacedBy(l, pos, st, qui, it);
        if (!l.isClientSide) poserParties(l, pos, st);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState st, Level l, BlockPos pos, BlockState ancien, boolean deplace) {
        super.onPlace(st, l, pos, ancien, deplace);
        if (!l.isClientSide && !ancien.is(st.getBlock())) poserParties(l, pos, st);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState st, Level l, BlockPos pos, BlockState nouveau, boolean deplace) {
        if (!l.isClientSide && !nouveau.is(st.getBlock())) retirerParties(l, pos, st);
        super.onRemove(st, l, pos, nouveau, deplace);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState st, BlockGetter l, BlockPos pos, CollisionContext ctx) {
        return encombrement.outline(st.getValue(FACING), 0, 0, 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState st, BlockGetter l, BlockPos pos, CollisionContext ctx) {
        return encombrement.collision(st.getValue(FACING), 0, 0, 0);
    }

    @Override
    public RenderShape getRenderShape(BlockState st) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return HeralizLoot.COFFRE_BE.get().create(pos, st);   // usine : version serveur si la partie serveur est là
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState st, BlockEntityType<T> t) {
        return createTickerHelper(t, HeralizLoot.COFFRE_BE.get(), l.isClientSide ? CoffreBlockEntity::tickClient
                : CoffreBlockEntity::tickServeur);
    }

    @Override
    public InteractionResult use(BlockState st, Level l, BlockPos pos, Player p, InteractionHand main, BlockHitResult hit) {
        if (!l.isClientSide && p instanceof ServerPlayer sp && l.getBlockEntity(pos) instanceof CoffreBlockEntity be) {
            be.ouvrir(sp);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
