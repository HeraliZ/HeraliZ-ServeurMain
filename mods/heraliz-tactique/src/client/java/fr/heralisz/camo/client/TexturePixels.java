package fr.heralisz.camo.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Pixels de la texture d'une arme, relus une fois sur la carte graphique (fil de rendu) quand sa carte de positions est
 * préparée : sert à reconnaître les faces vraiment lumineuses des pièces {@code *_illuminated} (voir
 * {@link PosMap#classerLumineuses}). Quelques millisecondes une seule fois par modèle ; rien n'est gardé ensuite.
 */
public final class TexturePixels {
   private static final Logger LOG = LogUtils.getLogger();
   private final int w, h;
   /** ABGR (format de NativeImage). */
   private final int[] px;

   private TexturePixels(int w, int h, int[] px) {
      this.w = w;
      this.h = h;
      this.px = px;
   }

   /** Lit la texture (déjà chargée par le rendu de l'arme), ou null si elle est illisible ou démesurée. */
   @Nullable
   public static TexturePixels lire(ResourceLocation texture) {
      if (!RenderSystem.isOnRenderThread()) return null;
      try {
         AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(texture);
         RenderSystem.bindTexture(t.getId());
         int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
         int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
         if (w <= 0 || h <= 0 || (long) w * h > 4096L * 4096L) return null;
         try (NativeImage img = new NativeImage(w, h, false)) {
            img.downloadTexture(0, false);
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
               for (int x = 0; x < w; x++) px[y * w + x] = img.getPixelRGBA(x, y);
            }
            return new TexturePixels(w, h, px);
         }
      } catch (RuntimeException e) {
         LOG.debug("Camo : texture {} illisible ({}), pièces lumineuses exclues en entier", texture, e.toString());
         return null;
      }
   }

   /**
    * Vrai si le rectangle de texture (UV 0..1) est vif : au moins 18 % de ses texels opaques sont saturés et lumineux, ou
    * presque blancs (jusqu'à 8 x 8 échantillons).
    */
   public boolean vive(float u0, float v0, float u1, float v1) {
      int x0 = borne((int) Math.floor(u0 * w), w), x1 = borne((int) Math.ceil(u1 * w) - 1, w);
      int y0 = borne((int) Math.floor(v0 * h), h), y1 = borne((int) Math.ceil(v1 * h) - 1, h);
      if (x1 < x0) x1 = x0;
      if (y1 < y0) y1 = y0;
      int nx = Math.min(8, x1 - x0 + 1), ny = Math.min(8, y1 - y0 + 1);
      int opaques = 0, vives = 0;
      for (int j = 0; j < ny; j++) {
         int y = ny == 1 ? (y0 + y1) / 2 : y0 + (y1 - y0) * j / (ny - 1);
         for (int i = 0; i < nx; i++) {
            int x = nx == 1 ? (x0 + x1) / 2 : x0 + (x1 - x0) * i / (nx - 1);
            int c = px[y * w + x];
            int a = (c >>> 24) & 255;
            if (a < 26) continue;
            opaques++;
            int r = c & 255, g = (c >> 8) & 255, b = (c >> 16) & 255;
            int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
            float v = mx / 255F, sat = mx == 0 ? 0 : (mx - mn) / (float) mx;
            if ((v > 0.8F && sat < 0.3F) || (v > 0.55F && sat > 0.45F)) vives++;
         }
      }
      return opaques > 0 && vives >= Math.max(1, Math.round(opaques * 0.18F));
   }

   private static int borne(int v, int n) {
      return Math.max(0, Math.min(n - 1, v));
   }
}
