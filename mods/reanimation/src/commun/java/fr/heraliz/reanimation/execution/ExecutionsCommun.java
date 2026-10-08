package fr.heraliz.reanimation.execution;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Part commune des exécutions (docs/METHODE_CLIENT_SERVEUR.md §5) : ce que lisent le client et les règles appliquées des
 * deux côtés. Le reste (déroulé, coups, effets, déblocages) est dans {@code Executions}, partie serveur, qui garde ses
 * noms (lu par réflexion par heraliz_corps : {@code Executions.of}, {@code Executions$Execution.type}).
 */
public final class ExecutionsCommun {
   /** Vrai pendant un coup d'essai ({@code Executions.canHurt}, serveur) : nos propres refus ne s'appliquent pas. */
   static boolean probing;

   private ExecutionsCommun() {
   }

   /** Vrai pendant un coup d'essai (toujours faux sur le client). */
   public static boolean probing() {
      return probing;
   }

   /** Partie serveur : début / fin d'un coup d'essai (même champ qu'avant, lu par {@link #probing()}). */
   static void probing(boolean value) {
      probing = value;
   }

   /** Exécution liée à l'arme tenue (couteaux TaCZ, armes de mêlée HeralisZ, objets), ou null. */
   @Nullable
   public static ExecutionType forWeapon(ItemStack stack) {
      if (stack.isEmpty()) {
         return null;
      }

      ResourceLocation item = ForgeRegistries.ITEMS.getKey(stack.getItem());
      String[] ids = new String[]{
         item != null ? item.getPath() : "",
         path(stack.getTag() != null ? stack.getTag().getString("GunId") : ""),
         path(stack.getTag() != null ? stack.getTag().getString("MeleeWeaponId") : "")
      };

      for (ExecutionType type : ExecutionType.values()) {
         if (type.weapon != null && !type.variant()) {
            for (String id : ids) {
               if (!id.isEmpty() && type.weapon.test(id)) {
                  return type;
               }
            }
         }
      }

      return null;
   }

   private static String path(String id) {
      int colon = id.indexOf(58);
      return colon < 0 ? id : id.substring(colon + 1);
   }
}
