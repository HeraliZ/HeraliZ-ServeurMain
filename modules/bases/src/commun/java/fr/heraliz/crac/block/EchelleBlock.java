package fr.heraliz.crac.block;

import fr.heraliz.crac.block.entity.EchelleBlockEntity;
import fr.heraliz.crac.build.logic.PlacementRules;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.world.Occupant;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.build.world.StructureBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Échelle de raid façon Rust (demande du propriétaire du 03/11) : se pose contre une pièce de base, dans n'importe quelle
 * base (la sienne ou celle d'un autre clan : c'est son but pendant un raid), jamais ailleurs ; on y grimpe comme à une
 * échelle de Minecraft. Une case par objet, empilable : un étage de base (3 blocs) se monte avec 3 échelles, plus une au-dessus
 * du mur pour passer sur le toit.
 * <ul>
 *   <li><b>Où</b> ({@link #pose}) : contre la face d'un mur droit sans porte, dans la case de ce mur (le mur lui laisse sa
 *   moitié, comme aux coffres : {@code StructureService.ceder}, même règle d'enfouissement ; le mur paraît toujours plein),
 *   dedans comme dehors ; contre le flanc plein d'une fondation (case libre) ; au-dessus d'une échelle (la même
 *   colonne, même sens), au plus une case au-dessus de la dernière échelle tenue par une pièce (pour passer le bord d'un
 *   toit, pas de tour d'échelles).</li>
 *   <li><b>Tient</b> ({@link #canSurvive}) tant que la case derrière elle est une case de construction (ou, pour la case du
 *   dessus, l'échelle tenue juste dessous) : mur ou fondation détruits, elle tombe, sans rien lâcher.</li>
 *   <li><b>Casse</b> : à la main et aux balles, comme la barricade (150 PV, au moins 15 par coup de poing ou d'arme de
 *   mêlée, les balles font leurs dégâts) ; incassable autrement (ni pioche ni explosion de Minecraft). Rien n'est lâché.
 *   Côté serveur : {@code placable/EchelleServeur}.</li>
 * </ul>
 * Modèle provisoire (texture de l'échelle de Minecraft), en attente du modèle des bases validé en rendus.
 */
public class EchelleBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Posée dans la case d'un mur : la face du mur est à 2 px du bord de la case (moitié du mur dessinée par sa voisine). */
    public static final BooleanProperty MUR = BooleanProperty.create("mur");
    /** Épaisseur de l'échelle (px), comme celle de Minecraft. */
    public static final double EPAISSEUR = 3.0;
    /** Pose en cours décidée par {@link fr.heraliz.crac.item.EchelleItem} : l'état à donner au bloc. */
    public static final ThreadLocal<BlockState> POSE = new ThreadLocal<>();
    /** [sens 2D][mur ? 1 : 0] */
    private static final VoxelShape[][] FORMES = new VoxelShape[4][2];

    static {
        for (Direction f : Direction.Plane.HORIZONTAL) {
            for (int m = 0; m < 2; m++) {
                double a = m == 1 ? fr.heraliz.crac.build.geom.PieceSolids.WALL_HALF : 0.0;   // écart au bord, côté mur
                double b = a + EPAISSEUR;
                FORMES[f.get2DDataValue()][m] = switch (f) {
                    case NORTH -> Block.box(1, 0, 16 - b, 15, 16, 16 - a);   // mur au sud
                    case SOUTH -> Block.box(1, 0, a, 15, 16, b);             // mur au nord
                    case EAST -> Block.box(a, 0, 1, b, 16, 15);              // mur à l'ouest
                    default -> Block.box(16 - b, 0, 1, 16 - a, 16, 15);     // mur à l'est
                };
            }
        }
    }

    public EchelleBlock(Properties p) {
        super(p);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MUR, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, MUR);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMES[s.getValue(FACING).get2DDataValue()][s.getValue(MUR) ? 1 : 0];
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** On y grimpe comme à une échelle de Minecraft. */
    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
    }

    /** Posée seulement par son objet ({@link #POSE}). */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return POSE.get();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return tenue(level, pos, state) || appuyee(level, pos, state);
    }

    /** Tenue par une pièce : la case derrière elle est une case de construction. */
    static boolean tenue(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).getBlock() instanceof StructureBlock;
    }

    /** Juste au-dessus d'une échelle tenue par une pièce, même sens. */
    static boolean appuyee(BlockGetter level, BlockPos pos, BlockState state) {
        BlockState dessous = level.getBlockState(pos.below());
        return dessous.getBlock() instanceof EchelleBlock && dessous.getValue(FACING) == state.getValue(FACING)
            && tenue(level, pos.below(), dessous);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState voisin, LevelAccessor level, BlockPos pos, BlockPos voisinPos) {
        return this.canSurvive(state, level, pos) ? state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EchelleBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------- pose

    /** Où l'échelle visée se pose : case, sens (vers le joueur), dans la case d'un mur (à céder) ou non. */
    public record Pose(BlockPos pos, Direction facing, boolean mur) {
        public BlockState etat(Block b) {
            return b.defaultBlockState().setValue(FACING, this.facing).setValue(MUR, this.mur);
        }
    }

    /** La place de l'échelle pour ce clic, ou null (refusée). Mêmes règles chez le joueur et au serveur. */
    @Nullable
    public static Pose pose(BlockGetter level, Player p, BlockHitResult hit, BlockPos cliquee) {
        BlockState vise = level.getBlockState(hit.getBlockPos());
        if (vise.getBlock() instanceof EchelleBlock) {
            // au-dessus de la colonne d'échelles visée, même sens
            Direction f = vise.getValue(FACING);
            BlockPos c = hit.getBlockPos().above();
            for (int i = 0; i < 64 && level.getBlockState(c).getBlock() instanceof EchelleBlock; i++) {
                c = c.above();
            }
            return suite(level, c, f);
        }
        Direction face = hit.getDirection();
        if (!face.getAxis().isHorizontal()) {
            return null;
        }
        // la face d'un mur : la case touchée (un pas dans la face depuis le point visé) est la case du mur
        Vec3 v = hit.getLocation();
        BlockPos touchee = BlockPos.containing(v.x - face.getStepX() * 1.0E-3, v.y, v.z - face.getStepZ() * 1.0E-3);
        if (caseMur(level, touchee, face.getOpposite())) {
            return new Pose(touchee.immutable(), face, true);
        }
        // le flanc plein d'une fondation (ou de toute case de construction dont la forme touche ce bord) : case libre devant
        if (vise.getBlock() instanceof StructureBlock && level.getBlockState(cliquee).canBeReplaced()
            && touchePlein(level, hit.getBlockPos(), face, v)) {
            return new Pose(cliquee.immutable(), face, false);
        }
        return null;
    }

    /** La case {@code c} au-dessus d'une colonne d'échelles de sens {@code f} : case de mur, ou case libre appuyée. */
    @Nullable
    private static Pose suite(BlockGetter level, BlockPos c, Direction f) {
        if (caseMur(level, c, f.getOpposite())) {
            return new Pose(c.immutable(), f, true);
        }
        BlockState ici = level.getBlockState(c);
        if (!ici.canBeReplaced()) {
            return null;
        }
        BlockState essai = fr.heraliz.crac.registry.ModBlocks.ECHELLE.get().defaultBlockState().setValue(FACING, f);
        if (tenue(level, c, essai)) {
            return new Pose(c.immutable(), f, false);
        }
        return appuyee(level, c, essai) ? new Pose(c.immutable(), f, false) : null;
    }

    /** La forme de la case de construction {@code pos} va jusqu'au bord {@code face} au point visé (un flanc plein). */
    private static boolean touchePlein(BlockGetter level, BlockPos pos, Direction face, Vec3 v) {
        double bord = face.getAxis() == Direction.Axis.X ? pos.getX() + (face == Direction.EAST ? 1.0 : 0.0)
            : pos.getZ() + (face == Direction.SOUTH ? 1.0 : 0.0);
        double ici = face.getAxis() == Direction.Axis.X ? v.x : v.z;
        return Math.abs(ici - bord) < 1.0E-3;
    }

    /**
     * La case {@code cell} est une case de mur que l'échelle peut prendre, dos au mur {@code dos} : elle ne tient que des
     * murs droits sans porte, dont un a son plan sur le bord {@code dos} de la case et aucun sur le bord opposé (devant
     * l'échelle) ; chacun peut la laisser (son autre moitié ferme le bord : règle d'enfouissement, comme aux coffres).
     */
    public static boolean caseMur(BlockGetter level, BlockPos cell, Direction dos) {
        if (!(level.getBlockState(cell).getBlock() instanceof StructureBlock)
            || !(level.getBlockEntity(cell) instanceof StructureBlockEntity be)) {
            return false;
        }
        List<Occupant> occ = be.occupants();
        if (occ.isEmpty()) {
            return false;
        }
        boolean contre = false;
        for (Occupant o : occ) {
            PieceKey k = o.key();
            if (!k.kind().isWall() || k.kind().hasDoor() || k.orient() % 3 != 0) {
                return false;
            }
            boolean versZ = k.orient() == 3;
            double plan = versZ ? k.origin().getX() * 16.0 + k.anchor().toX() : k.origin().getZ() * 16.0 + k.anchor().toZ();
            double bord = versZ ? cell.getX() * 16.0 : cell.getZ() * 16.0;
            Direction cote;
            if (Math.abs(plan - bord) < 1e-6) {
                cote = versZ ? Direction.WEST : Direction.NORTH;
            } else if (Math.abs(plan - bord - 16.0) < 1e-6) {
                cote = versZ ? Direction.EAST : Direction.SOUTH;
            } else {
                return false;
            }
            if (cote == dos) {
                contre = true;
            } else if (cote == dos.getOpposite()) {
                return false;   // un mur devant l'échelle
            }
            long id = o.pieceId();
            if (!PlacementRules.mayBury(k, cell, m -> tientLaPiece(level, m, id))) {
                return false;
            }
        }
        return contre;
    }

    private static boolean tientLaPiece(BlockGetter level, BlockPos pos, long id) {
        if (!(level.getBlockEntity(pos) instanceof StructureBlockEntity be)) {
            return false;
        }
        for (Occupant o : be.occupants()) {
            if (o.pieceId() == id) {
                return true;
            }
        }
        return false;
    }
}
