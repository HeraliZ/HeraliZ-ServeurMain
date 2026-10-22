package com.mrcrayfish.backpacked.core;

import com.mrcrayfish.backpacked.platform.Services;
import com.mrcrayfish.framework.api.registry.RegistryContainer;
import com.mrcrayfish.framework.api.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;

@RegistryContainer
public class ArkaniaZModItems {
   // HeraliZ : identifiants des extensions de sac retirées, gardés comme objets vides (ni onglet, ni loot) : la liste
   // curios:back de geckolib222 les cite encore, et une liste qui cite un objet absent est refusée en entier par
   // Minecraft (plus aucun sac dans la case du dos, donc plus aucun sac ouvrable)
   public static final RegistryEntry<Item> BACKPACK_EXPAND2 = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_expand2"), () -> new Item(new Properties().stacksTo(1))
   );
   public static final RegistryEntry<Item> BACKPACK_EXPAND1 = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_expand1"), () -> new Item(new Properties().stacksTo(1))
   );
   public static final RegistryEntry<Item> BACKPACK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 2, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_RED = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_red"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_BLUE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_blue"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_GREEN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_green"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_BLACK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_black"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_ORANGE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_orange"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_PINK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_pink"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_YELLOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_yellow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_WHITE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_white"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_SNOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_snow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 2)
   );
   // >>> HeralisZ : variantes animaux (tools/backpack_variants.py)
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_COW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_cow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_PIG = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_pig"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_ENDERMAN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_enderman"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 2)
   );
   // <<< HeralisZ : variantes animaux
   // >>> HeralisZ : sacs de Fuse (tools/fuse_backpacks.py)
   public static final RegistryEntry<Item> BACKPACK_SPORTS_BAG = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sports_bag"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_TACTICAL_BLACK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_tactical_black"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_TACTICAL_GREEN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_tactical_green"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_BLACK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_black"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_URBAN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_urban"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_GREEN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_green"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_MULTICAM = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_multicam"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   // <<< HeralisZ : sacs de Fuse
   public static final RegistryEntry<Item> BACKPACK_SCHOOL = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_school"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 1, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_MILITARY_CAMMO_1 = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_military_camo_1"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_MILITARY_CAMMO_2 = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_military_camo_2"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_MILITARY_CAMMO_3 = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_military_camo_3"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_MILITARY_CAMMO_4 = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_military_camo_4"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_RED = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_red"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_BLUE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_blue"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_GREEN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_green"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_BLACK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_black"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_ORANGE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_orange"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_PINK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_pink"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_YELLOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_yellow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_WHITE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_white"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_SNOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_snow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   // >>> HeralisZ : variantes de couleur (outils/sacs_couleurs.py)
   public static final RegistryEntry<Item> BACKPACK_SPORTS_BAG_RED = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sports_bag_red"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_SPORTS_BAG_BLUE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sports_bag_blue"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_SPORTS_BAG_GREEN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sports_bag_green"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_SPORTS_BAG_ORANGE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sports_bag_orange"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_SPORTS_BAG_PINK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sports_bag_pink"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_SPORTS_BAG_YELLOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sports_bag_yellow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_SPORTS_BAG_WHITE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sports_bag_white"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_SPORTS_BAG_SNOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sports_bag_snow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_TACTICAL_RED = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_tactical_red"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_TACTICAL_BLUE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_tactical_blue"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_TACTICAL_ORANGE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_tactical_orange"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_TACTICAL_PINK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_tactical_pink"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_TACTICAL_YELLOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_tactical_yellow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_TACTICAL_WHITE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_tactical_white"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_TACTICAL_SNOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_tactical_snow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_RED = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_red"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_BLUE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_blue"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_ORANGE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_orange"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_PINK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_pink"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_YELLOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_yellow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_WHITE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_white"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_SNOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_snow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 3)
   );
   // <<< HeralisZ : variantes de couleur
}
