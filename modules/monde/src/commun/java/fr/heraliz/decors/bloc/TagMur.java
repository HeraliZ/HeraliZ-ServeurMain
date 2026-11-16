package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class TagMur extends Deco {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<TagMur.Motif> MOTIF = EnumProperty.create("motif", TagMur.Motif.class);

    public TagMur(Properties p) {
        super(p, s -> tourner(boite(0.0, 0.0, 15.0, 16.0, 16.0, 16.0), s.getValue(FACING)), true, Effet.AUCUN);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOTIF, TagMur.Motif.VOUS_ETES_MORTS));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(FACING, MOTIF);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction f = c.getClickedFace();
        return this.defaultBlockState().setValue(FACING, f.getAxis().isHorizontal() ? f : c.getHorizontalDirection().getOpposite());
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult r) {
        if (!l.isClientSide) {
            TagMur.Motif[] v = TagMur.Motif.values();
            l.setBlock(p, s.setValue(MOTIF, v[(s.getValue(MOTIF).ordinal() + 1) % v.length]), 3);
        }

        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    public static enum Motif implements StringRepresentable {
        VOUS_ETES_MORTS,
        BON_COURAGE,
        AIDEZ_NOUS,
        NE_PAS_ENTRER,
        ZOMBIES,
        CLOWN,
        CRANE,
        FLECHE,
        CROIX,
        COMPTEUR,
        SURVIVANTS,
        DANGER,
        COUREZ,
        PAS_DE_SORTIE,
        ILS_ENTENDENT,
        INFECTE,
        QUARANTAINE,
        DIEU_NOUS_A_ABANDONNES,
        NOUS_SOMMES_LA,
        NE_PAS_OUVRIR,
        MAINS_SANGLANTES,
        TRACES_DE_GRIFFES,
        CIBLE,
        SOS;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
