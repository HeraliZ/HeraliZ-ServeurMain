package fr.heraliz.loot;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import fr.heraliz.loot.pont.Crochet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;

/**
 * Bloc invisible qui remplit le volume d'un objet sur plusieurs blocs (repris du casino) : il connaît le décalage vers
 * le bloc principal (dx, dy, dz), lui transmet les clics et prend sa forme ; le casser casse l'objet entier.
 */
public class PartieBlock extends Block {
    /** Décalage maximal vers le bloc principal : 3 blocs de côté (véhicules de 5 m repris d'ArkaniaZ), 3 de haut. */
    public static final int PORTEE = 3, HAUTEUR = 3;
    public static final IntegerProperty DX = IntegerProperty.create("dx", 0, PORTEE * 2);
    public static final IntegerProperty DY = IntegerProperty.create("dy", 0, HAUTEUR);
    public static final IntegerProperty DZ = IntegerProperty.create("dz", 0, PORTEE * 2);

    public PartieBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(DX, PORTEE).setValue(DY, 0).setValue(DZ, PORTEE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(DX, DY, DZ);
    }

    /** État d'une partie située à (dx, dy, dz) du bloc principal (null hors limites). */
    @Nullable
    public static BlockState pourDecalage(int dx, int dy, int dz) {
        if (Math.abs(dx) > PORTEE || Math.abs(dz) > PORTEE || dy < 0 || dy > HAUTEUR) return null;
        return HeralizLoot.PARTIE.get().defaultBlockState().setValue(DX, dx + PORTEE).setValue(DY, dy).setValue(DZ, dz + PORTEE);
    }

    static int[] decalage(BlockState s) {
        return new int[]{s.getValue(DX) - PORTEE, s.getValue(DY), s.getValue(DZ) - PORTEE};
    }

    public static BlockPos principal(BlockPos pos, BlockState s) {
        int[] o = decalage(s);
        return pos.offset(-o[0], -o[1], -o[2]);
    }

    @Nullable
    private static BlockState maitre(BlockGetter l, BlockPos pos, BlockState s) {
        BlockState m = l.getBlockState(principal(pos, s));
        return m.getBlock() instanceof Encombrant ? m : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand main, BlockHitResult hit) {
        BlockPos mp = principal(pos, s);
        BlockState m = l.getBlockState(mp);
        return m.getBlock() instanceof Encombrant ? m.use(l, p, main, hit.withPosition(mp)) : InteractionResult.PASS;
    }

    @Override
    public void playerWillDestroy(Level l, BlockPos pos, BlockState s, Player p) {
        BlockPos mp = principal(pos, s);
        if (!l.isClientSide && l.getBlockState(mp).getBlock() instanceof Encombrant) l.setBlock(mp, Blocks.AIR.defaultBlockState(), 35);
        super.playerWillDestroy(l, pos, s, p);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemStack getCloneItemStack(BlockGetter l, BlockPos pos, BlockState s) {
        BlockState m = maitre(l, pos, s);
        return m != null ? new ItemStack(m.getBlock()) : ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos pos, CollisionContext ctx) {
        BlockState m = maitre(l, pos, s);
        if (m == null) return Shapes.block();
        int[] o = decalage(s);
        return ((Encombrant) m.getBlock()).encombrement().outline(m.getValue(HorizontalDirectionalBlock.FACING), o[0], o[1], o[2]);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos pos, CollisionContext ctx) {
        BlockState m = maitre(l, pos, s);
        if (m == null) return Shapes.empty();
        int[] o = decalage(s);
        return ((Encombrant) m.getBlock()).encombrement().collision(m.getValue(HorizontalDirectionalBlock.FACING), o[0], o[1], o[2]);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos pos) {
        return 1.0F;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState s, BlockGetter l, BlockPos pos, PathComputationType t) {
        return false;
    }

    /** Pas de particules de coup ni de casse (bloc invisible) : branche par la partie client. */
    public static final Crochet<Consumer<IClientBlockExtensions>> EFFETS_CLIENT = new Crochet<>();

    @Override
    public void initializeClient(Consumer<IClientBlockExtensions> consumer) {
        EFFETS_CLIENT.accept(consumer);
    }
}
