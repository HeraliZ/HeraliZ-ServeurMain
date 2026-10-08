package fr.heralisz.n07.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.client.vfx.VfxDraw;
import fr.heralisz.n07.client.vfx.VfxRenderTypes;
import fr.heralisz.n07.dungeon.PropBlockEntity;
import fr.heralisz.n07.dungeon.PropKind;
import fr.heralisz.n07.dungeon.RadChannelBlock;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Décors animés des donjons. La buse d'évacuation crache un jet continu de liquide radioactif : un tube qui suit la parabole de
 * la chute (texture qui défile dans le sens du courant, cœur vif + halo), qui s'élargit en tombant et s'écrase dans une
 * couronne d'écume là où il touche le sol (la rigole ou le bassin en dessous). La cuve géante est dessinée en deux passes :
 * tout sauf le verre, puis le verre translucide par-dessus le liquide lumineux.
 */
public class PropRenderer extends GeoBlockRenderer<PropBlockEntity> {
   private static final int N = PropKind.values().length;
   private static final ResourceLocation[] GEO = new ResourceLocation[N], TEX = new ResourceLocation[N], ANIM = new ResourceLocation[N];
   private static final ResourceLocation T_STREAM = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/rad_stream.png");
   private static final ResourceLocation T_FOAM = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/rad_foam.png");
   private static final ResourceLocation T_SLUDGE = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/sludge_stream.png");
   private static final ResourceLocation T_GLOW = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/glow.png");
   private static final ResourceLocation T_CONE = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/light_cone.png");
   private static final ResourceLocation T_SILK = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/silk.png");
   private static final ResourceLocation T_CHAIN = new ResourceLocation("minecraft", "textures/block/chain.png");
   private static final ResourceLocation T_VINE = new ResourceLocation("minecraft", "textures/block/cave_vines_plant.png");
   /** Sortie de la buse : distance au centre du bloc (vers l'avant) et hauteur sous l'axe du tuyau. */
   public static final double MOUTH = 1.12, LIP = 0.42;
   /** Vitesse de sortie (blocs/tick) et gravité du liquide. */
   public static final double V0 = 0.12, GRAV = 0.045;

   static {
      for (PropKind k : PropKind.values()) {
         GEO[k.ordinal()] = new ResourceLocation(HeraliszN07.MODID, "geo/block/" + k.model() + ".geo.json");
         TEX[k.ordinal()] = new ResourceLocation(HeraliszN07.MODID, "textures/entity/prop/" + k.model() + ".png");
         ANIM[k.ordinal()] = new ResourceLocation(HeraliszN07.MODID, "animations/block/" + k.model() + ".animation.json");
      }
      // la statue reprend le modèle et les animations du Chevalier, taillés dans la pierre
      int s = PropKind.KNIGHT_STATUE.ordinal();
      GEO[s] = new ResourceLocation(HeraliszN07.MODID, "geo/entity/faceless_knight.geo.json");
      TEX[s] = new ResourceLocation(HeraliszN07.MODID, "textures/entity/faceless_knight_stone.png");
      ANIM[s] = new ResourceLocation(HeraliszN07.MODID, "animations/entity/faceless_knight.animation.json");
   }

   /** Os du Chevalier : ceux de la statue (le reste — racines, flammes, yeux, heaume brisé — n'est pas sculpté). */
   private static final String[] STATUE_SHOW = {"body", "torso", "head", "helm", "cape", "arm_r", "pauldron_r", "forearm_r", "hand_r", "sword", "arm_l",
      "pauldron_l", "forearm_l", "hand_l", "thigh_r", "shin_r", "foot_r", "thigh_l", "shin_l", "foot_l"};
   private static final String[] STATUE_HIDE = {"helm_broken", "eyes", "blaze", "cape_burnt", "chest_roots", "blade_fire", "shoulder_roots", "roots"};

   private enum Pass { ALL, SOLID, LIQUID, GLASS }

   private Pass pass = Pass.ALL;

