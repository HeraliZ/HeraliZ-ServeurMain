package com.mrcrayfish.backpacked.core;

import com.mrcrayfish.backpacked.platform.Services;
import com.mrcrayfish.framework.api.registry.RegistryContainer;
import com.mrcrayfish.framework.api.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;

@RegistryContainer
public class ArkaniaZModItems {
   public static final RegistryEntry<Item> BACKPACK_EXPAND2 = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_expand2"), () -> Services.BACKPACK.createBackpackExpand2Item(new Properties().stacksTo(1))
   );
   public static final RegistryEntry<Item> BACKPACK_EXPAND1 = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_expand1"), () -> Services.BACKPACK.createBackpackExpand1Item(new Properties().stacksTo(1))
   );
   public static final RegistryEntry<Item> BACKPACK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 2, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_RED = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_red"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_BLUE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_blue"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_GREEN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_green"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_BLACK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_black"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_ORANGE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_orange"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_PINK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_pink"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_YELLOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_yellow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_WHITE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_white"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_SNOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_snow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 5)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_RED = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_red"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 6, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_BLUE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_blue"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 6, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_GREEN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_green"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 6, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_BLACK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_black"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 6, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_ORANGE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_orange"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 6, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_PINK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_pink"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 6, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_YELLOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_yellow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 6, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_WHITE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_white"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 6, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_SNOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_snow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 6, 4)
   );
   // >>> HeralisZ : variantes animaux (tools/backpack_variants.py)
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_COW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_cow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_PIG = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_pig"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_SHEEP = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_sheep"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_ZOMBIE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_zombie"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_DRY_CREEPER = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_dry_creeper"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_SNIPER_CASE_ENDERMAN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sniper_case_enderman"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 6, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_TALOON_AXOLOTL = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_taloon_axolotl"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   // <<< HeralisZ : variantes animaux
   // >>> HeralisZ : sacs de Fuse (tools/fuse_backpacks.py)
   public static final RegistryEntry<Item> BACKPACK_SPORTS_BAG = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_sports_bag"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_TACTICAL_BLACK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_tactical_black"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_TACTICAL_GREEN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_tactical_green"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_BLACK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_black"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 5)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_URBAN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_urban"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 5)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_GREEN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_green"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 5)
   );
   public static final RegistryEntry<Item> BACKPACK_HIKING_MULTICAM = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_hiking_multicam"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 5)
   );
   // <<< HeralisZ : sacs de Fuse
   public static final RegistryEntry<Item> BACKPACK_SCHOOL = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_school"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 1, 2)
   );
   public static final RegistryEntry<Item> BACKPACK_DRY_RED = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_dry_red"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_DRY_BLUE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_dry_blue"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_DRY_GREEN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_dry_green"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_DRY_BLACK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_dry_black"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_DRY_ORANGE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_dry_orange"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_DRY_PINK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_dry_pink"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_DRY_YELLOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_dry_yellow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_DRY_WHITE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_dry_white"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_DRY_SNOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_dry_snow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_MILITARY_CAMMO_1 = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_military_camo_1"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_MILITARY_CAMMO_2 = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_military_camo_2"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_MILITARY_CAMMO_3 = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_military_camo_3"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MOUNTAIN_MILITARY_CAMMO_4 = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_mountain_military_camo_4"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 5, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_RED = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_red"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_BLUE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_blue"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_GREEN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_green"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_BLACK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_black"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_ORANGE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_orange"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_PINK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_pink"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_YELLOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_yellow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_WHITE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_white"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_MILITARY_SPORT_BAG_SNOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_military_sport_bag_snow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 4, 4)
   );
   public static final RegistryEntry<Item> BACKPACK_TALON_RED = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_taloon_red"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_TALON_BLUE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_taloon_blue"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_TALON_GREEN = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_taloon_green"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_TALON_BLACK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_taloon_black"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_TALON_ORANGE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_taloon_orange"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_TALON_PINK = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_taloon_pink"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_TALON_YELLOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_taloon_yellow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_TALON_WHITE = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_taloon_white"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
   public static final RegistryEntry<Item> BACKPACK_TALON_SNOW = RegistryEntry.item(
      new ResourceLocation("backpacked", "backpack_taloon_snow"), () -> Services.BACKPACK.createBackpackItem(new Properties().stacksTo(1), 3, 3)
   );
}
