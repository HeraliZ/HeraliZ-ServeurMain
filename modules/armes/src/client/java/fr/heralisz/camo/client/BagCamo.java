package fr.heralisz.camo.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import fr.heralisz.camo.BagSupport;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Camos sur les sacs HeralisZ Backpack. Le camo est la même étiquette NBT que sur les armes (CamoData) ; au rendu du sac
 * (porté sur le dos : ItemRenderer, voir ItemRendererBagMixin), la texture passe par le shader camo avec une carte de
 * positions du modèle du sac. Seul le tissu reçoit le camo (masque assets/backpacked/camo_masks, écrit par
 * tools/backpack_variants.py --masks) : boucles, sangles, têtes et visages d'animaux gardent leur texture.
 * <p>Skins X : même surface que sur l'arme, plus leur effet 3D version sac (fx.BagEffects : ailes du Séraphin, chaînes…),
 * dessiné une fois par image au rendu du modèle de base du sac (afterRender).
 * <p>Pose du camo : par le craft (item/CamoBagRecipe : sac + camo dans la grille).
 */
public final class BagCamo {
   private static final Logger LOG = LogUtils.getLogger();
   /** Carte de positions par modèle de sac (vide : modèle inutilisable, on ne réessaie pas). */
   private static final Map<BakedModel, Optional<Entry>> MAPS = new IdentityHashMap<>();
   private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

   /** min / max : boîte du modèle de base (coordonnées du modèle, en blocs). */
   private record Entry(PosMap map, float u0, float v0, float du, float dv, float[] min, float[] max, float[] depth) {}

   /** Côté de la carte de profondeur du dos du sac (effets qui épousent sa vraie surface : chaînes…). */
   private static final int DEPTH = 32;

   /** Reflets d'enchantement : laissés tels quels. */
   private static final Set<RenderType> GLINTS = Set.of(RenderType.glint(), RenderType.glintDirect(), RenderType.glintTranslucent(),
      RenderType.entityGlint(), RenderType.entityGlintDirect(), RenderType.armorGlint(), RenderType.armorEntityGlint());

   private BagCamo() {
   }

   /** Tampons du rendu d'un objet : inchangés, sauf pour un sac camouflé (le tissu passe par le shader camo). */
   public static MultiBufferSource wrap(ItemStack stack, MultiBufferSource buffers) {
      CompoundTag tag = stack.getTag();
      if (tag == null || !tag.contains(CamoData.KEY) || !BagSupport.isBag(stack)) {
         return buffers;
      }
      Camo camo = CamoData.get(stack);
      if (camo == null || camo.gun != null || CamoShaders.get(camo.shaderId) == null) {
         return buffers;
      }
      BakedModel base = Impl.baseModel(stack);
      Entry e = base == null ? null : MAPS.computeIfAbsent(base, BagCamo::bake).orElse(null);
      if (e == null) {
         return buffers;
      }
      RenderType camoType = CamoRenderType.bag(e.map.glId, camo.shaderId, camo.parts, e.u0, e.v0, e.du, e.dv);
      return type -> buffers.getBuffer(GLINTS.contains(type) ? type : camoType);
   }

