package fr.heraliz.crac.placable;

import java.util.Collection;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.build.world.StructureBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Placables stand on something, like Rust deployables: a full top face below (terrain, a workbench), a building floor
 * (never a roof, a wall top or a step: rule 14) or another placable. When that support goes they fall with it instead of
 * floating.
 * <p>HeraliZ (03/11, demande du propriétaire : carrés tournés et triangles « propres », objets droits sur la grille) : sur
 * une base, c'est la VRAIE forme de l'objet (sa forme de contour, avec la taille de chaque objet) qui doit être entièrement
 * au-dessus des fondations et planchers ({@link StructureBlock#solSousForme}), et elle ne doit toucher aucune pièce (mur,
 * porte, fenêtre, poteau…) d'un bloc voisin ({@link #touchePiece}). Remplace la règle « moitié de case avec son centre »
 * de la 1.33.0, qui laissait dépasser les caisses du bord du sol.
 * <p>HeraliZ (11/11) : à la pose, un objet de base ne se pose plus sur le terrain, même dans sa base : seulement sur une
 * fondation ou un plancher, au besoin sur d'autres objets qui y reposent ({@link #solDeBaseProblem}). Ce qui tient un
 * objet déjà posé ({@link #isSupported}, chutes) ne change pas.
 */
public final class PlacableSupport {
    /** How many stacked placables one removal may bring down. */
    private static final int MAX_CASCADE = 16;
    /** Refusal on a structure block that no foundation or floor covers (a roof, a wall top, a step). */
    public static final String NOT_A_FLOOR = "crac.placable.not_a_floor";
    /** HeraliZ (11/11) : refus d'un objet de base posé ailleurs que sur une fondation ou un plancher de la base de son clan. */
    public static final String BASE_SEULEMENT = "crac.placable.base_seulement";

    private PlacableSupport() {
    }

    /** The server config, synced to clients; false while it is not loaded (the client ghost may ask early). */
    public static boolean enabled() {
        return Config.SPEC.isLoaded() && Config.placablesNeedSupport.get();
    }

    /** Whether a placable may stand at {@code pos} with what is below it. */
    public static boolean isSupported(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BellBlock) {
            // bells hang from walls and ceilings and drop by themselves when their attachment goes
            return true;
        }
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        if (fr.heraliz.crac.block.Adosse.bas(state)) {
            // HeraliZ (28/10) : descendu d'un demi-bloc, il repose sur un plancher à mi-bloc qui couvre toute la case, et
            // rien de cette case (mur, demi-mur) ne rentre dans sa moitié basse
            return StructureBlock.tientEnBas(level, below) && fr.heraliz.crac.block.Adosse.libreDessous(level, pos, state);
        }
        if (under.getBlock() instanceof StructureBlock) {
            // on a building only foundations and floors hold placables, whatever the collision of the block's top ;
            // HeraliZ (03/11) : sous toute la forme de l'objet
            return StructureBlock.solSousForme(level, pos, forme(level, pos, state)) == StructureBlock.SOL_OK;
        }
        // a bed or sleeping bag is a mat on the floor: nothing stands on it
        return under.isFaceSturdy(level, below, Direction.UP) || PlacableSet.contains(level, under.getBlock()) && !(under.getBlock() instanceof BedBlock);
    }

    /** Whether every cell of a placement that does not stand on another of its own cells is supported. */
    public static boolean isSupported(Level level, Collection<BlockPos> cells, BlockState state) {
        for (BlockPos cell : cells) {
            if (!cells.contains(cell.below()) && !isSupported(level, cell, state)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Why a placable may not stand over these cells, in the words the server and the placement ghost show, or null
     * when every cell is supported. A building surface that cannot hold it says why: a floor half a wall up has its top
     * mid-block, and a block cut by a rotated or triangle edge is only partly covered (§7.5).
     */
    @Nullable
    public static String supportProblem(Level level, Collection<BlockPos> cells, BlockState state) {
        for (BlockPos cell : cells) {
            if (cells.contains(cell.below()) || isSupported(level, cell, state)) continue;
            if (fr.heraliz.crac.block.Adosse.bas(state) && StructureBlock.tientEnBas(level, cell.below())) {
                return "crac.placable.bas_gene";   // un mur ou un demi-mur dépasse du plancher là où l'objet descendrait
            }
            if (level.getBlockState(cell.below()).getBlock() instanceof StructureBlock) {
                return switch (StructureBlock.solSousForme(level, cell, forme(level, cell, state))) {
                    case StructureBlock.SOL_MI_BLOC -> "crac.placable.half_level";
                    case StructureBlock.SOL_DEPASSE -> "crac.placable.cut";
                    default -> NOT_A_FLOOR;
                };
            }
            return "crac.placable.needs_floor";
        }
        // HeraliZ (03/11) : posé, l'objet ne doit toucher aucune pièce d'un bloc voisin (mur en biais, porte, poteau…)
        for (BlockPos cell : cells) {
            if (touchePiece(level, cell, state)) {
                return "crac.placable.touche_piece";
            }
        }
        return null;
    }

    /**
     * HeraliZ (11/11, demande du propriétaire : « bloquer le fait de pouvoir poser n'importe quel item de base hors d'une
     * fondation de notre clan ») : dans une base, un objet posé repose sur une fondation ou un plancher (une pièce de
     * construction), directement ou sur d'autres objets posés qui y reposent (caisse sur caisse) ; jamais sur le terrain
     * de la zone. Rend {@link #BASE_SEULEMENT} quand rien de l'objet ne repose sur la base, {@code crac.placable.cut}
     * quand une partie seulement y repose (coffre à cheval sur le bord), sinon null. Le reste (sol sous toute la forme,
     * toit, mi-bloc, pièce touchée) est la règle de {@link #supportProblem}. Seulement à la pose et au fantôme de pose :
     * quelques blocs lus sous l'objet.
     */
    @Nullable
    public static String solDeBaseProblem(Level level, Collection<BlockPos> cells) {
        boolean surLaBase = false;
        boolean ailleurs = false;
        for (BlockPos cell : cells) {
            if (cells.contains(cell.below())) continue;
            if (reposeSurLaBase(level, cell.below())) {
                surLaBase = true;
            } else {
                ailleurs = true;
            }
        }
        if (!ailleurs) {
            return null;
        }
        return surLaBase ? "crac.placable.cut" : BASE_SEULEMENT;
    }

    /** Le bloc {@code dessous} est une pièce de construction, ou un objet posé (pile) qui finit sur une pièce de construction. */
    private static boolean reposeSurLaBase(Level level, BlockPos dessous) {
        BlockPos.MutableBlockPos q = dessous.mutable();
        for (int i = 0; i <= MAX_CASCADE; i++) {
            BlockState s = level.getBlockState(q);
            if (s.getBlock() instanceof StructureBlock) {
                return true;
            }
            // un lit ou un sac de couchage est un tapis : rien ne tient dessus (isSupported)
            if (!PlacableSet.contains(level, s.getBlock()) || s.getBlock() instanceof BedBlock) {
                return false;
            }
            q.move(Direction.DOWN);
        }
        return false;
    }

    /**
     * HeraliZ (03/11) : la forme de l'objet de l'état {@code state} posé en {@code pos} (contour : ce qu'on voit et vise ;
     * le bloc entier pour un objet sans forme). Forme du bloc lui-même, sans le monde autour : rien n'est lu.
     */
    public static net.minecraft.world.phys.shapes.VoxelShape forme(Level level, BlockPos pos, BlockState state) {
        net.minecraft.world.phys.shapes.VoxelShape f = state.getShape(level, pos);
        return f.isEmpty() ? net.minecraft.world.phys.shapes.Shapes.block() : f;
    }

    /**
     * HeraliZ (03/11, demande du propriétaire : rien ne dépasse d'un mur) : la forme de l'objet posé en {@code pos} entre
     * dans la collision d'une pièce d'un bloc de construction voisin (mur en biais, porte, fenêtre, poteau, plancher…).
     * Son propre bloc est libre (sinon rien ne s'y pose) : seuls les blocs que ses boîtes atteignent hors de lui sont lus,
     * aucun le plus souvent. Un jeu d'un centième de pixel : un objet poussé contre un mur, à 0,25 px de lui, ne le touche
     * pas. Seulement à la pose (et au fantôme de pose).
     */
    public static boolean touchePiece(Level level, BlockPos pos, BlockState state) {
        double jeu = 0.01 / 16.0;
        for (net.minecraft.world.phys.AABB b : forme(level, pos, state).toAabbs()) {
            net.minecraft.world.phys.AABB m = b.move(pos).deflate(jeu);
            for (int x = (int) Math.floor(m.minX); x <= (int) Math.floor(m.maxX); x++) {
                for (int y = (int) Math.floor(m.minY); y <= (int) Math.floor(m.maxY); y++) {
                    for (int z = (int) Math.floor(m.minZ); z <= (int) Math.floor(m.maxZ); z++) {
                        if (x == pos.getX() && y == pos.getY() && z == pos.getZ()) continue;
                        BlockPos q = new BlockPos(x, y, z);
                        BlockState s = level.getBlockState(q);
                        if (!(s.getBlock() instanceof StructureBlock)) continue;
                        for (net.minecraft.world.phys.AABB c : s.getCollisionShape(level, q).toAabbs()) {
                            if (c.move(q).intersects(m)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether the block at {@code pos} still holds what stands on it once a piece of it is gone: ground or any other
     * block keeps doing so, a structure block only while foundations or floors cover its top
     * ({@link StructureBlock#holdsBlockOnTop}), air never.
     */
    public static boolean holdsWhatStandsOn(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof StructureBlock) {
            return StructureBlock.holdsBlockOnTop(level, pos);
        }
        return !s.isAir();
    }

    /**
     * HeraliZ (03/11) : {@link #holdsWhatStandsOn} pour l'objet {@code dessus} posé en {@code posDessus} sur le bloc
     * {@code pos} : sur un bloc de construction, le sol doit rester sous toute sa forme ({@link StructureBlock#solSousForme}).
     */
    public static boolean holdsWhatStandsOn(Level level, BlockPos pos, BlockPos posDessus, BlockState dessus) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof StructureBlock) {
            return StructureBlock.solSousForme(level, posDessus, forme(level, posDessus, dessus)) == StructureBlock.SOL_OK;
        }
        return !s.isAir();
    }




}
