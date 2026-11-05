package fr.heraliz.crac.block;

import fr.heraliz.crac.block.entity.EchelleBlockEntity;
import fr.heraliz.crac.build.geom.PieceGeometry;
import fr.heraliz.crac.build.geom.Prism;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
 * Échelle de raid façon Rust (demande du propriétaire du 03/11) : se pose contre une pièce de base, dans n'importe quelle
 * base (la sienne ou celle d'un autre clan : c'est son but pendant un raid), jamais ailleurs ; on y grimpe comme à une
 * échelle de Minecraft.
 * <ul>
 *   <li><b>Taille</b> (validée le 03/11) : une échelle = la hauteur d'un mur (un étage, {@link #HAUTEUR} blocs), posée
 *   d'un coup ; pour deux étages, deux échelles (la 2e se pose au-dessus de la 1re, en visant celle-ci). Trois cases, une
 *   par partie ({@link #PARTIE}) ; la partie du bas garde les PV et la place ({@link EchelleBlockEntity}), un coup sur une
 *   autre partie compte pour toute l'échelle.</li>
 *   <li><b>Où</b> (pose libre, demande du propriétaire du 05/11, {@link #pose}) : contre n'importe quelle face debout
 *   d'une pièce de base (mur droit ou en biais, demi-mur, mur posé sur un triangle, côté d'une fondation…), centrée
 *   là où le joueur vise : son milieu au point visé (à la case près en hauteur), son dos contre la surface, tournée comme
 *   elle (n'importe quel angle). Elle peut dépasser de la pièce (au-dessus d'un demi-mur, d'une fondation). Ses trois
 *   cases sont celles où se tient le grimpeur, juste devant la surface : de l'air, ou la moitié d'un mur droit qui la lui
 *   laisse (comme aux coffres : {@code StructureService.ceder}) ; sinon la pose est refusée (viser un peu à côté). Pas
 *   contre un mur avec porte. Elle est dessinée à sa vraie place par {@code client/render/EchelleRendu}.</li>
 *   <li><b>Tient</b> tant que la pièce visée est là (sa case : {@link EchelleBlockEntity#appui}, revue toutes les 2 s) ;
 *   sinon elle tombe (toute), sans rien lâcher. Échelle d'avant le 05/11 : tant que la case derrière elle est une case de
 *   construction, comme avant.</li>
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
    /** Pose en cours décidée par {@link fr.heraliz.crac.item.EchelleItem} : l'état de la partie du bas et sa place. */
    public static final ThreadLocal<BlockState> POSE = new ThreadLocal<>();
    public static final ThreadLocal<Pose> POSE_PLACE = new ThreadLocal<>();
    /** Distance (px) entre la surface et le centre du grimpeur collé à l'échelle : ses cases sont là. */
    private static final double[] RECUL = {6.0, 9.0, 12.0};
    /** Écart (px) toléré entre le point visé et la surface exacte de la pièce (formes en petits cubes). */
    private static final double TOLERANCE = 3.0;
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

    /** Forme d'une échelle d'avant le 05/11 (dos au mur de sa case), aussi le repli d'une échelle libre. */
    public static VoxelShape formeDAvant(Direction facing) {
        return FORMES[(facing.getAxis().isHorizontal() ? facing : Direction.NORTH).get2DDataValue()];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PARTIE);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        if (l.getBlockEntity(bas(s, p)) instanceof EchelleBlockEntity be) {
            VoxelShape f = be.forme(s.getValue(PARTIE));
            if (f != null) {
                return f;
            }
        }
        return formeDAvant(s.getValue(FACING));
    }

    /** Dessinée en entier par la partie du bas, à sa vraie place ({@code client/render/EchelleRendu}). */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
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

    /** La partie du bas est posée par le chemin habituel (règles de la base) ; elle garde sa place ; les deux autres la suivent. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide && state.getValue(PARTIE) == 0) {
            Pose p = POSE_PLACE.get();
            if (p != null && p.bas().equals(pos) && level.getBlockEntity(pos) instanceof EchelleBlockEntity be) {
                be.placer(p.sx() - pos.getX(), p.sz() - pos.getZ(), p.nx(), p.nz(), p.appui());
            }
            for (int i = 1; i < HAUTEUR; i++) {
                level.setBlock(pos.above(i), state.setValue(PARTIE, i), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Tient tant que la pièce qui la porte est là (pose libre) ; échelle d'avant : la case derrière elle est une case de
     * construction. Les parties du haut suivent celle du bas ({@link #updateShape}). Pendant la pose : toujours.
     */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (POSE.get() != null || state.getValue(PARTIE) > 0) {
            return true;
        }
        if (level.getBlockEntity(pos) instanceof EchelleBlockEntity be && be.appui() != null) {
            return level.getBlockState(be.appui()).getBlock() instanceof StructureBlock;
        }
        return level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).getBlock() instanceof StructureBlock;
    }

    /** Une partie voisine manque (cassée, effacée) ou ce qui la porte est parti : celle-ci part aussi. */
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

    /** Seule la partie du bas a les PV et la place. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PARTIE) == 0 ? new EchelleBlockEntity(pos, state) : null;
    }

    /**
     * Pièce qui porte une échelle libre revue toutes les 2 s au serveur (elle n'est pas toujours voisine de ses cases :
     * les voisins seuls ne suffisent pas à le savoir). Rien chez le joueur ; rien pour les échelles d'avant.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(PARTIE) != 0) {
            return null;
        }
        return (l, pos, s, be) -> {
            if (Math.floorMod(l.getGameTime() + pos.asLong(), 40L) == 0 && be instanceof EchelleBlockEntity e && e.appui() != null
                && l.isLoaded(e.appui()) && !(l.getBlockState(e.appui()).getBlock() instanceof StructureBlock)) {
                l.destroyBlock(pos, false);
            }
        };
    }

    // ---------------------------------------------------------------- pose

    /**
     * Où l'échelle visée se pose : case de la partie du bas, sens (le plus proche de sa direction : formes de repli),
     * point d'appui {@code (sx, sz)} sur la surface (monde, en blocs), direction {@code (nx, nz)} vers le grimpeur, et case
     * de la pièce qui la porte. Ses trois cases sont libres ou des moitiés de mur droit qui peuvent la laisser.
     */
    public record Pose(BlockPos bas, Direction facing, double sx, double sz, double nx, double nz, BlockPos appui) {
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
            // l'étage au-dessus de l'échelle visée, même place et même sens
            BlockPos b = bas(vise, hit.getBlockPos());
            if (!(level.getBlockEntity(b) instanceof EchelleBlockEntity be)) {
                return null;
            }
            double[] p = be.place();
            BlockPos appui = be.appui() != null ? be.appui() : b.relative(vise.getValue(FACING).getOpposite());
            BlockPos haut = b.above(HAUTEUR);
            return colonneLibre(level, haut, vise.getValue(FACING)) ? new Pose(haut, vise.getValue(FACING), b.getX() + p[0],
                b.getZ() + p[1], p[2], p[3], appui) : null;
        }
        if (!hit.getDirection().getAxis().isHorizontal()
            || !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof StructureBlock)
            || !(level.getBlockEntity(hit.getBlockPos()) instanceof StructureBlockEntity se)) {
            return null;
        }
        Vec3 v = hit.getLocation();
        double[] surface = surface(se.occupants(), v);
        if (surface == null) {
            return null;
        }
        double sx = surface[0], sz = surface[1], nx = surface[2], nz = surface[3];
        Direction facing = Direction.getNearest(nx, 0, nz);
        // milieu de l'échelle au point visé, à la case près ; sinon une case plus haut ou plus bas (sol, plafond)
        int b0 = (int) Math.floor(v.y - HAUTEUR / 2.0 + 0.5);
        for (double recul : RECUL) {
            int cx = (int) Math.floor(sx + nx * recul / 16.0), cz = (int) Math.floor(sz + nz * recul / 16.0);
            for (int dy : new int[]{0, 1, -1}) {
                BlockPos bas = new BlockPos(cx, b0 + dy, cz);
                if (colonneLibre(level, bas, facing)) {
                    return new Pose(bas, facing, sx, sz, nx, nz, hit.getBlockPos().immutable());
                }
            }
        }
        return null;
    }

    /**
     * Surface visée : face debout de la pièce la plus proche du point visé (dans les pièces de cette case), avec son point
     * exact et sa direction vers l'extérieur ({@code {sx, sz, nx, nz}}, monde, en blocs), ou null (rien d'assez proche,
     * mur avec porte).
     */
    @Nullable
    private static double[] surface(List<Occupant> occupants, Vec3 v) {
        double best = TOLERANCE * TOLERANCE;
        double[] out = null;
        for (Occupant o : occupants) {
            PieceKey k = o.key();
            if (k.kind().isWall() && k.kind().hasDoor()) {
                continue;
            }
            BlockPos origine = k.origin();
            double px = (v.x - origine.getX()) * 16.0, py = (v.y - origine.getY()) * 16.0, pz = (v.z - origine.getZ()) * 16.0;
            for (Prism pr : PieceGeometry.solids(k, o.state(), o.variant())) {
                if (py < pr.y0() - 0.5 || py > pr.y1() + 0.5) {
                    continue;
                }
                double[][] poly = pr.poly();
                double cx = 0, cz = 0;
                for (double[] q : poly) {
                    cx += q[0];
                    cz += q[1];
                }
                cx /= poly.length;
                cz /= poly.length;
                for (int i = 0; i < poly.length; i++) {
                    double[] a = poly[i], b = poly[(i + 1) % poly.length];
                    double ex = b[0] - a[0], ez = b[1] - a[1], len2 = ex * ex + ez * ez;
                    if (len2 < 1e-6) {
                        continue;
                    }
                    double t = Math.max(0, Math.min(1, ((px - a[0]) * ex + (pz - a[1]) * ez) / len2));
                    double qx = a[0] + ex * t, qz = a[1] + ez * t;
                    double d2 = (px - qx) * (px - qx) + (pz - qz) * (pz - qz);
                    if (d2 >= best) {
                        continue;
                    }
                    double len = Math.sqrt(len2), nx = ez / len, nz = -ex / len;
                    if (nx * ((a[0] + b[0]) / 2 - cx) + nz * ((a[1] + b[1]) / 2 - cz) < 0) {
                        nx = -nx;
                        nz = -nz;
                    }
                    best = d2;
                    out = new double[]{origine.getX() + qx / 16.0, origine.getZ() + qz / 16.0, nx, nz};
                }
            }
        }
        return out;
    }

    /** Les trois cases à partir de {@code bas} : chacune libre, ou la moitié d'un mur droit (dos au mur) qui peut la laisser. */
    private static boolean colonneLibre(BlockGetter level, BlockPos bas, Direction facing) {
        for (int i = 0; i < HAUTEUR; i++) {
            BlockPos c = bas.above(i);
            BlockState s = level.getBlockState(c);
            if (s.getBlock() instanceof StructureBlock) {
                if (murContre(level, c, facing.getOpposite()) == null) {
                    return false;
                }
            } else if (!s.canBeReplaced() || !s.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
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
