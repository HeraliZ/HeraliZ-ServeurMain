package com.mrcrayfish.backpacked.core;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

public class ModLayerDefinitions {
   public static final ModelLayerLocation STANDARD = register("standard");
   public static final ModelLayerLocation CLASSIC = register("classic");
   public static final ModelLayerLocation BAMBOO_BASKET = register("bamboo_basket");
   public static final ModelLayerLocation ROCKET = register("rocket");
   public static final ModelLayerLocation MINI_CHEST = register("mini_chest");
   public static final ModelLayerLocation TRASH_CAN = register("trash_can");
   public static final ModelLayerLocation TALOON_GREEN = register("taloon_green");
   public static final ModelLayerLocation TALOON_RED = register("taloon_red");
   public static final ModelLayerLocation TALOON_BLUE = register("taloon_blue");
   public static final ModelLayerLocation HONEY_JAR = register("honey_jar");
   public static final ModelLayerLocation TURTLE_SHELL = register("turtle_shell");
   public static final ModelLayerLocation CARDBOARD_BOX = register("cardboard_box");
   public static final ModelLayerLocation SHEEP_PLUSH = register("sheep_plush");
   public static final ModelLayerLocation WANDERING_BAG = register("wandering_bag");
   public static final ModelLayerLocation PIGLIN_PACK = register("piglin_pack");
   public static final ModelLayerLocation END_CRYSTAL = register("end_crystal");
   public static final ModelLayerLocation COGWHEEL = register("cog_wheel");
   public static final ModelLayerLocation MOUNTAIN_MILITARY = register("mountain_military");
   public static final ModelLayerLocation DEMON_SLAYER_AKAZA = register("demon_slayer_akaza");
   public static final ModelLayerLocation MOUNTAIN_RED = register("mountain_red");
   public static final ModelLayerLocation MOUNTAIN_BLUE = register("mountain_blue");
   public static final ModelLayerLocation MOUNTAIN_GREEN = register("mountain_green");
   public static final ModelLayerLocation MOUNTAIN_GOLD = register("mountain_gold");
   public static final ModelLayerLocation MOUNTAIN_BLACK = register("mountain_black");
   public static final ModelLayerLocation MOUNTAIN_SNOW = register("mountain_snow");
   public static final ModelLayerLocation DRY_BAG_YELLOW = register("dry_bag_yellow");
   public static final ModelLayerLocation SNIPER_CASE_GREEN = register("sniper_case_green");
   public static final ModelLayerLocation SNIPER_CASE_GOLD = register("sniper_case_gold");
   public static final ModelLayerLocation SNIPER_CASE_RED = register("sniper_case_red");
   public static final ModelLayerLocation SNIPER_CASE_BLUE = register("sniper_case_blue");
   public static final ModelLayerLocation SNIPER_CASE_SNOW = register("sniper_case_snow");
   public static final ModelLayerLocation SNIPER_CASE_YELLOW = register("sniper_case_yellow");
   public static final ModelLayerLocation SNIPER_CASE_BLACK = register("sniper_case_black");
   public static final ModelLayerLocation MOUNTAIN_ARCTIC = register("mountain_arctic");
   public static final ModelLayerLocation MOUNTAIN_ARKANIAZ = register("mountain_arkaniaz");
   public static final ModelLayerLocation SCHOOL = register("school");

   private static ModelLayerLocation register(String name) {
      return new ModelLayerLocation(new ResourceLocation("backpacked", name), "main");
   }
}
