package fr.heraliz.quetes.bloc;

import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.pont.Crochet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le recycleur du spawn (25/10, rendus validés) : machine de 2 blocs de large sur 1, posée face au joueur ; la case de
 * droite (vue de face) est un bloc invisible, {@link RecycleurPartie}, qui renvoie clics et casse à celle-ci. Modèle,
 * texture et états ÉCRITS par {@code modules/pnj/outils/recycleur_modele.py --installer}, jamais à la main.
 * <p>
 * Clic droit : le recycleur s'ouvre (mêmes règles que le PNJ recycleur, {@code config/heraliz_quetes/recyclage.json}),
 * décidé par la partie serveur ({@link #OUVRIR}). Incassable en survie, sans butin : le staff le pose et le retire en
 * créatif.
 */
public class Recycleur extends HorizontalDirectionalBlock {
    /** Ouverture demandée (serveur) : branchée par la partie serveur. */
    public static final Crochet<Ouverture> OUVRIR = new Crochet<>();

    public record Ouverture(ServerPlayer joueur, BlockPos pos) {
    }

    /** Forme de chaque case (corps de la machine, 20 px de haut), selon l'orientation. */
    static final VoxelShape NS = Block.box(0, 0, 1, 16, 20, 15), EO = Block.box(1, 0, 0, 15, 20, 16);

    public Recycleur(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    /** Case de droite (vue de face) : le modèle, tourné vers le nord, s'étend vers l'est. */
    public static BlockPos partie(BlockPos pos, Direction face) {
        return pos.relative(face.getClockWise());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction face = c.getHorizontalDirection().getOpposite();
        BlockPos p = partie(c.getClickedPos(), face);
        if (!c.getLevel().getBlockState(p).canBeReplaced(c)) return null;
        return defaultBlockState().setValue(FACING, face);
    }

    @Override
    public void setPlacedBy(Level l, BlockPos pos, BlockState s, net.minecraft.world.entity.LivingEntity qui, net.minecraft.world.item.ItemStack objet) {
        Direction face = s.getValue(FACING);
        l.setBlock(partie(pos, face), HeralizQuetes.RECYCLEUR_PARTIE.get().defaultBlockState().setValue(FACING, face), 3);
    }

    @Override
    public boolean canSurvive(BlockState s, LevelReader l, BlockPos pos) {
        return true;
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos pos, BlockState nouveau, boolean deplace) {
        if (!s.is(nouveau.getBlock())) {
            BlockPos p = partie(pos, s.getValue(FACING));
            if (l.getBlockState(p).is(HeralizQuetes.RECYCLEUR_PARTIE.get())) l.removeBlock(p, false);
        }
        super.onRemove(s, l, pos, nouveau, deplace);
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand main, BlockHitResult hit) {
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (p instanceof ServerPlayer sp) OUVRIR.accept(new Ouverture(sp, pos));
        return InteractionResult.CONSUME;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos pos, CollisionContext c) {
        Direction f = s.getValue(FACING);
        return f.getAxis() == Direction.Axis.Z ? NS : EO;
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }
}
