package com.hbm_m.client.render.shader;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.lang.reflect.Method;

public final class IrisBufferHelper {
   private static final int GL_QUADS = 7;
   private static Method irisBeginWithoutExtending;
   private static boolean irisChecked;
   private static Method connectorBeginWithoutExtending;
   private static boolean connectorChecked;

   public static void beginBlockQuads(BufferBuilder buffer) {
      begin(buffer, Mode.QUADS, DefaultVertexFormat.BLOCK);
   }

   public static void beginWithoutExtending(BufferBuilder buffer, Mode mode, VertexFormat format) {
      if (!tryIrisBeginWithoutExtending(buffer, mode, format)) {
         buffer.begin(mode, format);
      }
   }

   public static void begin(BufferBuilder buffer, Mode mode, VertexFormat format) {
      if (format != DefaultVertexFormat.BLOCK && format != DefaultVertexFormat.NEW_ENTITY && format != DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) {
         buffer.begin(mode, format);
      } else if (!tryIrisBeginWithoutExtending(buffer, mode, format)) {
         buffer.begin(mode, format);
      }
   }

   private static int getGlMode(Mode mode) {
      return switch (mode) {
         case QUADS -> 7;
         case TRIANGLES -> 4;
         case LINES -> 1;
         case LINE_STRIP -> 3;
         case TRIANGLE_STRIP -> 5;
         case TRIANGLE_FAN -> 6;
         default -> 7;
      };
   }

   private static boolean tryIrisBeginWithoutExtending(BufferBuilder buffer, Mode drawMode, VertexFormat vertexFormat) {
      if (!irisChecked) {
         irisChecked = true;

         for (String className : new String[]{"net.irisshaders.iris.vertices.ExtendingBufferBuilder", "net.coderbot.iris.vertices.ExtendingBufferBuilder"}) {
            try {
               Class<?> iface = Class.forName(className);
               if (iface.isInstance(buffer)) {
                  irisBeginWithoutExtending = iface.getMethod("iris$beginWithoutExtending", Mode.class, VertexFormat.class);
                  break;
               }
            } catch (ClassNotFoundException | NoSuchMethodException var12) {
            }
         }
      }

      if (irisBeginWithoutExtending != null) {
         try {
            irisBeginWithoutExtending.invoke(buffer, drawMode, vertexFormat);
            return true;
         } catch (Exception var11) {
         }
      }

      int glMode = getGlMode(drawMode);
      if (!connectorChecked) {
         connectorChecked = true;

         try {
            Class<?> iface = Class.forName("com.sinytra.forgified_fabric_api.fabric.mixin.renderer.indigo.MixinBufferBuilder");
            if (iface.isInstance(buffer)) {
               connectorBeginWithoutExtending = iface.getMethod("iris$beginWithoutExtending", int.class, VertexFormat.class);
            }
         } catch (ClassNotFoundException | NoSuchMethodException var9) {
         }

         if (connectorBeginWithoutExtending == null) {
            try {
               Class<?> iface = Class.forName("link.infra.indium.renderer.render.ExtendingBufferBuilder");
               if (iface.isInstance(buffer)) {
                  connectorBeginWithoutExtending = iface.getMethod("iris$beginWithoutExtending", int.class, VertexFormat.class);
               }
            } catch (ClassNotFoundException | NoSuchMethodException var8) {
            }
         }
      }

      if (connectorBeginWithoutExtending != null) {
         try {
            connectorBeginWithoutExtending.invoke(buffer, glMode, vertexFormat);
            return true;
         } catch (Exception var10) {
         }
      }

      return false;
   }
}