   public PropRenderer() {
      super(new Model());
      addRenderLayer(new AutoGlowingGeoLayer<>(this) {
         @Override
         public void render(PoseStack ps, PropBlockEntity be, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, float partial,
                            int light, int overlay) {
            if (!be.kind.glows()) return;
            if (!be.kind.lamp()) {
               super.render(ps, be, model, rt, buf, vc, partial, light, overlay);
               return;
            }
            // luminaire : la lueur suit le grésillement / la flamme / la pulsation
            float k = lampGlow(be, partial);
            if (k < 0.02F) return;
            RenderType e = getRenderType(be);
            getRenderer().reRender(model, ps, buf, be, e, buf.getBuffer(e), partial, LightTexture.FULL_BRIGHT, overlay, k, k, k, 1);
         }
      });
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

   @Override
   public boolean shouldRenderOffScreen(PropBlockEntity be) {
      // 1.1.0 : soumis a l occlusion des sections (ne se dessine plus a travers la roche, ex. depuis la carte au-dessus)
      return be.kind.giantFan();
   }

   @Override
   public int getViewDistance() {
      return 64;
   }

   // ------------------------------------------------------------------ cuve : verre en seconde passe
   @Override
   public void actuallyRender(PoseStack ps, PropBlockEntity be, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc,
                              boolean reRender, float partial, int light, int overlay, float r, float g, float b, float a) {
      if (!(be.kind.vat() || be.kind.tank()) || reRender) {
         super.actuallyRender(ps, be, model, rt, buf, vc, reRender, partial, light, overlay, r, g, b, a);
         return;
      }
      pass = Pass.SOLID;
      super.actuallyRender(ps, be, model, rt, buf, vc, false, partial, light, overlay, r, g, b, a);
      // au loin (> 40 blocs), les passes translucides ne se voient plus : on les saute (deux parcours du modèle en moins)
      if (net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 40 * 40) {
         pass = Pass.ALL;
         return;
      }
      // le liquide (lumineux, translucide) puis le verre par-dessus : types translucides de Minecraft (triés, bien mélangés)
      pass = Pass.LIQUID;
      RenderType lq = RenderType.entityTranslucentEmissive(TEX[be.kind.ordinal()]);
      super.actuallyRender(ps, be, model, lq, buf, buf.getBuffer(lq), true, partial, LightTexture.FULL_BRIGHT, overlay, 1, 1, 1, 1);
      pass = Pass.GLASS;
      RenderType gl = RenderType.entityTranslucent(TEX[be.kind.ordinal()]);
      super.actuallyRender(ps, be, model, gl, buf, buf.getBuffer(gl), true, partial, light, overlay, 1, 1, 1, 1);
      pass = Pass.ALL;
   }

   /** Verre et liquide sont translucides (on voit le minerai et les bulles au travers) ; le liquide luit par lui-même. */
   @Override
   public void renderCubesOfBone(PoseStack ps, GeoBone bone, VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
      String n = bone.getName();
      boolean liquid = n.equals("liquid");
      boolean trans = liquid || n.startsWith("glass");
      if (pass == Pass.SOLID && trans || pass == Pass.LIQUID && !liquid || pass == Pass.GLASS && (!trans || liquid)) return;
      if (tankNow != null && n.startsWith("coil_glow_") && pass != Pass.GLASS) {
         int k = n.charAt(n.length() - 1) - '0';
         if (k >= 0 && k < 4) {
            coilPose[k] = new Matrix4f(ps.last().pose());
            coilPivot[k] = new org.joml.Vector3f(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ());
         }
      }
      if (fanNow != null && n.startsWith("blade_")) {
         int k = n.charAt(n.length() - 1) - '0';
         if (k >= 0 && k < 3) {
            bladePose[k] = new Matrix4f(ps.last().pose());
            bladePivot[k] = new org.joml.Vector3f(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ());
         }
      }
      if (lampNow != null && n.equals("bulb")) {
         // ampoule éteinte : elle redevient du verre terne
         float k = 0.3F + 0.7F * lampK;
         super.renderCubesOfBone(ps, bone, buf, light, overlay, r * k, g * k, b * k, a);
         return;
      }
      if (liquid) return;   // le liquide est dessiné en direct (liquidColumn) : lumineux, translucide, animé
      super.renderCubesOfBone(ps, bone, buf, light, overlay, r, g, b, a);
   }

   // ------------------------------------------------------------------ jet de la buse
   @Override
   public void defaultRender(PoseStack ps, PropBlockEntity be, MultiBufferSource buf, RenderType rt, VertexConsumer vc, float yaw, float partial, int light) {
      if (be.kind.lamp()) {
         // au-delà de 48 blocs on ne verrait ni le balancement ni le grésillement : on ne dessine pas (FPS)
         var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
         if (cam.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 48 * 48) return;
         lamp(be, partial, ps, buf, light, rt, vc, yaw);
         return;
      }
      if (be.kind.giantFan()) {
         var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
         if (cam.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 96 * 96) return;
         Matrix4f root = new Matrix4f(ps.last().pose());
         fanNow = be;
         java.util.Arrays.fill(bladePose, null);
         super.defaultRender(ps, be, buf, rt, vc, yaw, partial, light);
         fanNow = null;
         giantShafts(be, partial, ps, root, buf);
         return;
      }
      if (be.kind.tank()) {
         // le bloc de la cuve est dans le sol : le modèle pose dessus
         TankView v = TankView.of(be, partial);
         TANK = v;
         tankNow = be;
         java.util.Arrays.fill(coilPose, null);
         ps.pushPose();
         ps.translate(0, 1, 0);
         Matrix4f root = new Matrix4f(ps.last().pose());
         int lit = net.minecraft.client.renderer.LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above(3));
         if (v.level > 0.01F) {
            // le liquide AVANT le modèle (le verre écrit la profondeur)
            float t = be.getLevel().getGameTime() + partial;
            liquidColumn(ps, buf, 2.9F, 1.05F, 1.05F + (9.55F - 1.05F) * v.level, 0x62D424, 0.6F + 0.25F * v.charge, t, false);
         }
         super.defaultRender(ps, be, buf, rt, vc, yaw, partial, lit);
         tankNow = null;
         tankFx(be, v, partial, ps, root, buf);
         ps.popPose();
         return;
      }
      if (be.kind.vat()) {
         // le liquide AVANT le modèle : le verre écrit la profondeur, ce qui est dessiné derrière lui ensuite disparaîtrait
         Level l = be.getLevel();
         float t = (l == null ? 0 : l.getGameTime()) + partial;
         liquidColumn(ps, buf, 1.88F, 0.9F, 5.3F, be.kind.sludge() ? 0x74842A : 0x62D424, be.kind.sludge() ? 0.55F : 0.75F, t, be.kind.sludge());
      }
      super.defaultRender(ps, be, buf, rt, vc, yaw, partial, light);
      if (be.kind.pipe()) stream(be, partial, ps, buf);
      if (be.kind.fan()) fanShaft(be, partial, ps, buf);
   }

   // ------------------------------------------------------------------ cuve de confinement du Sujet N-07
   /** État de la cuve en cours de rendu (lu par le modèle pour piloter ses os). */
   static TankView TANK;
   private PropBlockEntity tankNow;
   private final Matrix4f[] coilPose = new Matrix4f[4];
   /** Ventilateur géant en cours de rendu : pose des pales (repère caméra) et leurs pivots, relevés au passage. */
   private static PropBlockEntity fanNow;
   private final Matrix4f[] bladePose = new Matrix4f[3];
   private final org.joml.Vector3f[] bladePivot = new org.joml.Vector3f[3];
   private static final Map<PropBlockEntity, float[]> FAN_FLOOR = new WeakHashMap<>();

   /** Angle du rotor du ventilateur géant (radians) : un tour en 6 s. */
   static float giantFanAngle(PropBlockEntity be, float partial) {
      Level l = be.getLevel();
      float t = (l == null ? 0 : l.getGameTime() % 120000L) + partial;
      return t / 120F * Mth.TWO_PI;
   }

   /** Distance (blocs) du ventilateur au sol sous lui (mise en cache). */
   private static float fanDrop(PropBlockEntity be) {
      Level l = be.getLevel();
      long now = l == null ? 0 : l.getGameTime();
      float[] c = FAN_FLOOR.get(be);
      if (c != null && now - (long) c[1] < 400 && now >= (long) c[1]) return c[0];
      float drop = 24F;
      if (l != null) {
         BlockPos.MutableBlockPos m = be.getBlockPos().mutable();
         for (int i = 1; i <= 48; i++) {
            m.setY(be.getBlockPos().getY() - i);
            if (!l.getBlockState(m).getCollisionShape(l, m).isEmpty()) {
               drop = i - 1;
               break;
            }
         }
      }
      FAN_FLOOR.put(be, new float[] {drop, now});
      return drop;
   }

