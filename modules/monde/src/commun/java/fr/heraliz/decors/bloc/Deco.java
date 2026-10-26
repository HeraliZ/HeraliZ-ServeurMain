package fr.heraliz.decors.bloc;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Décors de la map : un modèle fixe (dans le maillage du chunk), une forme de sélection et de collision, un effet
 * d'ambiance. Les sous-classes ajoutent leurs propriétés (orientation, morceau, taille, pose).
 */
public class Deco extends Block {
    protected final Function<BlockState, VoxelShape> forme;
    protected final boolean traversable;
    protected final Effet effet;
    protected final boolean invisible;
    /**
     * Forme de chaque état, calculée une fois (24/10, optimisation) : getShape / getCollisionShape sont appelés à chaque
     * déplacement d'entité (zombies, joueurs), chaque recherche de chemin et chaque visée ; plusieurs formes étaient
     * refaites à chaque appel (Shapes.or + rotation : toilettes, lavabo, lits). Lue seulement après la construction.
     */
    private final Map<BlockState, VoxelShape> formes = new IdentityHashMap<>();

    public Deco(Properties p, Function<BlockState, VoxelShape> forme, boolean traversable, Effet effet) {
        this(p, forme, traversable, effet, false);
    }

    public Deco(Properties p, Function<BlockState, VoxelShape> forme, boolean traversable, Effet effet, boolean invisible) {
        super(p);
        this.forme = forme;
        this.traversable = traversable;
        this.effet = effet;
        this.invisible = invisible;
        // les propriétés des sous-classes sont déjà là (createBlockStateDefinition est appelé par le constructeur de Block)
        for (BlockState s : stateDefinition.getPossibleStates()) formes.put(s, forme.apply(s));
    }

    /** Forme de l'état (calculée d'avance ; secours : calculée à la demande). */
    protected VoxelShape forme(BlockState s) {
        VoxelShape v = formes.get(s);
        return v != null ? v : forme.apply(s);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return forme(s);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return traversable ? Shapes.empty() : forme(s);
    }

    @Override
    public VoxelShape getVisualShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return invisible ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) {
        return 1.0f;
    }

    @Override
    public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
        effet.jouer(s, l, p, r);
    }

    // ------------------------------------------------------------------ formes
    public static VoxelShape boite(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Block.box(x0, y0, z0, x1, y1, z1);
    }

    /** Tourne une forme dessinée face au nord vers la direction d (autour de l'axe vertical). */
    public static VoxelShape tourner(VoxelShape s, Direction d) {
        if (d == Direction.NORTH) {
            return s;
        }
        VoxelShape out = Shapes.empty();
        for (AABB b : s.toAabbs()) {
            AABB r = switch (d) {
                case SOUTH -> new AABB(1 - b.maxX, b.minY, 1 - b.maxZ, 1 - b.minX, b.maxY, 1 - b.minZ);
                case EAST -> new AABB(1 - b.maxZ, b.minY, b.minX, 1 - b.minZ, b.maxY, b.maxX);
                case WEST -> new AABB(b.minZ, b.minY, 1 - b.maxX, b.maxZ, b.maxY, 1 - b.minX);
                default -> b;
            };
            out = Shapes.or(out, Shapes.create(r));
        }
        return out;
    }

    // ------------------------------------------------------------------ décor orienté (face au joueur à la pose)
    public static class Oriente extends Deco {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

        public Oriente(Properties p, Function<BlockState, VoxelShape> forme, boolean traversable, Effet effet) {
            super(p, forme, traversable, effet);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext c) {
            return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
        }

        @Override
        public BlockState rotate(BlockState s, Rotation r) {
            return s.setValue(FACING, r.rotate(s.getValue(FACING)));
        }

        @Override
        public BlockState mirror(BlockState s, Mirror m) {
            return s.rotate(m.getRotation(s.getValue(FACING)));
        }
    }

    // ------------------------------------------------------------------ cristal, stalactite : taille et pose (sol / plafond)
    public static class Pousse extends Deco {
        public Pousse(Properties p, Function<BlockState, VoxelShape> forme, boolean traversable, Effet effet) {
            super(p, forme, traversable, effet);
            registerDefaultState(stateDefinition.any().setValue(Proprietes.TAILLE, Proprietes.Taille.MOYEN)
                    .setValue(Proprietes.POSE2, Proprietes.Pose.SOL));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(Proprietes.TAILLE, Proprietes.POSE2);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext c) {
            return defaultBlockState().setValue(Proprietes.POSE2, c.getClickedFace() == Direction.DOWN ? Proprietes.Pose.PLAFOND
                    : Proprietes.Pose.SOL);
        }
    }

    // ------------------------------------------------------------------ rocher : taille
    public static class Rocher extends Deco {
        public Rocher(Properties p, Function<BlockState, VoxelShape> forme) {
            super(p, forme, false, Effet.AUCUN);
            registerDefaultState(stateDefinition.any().setValue(Proprietes.TAILLE, Proprietes.Taille.MOYEN));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(Proprietes.TAILLE);
        }
    }

    // ------------------------------------------------------------------ décor selon un axe horizontal (guirlande)
    public static class Axe extends Deco {
        public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction.Axis> AXIS =
                BlockStateProperties.HORIZONTAL_AXIS;

        public Axe(Properties p, Function<BlockState, VoxelShape> forme, boolean traversable, Effet effet) {
            super(p, forme, traversable, effet);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(AXIS);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext c) {
            return defaultBlockState().setValue(AXIS, c.getHorizontalDirection().getAxis());
        }
    }

    // ------------------------------------------------------------------ décor en trois tailles (lampe suspendue)
    public static class ParTaille extends Deco {
        public ParTaille(Properties p, Function<BlockState, VoxelShape> forme, boolean traversable, Effet effet) {
            super(p, forme, traversable, effet);
            registerDefaultState(stateDefinition.any().setValue(Proprietes.TAILLE, Proprietes.Taille.MOYEN));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(Proprietes.TAILLE);
        }
    }

    // ------------------------------------------------------------------ lanterne : pose (sol, mur, plafond) et orientation
    public static class Lanterne extends Deco {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

        public Lanterne(Properties p, Function<BlockState, VoxelShape> forme) {
            super(p, forme, false, Effet.AUCUN);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Proprietes.POSE, Proprietes.Pose.SOL));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, Proprietes.POSE);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext c) {
            Direction f = c.getClickedFace();
            Proprietes.Pose pose = f == Direction.UP ? Proprietes.Pose.SOL : f == Direction.DOWN ? Proprietes.Pose.PLAFOND : Proprietes.Pose.MUR;
            Direction face = f.getAxis().isHorizontal() ? f : c.getHorizontalDirection().getOpposite();
            return defaultBlockState().setValue(Proprietes.POSE, pose).setValue(FACING, face);
        }
    }
}