   /**
    * Après le rendu d'un modèle de sac : si c'est le modèle de base d'un sac porté avec un skin X à effet, on dessine l'effet
    * (une seule fois par sac et par image). Le repère du sac vient de la pose du rendu : point du modèle p -> pose · (p - 0,5).
    */
   public static void afterRender(ItemStack stack, com.mojang.blaze3d.vertex.PoseStack pose, int light, BakedModel model) {
      CompoundTag tag = stack.getTag();
      if (tag == null || !tag.contains(CamoData.KEY) || !BagSupport.isBag(stack)) return;
      Camo camo = CamoData.get(stack);
      if (camo == null || fr.heralisz.camo.fx.BagEffects.get(camo.shaderId) == null) return;
      BakedModel base = Impl.baseModel(stack);
      if (base == null || model != base) return;
      Entry e = MAPS.computeIfAbsent(base, BagCamo::bake).orElse(null);
      if (e == null) return;
      org.joml.Matrix4f m = pose.last().pose();
      org.joml.Vector3f c = m.transformPosition(new org.joml.Vector3f((e.min[0] + e.max[0]) * 0.5F - 0.5F, (e.min[1] + e.max[1]) * 0.5F - 0.5F,
         (e.min[2] + e.max[2]) * 0.5F - 0.5F));
      org.joml.Vector3f neck = m.transformPosition(new org.joml.Vector3f(0, 0, 0));
      org.joml.Vector3f sx = m.transformDirection(new org.joml.Vector3f(1, 0, 0));
      org.joml.Vector3f sy = m.transformDirection(new org.joml.Vector3f(0, 1, 0));
      org.joml.Vector3f sz = m.transformDirection(new org.joml.Vector3f(0, 0, 1));
      float lx = sx.length(), ly = sy.length(), lz = sz.length();
      if (lx < 1e-6F || ly < 1e-6F || lz < 1e-6F) return;
      fr.heralisz.camo.fx.BagFrame b = new fr.heralisz.camo.fx.BagFrame(v(c), v(neck), v(sy).mul(1 / ly), v(sx).mul(1 / lx), v(sz).mul(1 / lz),
         (e.max[0] - e.min[0]) * 0.5F * lx, (e.max[1] - e.min[1]) * 0.5F * ly, (e.max[2] - e.min[2]) * 0.5F * lz, ly)
         .withBack(e.depth, DEPTH, e.min, e.max, new float[] {m.m00(), m.m10(), m.m20(), m.m30(), m.m01(), m.m11(), m.m21(), m.m31(), m.m02(), m.m12(),
            m.m22(), m.m32()});
      CamoFx.drawBag(camo.shaderId, b, light);
   }


   private static fr.heralisz.camo.fx.V v(org.joml.Vector3f a) {
      return new fr.heralisz.camo.fx.V(a.x, a.y, a.z);
   }

   public static void clear() {
      MAPS.values().forEach(o -> o.ifPresent(e -> e.map.release()));
      MAPS.clear();
   }

