package fr.heralisz.n07.client.mesh;

import fr.heralisz.n07.HeraliszN07;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Maillage sculpté d'un boss (fichier .mesh produit par tools/boss_forest/sculpt/export_mesh.py).
 * Chaque sommet porte 1 ou 2 influences d'os ; sa position et sa normale sont déjà exprimées dans l'espace de repos de chaque os
 * (mêmes coordonnées que les cubes GeckoLib), il suffit donc de les multiplier par la matrice courante de l'os.
 */
public final class MeshModel {
   public static final class Part {
      public String name;
      public int nv;
      public float[] u, v;
      public byte[] ninf;
      public short[] b0, b1;
      public float[] w0, w1;
      public float[] q0, q1, n0, n1;
      public int[] tris;
      /** Os utilisés par la partie (la partie n'est dessinée que si tous sont visibles). */
      public int[] bones;
   }

   public final String[] bones;
   public final Part[] parts;
   private final Map<String, Integer> index = new HashMap<>();

   private MeshModel(String[] bones, Part[] parts) {
      this.bones = bones;
      this.parts = parts;
      for (int i = 0; i < bones.length; i++) index.put(bones[i], i);
   }

   public int bone(String name) {
      Integer i = index.get(name);
      return i == null ? -1 : i;
   }

   private static final Map<ResourceLocation, MeshModel> CACHE = new HashMap<>();

   public static MeshModel get(ResourceLocation rl) {
      return CACHE.computeIfAbsent(rl, MeshModel::load);
   }

   private static MeshModel load(ResourceLocation rl) {
      try (InputStream raw = Minecraft.getInstance().getResourceManager().getResource(rl).orElseThrow().open();
           DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 1 << 16))) {
         byte[] magic = new byte[4];
         in.readFully(magic);
         if (magic[0] != 'H' || magic[1] != 'M' || magic[2] != 'S' || magic[3] != 'H') throw new IllegalStateException("pas un .mesh");
         in.readInt();
         String[] bones = new String[in.readInt()];
         for (int i = 0; i < bones.length; i++) bones[i] = in.readUTF();
         Part[] parts = new Part[in.readInt()];
         for (int pi = 0; pi < parts.length; pi++) {
            Part p = new Part();
            p.name = in.readUTF();
            int n = p.nv = in.readInt();
            p.u = new float[n];
            p.v = new float[n];
            p.ninf = new byte[n];
            p.b0 = new short[n];
            p.b1 = new short[n];
            p.w0 = new float[n];
            p.w1 = new float[n];
            p.q0 = new float[n * 3];
            p.q1 = new float[n * 3];
            p.n0 = new float[n * 3];
            p.n1 = new float[n * 3];
            java.util.BitSet used = new java.util.BitSet();
            for (int i = 0; i < n; i++) {
               p.u[i] = in.readFloat();
               p.v[i] = in.readFloat();
               int k = p.ninf[i] = in.readByte();
               for (int j = 0; j < k; j++) {
                  short b = in.readShort();
                  float w = in.readFloat();
                  float[] q = j == 0 ? p.q0 : p.q1, nn = j == 0 ? p.n0 : p.n1;
                  for (int c = 0; c < 3; c++) q[i * 3 + c] = in.readFloat();
                  for (int c = 0; c < 3; c++) nn[i * 3 + c] = in.readFloat();
                  if (j == 0) {
                     p.b0[i] = b;
                     p.w0[i] = w;
                  } else {
                     p.b1[i] = b;
                     p.w1[i] = w;
                  }
                  used.set(b);
               }
            }
            p.tris = new int[in.readInt() * 3];
            for (int i = 0; i < p.tris.length; i++) p.tris[i] = in.readInt();
            p.bones = used.stream().toArray();
            parts[pi] = p;
         }
         return new MeshModel(bones, parts);
      } catch (Exception e) {
         HeraliszN07.LOGGER.error("Maillage illisible : {}", rl, e);
         return new MeshModel(new String[0], new Part[0]);
      }
   }
}
