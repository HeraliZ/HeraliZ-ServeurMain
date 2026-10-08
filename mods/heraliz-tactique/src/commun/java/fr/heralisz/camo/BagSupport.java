package fr.heralisz.camo;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Sacs HeralisZ Backpack (fork de Backpacked, mod « backpacked ») : présents ou non, sans jamais charger leurs classes s'ils manquent. */
public final class BagSupport {
   public static final boolean PRESENT = ModList.get() != null && ModList.get().isLoaded("backpacked");

   public static boolean isBag(ItemStack stack) {
      return PRESENT && !stack.isEmpty() && Impl.isBag(stack);
   }

   /** Seule classe qui touche au mod des sacs (chargée seulement s'il est là). */
   private static final class Impl {
      static boolean isBag(ItemStack stack) {
         return stack.getItem() instanceof com.mrcrayfish.backpacked.item.BackpackItem;
      }
   }

   private BagSupport() {}
}
