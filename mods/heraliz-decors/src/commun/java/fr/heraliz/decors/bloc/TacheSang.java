package fr.heraliz.decors.bloc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tache de sang : une seule image à plat (un quadrilatère découpé, dans le maillage du chunk), au sol ou contre un mur.
 * Huit formes ; FACING tourne l'image au sol et, au mur, dit vers où elle regarde (le mur est derrière). Aucune
 * collision (on marche dessus, les balles passent), se remplace comme de l'herbe, disparaît si son support est cassé.
 * En créatif, un clic droit passe à la forme suivante.
 */
public class TacheSang extends Block {
    public enum Forme implements StringRepresentable {
        PETITE_FLAQUE("petite_flaque"), GRANDE_FLAQUE("grande_flaque"), ECLABOUSSURES("eclaboussures"), TRAINEE("trainee"),
        GOUTTES("gouttes"), EMPREINTES("empreintes"), MAIN("main"), PROJECTION("projection");
        private final String n;
        Forme(String n) { this.n = n; }
        @Override public String getSerializedName() { return n; }
    }

    public static final EnumProperty<Forme> FORME = EnumProperty.create("forme", Forme.class);
    public static final EnumProperty<Proprietes.Pose> POSE = Proprietes.POSE_SOL_MUR;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SOL = Deco.boite(0, 0, 0, 16, 0.5, 16);
    private static final VoxelShape[] MUR = new VoxelShape[4];
    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            MUR[d.get2DDataValue()] = Deco.tourner(Deco.boite(0, 0, 15.5, 16, 16, 16), d);
        }
    }

    public TacheSang(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FORME, Forme.PETITE_FLAQUE).setValue(POSE, Proprietes.Pose.SOL)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FORME, POSE, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction f = c.getClickedFace();
        if (f == Direction.DOWN) {
            return null;                                                     // pas de tache au plafond
        }
        BlockState s = f == Direction.UP
                ? defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite())
                : defaultBlockState().setValue(POSE, Proprietes.Pose.MUR).setValue(FORME, Forme.PROJECTION).setValue(FACING, f);
        return s.canSurvive(c.getLevel(), c.getClickedPos()) ? s : null;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
        if (!j.getAbilities().instabuild || !j.getItemInHand(m).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!l.isClientSide) {
            Forme[] f = Forme.values();
            l.setBlock(p, s.setValue(FORME, f[(s.getValue(FORME).ordinal() + 1) % f.length]), Block.UPDATE_CLIENTS);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    /** Le bloc qui porte la tache : dessous au sol, derrière au mur. */
    private static Direction support(BlockState s) {
        return s.getValue(POSE) == Proprietes.Pose.SOL ? Direction.DOWN : s.getValue(FACING).getOpposite();
    }

    @Override
    public boolean canSurvive(BlockState s, LevelReader l, BlockPos p) {
        Direction d = support(s);
        BlockPos q = p.relative(d);
        return l.getBlockState(q).isFaceSturdy(l, q, d.getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos p, BlockPos np) {
        return d == support(s) && !s.canSurvive(l, p) ? Blocks.AIR.defaultBlockState() : s;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(POSE) == Proprietes.Pose.SOL ? SOL : MUR[s.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getVisualShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
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
