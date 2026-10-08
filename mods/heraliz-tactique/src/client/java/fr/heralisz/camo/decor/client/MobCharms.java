package fr.heralisz.camo.decor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.camo.decor.DecorCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pendentifs de mobs : un vrai mob de Minecraft, jamais ajouté au monde, dessiné en miniature par le rendu d'entité du jeu
 * (modèle, texture, variante, et les petites animations qui ne dépendent que de l'âge : ailes, mouton « jeb_ »...).
 */
public final class MobCharms {
   private static final Map<DecorCatalog.Charm, Entity> MOBS = new EnumMap<>(DecorCatalog.Charm.class);
   @Nullable private static Level level;

   private MobCharms() {
   }

   @Nullable
   private static Entity mob(DecorCatalog.Charm d) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return null;
      if (mc.level != level) {
         MOBS.clear();
         level = mc.level;
      }
      Entity e = MOBS.get(d);
      if (e == null) {
         e = create(mc.level, d);
         if (e == null) return null;
         MOBS.put(d, e);
      }
      // âge : fait vivre les animations (ailes, couleurs du mouton jeb_, tentacules du ghast...)
      e.tickCount = (int) (System.currentTimeMillis() / 50L % 1_000_000L);
      return e;
   }

   @Nullable
   private static Entity create(Level level, DecorCatalog.Charm d) {
      EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(d.ref));
      if (type == null) return null;
      Entity e = type.create(level);
      if (e == null) return null;
      if (d.variant != null) {
         try {
            CompoundTag t = e.saveWithoutId(new CompoundTag());
            t.merge(TagParser.parseTag(d.variant));
            e.load(t);
         } catch (Exception ex) {
            // variante illisible : on garde le mob de base
         }
      }
      e.setYRot(0);
      e.setXRot(0);
      e.yRotO = 0;
      e.xRotO = 0;
      if (e instanceof LivingEntity le) {
         le.yBodyRot = le.yBodyRotO = 0;
         le.yHeadRot = le.yHeadRotO = 0;
      }
      return e;
   }

   /**
    * Pendu à l'anneau : le mob debout, accroché par le milieu du dos (le bout de la chaîne entre un peu dans le mob), sa plus
    * grande dimension ramenée à size.
    */
   static void renderHanging(DecorCatalog.Charm d, PoseStack ps, MultiBufferSource.BufferSource bs, int light, float size) {
      Entity e = mob(d);
      if (e == null) return;
      Visual v = visual(d, e);
      float[] b = v.box;
      float dx = b[3] - b[0], dy = b[4] - b[1], dz = b[5] - b[2];
      float longest = Math.max(0.2F, Math.max(dx, Math.max(dy, dz)));
      // un mob trapu (mouton, vache) paraît bien plus gros qu'un mob fin (chat) de même longueur : on tient compte du volume
      float k = 0.85F * size / Math.max(longest, 1.35F * (float) Math.cbrt(Math.max(1e-4F, dx * dy * dz)));
      ps.pushPose();
      ps.scale(k, k, k);
      ps.translate(-v.attach[0], -v.attach[1] + longest * 0.04F, -v.attach[2]);
      draw(e, ps, bs, light);
      ps.popPose();
      bs.endBatch();
   }

   /** Objet (icône d'inventaire, en main, au sol) : le mob debout, centré dans le cube [0, 1]³ de l'objet. */
   public static void renderItem(DecorCatalog.Charm d, PoseStack ps, MultiBufferSource buffers, int light) {
      Entity e = mob(d);
      if (e == null) return;
      float[] b = visual(d, e).box;
      float k = 0.95F / Math.max(0.2F, Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2])));
      ps.pushPose();
      ps.translate(0.5F, 0.5F, 0.5F);
      ps.scale(k, k, k);
      ps.translate(-(b[0] + b[3]) * 0.5F, -(b[1] + b[4]) * 0.5F, -(b[2] + b[5]) * 0.5F);
      draw(e, ps, buffers, light);
      ps.popPose();
   }

   /**
    * Dessin réel du mob (blocs, pieds à y = 0) : sa boîte (la boîte de collision ne suffit pas : queue du chat, tentacules du
    * ghast...) et le point d'accroche, sur la surface du dessus au droit du centre (le dos, pas le haut des oreilles).
    * Mesuré une fois en faisant dessiner le mob dans un collecteur de sommets (4 sommets par face).
    */
   private record Visual(float[] box, float[] attach) {
   }

   private static final Map<DecorCatalog.Charm, Visual> VISUAL = new EnumMap<>(DecorCatalog.Charm.class);

   private static Visual visual(DecorCatalog.Charm d, Entity e) {
      return VISUAL.computeIfAbsent(d, k -> {
         List<float[]> verts = new ArrayList<>();
         VertexConsumer rec = new VertexConsumer() {
            @Override
            public VertexConsumer vertex(double x, double y, double z) {
               verts.add(new float[] {(float) x, (float) y, (float) z});
               return this;
            }

            @Override
            public VertexConsumer color(int r, int g, int bl, int a) {
               return this;
            }

            @Override
            public VertexConsumer uv(float u, float v) {
               return this;
            }

            @Override
            public VertexConsumer overlayCoords(int u, int v) {
               return this;
            }

            @Override
            public VertexConsumer uv2(int u, int v) {
               return this;
            }

            @Override
            public VertexConsumer normal(float x, float y, float z) {
               return this;
            }

            @Override
            public void endVertex() {
            }

            @Override
            public void defaultColor(int r, int g, int bl, int a) {
            }

            @Override
            public void unsetDefaultColor() {
            }
         };
         draw(e, new PoseStack(), type -> rec, LightTexture.FULL_BRIGHT);
         if (verts.isEmpty()) {
            float w = e.getBbWidth() * 0.5F;
            return new Visual(new float[] {-w, 0, -w, w, e.getBbHeight(), w}, new float[] {0, e.getBbHeight(), 0});
         }
         float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
         for (float[] p : verts) {
            for (int i = 0; i < 3; i++) {
               b[i] = Math.min(b[i], p[i]);
               b[i + 3] = Math.max(b[i + 3], p[i]);
            }
         }
         float cx = (b[0] + b[3]) * 0.5F, cz = (b[2] + b[5]) * 0.5F;
         // surface du dessus au droit du centre : face la plus haute que la verticale (cx, cz) traverse
         float top = Float.NaN;
         for (int i = 0; i + 3 < verts.size(); i += 4) {
            float y = crossY(verts.get(i), verts.get(i + 1), verts.get(i + 2), verts.get(i + 3), cx, cz);
            if (!Float.isNaN(y) && (Float.isNaN(top) || y > top)) top = y;
         }
         return new Visual(b, new float[] {cx, Float.isNaN(top) ? b[4] : top, cz});
      });
   }

   /** Hauteur où la verticale (x, z) traverse la face (quadrilatère plan), NaN si elle ne la traverse pas. */
   private static float crossY(float[] a, float[] b, float[] c, float[] d, float x, float z) {
      float[][] q = {a, b, c, d};
      int pos = 0, neg = 0;
      for (int i = 0; i < 4; i++) {
         float[] p0 = q[i], p1 = q[(i + 1) % 4];
         float cr = (p1[0] - p0[0]) * (z - p0[2]) - (p1[2] - p0[2]) * (x - p0[0]);
         if (cr > 1e-6F) pos++;
         else if (cr < -1e-6F) neg++;
      }
      if (pos > 0 && neg > 0) return Float.NaN;
      // plan de la face : y au point (x, z)
      float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = d[0] - a[0], vy = d[1] - a[1], vz = d[2] - a[2];
      float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
      if (Math.abs(ny) < 1e-6F) return Float.NaN;
      return a[1] - (nx * (x - a[0]) + nz * (z - a[2])) / ny;
   }

   private static void draw(Entity e, PoseStack ps, MultiBufferSource buffers, int light) {
      EntityRenderDispatcher disp = Minecraft.getInstance().getEntityRenderDispatcher();
      boolean boxes = disp.shouldRenderHitBoxes();
      disp.setRenderShadow(false);
      disp.setRenderHitBoxes(false);
      try {
         disp.render(e, 0, 0, 0, 0F, 1F, ps, buffers, light);
      } finally {
         disp.setRenderShadow(true);
         disp.setRenderHitBoxes(boxes);
      }
   }
}