   /**
    * Puits de lumière du ventilateur géant : trois secteurs de lumière poussiéreuse qui descendent jusqu'au sol dans les
    * ouvertures entre les pales (l'angle des pales est relu sur le modèle rendu), et leurs taches de lumière au sol.
    */
   private void giantShafts(PropBlockEntity be, float partial, PoseStack ps, Matrix4f root, MultiBufferSource buf) {
      Matrix4f inv = new Matrix4f(root).invert();
      float[] ang = new float[3];
      for (int k = 0; k < 3; k++) {
         if (bladePose[k] == null) return;
         org.joml.Vector3f p = bladePivot[k];
         org.joml.Vector4f w = inv.transform(bladePose[k].transform(new org.joml.Vector4f(p.x / 16F, p.y / 16F, p.z / 16F, 1)));
         ang[k] = (float) Math.atan2(w.z - 0.5, w.x - 0.5);
      }
      java.util.Arrays.sort(ang);
      float drop = fanDrop(be);
      float top = 0.3F, h = drop + top;
      float r0 = 3.45F, r1 = r0 + h * 0.06F;
      float half = (float) Math.toRadians(19);
      int col = 0xD6E8F4;
      VertexConsumer cone = buf.getBuffer(VfxRenderTypes.glow(T_CONE));
      VertexConsumer pool = buf.getBuffer(VfxRenderTypes.glow(T_GLOW));
      Matrix4f m = ps.last().pose();
      // le jour au fond du puits, derrière les pales (dessiné après le modèle : les pales le découpent)
      float t = be.getLevel() == null ? 0 : be.getLevel().getGameTime() + partial;
      VfxDraw.disc(pool, m, 0.5, 0.955, 0.5, 3.45, t * 0.002F, VfxDraw.argb(0xEAF4FF, 0.9F));
      VfxDraw.disc(pool, m, 0.5, 0.95, 0.5, 5.2, -t * 0.001F, VfxDraw.argb(0xC8DCEB, 0.35F));
      int seg = 8;
      for (int k = 0; k < 3; k++) {
         float a0 = ang[k] + half, a1 = (k < 2 ? ang[k + 1] : ang[0] + Mth.TWO_PI) - half;
         if (a1 <= a0) continue;
         int topC = VfxDraw.argb(col, 0.26F), botC = VfxDraw.argb(col, 0.0F);
         for (int i = 0; i < seg; i++) {
            float b0 = a0 + (a1 - a0) * i / seg, b1 = a0 + (a1 - a0) * (i + 1) / seg;
            float u0 = (float) i / seg, u1 = (float) (i + 1) / seg;
            // paroi extérieure du secteur
            VfxDraw.v(cone, m, 0.5 + Mth.cos(b0) * r0, top, 0.5 + Mth.sin(b0) * r0, u0, 0, topC);
            VfxDraw.v(cone, m, 0.5 + Mth.cos(b1) * r0, top, 0.5 + Mth.sin(b1) * r0, u1, 0, topC);
            VfxDraw.v(cone, m, 0.5 + Mth.cos(b1) * r1, top - h, 0.5 + Mth.sin(b1) * r1, u1, 1, botC);
            VfxDraw.v(cone, m, 0.5 + Mth.cos(b0) * r1, top - h, 0.5 + Mth.sin(b0) * r1, u0, 1, botC);
            // tache de lumière au sol (le secteur, en éventail)
            int poolC = VfxDraw.argb(col, 0.34F);
            float rr = r1 * 1.02F, fy = top - h + 0.03F;
            VfxDraw.v(pool, m, 0.5, fy, 0.5, 0.5F, 0.5F, poolC);
            VfxDraw.v(pool, m, 0.5, fy, 0.5, 0.5F, 0.5F, poolC);
            VfxDraw.v(pool, m, 0.5 + Mth.cos(b1) * rr, fy, 0.5 + Mth.sin(b1) * rr, 0.5F + Mth.cos(b1) * 0.5F, 0.5F + Mth.sin(b1) * 0.5F, poolC);
            VfxDraw.v(pool, m, 0.5 + Mth.cos(b0) * rr, fy, 0.5 + Mth.sin(b0) * rr, 0.5F + Mth.cos(b0) * 0.5F, 0.5F + Mth.sin(b0) * 0.5F, poolC);
         }
         // les deux flancs radiaux, plus pâles (on voit le faisceau de côté)
         for (float e : new float[] {a0, a1}) {
            int sideC = VfxDraw.argb(col, 0.1F);
            VfxDraw.v(cone, m, 0.5 + Mth.cos(e) * 0.8, top, 0.5 + Mth.sin(e) * 0.8, 0, 0, sideC);
            VfxDraw.v(cone, m, 0.5 + Mth.cos(e) * r0, top, 0.5 + Mth.sin(e) * r0, 1, 0, sideC);
            VfxDraw.v(cone, m, 0.5 + Mth.cos(e) * r1, top - h, 0.5 + Mth.sin(e) * r1, 1, 1, botC);
            VfxDraw.v(cone, m, 0.5 + Mth.cos(e) * 0.8, top - h, 0.5 + Mth.sin(e) * 0.8, 0, 1, botC);
         }
      }
   }
   private final org.joml.Vector3f[] coilPivot = new org.joml.Vector3f[4];
   private static final ResourceLocation T_ARC = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/beam_core.png");
   private static fr.heralisz.n07.entity.SujetN07 cloneDummy;
   /** Rayon du verre (blocs) et hauteur utile. */
   private static final float TANK_R = 3.0F, TANK_Y0 = 1.0F, TANK_Y1 = 10.0F;

