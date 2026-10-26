package com.hbm_m.client.render;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.ARBDrawInstanced;
import org.lwjgl.opengl.ARBInstancedArrays;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;

@OnlyIn(Dist.CLIENT)
public final class InstancedGlCompat {
   private InstancedGlCompat() {
   }

   public static boolean supportsInstancedAttributeDivisor() {
      try {
         GLCapabilities caps = resolveGlCapabilities();
         if (caps == null) {
            return false;
         }

         boolean hasDivisor = caps.glVertexAttribDivisor != 0L || caps.glVertexAttribDivisorARB != 0L;
         boolean hasDrawInstanced = caps.glDrawElementsInstanced != 0L || caps.glDrawElementsInstancedARB != 0L;
         return hasDivisor && hasDrawInstanced;
      } catch (Throwable ignored) {
         return false;
      }
   }

   @Nullable
   static GLCapabilities resolveGlCapabilities() {
      if (GLFW.glfwGetCurrentContext() == 0L) {
         return null;
      }

      GLCapabilities caps = GL.getCapabilities();
      if (caps != null) {
         return caps;
      }

      try {
         GL.createCapabilities();
      } catch (Throwable ignored) {
         return null;
      }

      return GL.getCapabilities();
   }

   public static void glVertexAttribDivisorCompat(int index, int divisor) {
      GLCapabilities caps = GL.getCapabilities();
      if (caps != null && caps.glVertexAttribDivisor != 0L) {
         GL33.glVertexAttribDivisor(index, divisor);
      } else {
         ARBInstancedArrays.glVertexAttribDivisorARB(index, divisor);
      }
   }

   public static void glDrawElementsInstancedCompat(int mode, int count, int type, long indices, int primcount) {
      GLCapabilities caps = GL.getCapabilities();
      if (caps != null && caps.glDrawElementsInstanced != 0L) {
         GL31.glDrawElementsInstanced(mode, count, type, indices, primcount);
      } else {
         ARBDrawInstanced.glDrawElementsInstancedARB(mode, count, type, indices, primcount);
      }
   }
}
