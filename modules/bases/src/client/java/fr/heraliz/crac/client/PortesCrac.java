package fr.heraliz.crac.client;

import fr.heraliz.crac.build.geom.PieceSolids;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.ClientMirror;
import fr.heraliz.crac.build.world.StructureBlock;
import java.util.function.BiPredicate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Portes et trappes des bases, déclarées au noyau ({@code fr.heraliz.noyau.api.Portes}, 31/10) : avec une arme en main,
 * le clic droit sur le battant d'une porte de base l'ouvre ou la ferme au lieu de viser (HeraliZ Tactique,
 * {@code PortesEnMain}). Même règle que le serveur : seulement en visant un battant, pas l'encadrement
 * ({@link PieceSolids#viseBattant}). Lu dans la copie des pièces du joueur ({@link ClientMirror}). Le serveur décide
 * toujours (droit « Portes » du clan, sas, quelqu'un dans le passage).
 */
final class PortesCrac {
    private PortesCrac() {
    }

    /** Déclaration au noyau, par réflexion (CRac ne dépend pas du noyau à la compilation) ; sans noyau : rien. */
    static void declarer() {
        BiPredicate<Level, BlockHitResult> test = PortesCrac::visee;
        try {
            Class.forName("fr.heraliz.noyau.api.Portes").getMethod("ajouter", BiPredicate.class).invoke(null, test);
        } catch (ReflectiveOperationException | LinkageError ex) {
            // noyau absent ou ancien : les armes gardent le clic droit sur les portes des bases
        }
    }

    private static boolean visee(Level level, BlockHitResult hit) {
        if (!(level.getBlockState(hit.getBlockPos()).getBlock() instanceof StructureBlock)) return false;
        for (Piece p : ClientMirror.view().occupants(hit.getBlockPos())) {
            if (p.kind().hasDoor() && PieceSolids.viseBattant(p.key(), p.isOpen(), hit.getLocation())) return true;
        }
        return false;
    }
}
