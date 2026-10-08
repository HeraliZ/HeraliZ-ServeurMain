package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heraliz.reanimation.entity.BotEntity;
import fr.heraliz.reanimation.execution.ExecutionType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.minecraftforge.client.event.ViewportEvent.ComputeFov;
import org.joml.Vector3f;

public final class ExecutionFx {
   private static final int GHOST_TICKS = 50;
   private static final List<ExecutionFx.Ghost> GHOSTS = new ArrayList<>();
   private static int clock;
   private static PlayerModel<LivingEntity> wideGhost;
   private static PlayerModel<LivingEntity> slimGhost;
   private static float shake;
   private static float punch;
   private static float punchO;
   public static int flashTicks;
   public static int bannerTicks;
   public static Component bannerTitle = Component.empty();
   public static Component bannerSubtitle = Component.empty();
   public static boolean bannerForVictim;
   private static float bars;
   private static float barsO;
   static boolean gray;
   static float beat;
   static float beatO;
   private static float nearFlash;
   private static float nearFlashO;
   private static float darkness;
   private static float darknessO;
   private static float darknessTarget;
   private static PlayerModel<LivingEntity> wideSoul;
   private static PlayerModel<LivingEntity> slimSoul;

   private ExecutionFx() {
   }

   static void onEvent(Entity e, ClientDowned.Info info, int event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && event == 5 && info != null && info.has(256) && e instanceof LivingEntity body) {
         ExecutionScenes.Scene scene = ExecutionScenes.ofVictim(body);
         ResourceLocation skin = skinOf(body);
         if (skin != null && scene != null && scene.choreo().soulRises()) {
            GHOSTS.add(new ExecutionFx.Ghost(scene.origin, scene.yaw, scene.side, scene.choreo().corpse(scene.side), skin, slimOf(body), clock));
         }

         double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(body.position());
         shake = Math.max(shake, (float)Mth.clamp(1.0 - d / 24.0, 0.0, 1.0));
         boolean victim = body == mc.player;
         boolean executioner = info.partner == mc.player.getId();
         if (victim || executioner) {
            punch = 1.0F;
            flashTicks = 14;
            bannerTicks = 70;
            bannerForVictim = victim;
            ExecutionType type = ExecutionType.byIndex(info.style);
            Entity partner = mc.level.getEntity(info.partner);
            bannerTitle = Component.translatable(victim ? "hud.reanimation.executed" : "hud.reanimation.execution");
            bannerSubtitle = (Component)(victim && partner != null
               ? Component.translatable("hud.reanimation.executed_by", new Object[]{partner.getDisplayName()})
               : type.displayName());
         }
      }
   }

   static void slam(ExecutionScenes.Scene s) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level != null && mc.player != null) {
         Vec3 hit = s.world(CoupDeGrace.at(-0.3F, 0.0F, 0.1F));
         double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(hit);
         shake = Math.max(shake, (float)Mth.clamp(1.1 - d / 20.0, 0.0, 1.0));
         if (s.involves(mc.player)) {
            punch = Math.max(punch, 0.8F);
         }

         RandomSource random = level.getRandom();

         for (int i = 0; i < 36; i++) {
            double a = i * (Math.PI / 18) + random.nextDouble() * 0.1;
            double speed = 0.2 + random.nextDouble() * 0.12;
            level.addParticle(
               ParticleTypes.POOF,
               hit.x + Math.cos(a) * 0.4,
               hit.y + 0.05,
               hit.z + Math.sin(a) * 0.4,
               Math.cos(a) * speed,
               0.01 + random.nextDouble() * 0.03,
               Math.sin(a) * speed
            );
         }

         for (int i = 0; i < 14; i++) {
            level.addParticle(
               ParticleTypes.LARGE_SMOKE,
               hit.x + (random.nextDouble() - 0.5) * 1.4,
               hit.y + 0.1,
               hit.z + (random.nextDouble() - 0.5) * 1.4,
               0.0,
               0.03 + random.nextDouble() * 0.04,
               0.0
            );
         }
      }
   }

   static void impact(ExecutionScenes.Scene s, Vector3f local, float strength) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level != null && mc.player != null && level == s.level) {
         Vec3 hit = s.world(local);
         double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(hit);
         shake = Math.max(shake, (float)Mth.clamp((1.1 - d / (14.0 + 10.0 * strength)) * Math.min(1.0, strength), 0.0, 1.0));
         if (s.involves(mc.player)) {
            punch = Math.max(punch, 0.8F * Math.min(1.0F, strength));
         }

         RandomSource random = level.getRandom();
         int n = (int)(20.0F + 24.0F * strength);

         for (int i = 0; i < n; i++) {
            double a = i * ((Math.PI * 2) / n) + random.nextDouble() * 0.1;
            double speed = (0.12 + random.nextDouble() * 0.12) * (0.6 + 0.6 * strength);
            level.addParticle(
               ParticleTypes.POOF,
               hit.x + Math.cos(a) * 0.4,
               hit.y + 0.05,
               hit.z + Math.sin(a) * 0.4,
               Math.cos(a) * speed,
               0.01 + random.nextDouble() * 0.03,
               Math.sin(a) * speed
            );
         }

         for (int i = 0; i < (int)(8.0F * strength); i++) {
            level.addParticle(
               ParticleTypes.LARGE_SMOKE,
               hit.x + (random.nextDouble() - 0.5) * 1.4,
               hit.y + 0.1,
               hit.z + (random.nextDouble() - 0.5) * 1.4,
               0.0,
               0.03 + random.nextDouble() * 0.04,
               0.0
            );
         }
      }
   }

   static void flashNear(ExecutionScenes.Scene s, Vector3f local, float strength) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.level == s.level) {
         double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(s.world(local));
         nearFlash = Math.max(nearFlash, (float)Mth.clamp(strength * (1.0 - d / 40.0), 0.0, 1.0));
      }
   }

   static void darken(ExecutionScenes.Scene s, float amount) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.level == s.level) {
         double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(s.origin);
         darknessTarget = Math.max(darknessTarget, (float)Mth.clamp(amount * (1.0 - d / 32.0), 0.0, 1.0));
      }
   }

   public static float nearFlash(float partialTick) {
      return Mth.lerp(partialTick, nearFlashO, nearFlash);
   }

   public static float darkness(float partialTick) {
      return Mth.lerp(partialTick, darknessO, darkness);
   }

   public static boolean cinema() {
      return bars > 0.0F || barsO > 0.0F;
   }

   static PlayerModel<LivingEntity> soulModel(boolean slim) {
      if (wideSoul == null) {
         wideSoul = playerModel(false);
         slimSoul = playerModel(true);
      }

      return slim ? slimSoul : wideSoul;
   }

   static PlayerModel<LivingEntity> playerModel(boolean slim) {
      Minecraft mc = Minecraft.getInstance();
      PlayerModel<LivingEntity> model = mc == null
         ? new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim)
         : new PlayerModel(mc.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
      model.young = false;
      return model;
   }

   static ResourceLocation skinOf(LivingEntity e) {
      if (e instanceof AbstractClientPlayer player) {
         return player.getSkinTextureLocation();
      } else {
         return e instanceof BotEntity bot ? BotRenderer.skin(bot) : null;
      }
   }

   static boolean slimOf(LivingEntity e) {
      return e instanceof AbstractClientPlayer player && "slim".equals(player.getModelName());
   }

   static void tick(Minecraft mc) {
      clock++;
      shake *= 0.86F;
      if (shake < 0.01F) {
         shake = 0.0F;
      }

      punchO = punch;
      punch *= 0.72F;
      if (punch < 0.01F) {
         punch = 0.0F;
      }

      if (flashTicks > 0) {
         flashTicks--;
      }

      if (bannerTicks > 0) {
         bannerTicks--;
      }

      Vfx.tick();
      barsO = bars;
      beatO = beat;
      beat *= 0.72F;
      nearFlashO = nearFlash;
      nearFlash *= 0.8F;
      if (nearFlash < 0.01F) {
         nearFlash = 0.0F;
      }

      darknessO = darkness;
      darkness = darkness + (darknessTarget - darkness) * 0.15F;
      darknessTarget = 0.0F;
      ExecutionScenes.Scene local = ExecutionScenes.local();
      gray = local != null && local.state(0.0F) instanceof Script.Frame frame && frame.slowmo > 0.5F;
      boolean inside = local != null && local.ticks < 118;
      bars = inside ? Math.min(1.0F, bars + 0.14F) : Math.max(0.0F, bars - 0.1F);
      ClientLevel level = mc.level;
      if (level == null) {
         GHOSTS.clear();
      } else {
         RandomSource random = level.getRandom();
         Iterator<ExecutionFx.Ghost> it = GHOSTS.iterator();

         while (it.hasNext()) {
            ExecutionFx.Ghost g = it.next();
            int age = clock - g.born;
            if (age > 50) {
               it.remove();
            } else {
               Vec3 at = ghostCenter(g, age / 50.0F);

               for (int k = 0; k < 2; k++) {
                  Vfx.preset(
                     Vfx.Kind.SOUL,
                     at.x + (random.nextDouble() - 0.5) * 0.6,
                     at.y + (random.nextDouble() - 0.5) * 1.4,
                     at.z + (random.nextDouble() - 0.5) * 0.6,
                     0.0,
                     0.03,
                     0.0
                  );
               }
            }
         }
      }
   }

   private static double rise(float t) {
      return 2.6 * (1.0 - (1.0 - t) * (1.0 - t));
   }

   private static Vec3 ghostCenter(ExecutionFx.Ghost g, float t) {
      Vec3 f = Vec3.directionFromRotation(0.0F, g.yaw);
      Vec3 s = new Vec3(-f.z, 0.0, f.x);
      return g.origin.add(f.scale(g.corpse.z)).add(s.scale(-g.corpse.x)).add(0.0, g.corpse.y + rise(t) + 0.3, 0.0);
   }

   public static float bars(float partialTick) {
      return Mth.lerp(partialTick, barsO, bars);
   }

   static void clear() {
      GHOSTS.clear();
      shake = 0.0F;
      punchO = 0.0F;
      punch = 0.0F;
      flashTicks = 0;
      bannerTicks = 0;
      gray = false;
      Vfx.clear();
      beat = 0.0F;
      beatO = 0.0F;
      nearFlash = 0.0F;
      nearFlashO = 0.0F;
      darkness = 0.0F;
      darknessO = 0.0F;
      darknessTarget = 0.0F;
      barsO = 0.0F;
      bars = 0.0F;
   }

   static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_PARTICLES) {
         Vfx.render(event);
      } else if (event.getStage() == Stage.AFTER_SKY) {
         KnifeTrail.beginFrame(event);
      } else if (event.getStage() == Stage.AFTER_ENTITIES) {
         ExecutionScenes.renderCorpses(event);
         ExecutionScenes.renderExtras(event);
         if (!GHOSTS.isEmpty()) {
            renderGhosts(event);
         }

         SceneRenderer.render(event);
         KnifeTrail.render(event);
      }
   }

   static void heartbeat(float strength) {
      beat = Math.max(beat, strength);
   }

   static void kick(float shakeAmount, float punchAmount) {
      shake = Math.max(shake, shakeAmount);
      punch = Math.max(punch, punchAmount);
   }

   private static void renderGhosts(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (wideGhost == null) {
         wideGhost = new PlayerModel(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
         slimGhost = new PlayerModel(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
      }

      Vec3 cam = event.getCamera().getPosition();
      PoseStack ps = event.getPoseStack();
      BufferSource buffers = mc.renderBuffers().bufferSource();
      float pt = event.getPartialTick();

      for (ExecutionFx.Ghost g : GHOSTS) {
         float age = clock - g.born + pt;
         float t = Mth.clamp(age / 50.0F, 0.0F, 1.0F);
         float alpha = t < 0.12F ? t / 0.12F * 0.75F : 0.75F * (float)Math.pow(1.0F - t, 1.3);
         if (!(alpha <= 0.01F)) {
            PlayerModel<LivingEntity> model = g.slim ? slimGhost : wideGhost;
            pose(model, age);
            float sitUp = CoupDeGrace.smooth(age / 16.0F);
            CoupDeGrace.Body body = new CoupDeGrace.Body()
               .turn(-90.0F * g.side, 90.0F * (1.0F - sitUp), 0.0F, age * 1.5F)
               .anchor(0.7040626F, new Vector3f(g.corpse).add(CoupDeGrace.at(0.0F, -0.35F * g.side, (float)rise(t))));
            ps.pushPose();
            ps.translate(g.origin.x - cam.x, g.origin.y - cam.y, g.origin.z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(-g.yaw));
            ps.translate(body.pos.x, body.pos.y, body.pos.z);
            ps.mulPose(body.rot);
            ps.scale(-1.0F, -1.0F, 1.0F);
            float s = 0.9375F * (1.0F + 0.18F * t);
            ps.scale(s, s, s);
            ps.translate(0.0F, -1.501F, 0.0F);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(g.skin));
            model.renderToBuffer(ps, vc, 15728880, OverlayTexture.NO_OVERLAY, 0.55F, 0.85F, 1.0F, alpha);
            ps.popPose();
         }
      }

      buffers.endBatch();
   }

   private static void pose(PlayerModel<LivingEntity> m, float age) {
      float open = Mth.clamp(age / 14.0F, 0.0F, 1.0F);
      m.head.xRot = -0.45F * open;
      m.head.yRot = 0.0F;
      m.body.xRot = -0.08F * open;
      m.rightArm.xRot = -0.25F * open;
      m.leftArm.xRot = -0.25F * open;
      m.rightArm.zRot = (1.2F + 0.08F * Mth.sin(age * 0.2F)) * open;
      m.leftArm.zRot = -(1.2F + 0.08F * Mth.sin(age * 0.2F)) * open;
      m.rightLeg.xRot = 0.15F * open;
      m.leftLeg.xRot = 0.25F * open;
      m.rightLeg.zRot = 0.05F;
      m.leftLeg.zRot = -0.05F;
      m.hat.copyFrom(m.head);
      m.jacket.copyFrom(m.body);
      m.rightSleeve.copyFrom(m.rightArm);
      m.leftSleeve.copyFrom(m.leftArm);
      m.rightPants.copyFrom(m.rightLeg);
      m.leftPants.copyFrom(m.leftLeg);
   }

   static void onCameraAngles(ComputeCameraAngles event) {
      if (!(shake <= 0.0F)) {
         double time = (clock + event.getPartialTick()) * 1.7;
         float s = shake * shake;
         event.setYaw(event.getYaw() + (float)(Math.sin(time * 3.1) * 2.2 * s));
         event.setPitch(event.getPitch() + (float)(Math.cos(time * 2.3) * 2.6 * s));
         event.setRoll(event.getRoll() + (float)(Math.sin(time * 1.9) * 4.0 * s));
      }
   }

   static void onFov(ComputeFov event) {
      float shotFov = CinematicCamera.fov();
      if (shotFov > 0.0F && event.usedConfiguredFov()) {
         event.setFOV(shotFov);
      }

      float p = Mth.lerp((float)event.getPartialTick(), punchO, punch);
      if (p > 0.0F) {
         event.setFOV(event.getFOV() * (1.0 - 0.14 * p));
      }
   }

   private record Ghost(Vec3 origin, float yaw, int side, Vector3f corpse, ResourceLocation skin, boolean slim, int born) {
   }
}
