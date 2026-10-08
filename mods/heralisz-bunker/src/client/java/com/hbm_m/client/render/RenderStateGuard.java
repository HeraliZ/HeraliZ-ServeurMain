package com.hbm_m.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

@OnlyIn(Dist.CLIENT)
public final class RenderStateGuard implements AutoCloseable {
   private final int previousVao = GL11.glGetInteger(34229);
   private final int previousArrayBuffer = GL11.glGetInteger(34964);
   private final boolean cullEnabled = GL11.glIsEnabled(2884);
   private final boolean depthTestEnabled = GL11.glIsEnabled(2929);
   private final boolean depthMaskEnabled = GL11.glGetBoolean(2930);
   private final int depthFunc = GL11.glGetInteger(2932);
   private final boolean blendEnabled = GL11.glIsEnabled(3042);
   private final int blendSrcRgb = GL11.glGetInteger(32969);
   private final int blendDstRgb = GL11.glGetInteger(32968);
   private final int blendSrcAlpha = GL11.glGetInteger(32971);
   private final int blendDstAlpha = GL11.glGetInteger(32970);

   private RenderStateGuard() {
   }

   public static RenderStateGuard snapshot() {
      return new RenderStateGuard();
   }

   @Override
   public void close() {
      if (this.cullEnabled) {
         RenderSystem.enableCull();
      } else {
         RenderSystem.disableCull();
      }

      if (this.depthTestEnabled) {
         RenderSystem.enableDepthTest();
      } else {
         RenderSystem.disableDepthTest();
      }

      RenderSystem.depthMask(this.depthMaskEnabled);
      RenderSystem.depthFunc(this.depthFunc);
      if (this.blendEnabled) {
         RenderSystem.enableBlend();
      } else {
         RenderSystem.disableBlend();
      }

      RenderSystem.blendFuncSeparate(this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
      GlVaoSafety.bindVertexArray(this.previousVao);
      GL15.glBindBuffer(34962, this.previousArrayBuffer);
   }
}
