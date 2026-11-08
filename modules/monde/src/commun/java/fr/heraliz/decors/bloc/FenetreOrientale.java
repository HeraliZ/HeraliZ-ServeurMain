package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FenetreOrientale extends Deco {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<FenetreOrientale.Partie> PARTIE = EnumProperty.create("partie", FenetreOrientale.Partie.class);

    public FenetreOrientale(Properties p) {
        this(p, FenetreOrientale.Saillie.AUCUNE);
    }

    public FenetreOrientale(Properties p, FenetreOrientale.Saillie saillie) {
        super(p, s -> tourner(formeNord(saillie, s.getValue(PARTIE)), s.getValue(FACING)), false, Effet.AUCUN);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PARTIE, FenetreOrientale.Partie.SEULE));
    }

    private static VoxelShape formeNord(FenetreOrientale.Saillie saillie, FenetreOrientale.Partie partie) {
        boolean basFin = partie == FenetreOrientale.Partie.SEULE || partie == FenetreOrientale.Partie.BAS;
        boolean hautFin = partie == FenetreOrientale.Partie.SEULE || partie == FenetreOrientale.Partie.HAUT;
        VoxelShape v = boite(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
        if (saillie == FenetreOrientale.Saillie.APPUI && basFin) {
            v = Shapes.or(v, boite(0.0, 0.0, 4.5, 16.0, 1.5, 11.5));
        }

        if (saillie == FenetreOrientale.Saillie.VOLETS) {
            double y0 = basFin ? 0.5 : 0.0;
            double y1 = hautFin ? 15.5 : 16.0;
            double pas = 7.0 * Math.sqrt(0.5) / 5.0;

            for (int i = 0; i < 5; i++) {
                double a = i * pas;
                double b = (i + 1) * pas;
                double z0 = Math.max(0.0, 6.0 - b - 0.7);
                double z1 = 6.0 - a;
                v = Shapes.or(v, boite(Math.max(0.0, 1.0 + a - 0.7), y0, z0, 1.0 + b, y1, z1), boite(15.0 - b, y0, z0, Math.min(16.0, 15.0 - a + 0.7), y1, z1));
            }
        }

        return v.optimize();
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(FACING, PARTIE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction f = c.getHorizontalDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING, f).setValue(PARTIE, this.partie(c.getLevel(), c.getClickedPos(), f));
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState voisin, LevelAccessor l, BlockPos p, BlockPos pv) {
        return d.getAxis() != Axis.Y ? s : s.setValue(PARTIE, this.partie(l, p, s.getValue(FACING)));
    }

    private FenetreOrientale.Partie partie(LevelAccessor l, BlockPos p, Direction f) {
        boolean dessous = this.meme(l.getBlockState(p.below()), f);
        boolean dessus = this.meme(l.getBlockState(p.above()), f);
        return dessous
            ? (dessus ? FenetreOrientale.Partie.MILIEU : FenetreOrientale.Partie.HAUT)
            : (dessus ? FenetreOrientale.Partie.BAS : FenetreOrientale.Partie.SEULE);
    }

    private boolean meme(BlockState s, Direction f) {
        return s.is(this) && s.getValue(FACING) == f;
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    public static enum Partie implements StringRepresentable {
        SEULE,
        BAS,
        MILIEU,
        HAUT;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static enum Saillie {
        AUCUNE,
        APPUI,
        VOLETS;
    }
}
