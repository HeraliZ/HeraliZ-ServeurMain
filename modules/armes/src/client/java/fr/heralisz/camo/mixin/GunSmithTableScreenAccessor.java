package fr.heralisz.camo.mixin;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.gui.components.GunPackList;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Table d'armurier (fr.heralisz.camo.client.atelier.TableArmurier) : l'état de l'écran de TaCZ, que notre écran pilote
 * (onglets et recettes classés par TaCZ, recette choisie, matériaux comptés par TaCZ et TaCZ Addon).
 */
@Mixin(value = GunSmithTableScreen.class, remap = false)
public interface GunSmithTableScreenAccessor {
   @Accessor("recipeKeys")
   LinkedHashMap<ResourceLocation, TabConfig> heralisz$onglets();

   @Accessor("recipes")
   Map<ResourceLocation, List<ResourceLocation>> heralisz$recettes();

   @Accessor("selectedType")
   ResourceLocation heralisz$type();

   @Accessor("selectedType")
   void heralisz$setType(ResourceLocation type);

   @Accessor("selectedRecipeList")
   void heralisz$setListeType(List<ResourceLocation> liste);

   @Accessor("selectedRecipe")
   GunSmithTableRecipe heralisz$recette();

   @Accessor("selectedRecipe")
   void heralisz$setRecette(GunSmithTableRecipe recette);

   @Accessor("playerIngredientCount")
   Int2IntArrayMap heralisz$comptes();

   @Accessor("filterList")
   GunPackList heralisz$filtres();

   @Invoker("getPlayerIngredientCount")
   void heralisz$compter(GunSmithTableRecipe recette);
}
