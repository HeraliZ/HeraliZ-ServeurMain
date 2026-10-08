package fr.heralisz.matchs.clashwar.bloc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import javax.annotation.Nullable;

/**
 * Bloc maitre du mur « tableau du tournoi » (coin bas-gauche vu depuis la salle) : porte le bloc-entite dont le rendu
 * dessine tout le tableau sur la face avant du mur (voir {@code clashwar.client.RenduMur}). Pose a la main : l'ecran
 * regarde le joueur et s'etend de {@value MurTableau#LARGEUR_DEFAUT} x {@value MurTableau#HAUTEUR_DEFAUT} blocs vers sa
 * droite et vers le haut (pose refusee si la place manque). {@code /clashwar mur} pose une autre taille.
 */
public class TableauBloc extends HorizontalDirectionalBlock implements EntityBlock {
    public TableauBloc(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getHorizontalDirection().getOpposite();
        if (!MurTableau.libre(ctx.getLevel(), ctx.getClickedPos(), face, MurTableau.LARGEUR_DEFAUT, MurTableau.HAUTEUR_DEFAUT)) return null;
        return defaultBlockState().setValue(FACING, face);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        Direction face = state.getValue(FACING);
        MurTableau.poserBords(level, pos, face, MurTableau.LARGEUR_DEFAUT, MurTableau.HAUTEUR_DEFAUT);
        if (level.getBlockEntity(pos) instanceof TableauBlocEntite be) be.taille(MurTableau.LARGEUR_DEFAUT, MurTableau.HAUTEUR_DEFAUT);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState neuf, boolean deplace) {
        if (!state.is(neuf.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof TableauBlocEntite be) {
            MurTableau.retirerBords(level, pos, state.getValue(FACING), be.largeur(), be.hauteur());
        }
        super.onRemove(state, level, pos, neuf, deplace);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TableauBlocEntite(pos, state);
    }
}
