package com.hbm_m.client.render;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface IrisCompanionMeshResource {
   boolean ensureBuilt();

   int getVaoId();

   int getIndexCount();

   void prepareForShader(int var1);

   boolean supportsPerVertexLightmap();

   int getUv2Location();

   void ensureLightmapCapacity(int var1);

   void writeInstanceLightmap(int var1, float[] var2);

   void finishLightmapWrites();

   void activatePerVertexLightmap();

   void bindLightmapForInstance(int var1);

   void restoreConstantLightmap();

   void destroy();
}
