package fr.heralisz.bunker.client;

import com.hbm_m.block.UniversalMachinePartBlock;
import com.hbm_m.block.decorations.DoorBlock;
import com.hbm_m.interfaces.IMultiblockPart;
import fr.heralisz.bunker.keycard.KeycardDoors;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Portes animées du bunker qui s'ouvrent à la main, déclarées au noyau ({@code fr.heraliz.noyau.api.Portes}, HeraliZ,
 * 31/10) : avec une arme en main, le clic droit sur une de ces portes (bloc principal ou une de ses parties) l'ouvre ou la
 * ferme au lieu de viser (HeraliZ Tactique, {@code PortesEnMain}). Les portes à carte ne sont pas déclarées : elles ne
 * s'ouvrent qu'au lecteur de carte, le clic garde la visée. Le serveur décide toujours.
 */
final class PortesBunker {
    private PortesBunker() {
    }

    /** Déclaration au noyau, par réflexion (Bunker ne dépend pas du noyau à la compilation) ; sans noyau : rien. */
    static void declarer() {
        BiPredicate<Level, BlockHitResult> test = PortesBunker::visee;
        try {
            Class.forName("fr.heraliz.noyau.api.Portes").getMethod("ajouter", BiPredicate.class).invoke(null, test);
        } catch (ReflectiveOperationException | LinkageError ex) {
            // noyau absent ou ancien : les armes gardent le clic droit sur ces portes
        }
    }

    private static boolean visee(Level level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Block b = level.getBlockState(pos).getBlock();
        if (b instanceof UniversalMachinePartBlock && level.getBlockEntity(pos) instanceof IMultiblockPart partie) {
            BlockPos maitre = partie.getControllerPos();
            if (maitre == null) return false;
            b = level.getBlockState(maitre).getBlock();
        }
        return b instanceof DoorBlock && KeycardDoors.levelOf(b) == null;
    }
}
