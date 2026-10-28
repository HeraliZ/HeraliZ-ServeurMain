package fr.heraliz.crac.block;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import fr.heraliz.crac.build.world.StructureBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Objets adossés (CRac 1.11.0, demande du propriétaire du 18/10 : « l'armoire vraiment dans l'angle »). Les murs de CRac
 * occupent toute la rangée de cases qui longe le bord d'une fondation (4 px centrés sur le bord) : aucun bloc ne peut se
 * poser contre un mur. Un objet adossé ({@link Adossable} : armoire à outils, caisse en métal) reste posé dans la case
 * libre voisine, mais sa forme et son modèle sont poussés d'une case vers le mur (ou vers l'angle), à 0,25 px de la face
 * du mur. La case du mur dessine et renvoie à l'objet la partie de l'objet qui est chez elle ({@link StructureBlock} :
 * visée, clic droit, casse).
 * <p>Propriété {@link #ADOSSE} : {@code (sx + 1) * 3 + (sz + 1)}, sx et sz de -1 à 1 (4 : rien). Modèles décalés écrits par
 * {@code mods/crac/outils/adossement.py} (mêmes nombres, à changer des deux côtés).
 */
public final class Adosse {
    public static final IntegerProperty ADOSSE = IntegerProperty.create("adosse", 0, 8);
    /**
     * Objet posé sur un plancher à mi-bloc (HeraliZ, 28/10, demande du propriétaire : un plancher posé sur des demi-murs a
     * son dessus au milieu d'une case ; avant, rien ne s'y posait ou l'objet flottait). L'objet reste dans la case du
     * dessus, mais son modèle ET sa forme (collision, visée) descendent d'un demi-bloc ({@link #DEMI} px) : le bas du
     * modèle est sur le vrai dessus du plancher. Gardé dans l'état du bloc (sauvegarde, envoi aux joueurs). Coffres et
     * caisse en métal seulement ({@link #peutDescendre}).
     */
    public static final BooleanProperty BAS = BooleanProperty.create("bas");
    /** Descente d'un objet {@link #BAS} : un demi-bloc (px), la hauteur du dessus d'un plancher posé sur des demi-murs. */
    public static final double DEMI = 8.0;
    public static final int AUCUN = 4;
    /** Bord de la boîte poussée (px de la case de l'objet) : face du mur de la case voisine moins 0,25 px. */
    static final double LIMITE_HAUT = 29.75, LIMITE_BAS = -13.75;
    /** Case visée par le joueur quand l'objet a été posé à côté (objet tenu : {@link ObjetAdossable}). */
    static final ThreadLocal<BlockPos> CIBLE = new ThreadLocal<>();
    /**
     * Version des objets adossés, montée à chaque pose, retrait, chargement ou déchargement d'un objet adossé, et quand
     * son sens ou son décalage change. Les cases de mur gardent leur forme avec objets
     * ({@code StructureBlockEntity#avecObjets}) tant qu'elle n'a pas bougé : avant, elles relisaient leurs 26 voisines
     * à chaque tick. Une seule pour tous les mondes (serveur et joueur) : un changement n'importe où fait refaire ces
     * formes au prochain appel, jamais de forme en retard.
     */
    private static final AtomicLong VERSION = new AtomicLong();

    private Adosse() {
    }

    /** Version actuelle des objets adossés (voir {@link #VERSION}). */
    public static long version() {
        return VERSION.get();
    }

    /** Un objet adossé a été posé, retiré, chargé ou déchargé : les formes gardées des cases de mur sont à refaire. */
    public static void changement() {
        VERSION.incrementAndGet();
    }

    /** Un état d'objet adossé a changé : formes à refaire si sa boîte ({@link #boiteMonde}) a pu changer. */
    public static void changement(BlockState avant, BlockState apres) {
        boolean meme = avant.getBlock() == apres.getBlock()
            && avant.hasProperty(ClaimBlock.FACING) && apres.hasProperty(ClaimBlock.FACING)
            && avant.getValue(ClaimBlock.FACING) == apres.getValue(ClaimBlock.FACING)
            && avant.hasProperty(ADOSSE) && apres.hasProperty(ADOSSE)
            && avant.getValue(ADOSSE).intValue() == apres.getValue(ADOSSE).intValue()
            && bas(avant) == bas(apres);
        if (!meme) {
            changement();
        }
    }

    /** Un objet qui peut se coller aux murs. */
    public interface Adossable {
        /** Boîte (px) de l'objet tourné vers le nord, sans décalage : x0, z0, x1, z1. */
        double[] boiteNord();

        /** Hauteur de l'objet en px depuis le bas de sa case (l'armoire : 32 depuis sa case du bas). */
        double hauteur(BlockState state);

        /** Bas de l'objet en px sous sa case (la case du haut de l'armoire : 16). */
        default double dessous(BlockState state) {
            return 0;
        }
    }

    /** L'objet de cet état est descendu sur un plancher à mi-bloc ({@link #BAS}). */
    public static boolean bas(BlockState s) {
        return s.hasProperty(BAS) && s.getValue(BAS);
    }

    /** Ce bloc sait descendre sur un plancher à mi-bloc (coffres, caisse en métal). */
    public static boolean peutDescendre(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState().hasProperty(BAS);
    }

    /**
     * Pose en {@code pos} : l'objet doit descendre ({@link #BAS}) quand la case dessous est une case de construction dont un
     * plancher a son dessus au milieu ({@link StructureBlock#tientEnBas}).
     */
    public static boolean doitDescendre(BlockGetter level, BlockPos pos) {
        return StructureBlock.tientEnBas(level, pos.below());
    }

    /**
     * L'objet descendu ne rentre dans rien de la case de construction de sa colonne, sous lui (mur, demi-mur ou rampe qui
     * dépasse du plancher) : sa boîte, poussée comprise, ne touche pas leur collision. Vrai pour un objet qui n'est pas
     * descendu.
     */
    public static boolean libreDessous(BlockGetter level, BlockPos pos, BlockState etat) {
        if (!bas(etat)) {
            return true;
        }
        AABB boite = boiteMonde(etat, pos).deflate(1.0E-4);
        BlockPos c = pos.below();
        if (!(level.getBlockEntity(c) instanceof fr.heraliz.crac.build.world.StructureBlockEntity be)) {
            return false;
        }
        VoxelShape pieces = be.shape();
        return pieces.isEmpty() || !Shapes.joinIsNotEmpty(pieces.move(c.getX(), c.getY(), c.getZ()), Shapes.create(boite), BooleanOp.AND);
    }

    public static int sx(int v) {
        return v / 3 - 1;
    }

    public static int sz(int v) {
        return v % 3 - 1;
    }

    public static int code(int sx, int sz) {
        return (Mth.clamp(sx, -1, 1) + 1) * 3 + Mth.clamp(sz, -1, 1) + 1;
    }

    /** La boîte (x0, z0, x1, z1 en px) tournée pour {@code facing}, comme le modèle tourné par l'état. */
    public static double[] tourner(double[] n, Direction facing) {
        return switch (facing) {
            case SOUTH -> new double[]{16 - n[2], 16 - n[3], 16 - n[0], 16 - n[1]};
            case WEST -> new double[]{n[1], 16 - n[2], n[3], 16 - n[0]};
            case EAST -> new double[]{16 - n[3], n[0], 16 - n[1], n[2]};
            default -> n.clone();
        };
    }

    /** Décalage (px, x et z) de la boîte tournée {@code b} poussée selon {@code v}. */
    public static double[] decalage(double[] b, int v) {
        int x = sx(v), z = sz(v);
        double dx = x > 0 ? LIMITE_HAUT - b[2] : x < 0 ? LIMITE_BAS - b[0] : 0;
        double dz = z > 0 ? LIMITE_HAUT - b[3] : z < 0 ? LIMITE_BAS - b[1] : 0;
        return new double[]{dx, dz};
    }

    /** Une forme (faite pour le sens {@code facing}, sans décalage) poussée selon {@code v}. */
    public static VoxelShape decaler(VoxelShape forme, double[] boiteNord, Direction facing, int v) {
        if (v == AUCUN) {
            return forme;
        }
        double[] d = decalage(tourner(boiteNord, facing), v);
        return forme.move(d[0] / 16.0, 0, d[1] / 16.0);
    }

    /**
     * {@link #decaler} calculée une fois pour chaque sens (index {@link Direction#get2DDataValue()}) et chaque valeur de
     * {@link #ADOSSE} : les formes des objets adossés sont demandées à chaque collision, sans rien refaire.
     */
    public static VoxelShape[][] formesDecalees(Function<Direction, VoxelShape> formeDuSens, double[] boiteNord) {
        VoxelShape[][] t = new VoxelShape[4][9];
        for (Direction f : Direction.Plane.HORIZONTAL) {
            VoxelShape forme = formeDuSens.apply(f);
            for (int v = 0; v < 9; v++) {
                t[f.get2DDataValue()][v] = decaler(forme, boiteNord, f, v);
            }
        }
        return t;
    }

    /** Les formes d'une table {@link #formesDecalees} descendues d'un demi-bloc ({@link #BAS}). */
    public static VoxelShape[][] descendre(VoxelShape[][] t) {
        VoxelShape[][] out = new VoxelShape[t.length][];
        for (int i = 0; i < t.length; i++) {
            out[i] = new VoxelShape[t[i].length];
            for (int j = 0; j < t[i].length; j++) {
                out[i][j] = t[i][j].move(0, -DEMI / 16.0, 0);
            }
        }
        return out;
    }

    /** Boîte de l'objet dans le monde (son modèle et sa forme), décalage compris. */
    public static AABB boiteMonde(BlockState s, BlockPos pos) {
        Adossable a = (Adossable) s.getBlock();
        Direction f = s.getValue(ClaimBlock.FACING);
        int v = s.getValue(ADOSSE);
        double[] b = tourner(a.boiteNord(), f);
        double[] d = decalage(b, v);
        return new AABB(pos.getX() + (b[0] + d[0]) / 16.0, pos.getY() - a.dessous(s) / 16.0, pos.getZ() + (b[1] + d[1]) / 16.0,
            pos.getX() + (b[2] + d[0]) / 16.0, pos.getY() + (a.hauteur(s) - a.dessous(s)) / 16.0, pos.getZ() + (b[3] + d[1]) / 16.0);
    }

    public static boolean estStructure(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof StructureBlock;
    }

    // ---------------------------------------------------------------- pose

    /**
     * Le sens où pousser l'objet qu'on pose en {@code pos} : vers la case visée si l'objet a été posé à côté
     * ({@link ObjetAdossable}), sinon vers le mur voisin (deux murs en face l'un de l'autre : du côté visé dans la case),
     * puis le premier choix qui tient sans toucher un mur ni un autre objet adossé (d'abord les deux axes, puis un seul).
     */
    public static int choisir(BlockPlaceContext ctx, BlockState etat) {
        BlockGetter level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockPos cible = CIBLE.get();
        int x, z;
        if (cible != null) {
            x = Integer.signum(cible.getX() - pos.getX());
            z = Integer.signum(cible.getZ() - pos.getZ());
        } else {
            Vec3 clic = ctx.getClickLocation();
            x = axe(level, pos, Direction.EAST, clic.x - pos.getX() - 0.5);
            z = axe(level, pos, Direction.SOUTH, clic.z - pos.getZ() - 0.5);
        }
        for (int[] c : new int[][]{{x, z}, {x, 0}, {0, z}}) {
            if ((c[0] != 0 || c[1] != 0)) {
                BlockState essai = etat.setValue(ADOSSE, code(c[0], c[1]));
                if (tient(level, pos, essai) && appuye(level, List.of(pos), essai)) {
                    return code(c[0], c[1]);
                }
            }
        }
        return AUCUN;
    }

    /**
     * Part de la case visée, de chaque côté, où un clic pousse l'objet vers le mur de ce côté (HeraliZ, 28/10). Avant,
     * n'importe quel clic dans une case libre bordée de murs poussait l'objet de près d'un bloc vers le mur (du côté de la
     * moitié visée) : un coffre visé au milieu d'une pièce partait contre un mur ou dans un angle. Un clic au milieu de la
     * case le laisse au milieu ; viser la case du mur (ou le quart de la case contre lui) le colle au mur.
     */
    static final double BORD_CLIC = 0.25;

    /**
     * L'objet poussé ({@link #ADOSSE}) touche vraiment, de chaque côté où il est poussé, la forme d'une pièce de
     * construction (HeraliZ, 28/10) : la poussée est faite pour un mur droit au bord d'une fondation (face à 0,25 px) ; vers
     * une case de construction qui n'a pas de mur à cet endroit (rampe, escalier, mur de biais d'un triangle, plancher), il
     * restait un vide ou l'objet partait de travers. {@code cases} : les cases de l'objet (deux pour un coffre de deux cases).
     */
    public static boolean appuye(BlockGetter level, List<BlockPos> cases, BlockState etat) {
        int v = etat.getValue(ADOSSE);
        if (v == AUCUN) {
            return true;
        }
        int x = sx(v), z = sz(v);
        boolean okX = x == 0, okZ = z == 0;
        double e = 1.0 / 16.0;   // un pixel au-delà de la boîte
        for (BlockPos pos : cases) {
            BlockState s = etat.getBlock() instanceof CoffreBlock && !pos.equals(cases.get(0)) ? etat.setValue(CoffreBlock.ANNEXE, true) : etat;
            AABB b = boiteMonde(s, pos).deflate(1.0E-3);
            if (!okX) {
                AABB tranche = x > 0 ? new AABB(b.maxX, b.minY, b.minZ, b.maxX + e, b.maxY, b.maxZ)
                    : new AABB(b.minX - e, b.minY, b.minZ, b.minX, b.maxY, b.maxZ);
                okX = touchePiece(level, tranche);
            }
            if (!okZ) {
                AABB tranche = z > 0 ? new AABB(b.minX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ + e)
                    : new AABB(b.minX, b.minY, b.minZ - e, b.maxX, b.maxY, b.minZ);
                okZ = touchePiece(level, tranche);
            }
        }
        return okX && okZ;
    }

    /** Une pièce de construction (pas un objet adossé) occupe une partie de cette boîte. */
    private static boolean touchePiece(BlockGetter level, AABB boite) {
        for (BlockPos c : BlockPos.betweenClosed(BlockPos.containing(boite.minX, boite.minY, boite.minZ),
            BlockPos.containing(boite.maxX, boite.maxY, boite.maxZ))) {
            if (level.getBlockEntity(c) instanceof fr.heraliz.crac.build.world.StructureBlockEntity be) {
                VoxelShape p = be.shape();
                if (!p.isEmpty() && Shapes.joinIsNotEmpty(p.move(c.getX(), c.getY(), c.getZ()), Shapes.create(boite), BooleanOp.AND)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Côté (1, -1) vers lequel pousser sur un axe : le mur du côté du quart de case visé ({@link #BORD_CLIC}), sinon 0. */
    private static int axe(BlockGetter level, BlockPos pos, Direction plus, double clic) {
        if (clic >= 0.5 - BORD_CLIC) {
            return estStructure(level, pos.relative(plus)) ? 1 : 0;
        }
        if (clic <= -(0.5 - BORD_CLIC)) {
            return estStructure(level, pos.relative(plus.getOpposite())) ? -1 : 0;
        }
        return 0;   // clic au milieu de la case : pas de poussée de ce côté
    }

    /**
     * L'objet décalé tient : chaque case qu'il touche hors de la sienne est une case de construction dont la forme ne
     * le touche pas, et il ne touche aucun autre objet adossé.
     */
    public static boolean tient(BlockGetter level, BlockPos pos, BlockState etat) {
        AABB boite = boiteMonde(etat, pos).deflate(1.0E-4);
        for (BlockPos c : BlockPos.betweenClosed(BlockPos.containing(boite.minX, boite.minY, boite.minZ),
            BlockPos.containing(boite.maxX, boite.maxY, boite.maxZ))) {
            if (c.getX() == pos.getX() && c.getZ() == pos.getZ()) {
                continue;   // sa propre colonne (l'armoire : ses deux cases)
            }
            BlockState s = level.getBlockState(c);
            if (!(s.getBlock() instanceof StructureBlock)) {
                return false;
            }
            // jamais devant une porte : son battant ouvert passerait dans l'objet
            if (level.getBlockEntity(c) instanceof fr.heraliz.crac.build.world.StructureBlockEntity be) {
                for (fr.heraliz.crac.build.world.Occupant o : be.occupants()) {
                    if (o.key().kind().hasDoor()) {
                        return false;
                    }
                }
            }
            VoxelShape mur = s.getCollisionShape(level, c, CollisionContext.empty());
            if (!mur.isEmpty() && Shapes.joinIsNotEmpty(mur.move(c.getX(), c.getY(), c.getZ()), Shapes.create(boite), BooleanOp.AND)) {
                return false;
            }
        }
        for (Voisin o : voisins(level, pos, 2)) {
            if (o.boite().intersects(boite) && !memeObjet(pos, o.pos())) {
                return false;
            }
        }
        return true;
    }

    /** La case du haut de l'armoire et sa case du bas sont un même objet. */
    private static boolean memeObjet(BlockPos a, BlockPos b) {
        return a.getX() == b.getX() && a.getZ() == b.getZ() && Math.abs(a.getY() - b.getY()) <= 1;
    }

    /** Un objet adossé décalé près d'une case, avec sa boîte dans le monde. */
    public record Voisin(BlockPos pos, BlockState state, AABB boite) {
    }

    /** Les objets adossés (décalés) à {@code r} cases au plus de {@code pos}, sur sa hauteur et d'une case dessus et dessous. */
    public static List<Voisin> voisins(BlockGetter level, BlockPos pos, int r) {
        List<Voisin> out = new ArrayList<>(2);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dz == 0 && dy == 0) {
                        continue;
                    }
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState s = level.getBlockState(m);
                    // la case du haut de l'armoire est comptée avec sa case du bas (boîte de toute l'armoire)
                    // un objet descendu ({@link #BAS}) dépasse dans la case dessous, même sans être poussé
                    if (s.getBlock() instanceof Adossable && !(s.getBlock() instanceof ClaimBlockHaut)
                        && s.hasProperty(ADOSSE) && (s.getValue(ADOSSE) != AUCUN || bas(s))) {
                        BlockPos p = m.immutable();
                        out.add(new Voisin(p, s, boiteMonde(s, p)));
                    }
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- la case du mur

    /** Ce que les objets adossés voisins ont dans la case de mur {@code pos} (forme de visée de la case). */
    public static VoxelShape morceaux(BlockGetter level, BlockPos pos) {
        VoxelShape out = Shapes.empty();
        AABB caseMur = new AABB(pos);
        for (Voisin o : voisins(level, pos, 1)) {
            if (o.boite().intersects(caseMur)) {
                AABB i = o.boite().intersect(caseMur).move(-pos.getX(), -pos.getY(), -pos.getZ());
                out = Shapes.or(out, Shapes.create(i));
            }
        }
        return out;
    }

    /** L'objet adossé qui contient ce point (clic droit dans une case de mur), sinon null. */
    @Nullable
    public static Voisin contenant(BlockGetter level, BlockPos pos, Vec3 point) {
        for (Voisin o : voisins(level, pos, 1)) {
            if (o.boite().inflate(0.01).contains(point)) {
                return o;
            }
        }
        return null;
    }

    /**
     * L'objet adossé que le joueur vise dans la case de mur {@code pos}, s'il est devant la partie du mur de cette case
     * (casse) ; sinon null.
     */
    @Nullable
    public static Voisin vise(BlockGetter level, BlockPos pos, Player p, VoxelShape mur) {
        Vec3 oeil = p.getEyePosition();
        Vec3 fin = oeil.add(p.getViewVector(1.0F).scale(p.getBlockReach() + 1.0));
        double murD = Double.MAX_VALUE;
        if (!mur.isEmpty()) {
            var hit = mur.clip(oeil, fin, pos);
            if (hit != null) {
                murD = hit.getLocation().distanceToSqr(oeil);
            }
        }
        Voisin best = null;
        double bestD = murD;
        for (Voisin o : voisins(level, pos, 1)) {
            var h = o.boite().clip(oeil, fin);
            if (h.isPresent() && h.get().distanceToSqr(oeil) < bestD) {
                bestD = h.get().distanceToSqr(oeil);
                best = o;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- objet tenu

    /**
     * Case libre d'où l'objet peut atteindre la case occupée {@code cible} (visée par le joueur) : une des 8 voisines,
     * libre (et la case du dessus pour {@code hauteur} 2), la plus proche du joueur.
     */
    @Nullable
    static BlockPos caseLibre(BlockPlaceContext ctx, BlockPos cible, int hauteur, BlockState etat) {
        net.minecraft.world.level.Level level = ctx.getLevel();
        if (!estStructure(level, cible)) {
            return null;
        }
        Vec3 oeil = ctx.getPlayer() == null ? Vec3.atCenterOf(cible) : ctx.getPlayer().getEyePosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos c = cible.offset(dx, 0, dz);
                boolean libre = true;
                for (int h = 0; h < hauteur && libre; h++) {
                    libre = level.getBlockState(c.above(h)).canBeReplaced();
                }
                // HeraliZ (28/10) : seulement une case qui le porte (fondation, plancher plein) ; avant, un objet visé
                // sur un plancher de demi-mur sous un autre plancher partait dans une case voisine, parfois en l'air
                // (28/10) un coffre ou une caisse descend sur un plancher à mi-bloc (BAS) : la case est jugée comme il s'y poserait
                BlockState ici = etat.hasProperty(BAS) ? etat.setValue(BAS, doitDescendre(level, c)) : etat;
                libre = libre && fr.heraliz.crac.placable.PlacableSupport.isSupported(level, c, ici);
                double d = Vec3.atCenterOf(c).distanceToSqr(oeil);
                if (libre && d < bestD) {
                    bestD = d;
                    best = c;
                }
            }
        }
        return best;
    }
}
