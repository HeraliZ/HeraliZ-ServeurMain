package fr.heraliz.crac.block;

import fr.heraliz.crac.pont.Serveur;
import fr.heraliz.crac.heraliz.Txt;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.network.C2SRequestNearbyClaims;
import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
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

/**
 * Armoire à outils de la base (Claim Block). Depuis CRac 1.10.0 (modèle « casier militaire » validé le 18/10, écrit par
 * {@code mods/crac/outils/tc_modele.py}) : 1 bloc de large et 2 de haut comme une porte, face avant vers le joueur qui la
 * pose, dos contre le mur. La case du haut est {@link ClaimBlockHaut} (invisible : le modèle de 2 blocs est dessiné par
 * celle du bas), qui renvoie les clics et la casse au bloc du bas.
 */
public class ClaimBlock extends Block implements EntityBlock {
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 1, 3);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Formes du bas, par sens (face avant au nord dans le modèle : x 1 à 15, z 3 à 15,75). */
    private static final VoxelShape[] BAS = formes(1, 3, 15, 15.75);

    public ClaimBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TIER, 1).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(TIER, FACING);
    }

    /** Une boîte pleine haute de 16, tournée pour chaque sens (index {@link Direction#get2DDataValue()}). */
    static VoxelShape[] formes(double x0, double z0, double x1, double z1) {
        VoxelShape[] f = new VoxelShape[4];
        for (Direction d : Direction.Plane.HORIZONTAL) {
            f[d.get2DDataValue()] = switch (d) {
                case SOUTH -> Block.box(16 - x1, 0, 16 - z1, 16 - x0, 16, 16 - z0);
                case WEST -> Block.box(z0, 0, 16 - x1, z1, 16, 16 - x0);
                case EAST -> Block.box(16 - z1, 0, x0, 16 - z0, 16, x1);
                default -> Block.box(x0, 0, z0, x1, 16, z1);
            };
        }
        return f;
    }

    /** Face avant vers le joueur ; il faut aussi la case du dessus libre. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos haut = ctx.getClickedPos().above();
        if (haut.getY() >= ctx.getLevel().getMaxBuildHeight() || !ctx.getLevel().getBlockState(haut).canBeReplaced(ctx)) {
            return null;
        }
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide) {
            poserHaut(level, pos, state);
        }
    }

    /** Pose la case du haut si elle est libre (aussi pour une armoire posée avant la 1.10.0, à son premier usage). */
    public static void poserHaut(Level level, BlockPos pos, BlockState state) {
        BlockPos haut = pos.above();
        BlockState h = level.getBlockState(haut);
        if (h.getBlock() instanceof ClaimBlockHaut) {
            if (h.getValue(FACING) != state.getValue(FACING)) {
                level.setBlock(haut, h.setValue(FACING, state.getValue(FACING)), Block.UPDATE_ALL);
            }
        } else if (h.isAir() || h.canBeReplaced()) {
            level.setBlock(haut, ModBlocks.CLAIM_BLOCK_HAUT.get().defaultBlockState().setValue(FACING, state.getValue(FACING)), Block.UPDATE_ALL);
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
        return BAS[s.getValue(FACING).get2DDataValue()];
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
            // la case du haut part avec l'armoire, sans rien laisser tomber
            if (level.getBlockState(pos.above()).getBlock() instanceof ClaimBlockHaut) {
                level.setBlock(pos.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }

        super.onRemove(state, level, pos, newState, moving);
    }
}
