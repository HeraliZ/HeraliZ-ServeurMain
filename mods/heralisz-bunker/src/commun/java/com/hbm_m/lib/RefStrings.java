package com.hbm_m.lib;

import net.minecraft.resources.ResourceLocation;

public class RefStrings {
   public static final String MODID = "heralisz_bunker";
   public static final String NAME = "Hbm's Nuclear Tech Mod Modernized";
   public static final String CLIENTSIDE = "com.hbm_m.main.ClientProxy";
   public static final String SERVERSIDE = "com.hbm_m.main.ServerProxy";

   public static ResourceLocation resourceLocation(String path) {
      return new ResourceLocation("heralisz_bunker", path);
   }
}