   private static Optional<Entry> bake(BakedModel model) {
      try {
         long t = System.nanoTime();
         TextureAtlasSprite sprite = null;
         List<PosMap.Quad> quads = new ArrayList<>();
         float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
         RandomSource rnd = RandomSource.create(42L);
         for (Direction side : SIDES) {
            for (BakedQuad q : model.getQuads(null, side, rnd)) {
               if (sprite == null) sprite = q.getSprite();
               if (q.getSprite() != sprite) continue;
               int[] v = q.getVertices();
               int stride = v.length / 4;
               float su = sprite.getU1() - sprite.getU0(), sv = sprite.getV1() - sprite.getV0();
               float[][] pts = new float[4][];
               for (int k = 0; k < 4; k++) {
                  int o = k * stride;
                  pts[k] = new float[] {Float.intBitsToFloat(v[o]), Float.intBitsToFloat(v[o + 1]), Float.intBitsToFloat(v[o + 2]),
                     (Float.intBitsToFloat(v[o + 4]) - sprite.getU0()) / su, (Float.intBitsToFloat(v[o + 5]) - sprite.getV0()) / sv};
                  for (int a = 0; a < 3; a++) {
                     min[a] = Math.min(min[a], pts[k][a]);
                     max[a] = Math.max(max[a], pts[k][a]);
                  }
               }
               quads.add(new PosMap.Quad(pts, 1.0F));
            }
         }
         if (sprite == null || quads.isEmpty()) return Optional.empty();
         // 2 texels par pixel de la texture du sac, bornée 128..512
         int side = Math.max(sprite.contents().width(), sprite.contents().height()) * 2;
         int n = 128;
         while (n < side && n < 512) n <<= 1;
         ResourceLocation name = sprite.contents().name();
         byte[] mask = null;
         int mw = 0, mh = 0;
         Optional<net.minecraft.server.packs.resources.Resource> res = Minecraft.getInstance().getResourceManager()
            .getResource(new ResourceLocation(name.getNamespace(), "camo_masks/" + name.getPath() + ".png"));
         if (res.isPresent()) {
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
               mw = img.getWidth();
               mh = img.getHeight();
               mask = new byte[mw * mh];
               for (int y = 0; y < mh; y++) {
                  for (int x = 0; x < mw; x++) {
                     // image en niveaux de gris : canal rouge (ABGR) ; 0 = pas de camo
                     mask[y * mw + x] = (byte) ((img.getPixelRGBA(x, y) & 0xFF) >= 128 ? 1 : 0);
                  }
               }
            }
         }
         PosMap map = PosMap.bakeQuads(quads, n, mask, mw, mh);
         // profondeur du dos : z maximal (vers l'extérieur) du modèle sur une grille de sa boîte, en échantillonnant chaque face
         float[] depth = new float[DEPTH * DEPTH];
         java.util.Arrays.fill(depth, Float.NaN);
         for (PosMap.Quad q : quads) {
            float[][] c4 = q.v();
            for (int sj = 0; sj <= 8; sj++) {
               for (int si = 0; si <= 8; si++) {
                  float a = si / 8F, bb = sj / 8F;
                  float[] p = new float[3];
                  for (int ax = 0; ax < 3; ax++) {
                     float top = c4[0][ax] + (c4[1][ax] - c4[0][ax]) * a, bot = c4[3][ax] + (c4[2][ax] - c4[3][ax]) * a;
                     p[ax] = top + (bot - top) * bb;
                  }
                  int gi = Math.round((p[0] - min[0]) / Math.max(max[0] - min[0], 1e-4F) * (DEPTH - 1));
                  int gj = Math.round((p[1] - min[1]) / Math.max(max[1] - min[1], 1e-4F) * (DEPTH - 1));
                  int idx = gj * DEPTH + gi;
                  if (Float.isNaN(depth[idx]) || p[2] > depth[idx]) depth[idx] = p[2];
               }
            }
         }
         LOG.debug("Carte camo du sac {} : {}², masque {}, {} ms", name, n, mask != null, (System.nanoTime() - t) / 1_000_000);
         return Optional.of(new Entry(map, sprite.getU0(), sprite.getV0(), sprite.getU1() - sprite.getU0(), sprite.getV1() - sprite.getV0(), min, max, depth));
      } catch (Exception | LinkageError e) {
         LOG.warn("Camo : impossible de préparer ce modèle de sac", e);
         return Optional.empty();
      }
   }

   /**
    * Pastille du camo en bas à droite de l'icône du sac (l'icône d'inventaire est une image plate : le camo ne se voit que
    * sur le sac porté ; la pastille dit qu'il y en a un).
    */
   public static final class Badge implements net.minecraftforge.client.IItemDecorator {
      @Override
      public boolean render(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, ItemStack stack, int x, int y) {
         Camo camo = CamoData.get(stack);
         if (camo == null) return false;
         g.pose().pushPose();
         g.pose().translate(0, 0, 250);
         g.fill(x + 8, y + 8, x + 17, y + 17, 0xE0101014);
         g.blit(new ResourceLocation("heralisz_camo", "textures/item/" + (camo.isSkinX() ? "skinx_" : "camo_") + camo.id + ".png"), x + 9, y + 9, 0,
            0, 7, 7, 7, 7);
         g.pose().popPose();
         return false;
      }
   }

   /** Enregistre la pastille sur tous les sacs (événement du bus du mod, client). */
   public static void registerBadges(net.minecraftforge.client.event.RegisterItemDecorationsEvent event) {
      if (!BagSupport.PRESENT) return;
      Badge badge = new Badge();
      for (net.minecraft.world.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
         if (BagSupport.isBag(new ItemStack(item))) {
            event.register(item, badge);
         }
      }
   }

   /** Accès au mod des sacs (chargé seulement s'il est là). */
   private static final class Impl {
      /** Même choix de modèle que le rendu du sac sur le dos (BackpackLayer du fork). */
      @Nullable
      static BakedModel baseModel(ItemStack stack) {
         var manager = com.mrcrayfish.backpacked.common.backpack.BackpackManager.instance();
         ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
         var backpack = id != null ? manager.getClientBackpack(id) : null;
         if (backpack == null) {
            CompoundTag tag = stack.getTag();
            backpack = manager.getClientBackpackOrDefault(tag != null ? tag.getString("BackpackModel") : "");
         }
         return backpack == null ? null : com.mrcrayfish.backpacked.platform.ClientServices.MODEL.getBakedModel(backpack.getBaseModel());
      }
   }
}
