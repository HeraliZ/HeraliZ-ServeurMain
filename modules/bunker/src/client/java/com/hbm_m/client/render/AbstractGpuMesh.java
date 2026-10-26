package com.hbm_m.client.render;

import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractGpuMesh {
   protected int vaoId = -1;
   protected int vboId = -1;
   protected int eboId = -1;
   protected int indexCount = 0;
   protected boolean initialized = false;
   protected final float[] objBbox = new float[]{0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
   protected boolean initFailed = false;

   public float[] getObjBbox() {
      return this.objBbox;
   }

   protected void setObjBboxFrom(SingleMeshVboRenderer.VboData data) {
      if (data != null) {
         this.objBbox[0] = data.minX;
         this.objBbox[1] = data.minY;
         this.objBbox[2] = data.minZ;
         this.objBbox[3] = data.maxX;
         this.objBbox[4] = data.maxY;
         this.objBbox[5] = data.maxZ;
      }
   }

   public void cleanup() {
      if (this.initialized) {
         int vaoToDelete = this.vaoId;
         int vboToDelete = this.vboId;
         int eboToDelete = this.eboId;
         this.vaoId = -1;
         this.vboId = -1;
         this.eboId = -1;
         this.indexCount = 0;
         this.initialized = false;
         this.initFailed = false;
         RenderSystem.recordRenderCall(() -> {
            try {
               if (vboToDelete != -1) {
                  GL15.glDeleteBuffers(vboToDelete);
               }

               if (eboToDelete != -1) {
                  GL15.glDeleteBuffers(eboToDelete);
               }

               if (vaoToDelete != -1) {
                  GL30.glDeleteVertexArrays(vaoToDelete);
               }
            } catch (Exception e) {
               MainRegistry.LOGGER.error("Error during GPU mesh cleanup", e);
            }
         });
      }
   }
}
