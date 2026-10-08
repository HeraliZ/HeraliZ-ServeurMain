package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;

/**
 * Machine Pack-a-Punch façon CoD Black Ops (2 blocs de large centrés sur la case de la carte, 2 de haut ; enseigne au-dessus :
 * 2,85 blocs, 3 blocs de hauteur libre conseillés). Case maîtresse (centre bas) = {@link PapBE}. Son corps fixe est un
 * modèle de bloc cuit dans les tronçons ({@code models/block/zb_pap_corps.json}, chargeur {@code heralisz_matchs:zbquads}) :
 * le rendu de bloc-entité ne dessine que les rouleaux, les lueurs, les halos et l'arme. {@link #ALIMENTE} éclaire les
 * alentours quand le courant est rétabli (et donc le corps cuit, éclairé par le monde).
 */
public class BlocPap extends BlocLarge {
    public static final BooleanProperty ALIMENTE = BooleanProperty.create("alimente");

    public BlocPap(Properties p) {
        super(p, 16, 16, 0, 16);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PARTIE, 0).setValue(ALIMENTE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(ALIMENTE);
    }

    /** Case maîtresse : corps fixe cuit (modèle de bloc) ; le bloc-entité dessine en plus les pièces animées. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PARTIE) == 0 ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PARTIE) == 0 ? new PapBE(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PARTIE) != 0) return null;
        return level.isClientSide ? createTickerHelper(type, ZbRegistres.PAP_BE.get(), PapBE::tickClient)
                : createTickerHelper(type, ZbRegistres.PAP_BE.get(), PapBE::tickServeur);
    }
}
