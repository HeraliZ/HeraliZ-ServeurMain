package com.hbm_m.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

public final class GlVaoSafety {
   private static int dummyVao = 0;

   private GlVaoSafety() {
   }

   public static int getDummyVao() {
      if (dummyVao == 0) {
         dummyVao = GL30.glGenVertexArrays();
      }

      return dummyVao;
   }

   public static void bindVertexArray(int vao) {
      GlStateManager._glBindVertexArray(vao);
   }

   public static int currentBinding() {
      return GL11.glGetInteger(34229);
   }

   public static void withAttribEditVao(Runnable work) {
      int previousVao = currentBinding();
      int previousArrayBuffer = GL11.glGetInteger(34964);
      boolean usedDummy = previousVao == 0;

      try {
         if (usedDummy) {
            GlStateManager._glBindVertexArray(getDummyVao());
         }

         work.run();
      } finally {
         GlStateManager._glBindVertexArray(previousVao);
         GL15.glBindBuffer(34962, previousArrayBuffer);
      }
   }
}
