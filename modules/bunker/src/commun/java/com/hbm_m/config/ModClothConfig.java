package com.hbm_m.config;
/** HeralisZ : réglages de rendu des portes (valeurs par défaut de HBM, sans fichier de config). */
public class ModClothConfig {
   private static final ModClothConfig INSTANCE = new ModClothConfig();
   public ModClothConfig.obstructionHighlightSettings obstructionHighlight = new ModClothConfig.obstructionHighlightSettings();
   public int modelUpdateDistance = 3;
   public int modelStaticRenderDistance = 8;
   public boolean enableOcclusionCulling = false;
   public boolean useSlicedLight = false;
   public boolean mdiDebugLogDispatch = false;
   public int maxInstancedInstancesPerPart = 4096;
   public boolean instanceVboOrphanBeforeUpload = true;
   public boolean useInstancedStaticRendering = false;
   public boolean gpuBoneSkinning = true;
   public boolean useMultiDrawIndirect = false;
   public boolean mdiVerboseSubdraws = false;
   public boolean enableDebugLogging = false;
   public static ModClothConfig get() { return INSTANCE; }
   public static boolean useInstancedBatching() { return false; }
   public static class obstructionHighlightSettings {
      public int obstructionHighlightAlpha = 20;
      public int obstructionHighlightDuration = 2;
   }
}
