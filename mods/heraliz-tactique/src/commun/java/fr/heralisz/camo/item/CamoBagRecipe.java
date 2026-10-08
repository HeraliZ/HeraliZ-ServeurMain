package fr.heralisz.camo.item;

import fr.heralisz.camo.BagSupport;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.HeraliszCamo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Camo sur un sac, par le craft : un sac (sans camo) + un camo ou un skin X, n'importe où dans la grille de craft (celle de
 * l'inventaire 2 x 2 ou l'établi) -> le même sac, camouflé. Le sac garde tout son contenu et ses réglages (on copie l'objet),
 * le camo est consommé. Définitif : un sac déjà camouflé ne se recraft pas. Les skins réservés à une arme (skins de boss)
 * ne vont pas sur les sacs. Fichier de recette : data/heralisz_camo/recipes/camo_bag.json.
 */
public class CamoBagRecipe extends CustomRecipe {
   public CamoBagRecipe(ResourceLocation id, CraftingBookCategory category) {
      super(id, category);
   }

   @Override
   public boolean matches(CraftingContainer grid, Level level) {
      return this.find(grid) != null;
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
            if (camo != null || ci.camo().gun != null) return null;
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
