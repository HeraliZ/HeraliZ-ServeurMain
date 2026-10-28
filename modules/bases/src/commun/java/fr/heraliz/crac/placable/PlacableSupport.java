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
 * Placables stand on something, like Rust deployables: a full top face below (terrain, a workbench), a block fully
 * covered by a foundation or floor (never a roof, a wall top or a step: rule 14) or another placable. When that support
 * goes they fall with it instead of floating.
 */
public final class PlacableSupport {
    /** How many stacked placables one removal may bring down. */
    private static final int MAX_CASCADE = 16;
    /** Refusal on a structure block that no foundation or floor covers (a roof, a wall top, a step). */
    public static final String NOT_A_FLOOR = "crac.placable.not_a_floor";

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
            // on a building only foundations and floors hold placables, whatever the collision of the block's top
            return StructureBlock.holdsBlockOnTop(level, below);
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
            StructureBlock.StandIssue issue = StructureBlock.standIssue(level, cell.below());
            if (issue == StructureBlock.StandIssue.HALF_LEVEL) {
                return "crac.placable.half_level";
            }
            if (issue == StructureBlock.StandIssue.PARTLY_COVERED) {
                return "crac.placable.cut";
            }
            if (level.getBlockState(cell.below()).getBlock() instanceof StructureBlock) {
                return NOT_A_FLOOR;
            }
            return "crac.placable.needs_floor";
        }
        return null;
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




}
