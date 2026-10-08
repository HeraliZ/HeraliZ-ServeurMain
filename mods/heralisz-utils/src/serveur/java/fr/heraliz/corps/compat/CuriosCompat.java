package fr.heraliz.corps.compat;

import fr.heraliz.corps.HeralizCorps;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Lien souple avec Curios : parcours des emplacements Curios d'un joueur (accessoires et emplacements cosmetiques). Sans
 * Curios : rien. La classe qui touche a l'API ({@link Api}) n'est chargee que si le mod est present.
 */
public final class CuriosCompat {
    private CuriosCompat() {
    }

    /** Emplacement du sac a dos (HeraliZ Backpack). */
    public static final String SAC = "back";

    @FunctionalInterface
    public interface Visiteur {
        void visiter(String id, int index, boolean cosmetique, ItemStack stack);
    }

    private static Boolean present;
    private static boolean casse;

    public static boolean present() {
        if (present == null) present = ModList.get() != null && ModList.get().isLoaded("curios");
        return present && !casse;
    }

    /** Visite chaque objet porte dans un emplacement Curios (cases pleines seulement, objets d'origine, sans copie). */
    public static void parcourir(Player joueur, Visiteur v) {
        if (!present()) return;
        try {
            Api.parcourir(joueur, v);
        } catch (LinkageError | RuntimeException e) {
            casse = true;
            HeralizCorps.LOG.warn("[corps] lien avec Curios coupe : {}", e.toString());
        }
    }

    private static final class Api {
        static void parcourir(Player joueur, Visiteur v) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(joueur).ifPresent(h -> h.getCurios().forEach((id, sh) -> {
                var stacks = sh.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack s = stacks.getStackInSlot(i);
                    if (!s.isEmpty()) v.visiter(id, i, false, s);
                }
                var cosmetiques = sh.getCosmeticStacks();
                for (int i = 0; i < cosmetiques.getSlots(); i++) {
                    ItemStack s = cosmetiques.getStackInSlot(i);
                    if (!s.isEmpty()) v.visiter(id, i, true, s);
                }
            }));
        }
    }
}
