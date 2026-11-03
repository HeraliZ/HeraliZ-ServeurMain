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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Échelle de raid façon Rust (demande du propriétaire du 03/11) : se pose contre un mur de base, dans n'importe quelle
 * base (la sienne ou celle d'un autre clan : c'est son but pendant un raid), jamais ailleurs ; on y grimpe comme à une
 * échelle de Minecraft.
 * <ul>
 *   <li><b>Taille</b> (validée le 03/11) : une échelle = la hauteur d'un mur (un étage, {@link #HAUTEUR} blocs), posée
 *   d'un coup ; pour deux étages, deux échelles (la 2e se pose au-dessus de la 1re, en visant celle-ci). Le dernier
 *   barreau est en haut du mur : on passe sur le toit ou à l'étage sans échelle de plus. Trois cases, une par partie
 *   ({@link #PARTIE}) ; la partie du bas garde les PV ({@link EchelleBlockEntity}), un coup sur une autre partie compte
 *   pour toute l'échelle.</li>
 *   <li><b>Où</b> ({@link #pose}) : contre la face d'un mur droit sans porte, dans les trois cases de ce mur, de son pied
 *   à son sommet (le mur lui laisse ces moitiés de case, comme aux coffres : {@code StructureService.ceder}, même règle
 *   d'enfouissement ; le mur paraît toujours plein), dedans comme dehors ; une case tenue par autre chose qu'un mur (un
 *   plancher au-dessus, côté intérieur) refuse la pose. Pas contre un mur en biais (côté en biais d'un triangle) : voir
 *   le compte rendu du 03/11 (un modèle de bloc ne tourne que par pas de 22,5°, ces murs sont à 30° ou 60°).</li>
 *   <li><b>Tient</b> ({@link #canSurvive}) tant que chaque case derrière elle est une case de construction : un mur
 *   détruit, l'échelle tombe (toute), sans rien lâcher, et le mur reprend ses cases.</li>
 *   <li><b>Casse</b> : à la main et aux balles, comme la barricade (150 PV), et d'un coup au C4 et à la charge explosive ;
 *   incassable autrement (ni pioche ni autre explosion). Rien n'est lâché. Côté serveur : {@code placable/EchelleServeur}.
 *   </li>
 *   <li><b>Sur l'échelle</b> : on ne tire pas avec les armes de TaCZ ({@link EchelleTirs}) ; le reste est permis.</li>
 * </ul>
 */
public class EchelleBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Hauteur d'une échelle en cases : celle d'un mur de base (48 px, PieceSolids.CELL). */
    public static final int HAUTEUR = 3;
    /** Partie de l'échelle : 0 en bas (garde les PV), {@link #HAUTEUR} - 1 en haut. */
    public static final IntegerProperty PARTIE = IntegerProperty.create("partie", 0, HAUTEUR - 1);
    /** Épaisseur de l'échelle (px), comme celle de Minecraft. */
    public static final double EPAISSEUR = 3.0;
    /** Pose en cours décidée par {@link fr.heraliz.crac.item.EchelleItem} : l'état de la partie du bas. */
    public static final ThreadLocal<BlockState> POSE = new ThreadLocal<>();
    /** [sens 2D] : dans la case d'un mur, la face du mur est à 2 px du bord (moitié du mur dessinée par sa voisine). */
    private static final VoxelShape[] FORMES = new VoxelShape[4];

    static {
        double a = fr.heraliz.crac.build.geom.PieceSolids.WALL_HALF;   // écart au bord, côté mur
        double b = a + EPAISSEUR;
        for (Direction f : Direction.Plane.HORIZONTAL) {
            FORMES[f.get2DDataValue()] = switch (f) {
                case NORTH -> Block.box(1, 0, 16 - b, 15, 16, 16 - a);   // mur au sud
                case SOUTH -> Block.box(1, 0, a, 15, 16, b);             // mur au nord
                case EAST -> Block.box(a, 0, 1, b, 16, 15);              // mur à l'ouest
                default -> Block.box(16 - b, 0, 1, 16 - a, 16, 15);     // mur à l'est
            };
        }
    }

    public EchelleBlock(Properties p) {
        super(p);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PARTIE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PARTIE);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMES[s.getValue(FACING).get2DDataValue()];
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

    /** La partie du bas est posée par le chemin habituel (règles de la base) ; les deux autres la suivent aussitôt. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide && state.getValue(PARTIE) == 0) {
            for (int i = 1; i < HAUTEUR; i++) {
                level.setBlock(pos.above(i), state.setValue(PARTIE, i), Block.UPDATE_ALL);
            }
        }
    }

    /** Tenue par une pièce : la case derrière elle est une case de construction (l'autre moitié du mur). */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).getBlock() instanceof StructureBlock;
    }

    /** Une partie voisine manque (cassée, effacée) ou le mur derrière est parti : celle-ci part aussi. */
    @Override
    public BlockState updateShape(BlockState s, Direction dir, BlockState voisin, LevelAccessor level, BlockPos pos, BlockPos voisinPos) {
        int p = s.getValue(PARTIE);
        if (dir == Direction.DOWN && p > 0 && !this.suite(s, voisin, p - 1)
            || dir == Direction.UP && p < HAUTEUR - 1 && !this.suite(s, voisin, p + 1)) {
            return Blocks.AIR.defaultBlockState();
        }
        return this.canSurvive(s, level, pos) ? s : Blocks.AIR.defaultBlockState();
    }

    private boolean suite(BlockState s, BlockState voisin, int partie) {
        return voisin.is(this) && voisin.getValue(PARTIE) == partie && voisin.getValue(FACING) == s.getValue(FACING);
    }

    /** La case de la partie du bas de l'échelle dont {@code s} est une partie. */
    public static BlockPos bas(BlockState s, BlockPos pos) {
        return pos.below(s.getValue(PARTIE));
    }

    /** Partie retirée : les autres partent avec elle (sans rien lâcher), et le mur reprend chaque case au tick suivant. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            fr.heraliz.crac.pont.Serveur.Moteur m = fr.heraliz.crac.pont.Serveur.MOTEUR;
            if (m != null) {
                m.objetAdosseRetire(sl, pos);
            }
            BlockPos bas = bas(state, pos);
            for (int i = 0; i < HAUTEUR; i++) {
                BlockPos c = bas.above(i);
                if (!c.equals(pos) && this.suite(state, level.getBlockState(c), i)) {
                    level.setBlock(c, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Seule la partie du bas a les PV. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PARTIE) == 0 ? new EchelleBlockEntity(pos, state) : null;
    }

    // ---------------------------------------------------------------- pose

    /** Où l'échelle visée se pose : case de la partie du bas, sens (vers le joueur). Ses trois cases sont des cases de mur. */
    public record Pose(BlockPos bas, Direction facing) {
        public BlockState etat(Block b, int partie) {
            return b.defaultBlockState().setValue(FACING, this.facing).setValue(PARTIE, partie);
        }

        public BlockPos partie(int i) {
            return this.bas.above(i);
        }
    }

    /** La place de l'échelle pour ce clic, ou null (refusée). Mêmes règles chez le joueur et au serveur. */
    @Nullable
    public static Pose pose(BlockGetter level, BlockHitResult hit) {
        BlockState vise = level.getBlockState(hit.getBlockPos());
        if (vise.getBlock() instanceof EchelleBlock) {
            // l'étage au-dessus de l'échelle visée, même sens (de case en case : au plus une échelle par étage de mur)
            Direction f = vise.getValue(FACING);
            return colonne(level, bas(vise, hit.getBlockPos()).above(HAUTEUR), f);
        }
        Direction face = hit.getDirection();
        if (!face.getAxis().isHorizontal()) {
            return null;
        }
        // la face d'un mur : la case touchée (un pas dans la face depuis le point visé) est une case du mur
        Vec3 v = hit.getLocation();
        BlockPos touchee = BlockPos.containing(v.x - face.getStepX() * 1.0E-3, v.y, v.z - face.getStepZ() * 1.0E-3);
        PieceKey mur = murContre(level, touchee, face.getOpposite());
        if (mur == null) {
            return null;
        }
        return colonne(level, new BlockPos(touchee.getX(), piedDuMur(mur), touchee.getZ()), face, mur);
    }

    /** Les trois cases à partir de {@code bas}, sens {@code f} : un étage de mur, du pied au sommet ; ou null. */
    @Nullable
    private static Pose colonne(BlockGetter level, BlockPos bas, Direction f) {
        PieceKey mur = murContre(level, bas, f.getOpposite());
        return mur == null ? null : colonne(level, bas, f, mur);
    }

    @Nullable
    private static Pose colonne(BlockGetter level, BlockPos bas, Direction f, PieceKey premier) {
        if (piedDuMur(premier) != bas.getY()) {
            return null;
        }
        for (int i = 1; i < HAUTEUR; i++) {
            PieceKey k = murContre(level, bas.above(i), f.getOpposite());
            if (k == null || piedDuMur(k) != bas.getY()) {
                return null;
            }
        }
        return new Pose(bas.immutable(), f);
    }

    /**
     * Case du pied du mur : sa base est à {@code 24 * h} px de l'origine du bâtiment ; un mur posé à mi-hauteur (sur un
     * demi-mur) commence au milieu d'une case, l'échelle prend alors la case du dessus (elle dépasse d'une demi-case en
     * haut, ce qui aide à passer sur le toit).
     */
    static int piedDuMur(PieceKey k) {
        int px = k.origin().getY() * 16 + 24 * k.h();
        return -Math.floorDiv(-px, 16);
    }

    /**
     * Le mur contre lequel l'échelle se pose dans la case {@code cell}, dos au mur {@code dos}, ou null : la case ne tient
     * que des murs droits sans porte, dont un a son plan sur le bord {@code dos} de la case (rendu) et aucun sur le bord
     * opposé (devant l'échelle) ; chacun peut la laisser (son autre moitié ferme le bord : règle d'enfouissement, comme
     * aux coffres).
     */
    @Nullable
    public static PieceKey murContre(BlockGetter level, BlockPos cell, Direction dos) {
        if (!(level.getBlockState(cell).getBlock() instanceof StructureBlock)
            || !(level.getBlockEntity(cell) instanceof StructureBlockEntity be)) {
            return null;
        }
        List<Occupant> occ = be.occupants();
        PieceKey contre = null;
        for (Occupant o : occ) {
            PieceKey k = o.key();
            if (!k.kind().isWall() || k.kind().hasDoor() || k.orient() % 3 != 0) {
                return null;
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
                return null;
            }
            if (cote == dos) {
                contre = k;
            } else if (cote == dos.getOpposite()) {
                return null;   // un mur devant l'échelle
            }
            long id = o.pieceId();
            if (!PlacementRules.mayBury(k, cell, m -> tientLaPiece(level, m, id))) {
                return null;
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
