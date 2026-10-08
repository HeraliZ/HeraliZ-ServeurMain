package fr.heralisz.camo;

import fr.heralisz.camo.item.CamoItem;
import fr.heralisz.camo.network.CamoNetwork;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.EnumMap;
import java.util.Map;

@Mod(HeraliszCamo.MODID)
public final class HeraliszCamo {
   public static final String MODID = "heralisz_camo";
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
   /** Recette « sac + camo -> sac camouflé » (data/heralisz_camo/recipes/camo_bag.json). */
   public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPES =
      DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);
   public static final RegistryObject<net.minecraft.world.item.crafting.RecipeSerializer<fr.heralisz.camo.item.CamoBagRecipe>> CAMO_BAG =
      RECIPES.register("camo_bag", () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(fr.heralisz.camo.item.CamoBagRecipe::new));
   public static final Map<Camo, RegistryObject<Item>> CAMO_ITEMS = new EnumMap<>(Camo.class);

   static {
      for (Camo camo : Camo.values()) {
         CAMO_ITEMS.put(camo, ITEMS.register((camo.isSkinX() ? "skinx_" : "camo_") + camo.id, () -> new CamoItem(camo)));
      }
   }

   // Fusion (heraliz_tactique) : les onglets d'origine « camos » et « skinx » (et « decor », voir DecorItems) sont remplacés
   // par les groupes Camos et Décorations de l'onglet unique « HeraliZ · Tactique » (déclarés par HeralizTactique).

   /** Icône du groupe Camos (celle de l'ancien onglet « camos »). */
   public static ItemStack iconeCamos() {
      return new ItemStack(CAMO_ITEMS.get(Camo.DARK_MATTER).get());
   }

   /** Groupe Camos : contenu de l'ancien onglet « camos », puis celui de l'ancien onglet « skinx » (même ordre). */
   public static void remplirCamos(CreativeModeTab.Output out) {
      CAMO_ITEMS.forEach((c, i) -> {
         if (!c.isSkinX()) out.accept(i.get());
      });
      CAMO_ITEMS.forEach((c, i) -> {
         if (c.isSkinX()) out.accept(i.get());
      });
   }

   /** Icône du groupe Décorations (celle de l'ancien onglet « decor »). */
   public static ItemStack iconeDecor() {
      return new ItemStack(fr.heralisz.camo.decor.DecorItems.STATTRAK.get());
   }

   /** Groupe Décorations : contenu de l'ancien onglet « decor » (stickers, pendentifs, module StatTrack). */
   public static void remplirDecor(CreativeModeTab.Output out) {
      fr.heralisz.camo.decor.DecorItems.ALL.forEach(i -> out.accept(i.get()));
   }

   public HeraliszCamo() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      fr.heralisz.camo.decor.DecorItems.init();
      ITEMS.register(bus);
      RECIPES.register(bus);
      bus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(CamoNetwork::init));
      // Fusion (heraliz_tactique) : parties serveur (camos appliqués, décors, compteur de kills) et client (rendu des camos)
      fr.heraliz.tactique.pont.Parties.serveur(MODID, "fr.heralisz.camo.serveur.PartieServeur", bus);
      fr.heraliz.tactique.pont.Parties.client(MODID, "fr.heralisz.camo.client.PartieClient", bus);
   }
}
