package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.reanimation.ClientConfig;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class KnifeTrail {
   private static final float LIFE = 5.0F;
   private static final Matrix4f WORLD_FROM_VIEW = new Matrix4f();
   private static boolean viewValid;
   private static final Map<ExecutionScenes.Scene, ArrayDeque<KnifeTrail.Sample>> TRAILS = new IdentityHashMap<>();
   private static final Map<ExecutionScenes.Scene, Vec3> LAST_TIP = new IdentityHashMap<>();

   private KnifeTrail() {
   }

   static void beginFrame(RenderLevelStageEvent event) {
      Vec3 cam = event.getCamera().getPosition();
      Matrix4f viewRot = new Matrix4f(event.getPoseStack().last().pose());
      WORLD_FROM_VIEW.translation((float)cam.x, (float)cam.y, (float)cam.z).mul(viewRot.invert());
      viewValid = true;
   }

   static void sample(ExecutionScenes.Scene s, float time, Matrix4f itemToView, Knife k) {
      if (viewValid) {
         Vector3f tipItem = Knife.armInItem().transformPosition(new Vector3f(k.blade.tip).div(16.0F));
         Vector3f tip = WORLD_FROM_VIEW.transformPosition(itemToView.transformPosition(tipItem));
         LAST_TIP.put(s, new Vec3(tip.x, tip.y, tip.z));
         if (!(k.trail <= 0.01F) && (Boolean)ClientConfig.KNIFE_TRAILS.get()) {
            Vector3f midItem = Knife.armInItem().transformPosition(new Vector3f(k.blade.tip).lerp(k.blade.pivot, 0.55F).div(16.0F));
            Vector3f mid = WORLD_FROM_VIEW.transformPosition(itemToView.transformPosition(midItem));
            ArrayDeque<KnifeTrail.Sample> trail = TRAILS.computeIfAbsent(s, key -> new ArrayDeque<>());
            KnifeTrail.Sample last = trail.peekLast();
            if (last == null || !(time < last.time) && (!(time - last.time < 0.08F) || !(last.tip.distanceSquared(tip) < 4.0E-4F))) {
               trail.addLast(new KnifeTrail.Sample(time, tip, mid, Mth.clamp(k.trail, 0.0F, 1.5F), k.blood));

               while (trail.size() > 64) {
                  trail.removeFirst();
               }
            }
         }
      }
   }

   @Nullable
   static Vec3 lastTip(ExecutionScenes.Scene s) {
      return LAST_TIP.get(s);
   }

   static void render(RenderLevelStageEvent event) {
      if (!TRAILS.isEmpty()) {
         Set<ExecutionScenes.Scene> alive = Collections.newSetFromMap(new IdentityHashMap<>());

         for (ExecutionScenes.Scene s : ExecutionScenes.all()) {
            alive.add(s);
         }

         TRAILS.keySet().retainAll(alive);
         LAST_TIP.keySet().retainAll(alive);
         Minecraft mc = Minecraft.getInstance();
         Vec3 cam = event.getCamera().getPosition();
         PoseStack ps = event.getPoseStack();
         Matrix4f pose = ps.last().pose();
         BufferSource buffers = mc.renderBuffers().bufferSource();
         VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
         float pt = event.getPartialTick();
         boolean drawn = false;

         for (Entry<ExecutionScenes.Scene, ArrayDeque<KnifeTrail.Sample>> e : TRAILS.entrySet()) {
            float now = e.getKey().time(pt);
            ArrayDeque<KnifeTrail.Sample> trail = e.getValue();
            Iterator<KnifeTrail.Sample> it = trail.iterator();

            while (it.hasNext()) {
               if (now - it.next().time > 5.0F) {
                  it.remove();
               }
            }

            KnifeTrail.Sample prev = null;

            for (KnifeTrail.Sample cur : trail) {
               if (prev != null && cur.time - prev.time < 1.5F) {
                  float a0 = alpha(prev, now);
                  float a1 = alpha(cur, now);
                  if (a0 > 0.005F || a1 > 0.005F) {
                     quad(vc, pose, cam, prev, cur, a0, a1);
                     drawn = true;
                  }
               }

               prev = cur;
            }
         }

         if (drawn) {
            buffers.endBatch(RenderType.lightning());
         }
      }
   }

   private static float alpha(KnifeTrail.Sample s, float now) {
      float k = 1.0F - Mth.clamp((now - s.time) / 5.0F, 0.0F, 1.0F);
      return 0.8F * s.strength * k * k;
   }

   private static void quad(VertexConsumer vc, Matrix4f pose, Vec3 cam, KnifeTrail.Sample a, KnifeTrail.Sample b, float alphaA, float alphaB) {
      float[] ca = color(a.blood);
      float[] cb = color(b.blood);
      vertex(vc, pose, cam, a.tip, ca, alphaA);
      vertex(vc, pose, cam, a.mid, ca, 0.0F);
      vertex(vc, pose, cam, b.mid, cb, 0.0F);
      vertex(vc, pose, cam, b.tip, cb, alphaB);
      vertex(vc, pose, cam, b.tip, cb, alphaB);
      vertex(vc, pose, cam, b.mid, cb, 0.0F);
      vertex(vc, pose, cam, a.mid, ca, 0.0F);
      vertex(vc, pose, cam, a.tip, ca, alphaA);
   }

   private static float[] color(float blood) {
      float b = 0.65F * Mth.clamp(blood, 0.0F, 1.0F);
      return new float[]{Mth.lerp(b, 0.85F, 0.9F), Mth.lerp(b, 0.92F, 0.12F), Mth.lerp(b, 1.0F, 0.1F)};
   }

   private static void vertex(VertexConsumer vc, Matrix4f pose, Vec3 cam, Vector3f p, float[] c, float alpha) {
      vc.vertex(pose, (float)(p.x - cam.x), (float)(p.y - cam.y), (float)(p.z - cam.z)).color(c[0], c[1], c[2], alpha).endVertex();
   }

   static void clear() {
      TRAILS.clear();
      LAST_TIP.clear();
      viewValid = false;
   }

   private record Sample(float time, Vector3f tip, Vector3f mid, float strength, float blood) {
   }
}
