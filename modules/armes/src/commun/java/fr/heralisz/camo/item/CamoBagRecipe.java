package fr.heralisz.camo.item;

import fr.heralisz.camo.BagSupport;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.HeraliszCamo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import javax.annotation.Nullable;

/**
 * Camo sur un sac, par le craft : un sac (sans camo) + un camo ou un skin X, n'importe où dans la grille de craft (celle de
 * l'inventaire 2 x 2 ou l'établi) -> le même sac, camouflé. Le sac garde tout son contenu et ses réglages (on copie l'objet),
 * le camo est consommé. Définitif : un sac déjà camouflé ne se recraft pas. Les skins réservés à une arme (skins de boss)
 * et les skins à vie ne vont pas sur les sacs. Fichier de recette : data/heralisz_camo/recipes/camo_bag.json.
 * <p>
 * Réservé aux admins (03/11, choix du propriétaire : pas de camo sur les sacs pour les joueurs) : le serveur ne propose le
 * sac camouflé que si le joueur devant la grille est op (niveau {@link #NIVEAU_ADMIN}). Ce joueur est trouvé par le menu
 * de la grille (grille 2 x 2 de l'inventaire ou de l'écran de Curios, établi) : le propriétaire des cases d'inventaire du
 * joueur de ce menu. Introuvable : refusé. Chez le joueur, rien n'est décidé (le serveur envoie le résultat).
 */
public class CamoBagRecipe extends CustomRecipe {
   public CamoBagRecipe(ResourceLocation id, CraftingBookCategory category) {
      super(id, category);
   }

   private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
   /** Niveau d'op qui peut poser un camo sur un sac (comme /camos). */
   public static final int NIVEAU_ADMIN = 2;

   @Override
   public boolean matches(CraftingContainer grid, Level level) {
      return this.find(grid) != null && (level.isClientSide || admin(grid));
   }

   /** Le joueur devant cette grille est-il op ? Serveur seulement ; faux si on ne le trouve pas. */
   private static boolean admin(CraftingContainer grid) {
      AbstractContainerMenu menu = menu(grid);
      if (menu == null) return false;
      for (Slot s : menu.slots) {
         if (s.container instanceof Inventory inv) {
            return inv.player.hasPermissions(NIVEAU_ADMIN);
         }
      }
      return false;
   }

   /**
    * Champ « menu » de chaque sorte de grille (privé chez Minecraft, son nom change selon les noms du jar : trouvé par son
    * type), cherché une fois par sorte de grille.
    */
   private static final java.util.Map<Class<?>, java.util.Optional<Field>> CHAMPS_MENU = new java.util.concurrent.ConcurrentHashMap<>();

   @Nullable
   private static AbstractContainerMenu menu(CraftingContainer grid) {
      Field f = CHAMPS_MENU.computeIfAbsent(grid.getClass(), CamoBagRecipe::chercherChamp).orElse(null);
      if (f == null) return null;
      try {
         return (AbstractContainerMenu) f.get(grid);
      } catch (IllegalAccessException | RuntimeException e) {
         return null;
      }
   }

   private static java.util.Optional<Field> chercherChamp(Class<?> sorte) {
      for (Class<?> c = sorte; c != null; c = c.getSuperclass()) {
         for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && AbstractContainerMenu.class.isAssignableFrom(f.getType())) {
               try {
                  f.setAccessible(true);
                  return java.util.Optional.of(f);
               } catch (RuntimeException e) {
                  LOG.warn("Camo sur sac : menu de la grille {} illisible ({})", sorte.getName(), e.toString());
                  return java.util.Optional.empty();
               }
            }
         }
      }
      LOG.warn("Camo sur sac : grille {} sans menu, camo sur sac refusé", sorte.getName());
      return java.util.Optional.empty();
   }

   @Override
   public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
      Object[] found = this.find(grid);
      if (found == null) return ItemStack.EMPTY;
      ItemStack bag = ((ItemStack) found[0]).copyWithCount(1);
      CamoData.set(bag, (Camo) found[1]);
      return bag;
   }

   /** {sac, camo} si la grille contient exactement un sac sans camo et un camo qui peut aller sur un sac, rien d'autre. */
   @Nullable
   private Object[] find(CraftingContainer grid) {
      ItemStack bag = null;
      Camo camo = null;
      for (int i = 0; i < grid.getContainerSize(); i++) {
         ItemStack s = grid.getItem(i);
         if (s.isEmpty()) continue;
         if (s.getItem() instanceof CamoItem ci) {
            // 03/11 : ni skin réservé à une arme, ni skin à vie (consommé ici, il était perdu) : CamoItem.vaSurUnSac
            if (camo != null || !CamoItem.vaSurUnSac(ci.camo())) return null;
            camo = ci.camo();
         } else if (BagSupport.isBag(s)) {
            if (bag != null || CamoData.get(s) != null) return null;
            bag = s;
         } else {
            return null;
         }
      }
      return bag != null && camo != null ? new Object[] {bag, camo} : null;
   }

   @Override
   public boolean canCraftInDimensions(int width, int height) {
      return width * height >= 2;
   }

   @Override
   public RecipeSerializer<?> getSerializer() {
      return HeraliszCamo.CAMO_BAG.get();
   }
}
