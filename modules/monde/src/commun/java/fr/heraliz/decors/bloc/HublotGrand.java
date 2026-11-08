package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.Shapes;

public class HublotGrand extends Deco {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public HublotGrand(Properties p) {
        super(p, s -> Shapes.block(), false, Effet.AUCUN);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Proprietes.QUART, Proprietes.Quart.SO));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(FACING, Proprietes.QUART);
    }

    private static int col(Proprietes.Quart q) {
        return q != Proprietes.Quart.NE && q != Proprietes.Quart.SE ? 0 : 1;
    }

    private static int rang(Proprietes.Quart q) {
        return q != Proprietes.Quart.NO && q != Proprietes.Quart.NE ? 0 : 1;
    }

    public static BlockPos position(BlockPos p, Direction f, Proprietes.Quart depuis, Proprietes.Quart vers) {
        return p.relative(f.getClockWise(), col(vers) - col(depuis)).above(rang(vers) - rang(depuis));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction f = c.getHorizontalDirection().getOpposite();

        for (Proprietes.Quart q : Proprietes.Quart.values()) {
            if (q != Proprietes.Quart.SO && !c.getLevel().getBlockState(position(c.getClickedPos(), f, Proprietes.Quart.SO, q)).canBeReplaced()) {
                return null;
            }
        }

        return this.defaultBlockState().setValue(FACING, f);
    }

    @Override
    public void setPlacedBy(Level l, BlockPos p, BlockState s, LivingEntity e, ItemStack st) {
        if (!l.isClientSide) {
            for (Proprietes.Quart q : Proprietes.Quart.values()) {
                if (q != s.getValue(Proprietes.QUART)) {
                    l.setBlock(position(p, s.getValue(FACING), s.getValue(Proprietes.QUART), q), s.setValue(Proprietes.QUART, q), 3);
                }
            }
        }
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean deplace) {
        super.onRemove(s, l, p, n, deplace);
        if (!l.isClientSide && !n.is(this)) {
            Direction f = s.getValue(FACING);

            for (Proprietes.Quart q : Proprietes.Quart.values()) {
                if (q != s.getValue(Proprietes.QUART)) {
                    BlockPos o = position(p, f, s.getValue(Proprietes.QUART), q);
                    BlockState so = l.getBlockState(o);
                    if (so.is(this) && so.getValue(FACING) == f && so.getValue(Proprietes.QUART) == q) {
                        l.setBlock(o, Blocks.AIR.defaultBlockState(), 35);
                    }
                }
            }
        }
    }

    private boolean meme(BlockGetter l, BlockPos q, Direction f) {
        BlockState s = l.getBlockState(q);
        return s.is(this) && s.getValue(FACING) == f;
    }

    private boolean valide(BlockGetter l, BlockPos p, Direction f, Proprietes.Quart q) {
        BlockPos h = p.relative(col(q) == 0 ? f.getClockWise() : f.getCounterClockWise());
        int dy = rang(q) == 0 ? 1 : -1;
        return this.meme(l, h, f) && this.meme(l, p.above(dy), f) && this.meme(l, h.above(dy), f);
    }

    private BlockState corrige(BlockGetter l, BlockPos p, BlockState s) {
        Direction f = s.getValue(FACING);
        if (this.valide(l, p, f, s.getValue(Proprietes.QUART))) {
            return s;
        } else {
            for (Proprietes.Quart q : Proprietes.Quart.values()) {
                if (this.valide(l, p, f, q)) {
                    return s.setValue(Proprietes.QUART, q);
                }
            }

            return s;
        }
    }

    @Override
    public boolean isRandomlyTicking(BlockState s) {
        return true;
    }

    @Override
    public void randomTick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        BlockState c = this.corrige(l, p, s);
        if (c != s) {
            l.setBlock(p, c, 2);
        }
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState voisin, LevelAccessor l, BlockPos p, BlockPos pv) {
        return this.corrige(l, p, s);
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s;
    }
}
