package fr.heralisz.sylvorn.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.sylvorn.HeraliszSylvorn;
import fr.heralisz.sylvorn.client.vfx.VfxDraw;
import fr.heralisz.sylvorn.client.vfx.VfxRenderTypes;
import fr.heralisz.sylvorn.dungeon.HeroKind;
import fr.heralisz.sylvorn.dungeon.HeroPropBE;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Rendu des décors « héros » du Nid (un seul rendu pour tous : modèle, texture et lueur d'après le type du bloc). */
public class HeroPropRenderer extends GeoBlockRenderer<HeroPropBE> {
   private static final ResourceLocation GLOW = new ResourceLocation(HeraliszSylvorn.MODID, "textures/vfx/glow.png");
   private static ResourceLocation r(String p) {
      return new ResourceLocation(HeraliszSylvorn.MODID, p);
   }

   public HeroPropRenderer() {
      super(new GeoModel<HeroPropBE>() {
         @Override
         public ResourceLocation getModelResource(HeroPropBE be) {
            return r("geo/block/" + be.kind().id + ".geo.json");
         }

         @Override
         public ResourceLocation getTextureResource(HeroPropBE be) {
            // D-054 : textures GeckoLib rangees dans textures/entity/prop (hors de l'atlas des blocs, qui ne prend que textures/block)
            return r("textures/entity/prop/" + be.kind().id + ".png");
         }

         @Override
         public ResourceLocation getAnimationResource(HeroPropBE be) {
            return r("animations/block/" + be.kind().id + ".animation.json");
         }
      });
      addRenderLayer(glowLayer = new GlowLayer(this));
   }

   private final GlowLayer glowLayer;

   private final class GlowLayer extends AutoGlowingGeoLayer<HeroPropBE> {
      GlowLayer(HeroPropRenderer r) {
         super(r);
      }

      @Override
      public void render(PoseStack ps, HeroPropBE be, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, float partial,
                         int light, int overlay) {
         if (baking) {                                                    // précalcul : la lueur seule, dans son propre tampon
            if (be.kind().glow) super.render(ps, be, model, rt, buf, vc, partial, light, overlay);
            return;
         }
         if (be.kind().glow) super.render(ps, be, model, rt, buf, vc, partial, light, overlay);
      }
   }

   // ================================================================ précalcul des décors fixes
   /** Décors dont seul le feu bouge : la maçonnerie est précalculée, les flammes sont des sprites. */
   private static boolean fireOnly(HeroKind k) {
      return k == HeroKind.CANDLES || k == HeroKind.SKULL_SMALL;
   }

   private static boolean cacheable(HeroKind k) {
      return !k.animated || fireOnly(k);
   }

   /** Précalculable dans cet état : fixe ; le cocon déchiré (pose tenue) ; les grappes et les pendus qui oscillent, au-delà de
    *  14 blocs (on ne voit plus leur balancement). */
   private static boolean cacheable(HeroPropBE be) {
      HeroKind k = be.kind();
      if (cacheable(k)) return true;
      if (k == HeroKind.BARK || k == HeroKind.ROOT_DOOR) return false;
      net.minecraft.world.phys.Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
      return be.getBlockPos().distToCenterSqr(cam) > 14 * 14;
   }

   private static Boolean shadersMod;

   private static boolean cacheOn() {
      if (shadersMod == null) {
         net.minecraftforge.fml.ModList ml = net.minecraftforge.fml.ModList.get();
         shadersMod = ml.isLoaded("oculus") || ml.isLoaded("iris");
      }
      return !shadersMod;
   }

   private record Flame(float x, float y, float z, float w, float h, long seed) {}

   private static final class Baked {
      VertexBuffer solid, glow;
      RenderType solidType, glowType;
      final List<Flame> flames = new ArrayList<>();
      int light = -1;
      long bakedAt, usedAt;

      void close() {
         if (solid != null) solid.close();
         if (glow != null) glow.close();
         solid = glow = null;
      }
   }

   private static BufferBuilder SOLID_BUF, GLOW_BUF;
   private final Map<Long, Baked> baked = new HashMap<>();
   private boolean baking;
   private List<Flame> flameSink;
   private MultiBufferSource frameBuf;
   private float framePartial;
   private long frame;

   @Override
   public void defaultRender(PoseStack ps, HeroPropBE be, MultiBufferSource buf, RenderType rt0, VertexConsumer vc0, float yaw, float partial, int light) {
      int overlay = OverlayTexture.NO_OVERLAY;
      frameBuf = buf;
      framePartial = partial;
      HeroKind k = be.kind();
      if (be.getLevel() == null || !cacheable(be) || !cacheOn()) {
         super.defaultRender(ps, be, buf, rt0, vc0, yaw, partial, light);
         return;
      }
      long now = be.getLevel().getGameTime();
      if (now != frame) {                                                   // ménage : ce qui n'est plus dessiné depuis 30 s
         frame = now;
         if (now % 200 == 0) {
            for (Iterator<Baked> it = baked.values().iterator(); it.hasNext(); ) {
               Baked b = it.next();
               if (now - b.usedAt > 600 || now < b.usedAt) {
                  b.close();
                  it.remove();
               }
            }
         }
      }
      long key = be.getBlockPos().asLong() * 31 + be.getBlockState().hashCode();
      Baked b = baked.get(key);
      if (b != null && Math.abs(net.minecraft.client.renderer.LightTexture.block(b.light) - net.minecraft.client.renderer.LightTexture.block(light)) >= 2 && now - b.bakedAt > 40) {         // la lumière a changé (torches allumées) : on refait
         b.close();
         baked.remove(key);
         b = null;
      }
      if (b == null) {
         b = bake(be, light);
         if (b == null) {
            super.defaultRender(ps, be, buf, rt0, vc0, yaw, partial, light);
            return;
         }
         baked.put(key, b);
      }
      b.usedAt = now;
      draw(b.solidType, b.solid, ps);
      if (b.glow != null) draw(b.glowType, b.glow, ps);
      if (fireOnly(k) ? fr.heralisz.sylvorn.dungeon.HeroPropBlock.lit(be.getBlockState()) : true)
         for (Flame f : b.flames) FireQuads.draw(ps, buf, f.x, f.y, f.z, f.w, f.h, false, f.seed, partial);
   }

   private Baked bake(HeroPropBE be, int light) {
      BakedGeoModel model = getGeoModel().getBakedModel(getGeoModel().getModelResource(be));
      if (model == null) return null;
      Baked b = new Baked();
      b.light = light;
      b.bakedAt = be.getLevel().getGameTime();
      ResourceLocation tex = getTextureLocation(be);
      b.solidType = getRenderType(be, tex, null, 0);
      if (SOLID_BUF == null) {                                             // deux tampons réutilisés : aucune fuite de mémoire native
         SOLID_BUF = new BufferBuilder(1 << 21);
         GLOW_BUF = new BufferBuilder(1 << 18);
      }
      BufferBuilder solid = SOLID_BUF, glow = GLOW_BUF;
      solid.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
      glow.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
      RenderType[] glowType = new RenderType[1];
      MultiBufferSource capture = rt -> {
         glowType[0] = rt;
         return glow;
      };
      PoseStack ps = new PoseStack();
      baking = true;
      flameSink = b.flames;
      try {
         actuallyRender(ps, be, model, b.solidType, capture, solid, false, 0, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
         if (be.kind().glow) glowLayer.render(ps, be, model, b.solidType, capture, solid, 0, light, OverlayTexture.NO_OVERLAY);
      } finally {
         baking = false;
         flameSink = null;
      }
      b.solid = upload(solid.end());
      BufferBuilder.RenderedBuffer g = glow.end();
      if (glowType[0] != null) {
         b.glowType = glowType[0];
         b.glow = upload(g);
      } else g.release();
      return b;
   }

   private static VertexBuffer upload(BufferBuilder.RenderedBuffer rb) {
      VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
      vb.bind();
      vb.upload(rb);
      VertexBuffer.unbind();
      return vb;
   }

   /** Le tampon précalculé, dessiné tout de suite avec la matrice de la case. Ses normales sont dans le repère du monde : on
    *  y remet les deux lumières du monde le temps du tracé, puis celles de la scène. */
   private static void draw(RenderType rt, VertexBuffer vb, PoseStack ps) {
      if (vb == null) return;
      rt.setupRenderState();
      ShaderInstance sh = RenderSystem.getShader();
      if (sh != null) {
         RenderSystem.setShaderLights(new Vector3f(0.2F, 1.0F, -0.7F).normalize(), new Vector3f(-0.2F, 1.0F, 0.7F).normalize());
         Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(ps.last().pose());
         vb.bind();
         vb.drawWithShader(mv, RenderSystem.getProjectionMatrix(), sh);
         VertexBuffer.unbind();
         com.mojang.blaze3d.platform.Lighting.setupLevel(new Matrix4f(ps.last().pose()).setTranslation(0, 0, 0));
      }
      rt.clearRenderState();
   }

   // ================================================================ les flammes : des sprites, plus des cubes qui grossissent
   @Override
   public void renderRecursively(PoseStack ps, HeroPropBE be, GeoBone bone, RenderType rt, MultiBufferSource buf, VertexConsumer vc, boolean isReRender,
                                 float partial, int light, int overlay, float red, float green, float blue, float alpha) {
      if (bone.getName().startsWith("fire")) {
         if (isReRender || bone.isHidden()) return;
         if (baking) {
            if (flameSink != null && bone.getScaleY() >= 0.05F) collectFlames(bone, ps.last().pose(), flameSink);    // repère de la case
            return;
         }
         if (bone.getScaleY() < 0.05F || frameBuf == null) return;
         List<Flame> out = new ArrayList<>();
         collectFlames(bone, null, out);                                   // repère de ps (l'os parent)
         for (Flame f : out) FireQuads.draw(ps, frameBuf, f.x, f.y, f.z, f.w, f.h, false, f.seed, framePartial);
         return;
      }
      super.renderRecursively(ps, be, bone, rt, buf, vc, isReRender, partial, light, overlay, red, green, blue, alpha);
   }

   /** Les cubes d'un os de feu, regroupés par colonne (une bougie = trois cubes empilés = une flamme), dans le repère de ps. */
   private static void collectFlames(GeoBone bone, Matrix4f m, List<Flame> out) {
      List<float[]> cols = new ArrayList<>();                            // x, z, y0, y1, w
      for (GeoCube c : bone.getCubes()) {
         float x0 = 1e9F, y0 = 1e9F, z0 = 1e9F, x1 = -1e9F, y1 = -1e9F, z1 = -1e9F;
         for (GeoQuad q : c.quads()) {
            if (q == null) continue;
            for (GeoVertex v : q.vertices()) {
               Vector3f p = m != null ? m.transformPosition(new Vector3f(v.position())) : new Vector3f(v.position());
               x0 = Math.min(x0, p.x); y0 = Math.min(y0, p.y); z0 = Math.min(z0, p.z);
               x1 = Math.max(x1, p.x); y1 = Math.max(y1, p.y); z1 = Math.max(z1, p.z);
            }
         }
         if (x0 > x1) continue;
         float cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, w = Math.max(x1 - x0, z1 - z0);
         float[] hit = null;
         for (float[] col : cols) {
            if (Math.abs(col[0] - cx) < 0.12F && Math.abs(col[1] - cz) < 0.12F) {
               hit = col;
               break;
            }
         }
         if (hit == null) cols.add(new float[] {cx, cz, y0, y1, w});
         else {
            hit[2] = Math.min(hit[2], y0);
            hit[3] = Math.max(hit[3], y1);
            hit[4] = Math.max(hit[4], w);
         }
      }
      for (float[] c : cols) {
         float h = Math.max(0.12F, (c[3] - c[2]) * 1.35F);
         float w = Math.max(c[4] * 2.0F, h * 0.42F);
         long seed = Float.floatToIntBits(c[0] * 7.1F + c[1] * 3.3F);
         out.add(new Flame(c[0], c[2] - 0.02F, c[1], w, h, seed));
      }
   }

   @Override
   protected void rotateBlock(Direction facing, PoseStack ps) {
      float deg = switch (facing) {
         case SOUTH -> 180F;
         case WEST -> 90F;
         case EAST -> 270F;
         default -> 0F;
      };
      ps.mulPose(Axis.YP.rotationDegrees(deg));
   }

   /** Grands décors (architecture, rosace, lustres...) : dessinés même si leur tronçon de monde est hors champ (ils le débordent),
    *  mais écartés à la main s'ils sont derrière la caméra ou trop loin. Petits décors : culling normal du jeu (tronçon + boîte). */
   @Override
   public boolean shouldRenderOffScreen(HeroPropBE be) {
      return be.kind().viewDistance > 100;
   }

   @Override
   public boolean shouldRender(HeroPropBE be, net.minecraft.world.phys.Vec3 cam) {
      HeroKind k = be.kind();
      net.minecraft.world.phys.AABB box = be.getRenderBoundingBox();
      net.minecraft.world.phys.Vec3 c = box.getCenter();
      double r = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())) * 0.5;
      double d = c.distanceTo(cam) - r;
      if (d > k.viewDistance) return false;
      if (d > 4) {                                                     // derrière la caméra : inutile de le dessiner
         net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
         org.joml.Vector3f look = camera.getLookVector();
         double dot = (c.x - cam.x) * look.x() + (c.y - cam.y) * look.y() + (c.z - cam.z) * look.z();
         if (dot < -r) return false;
      }
      return d < 6 || !occluded(be, box, cam);
   }

   // ---------------------------------------------------------------- occlusion : un décor caché derrière des murs n'est pas dessiné
   private static final java.util.WeakHashMap<HeroPropBE, long[]> OCC = new java.util.WeakHashMap<>();

   /** Vrai si aucun des points témoins du décor (centre, sommet, bords) n'est visible depuis la caméra : les murs de la
    *  cathédrale cachent tout l'intérieur quand on est dehors (réévalué tous les 5 ticks, lancers de rayons « visuels »). */
   private static boolean occluded(HeroPropBE be, net.minecraft.world.phys.AABB box, net.minecraft.world.phys.Vec3 cam) {
      net.minecraft.world.level.Level lv = be.getLevel();
      if (lv == null) return false;
      long now = lv.getGameTime();
      long[] e = OCC.get(be);
      if (e != null && now - e[0] < 5 && now >= e[0]) return e[1] != 0;
      net.minecraft.world.phys.Vec3 c = box.getCenter();
      double hx = Math.min(box.getXsize() * 0.4, 6), hz = Math.min(box.getZsize() * 0.4, 6);
      net.minecraft.world.phys.Vec3[] pts = {c, new net.minecraft.world.phys.Vec3(c.x, box.maxY - 0.4, c.z),
         net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()), c.add(hx, 0, 0), c.add(-hx, 0, 0), c.add(0, 0, hz), c.add(0, 0, -hz)};
      boolean hidden = true;
      for (net.minecraft.world.phys.Vec3 p : pts) {
         double dist = p.distanceTo(cam);
         net.minecraft.world.phys.BlockHitResult hit = lv.clip(new net.minecraft.world.level.ClipContext(cam, p,
            net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE,
            net.minecraft.client.Minecraft.getInstance().player));
         if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getLocation().distanceTo(cam) > dist - 1.8) {
            hidden = false;
            break;
         }
      }
      OCC.put(be, new long[] {now, hidden ? 1 : 0});
      return hidden;
   }

   @Override
   public int getViewDistance() {
      return 192;
   }
}
