package fr.heraliz.quetes.bloc;

import fr.heraliz.quetes.HeralizQuetes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Case de droite du recycleur (invisible : le modèle du {@link Recycleur} la dessine) : même forme, clic droit et casse
 * renvoyés au recycleur ; seule, elle disparaît.
 */
public class RecycleurPartie extends HorizontalDirectionalBlock {
    public RecycleurPartie(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    /** Le recycleur, à gauche de cette case (vue de face). */
    private static BlockPos principal(BlockPos pos, Direction face) {
        return pos.relative(face.getCounterClockWise());
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand main, BlockHitResult hit) {
        BlockPos m = principal(pos, s.getValue(FACING));
        BlockState ms = l.getBlockState(m);
        if (!ms.is(HeralizQuetes.RECYCLEUR.get())) return InteractionResult.PASS;
        return ms.use(l, p, main, new BlockHitResult(hit.getLocation(), hit.getDirection(), m, hit.isInside()));
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos pos, BlockState nouveau, boolean deplace) {
        if (!s.is(nouveau.getBlock())) {
            BlockPos m = principal(pos, s.getValue(FACING));
            if (l.getBlockState(m).is(HeralizQuetes.RECYCLEUR.get())) l.removeBlock(m, false);
        }
        super.onRemove(s, l, pos, nouveau, deplace);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState s, HitResult cible, BlockGetter l, BlockPos pos, Player p) {
        return new ItemStack(HeralizQuetes.RECYCLEUR_OBJET.get());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos pos, CollisionContext c) {
        return s.getValue(FACING).getAxis() == Direction.Axis.Z ? Recycleur.NS : Recycleur.EO;
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.INVISIBLE;
    }
}