   /** Arcs électriques des bobines vers le verre (et, au moment des coups, jusqu'au sujet), puis le clone qui grandit. */
   private void tankFx(PropBlockEntity be, TankView v, float partial, PoseStack ps, Matrix4f root, MultiBufferSource buf) {
      Level l = be.getLevel();
      if (l == null) return;
      float now = l.getGameTime() + partial;
      if (v.charge > 0.04F) {
         Matrix4f inv = new Matrix4f(root).invert();
         org.joml.Vector4f camH = inv.transform(new org.joml.Vector4f(0, 0, 0, 1));
         Vec3 cam = new Vec3(camH.x, camH.y, camH.z);
         VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glow(T_ARC));
         VertexConsumer halo = buf.getBuffer(VfxRenderTypes.glow(T_GLOW));
         long tick = (long) (now / 2);
         boolean hit = v.spawnT >= 0 && (Math.abs(v.spawnT - fr.heralisz.n07.entity.SujetN07.SPAWN_HIT1) < 3
            || Math.abs(v.spawnT - fr.heralisz.n07.entity.SujetN07.SPAWN_HIT2) < 3 || Math.abs(v.spawnT - fr.heralisz.n07.entity.SujetN07.SPAWN_BREAK) < 3);
         for (int k = 0; k < 4; k++) {
            if (coilPose[k] == null) continue;
            java.util.Random rng = new java.util.Random(tick * 7919L + k * 104729L + be.getBlockPos().asLong());
            if (!hit && rng.nextFloat() > 0.2F + 0.65F * v.charge) continue;
            org.joml.Vector3f p = coilPivot[k];
            float hx = -p.x, hz = -p.z, hl = (float) Math.sqrt(hx * hx + hz * hz);
            if (hl < 1e-3) continue;
            org.joml.Vector4f tipCam = coilPose[k].transform(new org.joml.Vector4f((p.x + hx / hl * 8F) / 16F, (p.y + 30F) / 16F, (p.z + hz / hl * 8F) / 16F, 1));
            org.joml.Vector4f tipL = inv.transform(tipCam);
            Vec3 tip = new Vec3(tipL.x, tipL.y, tipL.z);
            Vec3 c = new Vec3(0.5, tip.y, 0.5);
            Vec3 out = tip.subtract(c).multiply(1, 0, 1).normalize();
            int arcs = hit ? 3 : 1 + (v.charge > 0.6F ? 1 : 0);
            for (int j = 0; j < arcs; j++) {
               double ang = Math.atan2(out.z, out.x) + (rng.nextDouble() - 0.5) * 0.9;
               double y = Mth.clamp(tip.y + (rng.nextDouble() - 0.5) * 3.0, TANK_Y0 + 0.5, TANK_Y1 - 0.5);
               Vec3 end = new Vec3(0.5 + Math.cos(ang) * TANK_R, y, 0.5 + Math.sin(ang) * TANK_R);
               if (hit && j == 0) end = new Vec3(0.5 + out.x * 0.8, 5.0 + rng.nextDouble(), 0.5 + out.z * 0.8);
               arc(vc, root, cam, tip, end, rng, 0.07F + 0.05F * v.charge, VfxDraw.argb(0xE6F2FF, 0.95F));
               arc(vc, root, cam, tip, end, rng, 0.22F, VfxDraw.argb(0x5A9CFF, 0.35F * v.charge + (hit ? 0.3F : 0)));
               billboard(halo, ps, end.x, end.y, end.z, 1.2F + (hit ? 1.4F : 0), VfxDraw.argb(0x9CC8FF, 0.55F));
            }
            billboard(halo, ps, tip.x, tip.y, tip.z, 1.0F + v.charge, VfxDraw.argb(0x7AB0FF, 0.5F + 0.4F * v.charge));
         }
      }
      // l'explosion : un éclair vert qui remplit la cuve
      if (v.breakAge >= 0 && v.breakAge < 10) {
         float k = 1 - v.breakAge / 10F;
         billboard(buf.getBuffer(VfxRenderTypes.glow(T_GLOW)), ps, 0.5, 5.0, 0.5, 14F * (0.6F + 0.4F * k), VfxDraw.argb(0x9CFF6A, 0.9F * k));
      }
      if (v.clone >= 0) renderClone(be, v.clone, partial, ps, buf);
   }

   /** Éclair en zigzag de a à b (ruban tourné vers la caméra). */
   private static void arc(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, java.util.Random rng, float w, int argb) {
      int n = 8;
      Vec3 d = b.subtract(a);
      double len = d.length();
      Vec3 prev = a;
      for (int i = 1; i <= n; i++) {
         double u = (double) i / n;
         Vec3 q = a.add(d.scale(u));
         if (i < n) {
            double j = len * 0.09 * Math.sin(Math.PI * u);
            q = q.add((rng.nextDouble() - 0.5) * j * 2, (rng.nextDouble() - 0.5) * j * 2, (rng.nextDouble() - 0.5) * j * 2);
         }
         Vec3 seg = q.subtract(prev);
         Vec3 side = seg.cross(cam.subtract(prev));
         if (side.lengthSqr() > 1e-8) {
            side = side.normalize().scale(w / 2);
            VfxDraw.v(vc, m, prev.x - side.x, prev.y - side.y, prev.z - side.z, 0, 0, argb);
            VfxDraw.v(vc, m, prev.x + side.x, prev.y + side.y, prev.z + side.z, 1, 0, argb);
            VfxDraw.v(vc, m, q.x + side.x, q.y + side.y, q.z + side.z, 1, 1, argb);
            VfxDraw.v(vc, m, q.x - side.x, q.y - side.y, q.z - side.z, 0, 1, argb);
         }
         prev = q;
      }
   }

   /** Le clone qui se reforme dans la cuve pendant le délai de réapparition : une silhouette sombre qui grandit dans le liquide. */
   private static void renderClone(PropBlockEntity be, float growth, float partial, PoseStack ps, MultiBufferSource buf) {
      Level l = be.getLevel();
      if (l == null) return;
      if (cloneDummy == null || cloneDummy.level() != l) {
         cloneDummy = HeraliszN07.SUJET_N07.get().create(l);
         if (cloneDummy == null) return;
         cloneDummy.setDormant(true);
      }
      var sh = be.shrine();
      float yaw = sh != null ? sh.bossYaw() : 180F;
      BlockPos p = be.getBlockPos();
      cloneDummy.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
      cloneDummy.setYRot(yaw);
      cloneDummy.yRotO = yaw;
      cloneDummy.yBodyRot = yaw;
      cloneDummy.yBodyRotO = yaw;
      cloneDummy.setYHeadRot(yaw);
      cloneDummy.yHeadRotO = yaw;
      cloneDummy.tickCount = (int) l.getGameTime();
      ps.pushPose();
      ps.translate(0.5, 0.1, 0.5);
      float s = 0.3F + 0.7F * growth;
      ps.scale(s, s, s);
      int light = LightTexture.pack(1 + (int) (3 * growth), 0);
      net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().render(cloneDummy, 0, 0, 0, yaw, partial, ps, buf, light);
      ps.popPose();
   }

   // ------------------------------------------------------------------ luminaires suspendus
   private PropBlockEntity lampNow;
   private float lampK = 1;
   private static final Map<PropBlockEntity, float[]> SPAN = new WeakHashMap<>();

   /** {voûte, sol} en blocs relatifs au bas du bloc (la voûte = dessous du premier bloc solide au-dessus), recalculés de temps en temps. */
   static float[] span(PropBlockEntity be) {
      Level l = be.getLevel();
      long now = l == null ? 0 : l.getGameTime();
      float[] c = SPAN.get(be);
      if (c != null && now - (long) c[2] < 200 && now >= (long) c[2]) return c;
      BlockPos p = be.getBlockPos();
      float ceil = be.kind.attach() / 16F, floor = -6;
      if (l != null) {
         BlockPos.MutableBlockPos m = p.mutable();
         for (int i = 1; i <= 24; i++) {
            m.setY(p.getY() + i);
            if (!l.getBlockState(m).getCollisionShape(l, m).isEmpty()) {
               ceil = Math.max(ceil, i);
               break;
            }
         }
         for (int i = 0; i <= 24; i++) {
            m.setY(p.getY() - i);
            VoxelShape sh = l.getBlockState(m).getCollisionShape(l, m);
            if (!sh.isEmpty()) {
               floor = -i + (float) sh.max(Direction.Axis.Y);
               break;
            }
         }
      }
      c = new float[] {ceil, floor, now};
      SPAN.put(be, c);
      return c;
   }

   /** Intensité de la lueur (0..1) : ampoule qui grésille selon sa puissance, flammes qui dansent, œufs et spores qui pulsent. */
   public static float lampGlow(PropBlockEntity be, float partial) {
      Level l = be.getLevel();
      float t = (l == null ? 0 : l.getGameTime()) + partial;
      float ph = be.phase * Mth.TWO_PI;
      return switch (be.kind) {
         case CAGE_LAMP -> {
            BlockState st = be.getBlockState();
            int p = st.hasProperty(fr.heralisz.n07.dungeon.LampBlock.POWER) ? st.getValue(fr.heralisz.n07.dungeon.LampBlock.POWER) : 2;
            float base = p == 2 ? 1F : p == 1 ? 0.38F : 0.03F;
            // 100 Hz de secteur + micro-coupures des ampoules fatiguées
            float hum = 0.94F + 0.06F * Mth.sin(t * 5.3F + ph);
            if (be.faulty() && p == 2 && Mth.sin(t * 0.9F + ph) * Mth.sin(t * 2.3F) > 0.82F) hum *= 0.55F;
            yield base * hum;
         }
         case CHANDELIER -> 0.82F + 0.1F * Mth.sin(t * 0.7F + ph) + 0.08F * Mth.sin(t * 1.9F + ph * 2);
         case SPORE_LANTERN -> 0.62F + 0.38F * (0.5F + 0.5F * Mth.sin(t * 0.08F + ph));
         case BROOD_LAMP -> 0.55F + 0.45F * Mth.square(0.5F + 0.5F * Mth.sin(t * 0.06F + ph));
         default -> 1F;
      };
   }

   private void lamp(PropBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, RenderType rt, VertexConsumer vc, float yaw) {
      Level l = be.getLevel();
      float t = (l == null ? 0 : l.getGameTime()) + partial;
      float[] sp = span(be);
      float ceil = sp[0], floor = sp[1];
      float ay = be.kind.attach() / 16F;
      float ph = be.phase * Mth.TWO_PI;
      // balancement : pendule (période selon la longueur) modulé par des rafales ; la perturbation du donjon le fait trembler
      float len = Math.max(0.4F, ceil - be.kind.bulbY());
      float w = Mth.TWO_PI / (34F + 18F * Mth.sqrt(len));
      float gust = 0.6F + 0.4F * Mth.sin(t * 0.011F + ph * 1.7F) * Mth.sin(t * 0.0047F + ph);
      float amp = Mth.clamp((float) Math.toDegrees(0.07F * (0.6F + gust) / len), 0.5F, 5F);
      var sh = be.shrine();
      float surge = sh == null ? 0 : sh.surge(partial);
      float ax = amp * Mth.sin(t * w + ph) + surge * 1.6F * Mth.sin(t * 2.7F + ph);
      float az = amp * 0.7F * Mth.sin(t * w * 0.87F + ph * 2 + 1) + surge * 1.2F * Mth.sin(t * 3.1F + ph * 3);
      ps.pushPose();
      ps.translate(0.5, ceil, 0.5);
      ps.mulPose(Axis.XP.rotationDegrees(ax));
      ps.mulPose(Axis.ZP.rotationDegrees(az));
      ps.translate(-0.5, -ceil, -0.5);
      lampNow = be;
      lampK = lampGlow(be, partial);
      super.defaultRender(ps, be, buf, rt, vc, yaw, partial, light);
      lampNow = null;
      cord(be, ps, buf, ay, ceil, light);
      lampLight(be, ps, buf, floor, t);
      ps.popPose();
   }

   /** Suspension du point d'accroche jusqu'à la voûte : chaîne (deux plans croisés, comme la chaîne de Minecraft), soie ou liane. */
   private static void cord(PropBlockEntity be, PoseStack ps, MultiBufferSource buf, float y0, float y1, int light) {
      if (y1 - y0 < 0.05F) return;
      int kind = be.kind.cord();
      ResourceLocation tex = kind == 0 ? T_CHAIN : kind == 1 ? T_SILK : T_VINE;
      RenderType rt = kind == 1 ? VfxRenderTypes.glass(tex) : RenderType.entityCutoutNoCull(tex);
      VertexConsumer vc = buf.getBuffer(rt);
      Matrix4f m = ps.last().pose();
      org.joml.Matrix3f nm = ps.last().normal();
      float hw = kind == 0 ? 1.5F / 16F : kind == 1 ? 0.03F : 0.3F;
      float u0 = 0, u1 = kind == 0 ? 3F / 16F : 1F;
      float vLen = kind == 0 ? 1F : kind == 1 ? 0.5F : 1F;
      int col = kind == 1 ? 0xE8F2F0EA : 0xFFFFFFFF;
      for (int plane = 0; plane < 2; plane++) {
         float dx = plane == 0 ? hw : 0, dz = plane == 0 ? 0 : hw;
         float uA = u0 + (kind == 0 ? plane * 3F / 16F : 0), uB = u1 + (kind == 0 ? plane * 3F / 16F : 0);
         float v1 = (y1 - y0) / vLen;
         cordV(vc, m, nm, 0.5F - dx, y0, 0.5F - dz, uA, v1, col, light);
         cordV(vc, m, nm, 0.5F + dx, y0, 0.5F + dz, uB, v1, col, light);
         cordV(vc, m, nm, 0.5F + dx, y1, 0.5F + dz, uB, 0, col, light);
         cordV(vc, m, nm, 0.5F - dx, y1, 0.5F - dz, uA, 0, col, light);
      }
   }

   private static void cordV(VertexConsumer vc, Matrix4f m, org.joml.Matrix3f nm, float x, float y, float z, float u, float v, int argb, int light) {
      vc.vertex(m, x, y, z).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24).uv(u, v)
         .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, 0, 1, 0).endVertex();
   }

   /**
    * Colonne de liquide lumineux (cuves) : paroi cylindrique additive à texture de courant qui monte, surface d'écume qui tourne ;
    * sans écriture de profondeur, elle se voit à travers le verre et éclaire en vert ce qui flotte dedans.
    */
   private static void liquidColumn(PoseStack ps, MultiBufferSource buf, float r, float y0, float y1, int rgb, float a, float t, boolean sludge) {
      if (y1 - y0 < 0.02F) return;
      Matrix4f m = ps.last().pose();
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glow(sludge ? T_SLUDGE : T_STREAM));
      float h = y1 - y0;
      int top = VfxDraw.argb(rgb, a * 0.55F), bot = VfxDraw.argb(rgb, a);
      VfxDraw.wall(vc, m, 0.5, y0, 0.5, r, h, 32, 6, -t * 0.012F, bot, top);
      VfxDraw.wall(vc, m, 0.5, y0, 0.5, r * 0.55, h, 20, 3, -t * 0.02F, VfxDraw.argb(rgb, a * 0.5F), VfxDraw.argb(rgb, a * 0.25F));
      VertexConsumer foam = buf.getBuffer(VfxRenderTypes.glow(T_FOAM));
      VfxDraw.annulus(foam, m, 0.5, y1, 0.5, 0.0, r, 32, t * 0.004F, VfxDraw.argb(rgb, a * 0.7F));
      VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_GLOW)), m, 0.5, y1 + 0.02, 0.5, r * 1.1, t * 0.01F, VfxDraw.argb(rgb, a * 0.5F));
   }

   /** Puits de lumière sous le ventilateur : colonne poussiéreuse qui palpite au passage des six pales. */
   private static void fanShaft(PropBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf) {
      Level l = be.getLevel();
      if (l == null) return;
      float t = l.getGameTime() + partial;
      float ang = (t / 24F) * Mth.TWO_PI;                                   // un tour en 1,2 s (animation « idle »)
      float strobe = 0.72F + 0.28F * Math.abs(Mth.cos(ang * 3F));
      float floor = span(be)[1];
      float y0 = 0.45F, h = Math.min(y0 - floor, 14F);
      int col = 0xC8DCEB;
      cone(buf.getBuffer(VfxRenderTypes.glow(T_CONE)), ps.last().pose(), 0.5, y0, 0.5, 1.2, 1.2 + h * 0.18, h, VfxDraw.argb(col, 0.13F * strobe));
      cone(buf.getBuffer(VfxRenderTypes.glow(T_CONE)), ps.last().pose(), 0.5, y0, 0.5, 0.7, 0.9 + h * 0.1, h, VfxDraw.argb(col, 0.10F * strobe));
      if (h < 13.9F) VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_GLOW)), ps.last().pose(), 0.5, floor + 0.03, 0.5, 1.4 + h * 0.18, ang,
         VfxDraw.argb(col, 0.16F * strobe));
   }

   /** Halo autour de la source, cône de lumière poussiéreuse et flaque de lumière au sol (suivent le grésillement). */
   private static void lampLight(PropBlockEntity be, PoseStack ps, MultiBufferSource buf, float floor, float t) {
      float k = lampGlow(be, 0);
      if (k < 0.03F) return;
      int rgb = be.kind.glowColor();
      float by = be.kind.bulbY();
      VertexConsumer glow = buf.getBuffer(VfxRenderTypes.glow(T_GLOW));
      switch (be.kind) {
         case CAGE_LAMP -> {
            billboard(glow, ps, 0.5, by, 0.5, 1.5F, VfxDraw.argb(rgb, 0.55F * k));
            billboard(glow, ps, 0.5, by, 0.5, 4.2F, VfxDraw.argb(rgb, 0.10F * k));
            float y0 = 0.44F, y1 = Math.max(floor, y0 - 7F);
            float r0 = 0.46F, r1 = r0 + (y0 - y1) * 0.36F;
            cone(buf.getBuffer(VfxRenderTypes.glow(T_CONE)), ps.last().pose(), 0.5, y0, 0.5, r0, r1, y0 - y1, VfxDraw.argb(rgb, 0.16F * k));
            if (y0 - y1 < 6.9F) VfxDraw.disc(glow, ps.last().pose(), 0.5, y1 + 0.02, 0.5, r1 * 1.1, 0, VfxDraw.argb(rgb, 0.16F * k));
         }
         case CHANDELIER -> {
            for (int i = 0; i < 8; i++) {
               double a = Math.toRadians(i * 45 + 22.5);
               float fk = k * (0.85F + 0.15F * Mth.sin(t * 1.3F + i * 1.7F));
               billboard(glow, ps, 0.5 + Math.cos(a) * 1.25, 0.92, 0.5 + Math.sin(a) * 1.25, 0.7F, VfxDraw.argb(rgb, 0.5F * fk));
            }
            billboard(glow, ps, 0.5, by, 0.5, 5.5F, VfxDraw.argb(rgb, 0.08F * k));
            if (floor > -12) VfxDraw.disc(glow, ps.last().pose(), 0.5, floor + 0.02, 0.5, 3.2, 0, VfxDraw.argb(rgb, 0.10F * k));
         }
         default -> {
            billboard(glow, ps, 0.5, by, 0.5, 1.4F, VfxDraw.argb(rgb, 0.45F * k));
            billboard(glow, ps, 0.5, by, 0.5, 3.6F, VfxDraw.argb(rgb, 0.12F * k));
         }
      }
   }

   private static void billboard(VertexConsumer vc, PoseStack ps, double x, double y, double z, float size, int argb) {
      ps.pushPose();
      ps.translate(x, y, z);
      ps.mulPose(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
      Matrix4f m = ps.last().pose();
      float h = size / 2;
      VfxDraw.v(vc, m, -h, -h, 0, 0, 1, argb);
      VfxDraw.v(vc, m, h, -h, 0, 1, 1, argb);
      VfxDraw.v(vc, m, h, h, 0, 1, 0, argb);
      VfxDraw.v(vc, m, -h, h, 0, 0, 0, argb);
      ps.popPose();
   }

   /** Cône ouvert vers le bas (v 0 en haut) : texture de faisceau poussiéreux. */
   private static void cone(VertexConsumer vc, Matrix4f m, double cx, double top, double cz, double r0, double r1, double h, int argb) {
      int seg = 18;
      int bottom = argb & 0x00FFFFFF;
      for (int i = 0; i < seg; i++) {
         double a0 = i * Mth.TWO_PI / seg, a1 = (i + 1) * Mth.TWO_PI / seg;
         float u0 = (float) i / seg, u1 = (float) (i + 1) / seg;
         VfxDraw.v(vc, m, cx + Math.cos(a0) * r0, top, cz + Math.sin(a0) * r0, u0, 0, argb);
         VfxDraw.v(vc, m, cx + Math.cos(a1) * r0, top, cz + Math.sin(a1) * r0, u1, 0, argb);
         VfxDraw.v(vc, m, cx + Math.cos(a1) * r1, top - h, cz + Math.sin(a1) * r1, u1, 1, bottom);
         VfxDraw.v(vc, m, cx + Math.cos(a0) * r1, top - h, cz + Math.sin(a0) * r1, u0, 1, bottom);
      }
   }

   private static final Map<PropBlockEntity, double[]> LANDING = new WeakHashMap<>();

   /** Point d'impact du jet (coordonnées du monde) : {x, y, z, durée de chute en ticks} ; recalculé de temps en temps. */
   public static double[] landing(PropBlockEntity be) {
      Level l = be.getLevel();
      double[] c = LANDING.get(be);
      if (c != null && (l == null || (l.getGameTime() + be.getBlockPos().hashCode()) % 40 != 0)) return c;
      BlockPos bp = be.getBlockPos();
      Vec3 f = be.front();
      Vec3 o = Vec3.atCenterOf(bp).add(f.scale(MOUTH)).add(0, -LIP, 0);
      double floor = surfaceBelow(l, o);
      double t = 0;
      for (int it = 0; it < 3; it++) {
         t = Math.sqrt(2 * Math.max(0.05, o.y - floor) / GRAV);
         floor = surfaceBelow(l, o.add(f.scale(V0 * t)));
      }
      t = Math.sqrt(2 * Math.max(0.05, o.y - floor) / GRAV);
      Vec3 p = o.add(f.scale(V0 * t));
      c = new double[] {p.x, floor, p.z, t};
      LANDING.put(be, c);
      return c;
   }

   private static double surfaceBelow(Level l, Vec3 p) {
      if (l == null) return p.y - 6;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));
      for (int i = 0; i < 24; i++) {
         BlockState st = l.getBlockState(m);
         if (st.getBlock() instanceof RadChannelBlock && !st.getValue(RadChannelBlock.COVERED)) return m.getY() + RadChannelBlock.LEVEL / 16.0;
         VoxelShape sh = st.getCollisionShape(l, m);
         if (!sh.isEmpty()) return m.getY() + sh.max(Direction.Axis.Y);
         m.move(0, -1, 0);
      }
      return p.y - 24;
   }

   private static void stream(PropBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf) {
      Level l = be.getLevel();
      if (l == null) return;
      // jet coupe / en train de demarrer ou de s'arreter
      float amount = be.flowAmount(partial);
      if (amount <= 0.001F) return;
      boolean starting = be.flowing();
      double[] ld = landing(be);
      BlockPos bp = be.getBlockPos();
      Vec3 f = be.front();
      Vec3 side = new Vec3(-f.z, 0, f.x);
      float time = (float) (l.getGameTime() % 24000L) + partial;
      float pulse = 1 + 0.06F * Mth.sin(time * 0.21F + be.phase * 6.28F);
      // bouche, en coordonnées du bloc
      Vec3 o = new Vec3(0.5, 0.5 - LIP, 0.5).add(f.scale(MOUTH));
      double tl = ld[3];
      Matrix4f m = ps.last().pose();
      int seg = 20, ring = 10;
      Vec3[] pts = new Vec3[seg + 1];
      Vec3[] nrm = new Vec3[seg + 1];
      float[] arc = new float[seg + 1];
      // a la mise en marche le jet s'allonge depuis la bouche ; a l'arret sa queue se detache et tombe
      double tA = starting ? 0 : tl * (1 - amount), tB = starting ? tl * amount : tl;
      for (int i = 0; i <= seg; i++) {
         double t = tA + (tB - tA) * i / seg;
         double u = t / Math.max(1e-4, tl);
         Vec3 p = o.add(f.scale(V0 * t)).add(0, -0.5 * GRAV * t * t, 0);
         p = p.add(side.scale(0.04 * Math.sin(time * 0.33 + i * 0.6) * u));
         Vec3 tan = f.scale(V0).add(0, -GRAV * t, 0).normalize();
         pts[i] = p;
         nrm[i] = side.cross(tan).normalize();
         arc[i] = i == 0 ? 0 : arc[i - 1] + (float) pts[i].distanceTo(pts[i - 1]);
      }
      double lx = ld[0] - bp.getX(), ly = ld[1] - bp.getY() + 0.02, lz = ld[2] - bp.getZ();
      float crown = 0.35F + 0.12F * Mth.sin(time * 0.5F + 1.3F);
      boolean full = amount >= 0.999F;
      if (be.kind.sludge()) {
         // boue : épaisse, trouble, translucide (pas de lueur additive), qui s'écrase lourdement
         RenderType rt = VfxRenderTypes.glass(T_SLUDGE);
         tube(buf.getBuffer(rt), m, pts, nrm, arc, side, ring, 0.95 * pulse, 0.55, 1.3, time * 0.07F, 0.2F, VfxDraw.argb(0x8E9A34, 0.55F));
         tube(buf.getBuffer(rt), m, pts, nrm, arc, side, ring, 0.7 * pulse, 0.38, 1.15, time * 0.05F, 0F, VfxDraw.argb(0xB4BE52, 0.95F));
         if (!full) return;
         VfxDraw.annulus(buf.getBuffer(VfxRenderTypes.decal(T_FOAM)), m, lx, ly, lz, 0.05, 1.1 * pulse, 24, -time * 0.003F, VfxDraw.argb(0xC8C890, 0.8F));
         VfxDraw.wall(buf.getBuffer(rt), m, lx, ly, lz, 0.5 * pulse, crown * 0.8F, 16, 2, -time * 0.05F, VfxDraw.argb(0xB4BE52, 0.8F),
            VfxDraw.argb(0xB4BE52, 0));
         return;
      }
      // halo, puis cœur
      tube(buf.getBuffer(VfxRenderTypes.glow(T_STREAM)), m, pts, nrm, arc, side, ring, 0.86 * pulse, 0.46, 1.25, time * 0.13F, 0.33F,
         VfxDraw.argb(0x3CFF4A, 0.42F));
      tube(buf.getBuffer(VfxRenderTypes.glow(T_STREAM)), m, pts, nrm, arc, side, ring, 0.6 * pulse, 0.3, 1.1, time * 0.09F, 0F,
         VfxDraw.argb(0xC8FF5A, 0.95F));
      if (!full) return;
      // impact : écume + couronne d'éclaboussures
      VfxDraw.annulus(buf.getBuffer(VfxRenderTypes.glow(T_FOAM)), m, lx, ly, lz, 0.05, 1.05 * pulse, 24, -time * 0.004F, VfxDraw.argb(0xE8FFD0, 0.75F));
      VfxDraw.annulus(buf.getBuffer(VfxRenderTypes.glow(T_FOAM)), m, lx, ly + 0.01, lz, 0.4, 1.6, 24, time * 0.003F, VfxDraw.argb(0x7CFF3A, 0.35F));
      VfxDraw.wall(buf.getBuffer(VfxRenderTypes.glow(T_STREAM)), m, lx, ly, lz, 0.48 * pulse, crown, 16, 2, -time * 0.07F,
         VfxDraw.argb(0xD8FF8A, 0.7F), VfxDraw.argb(0xD8FF8A, 0));
   }

   /**
    * Tube elliptique le long des points : largeur w0 → w0*grow (à plat, perpendiculaire au jet), épaisseur th ; la texture défile
    * vers le bas du jet.
    */
   private static void tube(VertexConsumer vc, Matrix4f m, Vec3[] pts, Vec3[] nrm, float[] arc, Vec3 side, int ring, double w0, double th,
                            double grow, float scroll, float uOff, int argb) {
      int n = pts.length - 1;
      for (int i = 0; i < n; i++) {
         double u0 = (double) i / n, u1 = (double) (i + 1) / n;
         double wa = w0 * (1 + (grow - 1) * Math.pow(u0, 0.7)), wb = w0 * (1 + (grow - 1) * Math.pow(u1, 0.7));
         double ta = th * (1 + 0.5 * u0), tb = th * (1 + 0.5 * u1);
         float va = arc[i] * 0.45F - scroll, vb = arc[i + 1] * 0.45F - scroll;
         // fondu : discret à la sortie, plein ensuite, s'estompe dans l'écume
         float fa = Mth.clamp((float) u0 * 6, 0.35F, 1) * (float) Mth.clamp((1 - u0) * 8, 0, 1);
         float fb = Mth.clamp((float) u1 * 6, 0.35F, 1) * (float) Mth.clamp((1 - u1) * 8, 0, 1);
         for (int j = 0; j < ring; j++) {
            double a0 = Mth.TWO_PI * j / ring, a1 = Mth.TWO_PI * (j + 1) / ring;
            float s0 = (float) j / ring + uOff, s1 = (float) (j + 1) / ring + uOff;
            Vec3 p00 = pts[i].add(side.scale(Math.cos(a0) * wa / 2)).add(nrm[i].scale(Math.sin(a0) * ta / 2));
            Vec3 p01 = pts[i].add(side.scale(Math.cos(a1) * wa / 2)).add(nrm[i].scale(Math.sin(a1) * ta / 2));
            Vec3 p11 = pts[i + 1].add(side.scale(Math.cos(a1) * wb / 2)).add(nrm[i + 1].scale(Math.sin(a1) * tb / 2));
            Vec3 p10 = pts[i + 1].add(side.scale(Math.cos(a0) * wb / 2)).add(nrm[i + 1].scale(Math.sin(a0) * tb / 2));
            int ca = scaleAlpha(argb, fa), cb = scaleAlpha(argb, fb);
            VfxDraw.v(vc, m, p00.x, p00.y, p00.z, s0, va, ca);
            VfxDraw.v(vc, m, p01.x, p01.y, p01.z, s1, va, ca);
            VfxDraw.v(vc, m, p11.x, p11.y, p11.z, s1, vb, cb);
            VfxDraw.v(vc, m, p10.x, p10.y, p10.z, s0, vb, cb);
         }
      }
   }

   private static int scaleAlpha(int argb, float k) {
      int a = (int) (((argb >>> 24) & 255) * Mth.clamp(k, 0, 1));
      return (a << 24) | (argb & 0xFFFFFF);
   }

   static final class Model extends GeoModel<PropBlockEntity> {
      @Override
      public ResourceLocation getModelResource(PropBlockEntity be) {
         return GEO[be.kind.ordinal()];
      }

      @Override
      public ResourceLocation getTextureResource(PropBlockEntity be) {
         return TEX[be.kind.ordinal()];
      }

      @Override
      public ResourceLocation getAnimationResource(PropBlockEntity be) {
         return ANIM[be.kind.ordinal()];
      }

      @Override
      public void setCustomAnimations(PropBlockEntity be, long instanceId, software.bernie.geckolib.core.animation.AnimationState<PropBlockEntity> state) {
         super.setCustomAnimations(be, instanceId, state);
         if (be.kind.tank() && TANK != null) {
            TankView v = TANK;
            getBone("orbit").ifPresent(b -> b.setRotY((float) Math.toRadians(be.orbitAngle)));
            getBone("liquid").ifPresent(b -> {
               b.setHidden(v.level < 0.01F);
               b.setScaleY(Math.max(0.01F, v.level));
            });
            getBone("glass").ifPresent(b -> {
               b.setHidden(v.broken || v.glass < 0.01F);
               b.setScaleY(Math.max(0.01F, v.glass));
            });
            getBone("glass_shards").ifPresent(b -> b.setHidden(!v.broken));
            for (int k = 0; k < 4; k++) getBone("coil_glow_" + k).ifPresent(b -> b.setHidden(v.charge < 0.05F));
            return;
         }
         if (be.kind.giantFan()) {
            getBone("rotor").ifPresent(b -> b.setRotY(giantFanAngle(be, (float) state.getPartialTick())));
            return;
         }
         if (be.kind != PropKind.KNIGHT_STATUE) return;
         // le modèle est partagé avec le Chevalier vivant (et sa dépouille) : on remet tous les drapeaux à chaque image
         for (String n : STATUE_SHOW) {
            getBone(n).ifPresent(b -> {
               b.setHidden(false);
               b.setChildrenHidden(false);
            });
         }
         for (String n : STATUE_HIDE) getBone(n).ifPresent(b -> b.setHidden(true));
      }
   }
}
