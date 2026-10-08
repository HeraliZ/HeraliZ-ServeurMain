package com.hbm_m.client.loader.dae;

import java.util.ArrayList;
import java.util.List;

public class DaeMesh {
   public float[] positions;
   public float[] normals;
   public float[] uvs;
   public final List<int[]> tris = new ArrayList<>();

   public int getTriangleCount() {
      return this.tris.size();
   }
}
