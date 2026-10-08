package fr.heraliz.wonder.wintershowl;

import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Lien doux avec le mode zombies de HeraliZ Matchs (reflexion, comme {@link fr.heraliz.wonder.Regles}) : le mode deplace
 * lui-meme les zombies qui arrachent les planches d'une fenetre ou l'enjambent (sans leur IA). Un zombie gele a une
 * fenetre ne doit pas continuer : il vole en eclats presque tout de suite ({@code ZombiesApi.planches} : le bloc fait-il
 * partie d'une fenetre de la partie ?). Une recherche de 3 x 3 x 3 blocs, une fois par gel ; rien si Matchs est absent.
 */
final class MatchsGivre {
    private MatchsGivre() {
    }

    private static boolean cherche;
    @Nullable
    private static Method planches;
    private static final BlockPos.MutableBlockPos MP = new BlockPos.MutableBlockPos();

    /** La creature est-elle contre une fenetre d'une partie de zombies (planches a arracher ou embrasure a enjamber) ? */
    static boolean contreFenetre(Entity e) {
        Method m = methode();
        if (m == null) return false;
        Level niveau = e.level();
        BlockPos b = e.blockPosition();
        try {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        MP.set(b.getX() + dx, b.getY() + dy, b.getZ() + dz);
                        Object r = m.invoke(null, niveau, MP);
                        if (r instanceof Integer k && k >= 0) return true;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            planches = null;
        }
        return false;
    }

    @Nullable
    private static Method methode() {
        if (!cherche) {
            cherche = true;
            try {
                if (ModList.get() != null && ModList.get().isLoaded("heralisz_matchs")) {
                    planches = Class.forName("fr.heralisz.matchs.zombies.jeu.api.ZombiesApi").getMethod("planches", Level.class, BlockPos.class);
                }
            } catch (ReflectiveOperationException | LinkageError ex) {
                HeralizWonder.LOG.warn("[wonder] ZombiesApi.planches illisible ({}) : les zombies geles aux fenetres eclatent au delai normal", ex.toString());
                planches = null;
            }
        }
        return planches;
    }
}
