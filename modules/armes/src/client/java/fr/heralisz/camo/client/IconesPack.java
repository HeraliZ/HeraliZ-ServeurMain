package fr.heralisz.camo.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import fr.heralisz.camo.HeraliszCamo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Image « slot » du gunpack, lissée : les icônes des armes à feu sont rendues HORS DU JEU en 128 px depuis leur modèle 3D
 * (outils/icones_armes.py) ; TaCZ les dessinerait sans réduction douce (pixels pris au plus proche : escaliers à l'échelle
 * de l'inventaire). Ici, la même image du pack est chargée une fois avec ses réductions (mipmaps) et toujours filtrée en
 * douceur. Rien n'est photographié en jeu. Image introuvable ou illisible : TaCZ dessine la sienne, comme avant.
 */
public final class IconesPack {
   private static final Logger LOG = LogUtils.getLogger();
   /** Image du pack -> sa copie lissée. */
   private static final Map<ResourceLocation, ResourceLocation> LISSEES = new HashMap<>();
   private static final Set<ResourceLocation> ECHECS = new HashSet<>();
   private static int adresses;
   @Nullable
   private static SlotModel caseIcone;

   /** Dessine l'image lissée à la place de celle de TaCZ (même place, même taille). Faux si elle n'est pas disponible. */
   public static boolean dessiner(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
      GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
      ResourceLocation slot = display == null ? null : display.getSlotTexture();
      ResourceLocation loc = slot == null ? null : lissee(slot);
      if (loc == null) {
         return false;
      }
      pose.pushPose();
      pose.translate(0.5, 1.5, 0.5);
      pose.mulPose(Axis.ZN.rotationDegrees(180.0F));
      if (caseIcone == null) {
         caseIcone = new SlotModel();
      }
      caseIcone.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(loc)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
      pose.popPose();
      return true;
   }

   @Nullable
   private static ResourceLocation lissee(ResourceLocation slot) {
      ResourceLocation deja = LISSEES.get(slot);
      if (deja != null || ECHECS.contains(slot)) {
         return deja;
      }
      ResourceManager ressources = Minecraft.getInstance().getResourceManager();
      // TaCZ peut donner le chemin complet (textures/....png) ou l'identifiant court du gunpack (gun/slot/x)
      Optional<Resource> res = ressources.getResource(slot);
      if (res.isEmpty() && !slot.getPath().endsWith(".png")) {
         res = ressources.getResource(new ResourceLocation(slot.getNamespace(), "textures/" + slot.getPath() + ".png"));
      }
      if (res.isEmpty()) {
         ECHECS.add(slot);
         return null;
      }
      try (InputStream in = res.get().open(); NativeImage image = NativeImage.read(in)) {
         ResourceLocation loc = new ResourceLocation(HeraliszCamo.MODID, "icone_pack/" + adresses++);
         Minecraft.getInstance().getTextureManager().register(loc, new Lissee(image));
         LISSEES.put(slot, loc);
         return loc;
      } catch (Exception e) {
         ECHECS.add(slot);
         LOG.warn("Icône d'arme illisible ({}) : l'image de TaCZ reste affichée", slot, e);
         return null;
      }
   }

   /** Libère les copies (rechargement des ressources : les images ont pu changer ; déconnexion). */
   public static void vider() {
      if (!RenderSystem.isOnRenderThread()) {
         RenderSystem.recordRenderCall(IconesPack::vider);
         return;
      }
      TextureManager textures = Minecraft.getInstance().getTextureManager();
      for (ResourceLocation loc : LISSEES.values()) {
         textures.register(loc, MissingTextureAtlasSprite.getTexture());
      }
      LISSEES.clear();
      ECHECS.clear();
   }

   /** Texture avec toutes ses réductions, toujours filtrée en douceur (même quand le rendu la demande nette). */
   private static final class Lissee extends AbstractTexture {
      Lissee(NativeImage image) {
         int w = image.getWidth(), h = image.getHeight();
         int niveaux = Math.max(0, 31 - Integer.numberOfLeadingZeros(Math.min(w, h)));
         TextureUtil.prepareImage(this.getId(), niveaux, w, h);
         image.upload(0, 0, 0, 0, 0, w, h, true, true, niveaux > 0, false);
         GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
      }

      @Override
      public void load(ResourceManager manager) {
      }

      @Override
      public void setFilter(boolean blur, boolean mipmap) {
         super.setFilter(true, true);
      }
   }

   private IconesPack() {}
}
