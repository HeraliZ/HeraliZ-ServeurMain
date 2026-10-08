package com.hbm_m.client.render.shader;
/** HeralisZ : compatibilité shaders désactivée (rendu vanilla des portes). */
public final class ShaderCompatibilityDetector {
   public static boolean isExternalShaderActive() { return false; }
   public static boolean isRenderingShadowPass() { return false; }
   public static boolean shouldRenderBlockEntityOffScreen() { return false; }
   public static boolean canUseIrisExtendedShader() { return false; }
}
