package fr.heraliz.crac.api;

import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.build.world.StructureBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Ce qu'une case de construction de CRac cache à la vue, pour l'anti-ESP de Spartan, appelée PAR RÉFLEXION : ne jamais
 * renommer cette classe ni changer cette signature (HeraliZ, 28/10).
 */
public final class VueApi {
    private VueApi() {
    }

    /**
     * Forme qui cache la vue dans la case {@code pos} (coordonnées locales de la case, 0 à 1) : l'union des pièces qui
     * cachent ({@code Occupant#occludes} : pas les brindilles), c'est-à-dire leur forme d'occlusion : mur plein, porte
     * fermée (une porte de sas bloquée est vue ouverte) ; jamais l'ouverture d'une fenêtre, ses barreaux ni sa vitre
     * (transparente : on voit à travers). Forme vide quand rien ne cache. Null si ce n'est pas une case {@code crac:structure} ou si son
     * entité de bloc manque. Même forme que celle qui donne les faces pleines de l'état du bloc
     * ({@link StructureBlockEntity#occlusionShape()}).
     */
    @Nullable
    public static VoxelShape formeVue(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null || !(level.getBlockState(pos).getBlock() instanceof StructureBlock)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof StructureBlockEntity be ? be.occlusionShape() : null;
    }
}
