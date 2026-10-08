package fr.heraliz.reanimation.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

abstract class Script implements Choreography {
   static final float STRIKE = 96.0F;
   static final float END = 118.0F;
   static final float TAU = (float) (Math.PI * 2);
   static final float PI = (float) Math.PI;
   static final Vector3f PRONE = at(0.0F, 0.0F, 0.26F);
   static final Vector3f SUPINE = at(0.04F, 0.0F, 0.2F);
   static final Vector3f HEAD_CENTRE = new Vector3f(0.0F, -4.0F, 0.0F);
   private static final List<HumanoidModel<LivingEntity>> ACTOR_PROBES = new ArrayList<>();
   private static final List<HumanoidModel<LivingEntity>> VICTIM_PROBES = new ArrayList<>();
   private static int actorDepth;
   private static int victimDepth;
   private static final Map<Script.PlantKey, Matrix4f> PLANTS = new LinkedHashMap<Script.PlantKey, Matrix4f>(16, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<Script.PlantKey, Matrix4f> eldest) {
         return this.size() > 32;
      }
   };

   static Vector3f at(float fwd, float side, float up) {
      return CoupDeGrace.at(fwd, side, up);
   }

   static Script.Prop box(Script.Material material, Vector3f min, Vector3f size, float grow) {
      return new Script.Prop(material, new Matrix4f().translate(min).scale(size.x, size.y * Math.max(0.001F, grow), size.z));
   }

   abstract Blade blade();

   abstract float spin(float var1);

   Quaternionf wrist(float t) {
      return new Quaternionf();
   }

   float scale(float t) {
      return 1.0F;
   }

   Vector3f shift(float t) {
      return new Vector3f();
   }

   float slowmo(float t) {
      return 0.0F;
   }

   abstract void victim(Script.Frame var1, float var2);

   abstract void actor(Script.Frame var1, float var2);

   abstract void knife(Script.Frame var1, float var2);

   void props(Script.Frame f, float t) {
   }

   @Nullable
   Vector3f killPoint(Script.Frame f) {
      return null;
   }

   void plant(Knife k, Script.Plant p) {
      k.stuck(p.part(), p.tip(), p.dir(), p.axis());
   }

   Matrix4f planted(Script.Frame f, Script.Plant p) {
      return this.stuckInScene(f, p.part(), p.tip(), p.dir(), p.axis());
   }

   Vector3f plantedPivot(Script.Frame f, Script.Plant p) {
      return this.victimPoint(f, p.part(), p.pivot(this.blade(), this.scale(f.t)));
   }

   Matrix4f heldInPart(Script.Frame f, float t, Knife.Part part) {
      Script.PlantKey key = new Script.PlantKey(this, f.victimYaw, f.actorStart.x, f.actorStart.y, f.actorStart.z, f.actorYaw, t, part);
      Matrix4f cached = PLANTS.get(key);
      if (cached == null) {
         cached = this.victimPart(this.other(f, t), part).invert().mul(this.heldAt(f, t));
         PLANTS.put(key, cached);
      }

      return new Matrix4f(cached);
   }

   Matrix4f driving(Script.Frame f, float t, Script.Plant p, float from, float to) {
      return Knife.flight(this.blade(), this.heldAt(f, t), this.planted(f, p), CoupDeGrace.smooth(CoupDeGrace.phase(t, from, to)), 0.0F, 0.0F);
   }

   Script.Frame newFrame(float t) {
      return new Script.Frame(t, this.blade());
   }

   @Override
   public CoupDeGrace.State at(float t, int side, float victimYaw, Vector3f actorStart, float actorYaw) {
      return this.frame(t, victimYaw, actorStart, actorYaw, true);
   }

   Script.Frame frame(float t, float victimYaw, Vector3f actorStart, float actorYaw, boolean withKnife) {
      Script.Frame f = this.newFrame(t);
      f.full = withKnife;
      f.victimYaw = victimYaw;
      f.actorStart.set(actorStart);
      f.actorYaw = actorYaw;
      this.victim(f, t);
      this.actor(f, t);
      this.props(f, t);
      f.slowmo = this.slowmo(t);
      f.knife.scale = this.scale(t);
      if (withKnife) {
         f.knife.hand(this.spin(t), this.wrist(t), this.shift(t));
         this.knife(f, t);
      }

      return f;
   }

   Script.Frame other(Script.Frame f, float t) {
      return this.frame(t, f.victimYaw, f.actorStart, f.actorYaw, false);
   }

   static float entry(Script.Frame f, float t, Vector3f spot, float arrive, Vector3f out) {
      Vector3f start = new Vector3f(f.actorStart.x, 0.0F, f.actorStart.z);
      float travel = start.distance(spot);
      float u = CoupDeGrace.smooth(t / arrive);
      if (travel < 0.6F) {
         out.set(start).lerp(spot, u);
         f.walk = travel > 0.25F ? Keys.window(t, 0.0F, arrive, 2.0F) * 0.6F : 0.0F;
         f.stride = t * 0.9F;
         return Mth.rotLerp(CoupDeGrace.smooth(t / 3.0F), f.actorYaw, 180.0F);
      } else {
         boolean around = start.z < 0.25F;
         Vector3f[] path = around
            ? new Vector3f[]{start, at(Math.min(start.z, -0.2F), -1.55F, 0.0F), at(0.55F, -1.45F, 0.0F), spot}
            : new Vector3f[]{start, spot};
         out.set(along(path, u));
         Vector3f ahead = along(path, Math.min(1.0F, u + 0.06F));
         float heading = ahead.distanceSquared(out) > 1.0E-6F ? yawToward(out, ahead) : 180.0F;
         float settle = CoupDeGrace.smooth(CoupDeGrace.phase(t, arrive * 0.6F, arrive));
         f.walk = Keys.window(t, 0.0F, arrive, 2.0F);
         f.stride = t * 0.9F;
         return Mth.rotLerp(settle, Mth.rotLerp(CoupDeGrace.smooth(t / 3.0F), f.actorYaw, heading), 180.0F);
      }
   }

   static float solveLean(CoupDeGrace.Body a, Vector3f target, float wantPx) {
      return solveLean(a, target, wantPx, 0.0F, 0.0F);
   }

   static float solveLean(CoupDeGrace.Body a, Vector3f target, float wantPx, float prefer) {
      return solveLean(a, target, wantPx, prefer, 1.0F);
   }

   private static float solveLean(CoupDeGrace.Body a, Vector3f target, float wantPx, float prefer, float bias) {
      float best = 0.0F;
      float bestErr = Float.MAX_VALUE;

      for (float l = -0.2F; l <= 1.45F; l += 0.02F) {
         float err = Math.abs(shoulderDistance(a, target, l) - wantPx) + bias * Math.abs(l - prefer);
         if (err < bestErr) {
            bestErr = err;
            best = l;
         }
      }

      return best;
   }

   static float shoulderDistance(CoupDeGrace.Body a, Vector3f target, float lean) {
      Vector3f shoulder = a.scene(-5.0F, 12.0F - 10.0F * Mth.cos(lean), -10.0F * Mth.sin(lean));
      return shoulder.distance(target) / 0.05859375F;
   }

   float reach(Vector3f point, float t) {
      return this.inHand(point, t).length();
   }

   Vector3f inHand(Vector3f point, float t) {
      return Knife.inHand(this.blade(), point, this.spin(t), this.wrist(t), this.scale(t), this.shift(t));
   }

   Matrix4f heldAt(Script.Frame ctx, float t) {
      Script.Frame g = this.other(ctx, t);
      HumanoidModel<LivingEntity> m = enterActor();

      try {
         this.poseActor(m, g, t);
         return Knife.heldInScene(g.actor, m.rightArm, this.blade(), this.spin(t), this.wrist(t), this.scale(t), this.shift(t));
      } finally {
         actorDepth--;
      }
   }

   @Nullable
   Matrix4f knifeInScene(Script.Frame f) {
      Knife k = f.knife;
      switch (k.holder) {
         case HAND:
            HumanoidModel<LivingEntity> m = enterActor();

            try {
               this.poseActor(m, f, f.t);
               return Knife.heldInScene(f.actor, m.rightArm, k.blade, k.spin, k.wrist, k.scale, k.shift);
            } finally {
               actorDepth--;
            }
         case VICTIM:
            return this.victimPart(f, k.part).mul(k.matrix);
         case SCENE:
            return new Matrix4f(k.matrix);
         default:
            return null;
      }
   }

   Vector3f victimPoint(Script.Frame f, Knife.Part part, Vector3f localPx) {
      HumanoidModel<LivingEntity> m = enterVictim();

      try {
         this.poseVictim(m, f, f.t);
         return f.victim.scene(Rig.point(part.of(m), localPx));
      } finally {
         victimDepth--;
      }
   }

   Matrix4f victimPart(Script.Frame f, Knife.Part part) {
      HumanoidModel<LivingEntity> m = enterVictim();

      try {
         this.poseVictim(m, f, f.t);
         return Knife.sceneFromModel(f.victim).mul(Knife.partMatrix(part.of(m)));
      } finally {
         victimDepth--;
      }
   }

   static Vector3f spinAxis(Matrix4f item, Blade blade) {
      Vector3f p = Knife.through(item, blade.pivot);
      return Knife.through(item, new Vector3f(blade.pivot).add(blade.axis)).sub(p).normalize();
   }

   Matrix4f pulled(Script.Frame f, float t, Matrix4f stuck, float from, float to) {
      return Knife.flight(this.blade(), stuck, this.heldAt(f, to), CoupDeGrace.smooth(CoupDeGrace.phase(t, from, to)), 0.0F, 0.0F);
   }

   Matrix4f stuckInScene(Script.Frame f, Knife.Part part, Vector3f tipPx, Vector3f dir, Vector3f axis) {
      Knife k = new Knife(this.blade());
      k.scale = this.scale(f.t);
      k.stuck(part, tipPx, dir, axis);
      return this.victimPart(f, part).mul(k.matrix);
   }

   static Matrix4f flyingHead(Matrix4f start, Vector3f velocity, Vector3f spinAxis, float spinRate, float dt, float restitution) {
      Vector3f c0 = start.transformPosition(new Vector3f(HEAD_CENTRE).div(16.0F));
      float gravity = 0.04F;
      float radius = 0.24F;
      Vector3f p = new Vector3f(c0);
      Vector3f v = new Vector3f(velocity);
      float angle = 0.0F;
      float rate = spinRate;
      float settled = -1.0F;
      float step = 0.25F;

      for (float s = 0.0F; s < dt; s += step) {
         float h = Math.min(step, dt - s);
         v.y -= gravity * h;
         p.fma(h, v);
         angle += rate * h;
         if (p.y < radius) {
            p.y = radius;
            if (v.y < -0.06F) {
               v.y = -v.y * restitution;
               v.x *= 0.6F;
               v.z *= 0.6F;
               rate *= 0.55F;
            } else {
               v.set(0.0F);
               rate *= 0.8F;
               if (settled < 0.0F) {
                  settled = s;
               }
            }
         }
      }

      Quaternionf spin = new Quaternionf().rotationAxis(angle, new Vector3f(spinAxis).normalize());
      Matrix4f m = new Matrix4f().translate(p).rotate(spin).translate(-c0.x, -c0.y, -c0.z).mul(start);
      if (settled >= 0.0F) {
         float k = CoupDeGrace.smooth((dt - settled) / 6.0F);
         Quaternionf rot = m.getNormalizedRotation(new Quaternionf());
         Vector3f best = null;
         float bestDot = -2.0F;

         for (Vector3f axis : new Vector3f[]{
            new Vector3f(1.0F, 0.0F, 0.0F),
            new Vector3f(-1.0F, 0.0F, 0.0F),
            new Vector3f(0.0F, 1.0F, 0.0F),
            new Vector3f(0.0F, -1.0F, 0.0F),
            new Vector3f(0.0F, 0.0F, 1.0F),
            new Vector3f(0.0F, 0.0F, -1.0F)
         }) {
            Vector3f w = rot.transform(new Vector3f(axis));
            if (w.y > bestDot) {
               bestDot = w.y;
               best = w;
            }
         }

         Quaternionf level = new Quaternionf().rotationTo(best, new Vector3f(0.0F, 1.0F, 0.0F));
         Quaternionf part = new Quaternionf().slerp(level, k);
         Vector3f c = m.transformPosition(new Vector3f(HEAD_CENTRE).div(16.0F));
         m = new Matrix4f().translate(c).rotate(part).translate(-c.x, -c.y, -c.z).mul(m);
      }

      return m;
   }

   static Vector3f partPoint(CoupDeGrace.Body body, float x, float y, float z, float[] rot, Vector3f local) {
      Vector3f model = new Quaternionf().rotationZYX(rot[2], rot[1], rot[0]).transform(new Vector3f(local)).add(x, y, z);
      return body.scene(model);
   }

   static ModelPart part(float x, float y, float z, float[] rot) {
      ModelPart p = new ModelPart(List.of(), Map.of());
      p.setPos(x, y, z);
      p.setRotation(rot[0], rot[1], rot[2]);
      return p;
   }

   static float yawToward(Vector3f from, Vector3f to) {
      float dx = to.x - from.x;
      float dz = to.z - from.z;
      return 180.0F - (float)Math.toDegrees(Math.atan2(-dx, -dz));
   }

   static Vector3f along(Vector3f[] path, float u) {
      float total = 0.0F;

      for (int i = 1; i < path.length; i++) {
         total += path[i].distance(path[i - 1]);
      }

      float d = u * total;

      for (int i = 1; i < path.length; i++) {
         float seg = path[i].distance(path[i - 1]);
         if (d <= seg || i == path.length - 1) {
            return new Vector3f(path[i - 1]).lerp(path[i], seg <= 0.0F ? 1.0F : Math.min(1.0F, d / seg));
         }

         d -= seg;
      }

      return new Vector3f(path[path.length - 1]);
   }

   static void prone(Script.Frame f, float t, float roll, Vector3f offset) {
      float in = CoupDeGrace.smooth(t / 8.0F);
      float yaw = Mth.rotLerp(in, f.victimYaw, 90.0F);
      float r = yaw * (float) (Math.PI / 180.0);
      Vector3f c = new Vector3f(-Mth.sin(r) * 0.057F, 0.3F, Mth.cos(r) * 0.057F).lerp(PRONE, in).add(offset);
      f.victim.turn(yaw, -90.0F, 0.0F, roll).anchor(1.0556251F, c);
   }

   static void raiseChest(Script.Frame f, float degrees) {
      if (!(degrees <= 0.0F)) {
         CoupDeGrace.Body v = f.victim;
         Vector3f hips = v.scene(0.0F, 12.0F, 0.0F);
         v.rot.mul(new Quaternionf().rotationX(degrees * (float) (Math.PI / 180.0)));
         v.anchorPoint(new Vector3f(0.0F, 12.0F, 0.0F), hips);
         f.lift = degrees * (float) (Math.PI / 180.0);
      }
   }

   static void legsDown(HumanoidModel<?> m, Script.Frame f) {
      m.rightLeg.xRot = m.rightLeg.xRot + f.lift;
      m.leftLeg.xRot = m.leftLeg.xRot + f.lift;
   }

   static void handOnGround(ModelPart arm, Vector3f handLocal, CoupDeGrace.Body body, Vector3f toward, float amount) {
      Vector3f shoulder = body.scene(arm.x, arm.y, arm.z);
      float length = handLocal.length() * 0.05859375F;
      float height = Math.max(0.0F, shoulder.y - 0.01F);
      float ahead = height < length ? Mth.sqrt(length * length - height * height) : 0.0F;
      Vector3f flat = new Vector3f(toward.x, 0.0F, toward.z);
      if (flat.lengthSquared() > 1.0E-6F) {
         flat.normalize(ahead);
      }

      Vector3f hand = new Vector3f(shoulder.x, 0.01F, shoulder.z).add(flat);
      Rig.aim(arm, handLocal, body.model(hand), 0.0F, amount);
   }

   static void proneLimbs(HumanoidModel<?> m, Script.Frame f, float age, float pull1, float pull2, float flinch) {
      float wave = age * 0.8F;
      m.rightArm.xRot = -3.05F - 0.35F * pull1 - 0.45F * flinch;
      m.leftArm.xRot = -3.05F - 0.35F * pull2 - 0.45F * flinch;
      m.rightArm.zRot = -0.35F - 0.25F * pull1 + 0.1F * Mth.sin(wave);
      m.leftArm.zRot = 0.35F + 0.25F * pull2 - 0.1F * Mth.sin(wave + 1.0F);
      m.rightLeg.setRotation(0.04F, 0.0F, 0.07F);
      m.leftLeg.setRotation(0.04F, 0.0F, -0.07F);
      CoupDeGrace.look(m.head, f.victim.model(f.actor.scene(0.0F, -4.0F, 0.0F)), 1.2F, 0.6F);
      m.head.xRot = Math.min(m.head.xRot, Mth.lerp(flinch, -0.45F, -1.1F));
   }

   private static HumanoidModel<LivingEntity> enterActor() {
      if (ACTOR_PROBES.size() <= actorDepth) {
         ACTOR_PROBES.add(probe());
      }

      return ACTOR_PROBES.get(actorDepth++);
   }

   private static HumanoidModel<LivingEntity> enterVictim() {
      if (VICTIM_PROBES.size() <= victimDepth) {
         VICTIM_PROBES.add(probe());
      }

      return VICTIM_PROBES.get(victimDepth++);
   }

   static HumanoidModel<LivingEntity> probe() {
      return new HumanoidModel(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 32).bakeRoot());
   }

   static class Frame extends CoupDeGrace.State {
      final Knife knife;
      float drop;
      float kneel;
      float spread;
      float walk;
      float stride;
      float roll;
      float lift;
      boolean full;
      float victimYaw;
      Vector3f actorStart = new Vector3f();
      float actorYaw;
      boolean headOff;
      final Matrix4f head = new Matrix4f();
      final List<Script.Wound> wounds = new ArrayList<>();
      final List<Script.Prop> props = new ArrayList<>();
      float slowmo;

      Frame(float t, Blade blade) {
         super(t, 1);
         this.knife = new Knife(blade);
      }
   }

   enum Material {
      POST,
      BEAM;
   }

   record Plant(Knife.Part part, Vector3f tip, Vector3f dir, Vector3f axis) {
      Plant {
         dir = new Vector3f(dir).normalize();
      }

      Vector3f pivot(Blade blade, float scale) {
         return new Vector3f(this.tip).sub(new Vector3f(this.dir).mul(blade.length * scale));
      }

      Script.Plant tip(Vector3f tip) {
         return new Script.Plant(this.part, tip, this.dir, this.axis);
      }
   }

   private record PlantKey(Script script, float victimYaw, float x, float y, float z, float actorYaw, float t, Knife.Part part) {
   }

   record Prop(Script.Material material, Matrix4f matrix) {
   }

   record Wound(Knife.Part part, Vector3f from, Vector3f to, Vector3f normal, float width, float progress) {
   }
}
