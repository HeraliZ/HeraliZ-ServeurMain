package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.reanimation.execution.ExecutionType;
import fr.heraliz.reanimation.network.SyncPacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ExecutionScenes {
   private static final List<ExecutionScenes.Scene> SCENES = new ArrayList<>();
   @Nullable
   private static LivingEntity drawing;
   private static final Map<LivingEntity, ItemStack[]> HANDS = new IdentityHashMap<>();
   private static final Set<Entity> UNCULLED = Collections.newSetFromMap(new IdentityHashMap<>());

   private ExecutionScenes() {
   }

   static void onSync(SyncPacket p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         boolean executing = (p.flags & 128) != 0;
         boolean executed = (p.flags & 256) != 0;
         if (!executing && !executed) {
            Iterator<ExecutionScenes.Scene> it = SCENES.iterator();

            while (it.hasNext()) {
               ExecutionScenes.Scene s = it.next();
               if (s.actorId == p.entityId && !s.actorDone) {
                  if (s.struck) {
                     s.actorDone = true;
                  } else {
                     SceneAudio.dropped(s);
                     it.remove();
                  }
               } else if (s.victimId == p.entityId && !s.struck) {
                  SceneAudio.dropped(s);
                  it.remove();
               }
            }
         } else {
            int actorId = executing ? p.entityId : p.partnerId;
            int victimId = executing ? p.partnerId : p.entityId;
            ExecutionScenes.Scene s = find(actorId, victimId);
            if (s == null) {
               s = new ExecutionScenes.Scene(actorId, victimId, mc.level);
               SCENES.add(s);
            }

            s.type = ExecutionType.byIndex(p.style);
            s.origin = new Vec3(p.anchorX, p.anchorY, p.anchorZ);
            s.yaw = p.anchorYaw;
            s.side = p.anchorSide;
            s.ticks = p.revive;
            resolve(s, mc.level);
            if (!s.started) {
               start(s);
            }

            if (p.event == 5) {
               s.struck = true;
            }
         }
      }
   }

   @Nullable
   private static ExecutionScenes.Scene find(int actorId, int victimId) {
      for (ExecutionScenes.Scene s : SCENES) {
         if (s.actorId == actorId && s.victimId == victimId && !s.actorDone) {
            return s;
         }
      }

      return null;
   }

   private static void resolve(ExecutionScenes.Scene s, ClientLevel level) {
      if (s.actor == null && !s.actorDone && level.getEntity(s.actorId) instanceof LivingEntity a) {
         s.actor = a;
      }

      if (s.victim == null && !s.struck && level.getEntity(s.victimId) instanceof LivingEntity v) {
         s.victim = v;
      }
   }

   private static void start(ExecutionScenes.Scene s) {
      if (s.victim != null) {
         s.started = true;
         s.cached = null;
         s.victimYaw = s.victim.yBodyRot - s.yaw;
         if (s.actor != null && s.ticks < 3) {
            s.actorStart.set(s.local(s.actor.position()));
            s.actorYaw = s.actor.yBodyRot - s.yaw;
         }
      }
   }

   static void tick(Minecraft mc) {
      if (SCENES.isEmpty()) {
         if (!UNCULLED.isEmpty()) {
            updateCulling();
         }
      } else {
         Iterator<ExecutionScenes.Scene> it = SCENES.iterator();

         while (it.hasNext()) {
            ExecutionScenes.Scene s = it.next();
            if (s.level != mc.level) {
               SceneAudio.dropped(s);
               it.remove();
            } else {
               resolve(s, s.level);
               if (!s.started) {
                  start(s);
               }

               int before = s.ticks++;
               s.choreo().tick(s, before, s.ticks);
               if (s.struck && s.victim != null && s.victim.isAlive() && s.ticks > 96 + 6) {
                  s.corpseGone = true;
               }

               boolean over = s.struck ? s.ticks > s.type.sceneEndStruck() : s.ticks > s.type.sceneEnd();
               if (over) {
                  SceneAudio.dropped(s);
                  it.remove();
               }
            }
         }

         updateCulling();
      }
   }

   private static void updateCulling() {
      Set<Entity> moved = Collections.newSetFromMap(new IdentityHashMap<>());

      for (ExecutionScenes.Scene s : SCENES) {
         if (s.ticks <= 118 + 20) {
            if (s.actor != null && !s.actorDone) {
               moved.add(s.actor);
            }

            if (s.victim != null) {
               moved.add(s.victim);
            }
         }
      }

      Iterator<Entity> it = UNCULLED.iterator();

      while (it.hasNext()) {
         Entity e = it.next();
         if (!moved.contains(e)) {
            e.noCulling = false;
            it.remove();
         }
      }

      for (Entity e : moved) {
         if (!e.noCulling) {
            e.noCulling = true;
            UNCULLED.add(e);
         }
      }
   }

   static void clear() {
      restoreAllHands();
      SceneAudio.clear();
      SCENES.clear();

      for (Entity e : UNCULLED) {
         e.noCulling = false;
      }

      UNCULLED.clear();
      drawing = null;
   }

   static void renderExtras(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (!SCENES.isEmpty() && mc.level != null) {
         Vec3 cam = event.getCamera().getPosition();
         PoseStack ps = event.getPoseStack();
         BufferSource buffers = mc.renderBuffers().bufferSource();
         float pt = event.getPartialTick();

         for (ExecutionScenes.Scene s : SCENES) {
            if (s.ticks <= s.type.sceneEndStruck()) {
               CoupDeGrace.State state = s.state(pt);
               boolean slim = s.victim != null && ExecutionFx.slimOf(s.victim);
               Quaternionf facing = new Quaternionf().rotationY(s.yaw * (float) (Math.PI / 180.0)).mul(event.getCamera().rotation());
               int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(s.origin.x, s.origin.y + 0.5, s.origin.z));
               ResourceLocation actorSkin = s.actor != null ? ExecutionFx.skinOf(s.actor) : null;
               ResourceLocation victimSkin = s.victim != null ? ExecutionFx.skinOf(s.victim) : null;
               Choreography.Extras extras = new Choreography.Extras(
                  ExecutionFx.soulModel(slim),
                  mc.level.getGameTime(),
                  pt,
                  false,
                  facing,
                  light,
                  actorSkin != null ? actorSkin : FxTex.HOLOGRAM,
                  s.actor != null && ExecutionFx.slimOf(s.actor),
                  victimSkin != null ? victimSkin : FxTex.HOLOGRAM,
                  slim
               );
               ps.pushPose();
               ps.translate(s.origin.x - cam.x, s.origin.y - cam.y, s.origin.z - cam.z);
               ps.mulPose(Axis.YP.rotationDegrees(-s.yaw));
               s.choreo().renderExtras(ps, buffers, state, s.time(pt), extras);
               ps.popPose();
            }
         }

         buffers.endBatch();
      }
   }

   static void stashHands(LivingEntity e) {
      if (!SCENES.isEmpty() && !HANDS.containsKey(e) && (asActor(e) != null || asVictim(e) != null)) {
         ItemStack main = e.getMainHandItem();
         ItemStack off = e.getOffhandItem();
         if (!main.isEmpty() || !off.isEmpty()) {
            HANDS.put(e, new ItemStack[]{main, off});
            e.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            e.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
         }
      }
   }

   static void restoreHands(LivingEntity e) {
      if (!HANDS.isEmpty()) {
         ItemStack[] items = HANDS.remove(e);
         if (items != null) {
            e.setItemSlot(EquipmentSlot.MAINHAND, items[0]);
            e.setItemSlot(EquipmentSlot.OFFHAND, items[1]);
         }
      }
   }

   static void restoreAllHands() {
      if (!HANDS.isEmpty()) {
         for (LivingEntity e : new ArrayList<>(HANDS.keySet())) {
            restoreHands(e);
         }
      }
   }

   @Nullable
   static ExecutionScenes.Scene local() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return null;
      }

      for (ExecutionScenes.Scene s : SCENES) {
         if (s.involves(mc.player) && s.ticks < 118 + 12) {
            return s;
         }
      }

      return null;
   }

   @Nullable
   static ExecutionScenes.Scene ofActor(Entity e) {
      return SCENES.isEmpty() ? null : asActor(e);
   }

   static ItemStack heldItem(LivingEntity e) {
      ItemStack[] stashed = HANDS.get(e);
      return stashed != null ? stashed[0] : e.getMainHandItem();
   }

   @Nullable
   private static ExecutionScenes.Scene asActor(Entity e) {
      for (ExecutionScenes.Scene s : SCENES) {
         if (s.actor == e) {
            if (!s.actorDone && s.ticks < 118 + 40) {
               return s;
            }

            if (s.ticks < 118 + 8) {
               Vec3 end = s.world(s.choreo().end(s.side));
               if (e.position().distanceToSqr(end.x, e.getY(), end.z) > 0.04000000000000001) {
                  return s;
               }
            }
         }
      }

      return null;
   }

   @Nullable
   private static ExecutionScenes.Scene asVictim(Entity e) {
      for (ExecutionScenes.Scene s : SCENES) {
         if (s.victim == e && !s.corpseGone) {
            return s;
         }
      }

      return null;
   }

   static boolean involved(Entity e) {
      return !SCENES.isEmpty() && (asActor(e) != null || asVictim(e) != null);
   }

   static boolean hidden(Entity e) {
      if (!SCENES.isEmpty() && e != drawing) {
         float pt = Minecraft.getInstance().getFrameTime();
         ExecutionScenes.Scene s = asVictim(e);
         if (s == null || !s.struck && !(s.choreo().victimAlpha(s.state(pt)) < 1.0F)) {
            s = asActor(e);
            return s != null && s.choreo().actorAlpha(s.state(pt)) < 1.0F;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   static boolean pose(HumanoidModel<?> m, LivingEntity e, float age) {
      if (SCENES.isEmpty()) {
         return false;
      }

      float partial = Mth.clamp(age - e.tickCount, 0.0F, 1.0F);
      ExecutionScenes.Scene s = asActor(e);
      if (s != null) {
         s.choreo().poseActor(m, s.state(partial), age);
         return true;
      }

      s = asVictim(e);
      if (s != null) {
         CoupDeGrace.State state = s.state(partial);
         s.choreo().poseVictim(m, state, age);
         if (state instanceof Script.Frame f && f.headOff) {
            m.head.visible = false;
            m.hat.visible = false;
         }

         return true;
      } else {
         return false;
      }
   }

   static boolean push(LivingEntity e, PoseStack ps, float partialTick) {
      if (SCENES.isEmpty()) {
         return false;
      }

      ExecutionScenes.Scene s = asActor(e);
      boolean actor = s != null;
      if (s == null) {
         s = asVictim(e);
      }

      if (s == null) {
         return false;
      }

      CoupDeGrace.State state = s.state(partialTick);
      CoupDeGrace.Body body = actor ? state.actor : state.victim;
      double x = Mth.lerp(partialTick, e.xOld, e.getX());
      double y = Mth.lerp(partialTick, e.yOld, e.getY());
      double z = Mth.lerp(partialTick, e.zOld, e.getZ());
      ps.pushPose();
      ps.translate(s.origin.x - x, s.origin.y - y + (e.isCrouching() ? 0.125 : 0.0), s.origin.z - z);
      ps.mulPose(Axis.YP.rotationDegrees(-s.yaw));
      ps.translate(body.pos.x, body.pos.y, body.pos.z);
      ps.mulPose(body.rot);
      float swim = e.getSwimAmount(partialTick);
      if (swim > 0.0F) {
         if (e.isVisuallySwimming()) {
            ps.translate(0.0F, 1.0F, -0.3F);
         }

         float flat = e instanceof Player && e.isInWater() ? -90.0F - e.getXRot() : -90.0F;
         ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(swim, 0.0F, flat)));
      }

      ps.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot) - 180.0F));
      return true;
   }

   static void renderCorpses(RenderLevelStageEvent event) {
      if (!SCENES.isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
         Vec3 cam = event.getCamera().getPosition();
         PoseStack ps = event.getPoseStack();
         BufferSource buffers = mc.renderBuffers().bufferSource();
         float pt = event.getPartialTick();
         boolean drawn = false;

         for (ExecutionScenes.Scene s : SCENES) {
            LivingEntity body = s.victim;
            if (s.struck
               && !s.corpseGone
               && body != null
               && (body != mc.getCameraEntity() || !mc.options.getCameraType().isFirstPerson())
               && s.choreo().victimAlpha(s.state(pt)) >= 1.0F) {
               int deathTime = body.deathTime;
               int hurtTime = body.hurtTime;
               body.deathTime = 0;
               body.hurtTime = 0;
               drawing = body;

               try {
                  double x = Mth.lerp(pt, body.xOld, body.getX());
                  double y = Mth.lerp(pt, body.yOld, body.getY());
                  double z = Mth.lerp(pt, body.zOld, body.getZ());
                  dispatcher.render(
                     body, x - cam.x, y - cam.y, z - cam.z, body.getYRot(), pt, ps, buffers, dispatcher.getPackedLightCoords(body, pt)
                  );
                  drawn = true;
               } finally {
                  drawing = null;
                  body.deathTime = deathTime;
                  body.hurtTime = hurtTime;
               }
            }
         }

         if (drawn) {
            buffers.endBatch();
         }
      }
   }

   static Iterable<ExecutionScenes.Scene> all() {
      return SCENES;
   }

   @Nullable
   static ExecutionScenes.Scene ofVictim(Entity e) {
      return asVictim(e);
   }

   static final class Scene {
      final int actorId;
      final int victimId;
      final ClientLevel level;
      @Nullable
      LivingEntity actor;
      @Nullable
      LivingEntity victim;
      ExecutionType type = ExecutionType.COUP_DE_GRACE;
      Vec3 origin = Vec3.ZERO;
      float yaw;
      int side = 1;
      int ticks;
      boolean struck;
      boolean actorDone;
      boolean corpseGone;
      boolean started;
      float victimYaw;
      float actorYaw = 180.0F;
      final Vector3f actorStart = new Vector3f(0.0F, 0.0F, 0.75F);
      @Nullable
      private CoupDeGrace.State cached;
      private float cachedTime;
      @Nullable
      private ExecutionType cachedType;
      @Nullable
      private Choreography choreo;
      @Nullable
      private ExecutionType choreoType;
      @Nullable
      private LivingEntity choreoActor;

      Scene(int actorId, int victimId, ClientLevel level) {
         this.actorId = actorId;
         this.victimId = victimId;
         this.level = level;
      }

      float time(float partialTick) {
         return this.ticks + partialTick;
      }

      Choreography choreo() {
         if (this.choreoType != this.type || this.choreoActor != this.actor) {
            this.choreoType = this.type;
            this.choreoActor = this.actor;
            this.choreo = Choreography.of(this);
         }

         return this.choreo;
      }

      CoupDeGrace.State state(float partialTick) {
         float time = this.time(partialTick);
         if (this.cached == null || this.cachedTime != time || this.cachedType != this.type) {
            this.cached = this.choreo().at(time, this.side, this.victimYaw, this.actorStart, this.actorYaw);
            this.cachedTime = time;
            this.cachedType = this.type;
         }

         return this.cached;
      }

      Vec3 world(Vector3f p) {
         float r = -this.yaw * (float) (Math.PI / 180.0);
         float c = Mth.cos(r);
         float s = Mth.sin(r);
         return this.origin.add(p.x * c + p.z * s, p.y, -p.x * s + p.z * c);
      }

      Vector3f local(Vec3 w) {
         float r = this.yaw * (float) (Math.PI / 180.0);
         float c = Mth.cos(r);
         float s = Mth.sin(r);
         float x = (float)(w.x - this.origin.x);
         float z = (float)(w.z - this.origin.z);
         return new Vector3f(x * c + z * s, (float)(w.y - this.origin.y), -x * s + z * c);
      }

      boolean involves(Entity e) {
         return e == this.actor || e == this.victim;
      }
   }
}
