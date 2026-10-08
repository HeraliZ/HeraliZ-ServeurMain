package fr.heraliz.crac.block;

import fr.heraliz.crac.pont.Serveur;
import fr.heraliz.crac.heraliz.Txt;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.network.C2SRequestNearbyClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public class ClaimBlock extends Block implements EntityBlock {
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 1, 3);
    private static final VoxelShape BASE = Block.box(4.0, 0.0, 4.0, 12.0, 4.8, 12.0);
    private static final VoxelShape ROD = Block.box(6.4, 0.0, 6.4, 9.6, 28.0, 9.6);
    private static final VoxelShape SHAPE = Shapes.or(BASE, ROD);

    public ClaimBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TIER, 1));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(TIER);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClaimBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            Serveur.Moteur m = Serveur.MOTEUR;   // claim, droits, amélioration, stock : partie serveur (ClaimBlockLogique)
            return m != null ? m.claimUtiliser(this, state, sl, pos, sp, hand) : InteractionResult.CONSUME;
        } else {
            return InteractionResult.CONSUME;
        }
    }





    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ClaimBlockEntity be) {
                for (ItemStack drop : be.drainToStacks()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                }
            }

            Serveur.Moteur m = Serveur.MOTEUR;
            if (level instanceof ServerLevel sl && m != null) {
                m.claimRetire(sl, pos);
            }
        }

        super.onRemove(state, level, pos, newState, moving);
    }




}
