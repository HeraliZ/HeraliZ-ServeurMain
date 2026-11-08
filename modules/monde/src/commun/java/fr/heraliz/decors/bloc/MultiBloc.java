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

public abstract class MultiBloc<E extends Enum<E> & StringRepresentable & MultiBloc.Morceau> extends Deco {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected MultiBloc(Properties p, Function<BlockState, VoxelShape> forme) {
        super(p, forme, false, Effet.AUCUN);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(this.partie(), this.origine()));
    }

    public abstract EnumProperty<E> partie();

    protected abstract E origine();

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(FACING, this.partie());
    }

    public static BlockPos position(BlockPos p, Direction f, MultiBloc.Morceau depuis, MultiBloc.Morceau vers) {
        return p.relative(f.getClockWise(), vers.dx() - depuis.dx()).above(vers.dy() - depuis.dy()).relative(f.getOpposite(), vers.dz() - depuis.dz());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction f = c.getHorizontalDirection().getOpposite();

        for (E q : this.partie().getPossibleValues()) {
            if (q != this.origine() && !c.getLevel().getBlockState(position(c.getClickedPos(), f, this.origine(), q)).canBeReplaced()) {
                return null;
            }
        }

        return this.defaultBlockState().setValue(FACING, f);
    }

    @Override
    public void setPlacedBy(Level l, BlockPos p, BlockState s, LivingEntity e, ItemStack st) {
        if (!l.isClientSide) {
            E ici = s.getValue(this.partie());

            for (E q : this.partie().getPossibleValues()) {
                if (q != ici) {
                    l.setBlock(position(p, s.getValue(FACING), ici, q), s.setValue(this.partie(), q), 3);
                }
            }
        }
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean deplace) {
        super.onRemove(s, l, p, n, deplace);
        if (!l.isClientSide && !n.is(this)) {
            Direction f = s.getValue(FACING);
            E ici = s.getValue(this.partie());

            for (E q : this.partie().getPossibleValues()) {
                if (q != ici) {
                    BlockPos o = position(p, f, ici, q);
                    BlockState so = l.getBlockState(o);
                    if (so.is(this) && so.getValue(FACING) == f && so.<E>getValue(this.partie()) == q) {
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

    public static class Camion extends MultiBloc<MultiBloc.PartieCamion> {
        public static final EnumProperty<MultiBloc.PartieCamion> PARTIE = EnumProperty.create("partie", MultiBloc.PartieCamion.class);

        public Camion(Properties p, Function<BlockState, VoxelShape> forme) {
            super(p, forme);
        }

        @Override
        public EnumProperty<MultiBloc.PartieCamion> partie() {
            return PARTIE;
        }

        protected MultiBloc.PartieCamion origine() {
            return MultiBloc.PartieCamion.R0_G;
        }
    }

    public static class Chariot extends MultiBloc<MultiBloc.PartieChariot> {
        public static final EnumProperty<MultiBloc.PartieChariot> PARTIE = EnumProperty.create("partie", MultiBloc.PartieChariot.class);

        public Chariot(Properties p, Function<BlockState, VoxelShape> forme) {
            super(p, forme);
        }

        @Override
        public EnumProperty<MultiBloc.PartieChariot> partie() {
            return PARTIE;
        }

        protected MultiBloc.PartieChariot origine() {
            return MultiBloc.PartieChariot.AVANT_BAS;
        }
    }

    public static class Colonne extends MultiBloc<MultiBloc.PartieColonne> {
        public static final EnumProperty<MultiBloc.PartieColonne> PARTIE = EnumProperty.create("partie", MultiBloc.PartieColonne.class);

        public Colonne(Properties p, Function<BlockState, VoxelShape> forme) {
            super(p, forme);
        }

        @Override
        public EnumProperty<MultiBloc.PartieColonne> partie() {
            return PARTIE;
        }

        protected MultiBloc.PartieColonne origine() {
            return MultiBloc.PartieColonne.BAS;
        }
    }

    public static class EnginCarre extends MultiBloc<MultiBloc.PartieCarre> {
        public static final EnumProperty<MultiBloc.PartieCarre> PARTIE = EnumProperty.create("partie", MultiBloc.PartieCarre.class);

        public EnginCarre(Properties p, Function<BlockState, VoxelShape> forme) {
            super(p, forme);
        }

        @Override
        public EnumProperty<MultiBloc.PartieCarre> partie() {
            return PARTIE;
        }

        protected MultiBloc.PartieCarre origine() {
            return MultiBloc.PartieCarre.AVANT_GAUCHE;
        }
    }

    public static class EnginLong extends MultiBloc<MultiBloc.PartieLong> {
        public static final EnumProperty<MultiBloc.PartieLong> PARTIE = EnumProperty.create("partie", MultiBloc.PartieLong.class);

        public EnginLong(Properties p, Function<BlockState, VoxelShape> forme) {
            super(p, forme);
        }

        @Override
        public EnumProperty<MultiBloc.PartieLong> partie() {
            return PARTIE;
        }

        protected MultiBloc.PartieLong origine() {
            return MultiBloc.PartieLong.AVANT;
        }
    }

    public static class Lampadaire extends MultiBloc<MultiBloc.PartieLampadaire> {
        public static final EnumProperty<MultiBloc.PartieLampadaire> PARTIE = EnumProperty.create("partie", MultiBloc.PartieLampadaire.class);

        public Lampadaire(Properties p, Function<BlockState, VoxelShape> forme) {
            super(p, forme);
        }

        @Override
        public EnumProperty<MultiBloc.PartieLampadaire> partie() {
            return PARTIE;
        }

        protected MultiBloc.PartieLampadaire origine() {
            return MultiBloc.PartieLampadaire.PIED;
        }
    }

    public interface Morceau {
        int dx();

        int dy();

        int dz();
    }

    public static class Nacelle extends MultiBloc<MultiBloc.PartieNacelle> {
        public static final EnumProperty<MultiBloc.PartieNacelle> PARTIE = EnumProperty.create("partie", MultiBloc.PartieNacelle.class);

        public Nacelle(Properties p, Function<BlockState, VoxelShape> forme) {
            super(p, forme);
        }

        @Override
        public EnumProperty<MultiBloc.PartieNacelle> partie() {
            return PARTIE;
        }

        protected MultiBloc.PartieNacelle origine() {
            return MultiBloc.PartieNacelle.BAS_AVANT_GAUCHE;
        }
    }

    public static enum PartieCamion implements StringRepresentable, MultiBloc.Morceau {
        R0_G(0, 0, 0),
        R0_D(1, 0, 0),
        R1_G(0, 0, 1),
        R1_D(1, 0, 1),
        R2_G(0, 0, 2),
        R2_D(1, 0, 2),
        R3_G(0, 0, 3),
        R3_D(1, 0, 3),
        R4_G(0, 0, 4),
        R4_D(1, 0, 4),
        R5_G(0, 0, 5),
        R5_D(1, 0, 5),
        R6_G(0, 0, 6),
        R6_D(1, 0, 6),
        R7_G(0, 0, 7),
        R7_D(1, 0, 7),
        H0_G(0, 1, 0),
        H0_D(1, 1, 0),
        H1_G(0, 1, 1),
        H1_D(1, 1, 1);

        private final int x;
        private final int y;
        private final int z;

        private PartieCamion(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int dx() {
            return this.x;
        }

        @Override
        public int dy() {
            return this.y;
        }

        @Override
        public int dz() {
            return this.z;
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static enum PartieCarre implements StringRepresentable, MultiBloc.Morceau {
        AVANT_GAUCHE(0, 0, 0),
        AVANT_DROITE(1, 0, 0),
        ARRIERE_GAUCHE(0, 0, 1),
        ARRIERE_DROITE(1, 0, 1);

        private final int x;
        private final int y;
        private final int z;

        private PartieCarre(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int dx() {
            return this.x;
        }

        @Override
        public int dy() {
            return this.y;
        }

        @Override
        public int dz() {
            return this.z;
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static enum PartieChariot implements StringRepresentable, MultiBloc.Morceau {
        AVANT_BAS(0, 0, 0),
        AVANT_HAUT(0, 1, 0),
        ARRIERE_BAS(0, 0, 1),
        ARRIERE_HAUT(0, 1, 1);

        private final int x;
        private final int y;
        private final int z;

        private PartieChariot(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int dx() {
            return this.x;
        }

        @Override
        public int dy() {
            return this.y;
        }

        @Override
        public int dz() {
            return this.z;
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static enum PartieColonne implements StringRepresentable, MultiBloc.Morceau {
        BAS(0, 0, 0),
        HAUT(0, 1, 0);

        private final int x;
        private final int y;
        private final int z;

        private PartieColonne(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int dx() {
            return this.x;
        }

        @Override
        public int dy() {
            return this.y;
        }

        @Override
        public int dz() {
            return this.z;
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static enum PartieLampadaire implements StringRepresentable, MultiBloc.Morceau {
        PIED(0, 0, 0),
        FUT(0, 0, -1),
        TETE(0, 0, -2);

        private final int x;
        private final int y;
        private final int z;

        private PartieLampadaire(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int dx() {
            return this.x;
        }

        @Override
        public int dy() {
            return this.y;
        }

        @Override
        public int dz() {
            return this.z;
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static enum PartieLong implements StringRepresentable, MultiBloc.Morceau {
        AVANT(0, 0, 0),
        ARRIERE(0, 0, 1);

        private final int x;
        private final int y;
        private final int z;

        private PartieLong(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int dx() {
            return this.x;
        }

        @Override
        public int dy() {
            return this.y;
        }

        @Override
        public int dz() {
            return this.z;
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static enum PartieNacelle implements StringRepresentable, MultiBloc.Morceau {
        BAS_AVANT_GAUCHE(0, 0, 0),
        BAS_AVANT_DROITE(1, 0, 0),
        BAS_ARRIERE_GAUCHE(0, 0, 1),
        BAS_ARRIERE_DROITE(1, 0, 1),
        HAUT_AVANT_GAUCHE(0, 1, 0),
        HAUT_AVANT_DROITE(1, 1, 0),
        HAUT_ARRIERE_GAUCHE(0, 1, 1),
        HAUT_ARRIERE_DROITE(1, 1, 1);

        private final int x;
        private final int y;
        private final int z;

        private PartieNacelle(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int dx() {
            return this.x;
        }

        @Override
        public int dy() {
            return this.y;
        }

        @Override
        public int dz() {
            return this.z;
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static enum PartieVoiture implements StringRepresentable, MultiBloc.Morceau {
        R0_G,
        R0_D,
        R1_G,
        R1_D,
        R2_G,
        R2_D,
        R3_G,
        R3_D;

        @Override
        public int dx() {
            return this.ordinal() % 2;
        }

        @Override
        public int dy() {
            return 0;
        }

        @Override
        public int dz() {
            return this.ordinal() / 2;
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static class Voiture extends MultiBloc<MultiBloc.PartieVoiture> {
        public static final EnumProperty<MultiBloc.PartieVoiture> PARTIE = EnumProperty.create("partie", MultiBloc.PartieVoiture.class);

        public Voiture(Properties p, Function<BlockState, VoxelShape> forme) {
            super(p, forme);
        }

        @Override
        public EnumProperty<MultiBloc.PartieVoiture> partie() {
            return PARTIE;
        }

        protected MultiBloc.PartieVoiture origine() {
            return MultiBloc.PartieVoiture.R0_G;
        }
    }
}
