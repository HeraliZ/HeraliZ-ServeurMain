package fr.heralisz.bunker.barricade;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.bunker.HeraliszBunker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Modele 3D de la barricade en bois (mur de rondins couches) : donnees brutes ecrites par outils/barricade_modele.py
 * (modele : mods/heralisz-bunker/outils/BarricadeModele.java), lues une fois puis gardees en memoire. Deux versions : de
 * pres (~3 900 sommets) et de loin (~1 150, sans agrafes ni bouts de bois). Par sommet : x y z (px), u v, normale, couleur
 * (ombrage d'occlusion cuit). Quads.
 */
public final class BarricadeMesh {
   private static final ResourceLocation NEAR = new ResourceLocation(HeraliszBunker.MODID, "models/entity/wooden_barricade.bin");
   private static final ResourceLocation FAR = new ResourceLocation(HeraliszBunker.MODID, "models/entity/wooden_barricade_lod.bin");
   private static final org.slf4j.Logger LOG = LogUtils.getLogger();
   private static final int STRIDE = 11, MAGIC = 0x48424152;
   private static float[] near, far;
   private static boolean failed;

   /** A vider au rechargement des ressources (pack de textures, F3+T). */
   public static final ResourceManagerReloadListener RELOAD = rm -> {
      near = far = null;
      failed = false;
   };

   private BarricadeMesh() {
   }

   /** Sommets du modele (de pres ou de loin), ou null s'il est illisible. */
   @Nullable
   static float[] get(boolean lod) {
      if (near == null && !failed) {
         ResourceManager rm = Minecraft.getInstance().getResourceManager();
         try {
            near = read(rm, NEAR);
            far = read(rm, FAR);
         } catch (IOException | RuntimeException e) {
            failed = true;
            LOG.error("[heralisz_bunker] Modele de la barricade illisible", e);
         }
      }
      return lod ? far : near;
   }

   private static float[] read(ResourceManager rm, ResourceLocation loc) throws IOException {
      try (DataInputStream in = new DataInputStream(new BufferedInputStream(rm.open(loc)))) {
         if (in.readInt() != MAGIC) throw new IOException("format inconnu : " + loc);
         int n = in.readInt();
         float[] d = new float[n * STRIDE];
         for (int i = 0; i < d.length; i++) d[i] = in.readFloat();
         return d;
      }
   }

   /** Dessine le modele dans le repere de la pose (pixels convertis en blocs), teinte (r, g, b, a). */
   static void render(float[] d, PoseStack.Pose pose, VertexConsumer vc, int light, float r, float g, float b, float a) {
      Matrix4f m = pose.pose();
      Matrix3f n = pose.normal();
      for (int o = 0; o < d.length; o += STRIDE) {
         vc.vertex(m, d[o] / 16F, d[o + 1] / 16F, d[o + 2] / 16F).color(r * d[o + 8], g * d[o + 9], b * d[o + 10], a).uv(d[o + 3], d[o + 4])
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, d[o + 5], d[o + 6], d[o + 7]).endVertex();
      }
   }
}
