package com.hbm_m.client.render.shader;

import com.mojang.blaze3d.shaders.Uniform;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

public final class IrisDerivedMatrixUniforms {
   private static final int GL_FLOAT_MAT3 = 35675;
   private static final int GL_FLOAT_MAT4 = 35676;

   private IrisDerivedMatrixUniforms() {
   }

   public static IrisDerivedMatrixUniforms.Locations resolve(ShaderInstance shader) {
      if (shader == null) {
         return IrisDerivedMatrixUniforms.Locations.NONE;
      }

      int programId = shader.getId();
      if (programId <= 0) {
         return IrisDerivedMatrixUniforms.Locations.NONE;
      }

      int locModelView = firstMat4(programId, "ModelViewMat", "iris_ModelViewMat");
      if (locModelView < 0) {
         Uniform u = shader.getUniform("ModelViewMat");
         if (u != null) {
            int loc = u.getLocation();
            if (isMat4(programId, loc)) {
               locModelView = loc;
            }
         }
      }

      int locInverse = firstMat4(programId, "ModelViewMatInverse", "iris_ModelViewMatInverse");
      int locNormal = firstMat3(programId, "NormalMat", "iris_NormalMat");
      return new IrisDerivedMatrixUniforms.Locations(locModelView, locInverse, locNormal);
   }

   private static int firstMat4(int programId, String... names) {
      for (String name : names) {
         int loc = GL20.glGetUniformLocation(programId, name);
         if (isMat4(programId, loc)) {
            return loc;
         }
      }

      return -1;
   }

   private static int firstMat3(int programId, String... names) {
      for (String name : names) {
         int loc = GL20.glGetUniformLocation(programId, name);
         if (isMat3(programId, loc)) {
            return loc;
         }
      }

      return -1;
   }

   private static boolean isMat4(int programId, int location) {
      return location >= 0 && uniformType(programId, location) == 35676;
   }

   private static boolean isMat3(int programId, int location) {
      return location >= 0 && uniformType(programId, location) == 35675;
   }

   private static int uniformType(int programId, int location) {
      if (GL20.glGetProgrami(programId, 35714) == 0) {
         return -1;
      }

      int count = GL20.glGetProgrami(programId, 35718);
      MemoryStack stack = MemoryStack.stackPush();

      int var8;
      label54: {
         try {
            IntBuffer size = stack.mallocInt(1);
            IntBuffer type = stack.mallocInt(1);

            for (int i = 0; i < count; i++) {
               String name = GL20.glGetActiveUniform(programId, i, size, type);
               if (name != null && GL20.glGetUniformLocation(programId, name) == location) {
                  var8 = type.get(0);
                  break label54;
               }
            }
         } catch (Throwable var10) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (stack != null) {
            stack.close();
         }

         return -1;
      }

      if (stack != null) {
         stack.close();
      }

      return var8;
   }

   public record Locations(int modelView, int modelViewInverse, int normalMat) {
      public static final IrisDerivedMatrixUniforms.Locations NONE = new IrisDerivedMatrixUniforms.Locations(-1, -1, -1);
   }
}
