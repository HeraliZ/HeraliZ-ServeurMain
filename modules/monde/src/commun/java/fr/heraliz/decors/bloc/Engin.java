package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import java.util.Locale;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

public class Engin extends Deco {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Engin.Partie> PARTIE = EnumProperty.create("partie", Engin.Partie.class);

    public Engin(Properties p, Function<BlockState, VoxelShape> forme) {
        super(p, forme, false, Effet.AUCUN);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PARTIE, Engin.Partie.AVANT_GAUCHE));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(FACING, PARTIE);
    }

    public static BlockPos position(BlockPos pos, Direction f, Engin.Partie depuis, Engin.Partie vers) {
        return pos.relative(f.getClockWise(), vers.col - depuis.col).relative(f.getOpposite(), vers.rang - depuis.rang);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction f = c.getHorizontalDirection().getOpposite();

        for (Engin.Partie q : Engin.Partie.values()) {
            if (q != Engin.Partie.AVANT_GAUCHE && !c.getLevel().getBlockState(position(c.getClickedPos(), f, Engin.Partie.AVANT_GAUCHE, q)).canBeReplaced()) {
                return null;
            }
        }

        return this.defaultBlockState().setValue(FACING, f);
    }

    @Override
    public void setPlacedBy(Level l, BlockPos p, BlockState s, LivingEntity e, ItemStack st) {
        if (!l.isClientSide) {
            for (Engin.Partie q : Engin.Partie.values()) {
                if (q != s.getValue(PARTIE)) {
                    l.setBlock(position(p, s.getValue(FACING), s.getValue(PARTIE), q), s.setValue(PARTIE, q), 3);
                }
            }
        }
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean deplace) {
        super.onRemove(s, l, p, n, deplace);
        if (!l.isClientSide && !n.is(this)) {
            Direction f = s.getValue(FACING);

            for (Engin.Partie q : Engin.Partie.values()) {
                if (q != s.getValue(PARTIE)) {
                    BlockPos o = position(p, f, s.getValue(PARTIE), q);
                    BlockState so = l.getBlockState(o);
                    if (so.is(this) && so.getValue(FACING) == f && so.getValue(PARTIE) == q) {
                        l.setBlock(o, Blocks.AIR.defaultBlockState(), 35);
                    }
                }
            }
        }
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s;
    }

    public static enum Partie implements StringRepresentable {
        AVANT_GAUCHE(0, 0),
        AVANT_DROITE(1, 0),
        MILIEU_GAUCHE(0, 1),
        MILIEU_DROITE(1, 1),
        ARRIERE_GAUCHE(0, 2),
        ARRIERE_DROITE(1, 2);

        public final int col;
        public final int rang;

        private Partie(int col, int rang) {
            this.col = col;
            this.rang = rang;
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
