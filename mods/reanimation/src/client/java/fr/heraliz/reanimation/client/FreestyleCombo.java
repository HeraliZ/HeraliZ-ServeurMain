package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

final class FreestyleCombo extends Finisher {
   static final FreestyleCombo INSTANCE = new FreestyleCombo();
   static final float HAUL = 2.0F;
   static final float STAND = 14.0F;
   static final float LAUNCH = 32.0F;
   static final float SLAMMED = 46.0F;
   static final float UP_AGAIN = 58.0F;
   static final float TAUNT = 72.0F;
   static final float UPPER = 80.0F;
   static final float SLOW = 86.0F;
   static final float JUMP = 89.0F;
   static final float[] TICKS = new float[]{
      12.0F, 16.0F, 18.0F, 20.0F, 24.0F, 26.0F, 28.0F, 30.0F, 32.0F, 38.0F, 40.0F, 42.0F, 44.0F, 58.0F, 60.0F, 62.0F, 64.0F, 66.0F, 68.0F, 70.0F, 72.0F, 80.0F
   };
   static final FreestyleCombo.Blow[] BLOWS = new FreestyleCombo.Blow[]{
      FreestyleCombo.Blow.SLAP,
      FreestyleCombo.Blow.JAB,
      FreestyleCombo.Blow.JAB,
      FreestyleCombo.Blow.CROSS,
      FreestyleCombo.Blow.BODY,
      FreestyleCombo.Blow.BODY,
      FreestyleCombo.Blow.HOOK,
      FreestyleCombo.Blow.HOOK,
      FreestyleCombo.Blow.UPPER,
      FreestyleCombo.Blow.AIR_KICK,
      FreestyleCombo.Blow.AIR_PUNCH,
      FreestyleCombo.Blow.SPIN_KICK,
      FreestyleCombo.Blow.AXE,
      FreestyleCombo.Blow.FLURRY,
      FreestyleCombo.Blow.FLURRY,
      FreestyleCombo.Blow.FLURRY,
      FreestyleCombo.Blow.FLURRY,
      FreestyleCombo.Blow.FLURRY,
      FreestyleCombo.Blow.FLURRY,
      FreestyleCombo.Blow.FLURRY,
      FreestyleCombo.Blow.CROSS,
      FreestyleCombo.Blow.UPPER
   };
   static final int GOLD = 16767072;
   static final int WHITE = 16777215;

   private FreestyleCombo() {
      super(ExecutionType.FREESTYLE_COMBO);
   }

   @Override
   float endFwd() {
      return 0.3F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 0.9F;
   }

   static boolean big(int i) {
      FreestyleCombo.Blow b = BLOWS[i];
      return b == FreestyleCombo.Blow.CROSS || b == FreestyleCombo.Blow.UPPER || b == FreestyleCombo.Blow.AXE || b == FreestyleCombo.Blow.SPIN_KICK;
   }

   static int blow(float t) {
      int k = -1;

      for (int i = 0; i < TICKS.length; i++) {
         if (t >= TICKS[i] - 2.0F && t < TICKS[i] + 3.0F) {
            k = i;
         }
      }

      return k;
   }

   static float extend(int k, float t) {
      float hit = TICKS[k];
      float wind = BLOWS[k] == FreestyleCombo.Blow.FLURRY ? 1.0F : 2.0F;
      return t < hit ? smooth(phase(t, hit - wind, hit)) : 1.0F - smooth(phase(t, hit + 0.5F, hit + (BLOWS[k] == FreestyleCombo.Blow.FLURRY ? 1.5F : 3.0F)));
   }

   static float jolt(float t, FreestyleCombo.Blow... kinds) {
      float j = 0.0F;

      for (int i = 0; i < TICKS.length; i++) {
         for (FreestyleCombo.Blow kind : kinds) {
            if (BLOWS[i] == kind) {
               j += Keys.jolt(t, TICKS[i], 7.0F, 0.6F);
            }
         }
      }

      return j;
   }

   static float bodyFwd(float t) {
      return Keys.key(
         t,
         14.0F,
         0.0F,
         18.5F,
         -0.12F,
         20.5F,
         -0.42F,
         23.0F,
         -0.5F,
         32.0F,
         -0.5F,
         46.0F,
         -0.7F,
         58.0F,
         -0.7F,
         72.0F,
         -1.0F,
         78.0F,
         -0.85F,
         96.0F,
         -0.85F,
         104.0F,
         -1.6F
      );
   }

   static float air(float t) {
      if (!(t < 32.0F) && !(t >= 46.0F)) {
         float up = Spectacle.out(phase(t, 32.0F, 38.0F), 2.0F);
         float bumps = 0.0F;

         for (float h : new float[]{38.0F, 40.0F, 42.0F}) {
            bumps += 0.15F * Mth.sin((float) Math.PI * phase(t, h, h + 3.0F));
         }

         float slam = Spectacle.in(phase(t, 44.0F, 46.0F), 2.0F);
         return Mth.lerp(slam, 1.0556251F + 1.1F * up + bumps, 0.3F);
      } else {
         return 0.0F;
      }
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      CoupDeGrace.Body v = s.victim;
      float fwd = bodyFwd(t);
      if (t < 2.0F) {
         prone(v, t, victimYaw, s.side);
      } else if (t < 32.0F) {
         float up = ease(t, 2.0F, 14.0F);
         float pitch = 9.0F * Math.max(0.0F, jolt(t, FreestyleCombo.Blow.JAB, FreestyleCombo.Blow.CROSS))
            - 14.0F * Math.max(0.0F, jolt(t, FreestyleCombo.Blow.BODY));
         float yaw = 35.0F * jolt(t, FreestyleCombo.Blow.HOOK) * (t < 29.0F ? 1.0F : -1.0F) - 25.0F * jolt(t, FreestyleCombo.Blow.SLAP);
         float roll = 4.0F * Mth.sin(t * 0.17F) * ease(t, 14.0F, 20.0F);
         rise(v, up, s.side, yaw, at(fwd, 0.0F, 1.0556251F));
         if (up >= 1.0F) {
            v.turn(yaw, pitch, roll, 0.0F).feet(at(fwd, 0.0F, 0.0F));
         }
      } else if (t < 46.0F) {
         float tumble = 20.0F + (t - 32.0F) * 26.0F;
         float pitch = Mth.rotLerp(ease(t, 44.0F, 46.0F), tumble, -90.0F);
         v.turn(0.0F, pitch, 0.0F, 0.0F).anchor(1.0556251F, at(fwd, 0.0F, air(t)));
      } else if (t < 58.0F) {
         float bounce = 0.55F * Mth.sin((float) Math.PI * phase(t, 46.0F, 52.0F));
         float k = ease(t, 52.0F, 58.0F);
         v.turn(Mth.rotLerp(k, 0.0F, 0.0F), -90.0F * (1.0F - k) + 6.0F * Mth.sin((float) Math.PI * phase(t, 46.0F, 52.0F)), 0.0F, 0.0F)
            .anchor(1.0556251F, at(fwd, 0.0F, Mth.lerp(k, 0.3F + bounce, 1.0556251F)));
      } else if (t < 80.0F) {
         float shake = Keys.window(t, 58.0F, 73.0F, 1.5F);
         float pitch = 6.0F * Mth.sin(t * 3.1F) * shake + 8.0F * Math.max(0.0F, jolt(t, FreestyleCombo.Blow.CROSS));
         float yaw = 8.0F * Mth.sin(t * 2.3F) * shake + 12.0F * Mth.sin(t * 0.3F) * (1.0F - shake);
         float roll = 8.0F * Mth.sin(t * 0.25F) * (1.0F - shake);
         v.turn(yaw, pitch, roll, 0.0F).feet(at(fwd, 0.0F, 0.0F));
      } else if (t < 96.0F) {
         float lift = Spectacle.out(phase(t, 80.0F, 84.0F), 2.0F) * 0.5F - 0.1F * ease(t, 84.0F, 96.0F);
         float pitch = 25.0F * ease(t, 80.0F, 84.0F);
         v.turn(4.0F * (t - 80.0F), pitch, 0.0F, 0.0F).anchor(1.0556251F, at(fwd, 0.0F, 1.0556251F + lift));
      } else {
         float u = phase(t, 96.0F, 104.0F);
         float y = Mth.lerp(u, 1.455625F, 0.3F) + 0.45F * Mth.sin((float) Math.PI * Math.min(1.0F, u * 1.2F));
         float bounce = 0.1F * Mth.sin((float) Math.PI * phase(t, 104.0F, 108.0F));
         float spin = 64.0F + 300.0F * Spectacle.out(u, 2.0F);
         float pitch = Mth.rotLerp(smooth(u), 25.0F, -90.0F);
         v.turn(spin, pitch, 0.0F, 0.0F).anchor(1.0556251F, at(fwd, 0.0F, y + bounce));
      }

      int k = blow(t);
      float twist = 0.0F;
      if (k >= 0 && t < 89.0F) {
         float e = extend(k, t);

         twist = switch (BLOWS[k]) {
            case CROSS, UPPER, BODY, FLURRY -> -14.0F * e;
            case HOOK -> 22.0F * e;
            default -> 6.0F * e;
         };
      }

      float gap = Keys.key(
         t, 0.0F, 0.9F, 14.0F, 0.9F, 32.0F, 0.85F, 46.0F, 0.8F, 58.0F, 0.8F, 58.0F, 0.75F, 72.0F, 0.75F, 76.0F, 1.45F, 78.0F, 1.45F, 79.0F, 0.8F, 89.0F, 0.8F
      );
      float y = 0.03F * Math.abs(Mth.sin(t * (float) Math.PI / 8.0F)) * Keys.window(t, 14.0F, 32.0F, 3.0F);
      y += Keys.key(t, 35.0F, 0.0F, 38.0F, 1.0F, 43.0F, 1.15F, 44.0F, 1.3F, 46.0F, 0.0F);
      float yaw = 180.0F + twist;
      if (t >= 40.0F && t < 44.0F) {
         yaw += 360.0F * smooth(phase(t, 41.0F, 43.0F));
      }

      float pos = fwd + gap;
      if (t >= 89.0F) {
         float jump = phase(t, 89.0F, 99.0F);
         y = 0.55F * Mth.sin((float) Math.PI * jump);
         pos = bodyFwd(95.9F) + 0.95F;
         yaw = 180.0F - 450.0F * in(phase(t, 89.0F, 96.0F), 1.3F);
         yaw = Mth.rotLerp(ease(t, 100.0F, 108.0F), yaw, 180.0F);
         s.actorRoll = -32.0F * Keys.window(t, 93.0F, 99.0F, 2.0F);
         pos = Mth.lerp(ease(t, 102.0F, 112.0F), pos, this.endFwd());
      }

      s.actorPoint.set(at(pos, 0.0F, y));
      s.actorYaw = yaw;
      float crouch = Keys.window(t, 33.0F, 36.0F, 1.0F) + Keys.window(t, 46.0F, 50.0F, 1.0F);
      s.lean = 0.3F * Keys.window(t, 0.0F, 16.0F, 3.0F)
         + 0.12F
         + 0.25F * crouch
         + (k >= 0 && BLOWS[k] == FreestyleCombo.Blow.BODY ? 0.3F * extend(k, t) : 0.0F)
         - 0.25F * Keys.window(t, 73.0F, 78.0F, 1.5F);
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float haul = Keys.window(t, 0.0F, 14.0F, 3.0F);
      Vector3f collar = s.actor.model(s.victim.scene(0.0F, 1.0F, -2.0F));
      hold(m.rightArm, collar, haul);
      hold(m.leftArm, collar, haul);
      float guard = Keys.window(t, 12.0F, 90.0F, 2.0F);
      pose(m.rightArm, guard, -1.75F, -0.3F, -0.35F);
      pose(m.leftArm, guard, -1.85F, 0.3F, 0.35F);
      m.rightLeg.xRot = -0.3F * guard;
      m.leftLeg.xRot = 0.3F * guard;
      int k = blow(t);
      if (k >= 0 && t < 89.0F) {
         float e = extend(k, t);
         boolean left = BLOWS[k] == FreestyleCombo.Blow.FLURRY && k % 2 == 0;
         switch (BLOWS[k]) {
            case CROSS:
               pose(m.rightArm, e, -1.6F, 0.0F, -0.05F);
               break;
            case UPPER:
               pose(m.rightArm, e, -2.6F, 0.0F, -0.2F);
               break;
            case BODY:
               pose(k % 2 == 0 ? m.leftArm : m.rightArm, e, -1.2F, 0.0F, 0.0F);
               break;
            case FLURRY:
               pose(left ? m.leftArm : m.rightArm, e, -1.62F, 0.0F, left ? 0.08F : -0.08F);
               break;
            case HOOK:
               pose(m.leftArm, e, -1.5F, -0.9F, -0.6F);
               break;
            case SLAP:
               pose(m.rightArm, e, -1.5F, -0.9F, -0.8F);
               break;
            case JAB:
               pose(m.leftArm, e, -1.6F, 0.0F, 0.05F);
               break;
            case AIR_KICK:
               pose(m.rightLeg, e, -1.6F, 0.0F, 0.0F);
               pose(m.leftLeg, e, 0.4F, 0.0F, 0.0F);
               break;
            case AIR_PUNCH:
               pose(m.rightArm, e, -1.3F, 0.0F, 0.0F);
               break;
            case SPIN_KICK:
               pose(m.rightLeg, e, -0.2F, 0.0F, 1.5F);
               pose(m.rightArm, e, -0.4F, 0.0F, 1.1F);
               pose(m.leftArm, e, -0.5F, 0.0F, -1.2F);
               break;
            case AXE:
               float down = phase(t, TICKS[k] - 0.5F, TICKS[k] + 1.0F);
               pose(m.rightLeg, Math.max(e, down), Mth.lerp(down, -2.6F, -0.6F), 0.0F, 0.0F);
               pose(m.rightArm, e, 0.4F, 0.0F, 0.5F);
               pose(m.leftArm, e, 0.4F, 0.0F, -0.5F);
         }
      }

      float airborne = Keys.window(t, 36.0F, 46.0F, 1.5F);
      if (airborne > 0.0F
         && (k < 0 || BLOWS[k] != FreestyleCombo.Blow.AIR_KICK && BLOWS[k] != FreestyleCombo.Blow.AXE && BLOWS[k] != FreestyleCombo.Blow.SPIN_KICK)) {
         pose(m.rightLeg, airborne, -0.9F, 0.0F, 0.1F);
         pose(m.leftLeg, airborne, -0.4F, 0.0F, -0.1F);
      }

      float taunt = Keys.window(t, 73.0F, 78.0F, 1.5F);
      pose(m.rightArm, taunt, -1.3F, 0.0F, 0.2F + 0.25F * Mth.sin(age * 1.6F));
      pose(m.leftArm, taunt, -0.2F, 0.0F, -0.2F);
      if (t >= 89.0F) {
         float tuck = Keys.window(t, 89.0F, 94.0F, 2.0F);
         float kick = Keys.window(t, 93.0F, 100.0F, 2.5F);
         pose(m.rightLeg, tuck, -1.1F, 0.0F, 0.2F);
         pose(m.leftLeg, tuck * 0.6F, -0.5F, 0.0F, 0.0F);
         pose(m.rightLeg, kick, -0.1F, 0.0F, 1.5F);
         pose(m.leftLeg, kick, 0.2F, 0.0F, -0.1F);
         float arms = Keys.window(t, 89.0F, 102.0F, 2.0F);
         pose(m.rightArm, arms, -0.4F, 0.0F, 1.2F);
         pose(m.leftArm, arms, -0.6F, 0.0F, -1.3F);
         float land = Keys.window(t, 99.0F, 108.0F, 3.0F);
         pose(m.rightLeg, land, -0.4F, 0.0F, 0.15F);
         pose(m.leftLeg, land, 0.3F, 0.0F, -0.15F);
         float done = ease(t, 106.0F, 118.0F);
         pose(m.rightArm, done, 0.0F, 0.0F, 0.12F);
         pose(m.leftArm, done, 0.0F, 0.0F, -0.12F);
      }

      if (t < 89.0F) {
         look(m.head, s.actor.model(s.victim.axis(1.5F)), 0.9F, 1.0F);
      }
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < 8.0F) {
         lyingPose(m);
      }

      if (t >= 2.0F && t < 32.0F) {
         limp(m, age, ease(t, 4.0F, 14.0F));
         m.head.xRot = m.head.xRot - 0.5F * jolt(t, FreestyleCombo.Blow.JAB, FreestyleCombo.Blow.CROSS);
         m.head.yRot = m.head.yRot + (0.8F * jolt(t, FreestyleCombo.Blow.HOOK) - 0.6F * jolt(t, FreestyleCombo.Blow.SLAP));
         float body = Math.abs(jolt(t, FreestyleCombo.Blow.BODY));
         m.rightArm.xRot -= 0.6F * body;
         m.leftArm.xRot -= 0.6F * body;
         m.head.xRot += 0.4F * body;
      } else if (t >= 32.0F && t < 58.0F) {
         flail(m, age * 0.9F, 1.0F - ease(t, 50.0F, 58.0F));
         if (t >= 46.0F) {
            lyingPose(m);
            float up = ease(t, 52.0F, 58.0F);
            limp(m, age, up);
         }
      } else if (t >= 58.0F && t < 96.0F) {
         limp(m, age, 1.0F);
         float shake = Keys.window(t, 58.0F, 73.0F, 1.5F);
         m.head.xRot = m.head.xRot - 0.4F * shake * Math.abs(Mth.sin(t * 3.1F));
         m.head.yRot = m.head.yRot + 0.5F * shake * Mth.sin(t * 2.3F);
         m.rightArm.zRot = m.rightArm.zRot + 0.4F * shake * Mth.sin(t * 2.9F);
         m.leftArm.zRot = m.leftArm.zRot - 0.4F * shake * Mth.sin(t * 3.3F);
         float up = ease(t, 80.0F, 83.0F);
         pose(m.rightArm, up, -2.2F, 0.0F, 0.6F);
         pose(m.leftArm, up, -2.0F, 0.0F, -0.6F);
         pose(m.head, up, -0.9F, 0.0F, 0.0F);
      } else {
         flail(m, age, 1.0F - ease(t, 100.0F, 104.0F));
         float k = ease(t, 101.0F, 105.0F);
         pose(m.rightArm, k, -2.9F, 0.0F, -0.4F);
         pose(m.leftArm, k, -2.6F, 0.0F, 0.6F);
         pose(m.head, k, -0.3F, 0.9F, 0.0F);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      float mid = (s.actor.pos.z + s.victim.axis(1.0556251F).z) / 2.0F;
      float high = Math.max(s.victim.axis(1.0556251F).y, 1.0F);
      Vector3f between = at(mid, 0.0F, high);
      if (t < 16.0F) {
         return new CoupDeGrace.Shot(at(0.5F, -2.9F, 1.1F), at(0.45F, 0.0F, 1.0F), 0, true, 3.0F, 58.0F);
      } else if (t >= 20.0F && t < 23.0F) {
         return new CoupDeGrace.Shot(at(mid + 0.2F, -2.3F, 1.5F), between, 1, true, 8.0F, 52.0F);
      } else if (t >= 32.0F && t < 37.0F) {
         return new CoupDeGrace.Shot(at(mid + 0.4F, -1.9F, 0.3F), at(mid, 0.0F, high + 0.4F), 2, true, 6.0F, 66.0F);
      } else if (t >= 44.0F && t < 49.0F) {
         return new CoupDeGrace.Shot(at(mid + 0.5F, -2.0F, 3.2F), at(mid, 0.0F, 0.3F), 3, true, 6.0F, 60.0F);
      } else if (t >= 58.0F && t < 72.0F) {
         float a = -90.0F + (t - 58.0F) * 7.0F;
         return new CoupDeGrace.Shot(new Vector3f(Finisher.orbit(a, 2.5F, 1.4F)).add(at(mid, 0.0F, 0.0F)), between, 4, false, 9.0F, 56.0F);
      } else if (t >= 86.0F && t < 102.0F) {
         return new CoupDeGrace.Shot(at(mid + 0.6F, -3.1F, 0.5F), at(mid, 0.0F, 1.4F), 5, true, 3.0F, 60.0F);
      } else if (t >= 102.0F) {
         float k = ease(t, 102.0F, 128.0F);
         return new CoupDeGrace.Shot(at(mid + 0.6F, -3.1F, 0.5F).lerp(at(0.8F, -2.8F, 1.9F), k), at(-0.6F, 0.0F, 0.5F), 6, false, 2.5F, 60.0F);
      } else {
         return new CoupDeGrace.Shot(at(mid + 0.2F, -3.6F, 1.25F + 0.4F * (high - 1.0F)), between, 7, false, 5.0F, 56.0F);
      }
   }

   static Vector3f landing(Spectacle.Frame s, FreestyleCombo.Blow b) {
      return switch (b) {
         case BODY, AIR_PUNCH -> s.victim.scene(0.0F, 4.0F, -3.0F);
         default -> s.victim.scene(0.0F, -4.0F, -4.2F);
         case AIR_KICK, AXE -> s.victim.scene(0.0F, 2.0F, -2.5F);
      };
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;

      for (int i = 0; i < TICKS.length; i++) {
         float live = t - TICKS[i];
         if (!(live < 0.0F) && !(live > 5.0F)) {
            float a = 1.0F - live / 5.0F;
            Spectacle.Frame at = this.other(s, TICKS[i]);
            Vector3f p = landing(at, BLOWS[i]);
            boolean big = big(i);
            d.sprite(Sprite.STAR, p, (big ? 1.0F : 0.55F) + 0.3F * live, i * 0.7F, r(16767072), g(16767072), b(16767072), a, Draw.Blend.GLOW);
            d.sprite(Sprite.RING, p, 0.3F + 0.4F * live, 0.0F, r(16777215), g(16777215), b(16777215), a, Draw.Blend.GLOW);
            if (big) {
               d.glow(p, 3.0F * a * a, 1.0F, 0.97F, 0.85F, a);

               for (int n = 0; n < 8; n++) {
                  float ang = n * 0.785F + i;
                  Vector3f dir = new Vector3f(Mth.cos(ang), Mth.sin(ang) * 0.8F, Mth.sin(ang * 1.7F) * 0.6F).normalize();
                  d.streak(
                     new Vector3f(p).add(new Vector3f(dir).mul(0.25F + 0.3F * live)),
                     new Vector3f(p).add(new Vector3f(dir).mul(0.6F + 0.6F * live)),
                     0.06F,
                     1.0F,
                     1.0F,
                     1.0F,
                     a
                  );
               }
            }
         }
      }

      float flurry = Keys.window(t, 57.0F, 73.0F, 1.5F);
      if (flurry > 0.0F) {
         this.afterimages(d, s, ex, age, 2, 0.5F, 1.0F, 0.85F, 0.5F, 0.08F * flurry, true);
         Vector3f chest = s.victim.scene(0.0F, 3.0F, -3.0F);

         for (int n = 0; n < 4; n++) {
            float seed = (int)(age * 2.0F) * 7 + n * 13;
            Vector3f p = new Vector3f(chest).add(Mth.sin(seed) * 0.25F, Mth.cos(seed * 1.3F) * 0.35F, 0.0F);
            d.sprite(Sprite.STAR, p, 0.45F, seed, r(16767072), g(16767072), b(16767072), 0.8F * flurry, Draw.Blend.GLOW);
         }

         for (int n = 0; n < 6; n++) {
            float yy = 0.5F + 0.25F * n;
            Vector3f p = s.actor.axis(yy);
            d.streak(
               new Vector3f(p).add(at(0.9F, 0.15F * (n - 2.5F), 0.0F)),
               new Vector3f(p).add(at(0.25F, 0.15F * (n - 2.5F), 0.0F)),
               0.05F,
               1.0F,
               1.0F,
               1.0F,
               0.4F * flurry
            );
         }
      }

      float trail = Keys.window(t, 36.0F, 46.0F, 1.5F) + Keys.window(t, 89.0F, 98.0F, 1.5F);
      if (trail > 0.0F) {
         Spectacle.Frame before = this.other(s, t - 1.2F);
         d.streak(this.actorFoot(before, true), this.actorFoot(s, true), 0.35F, r(16767072), g(16767072), b(16767072), 0.7F * trail);
         this.afterimages(d, s, ex, age, 3, 0.8F, 0.6F, 0.85F, 1.0F, 0.12F * trail, true);
      }

      if (t >= 46.0F) {
         float k = phase(t, 46.0F, 56.0F);
         if (k < 1.0F) {
            d.decal(
               FxTex.SHOCK,
               at(bodyFwd(46.0F), 0.0F, 0.0F),
               1.0F + 5.0F * Spectacle.out(k, 2.0F),
               0.0F,
               r(16767072),
               g(16767072),
               b(16767072),
               0.8F * (1.0F - k),
               Draw.Blend.GLOW
            );
         }

         d.decal(FxTex.SCORCH, at(bodyFwd(46.0F), 0.0F, 0.0F), 2.2F, 0.6F, 1.0F, 1.0F, 1.0F, 0.6F * ease(t, 46.0F, 48.0F), Draw.Blend.SOFT);
      }

      if (t >= 96.0F && t < 108.0F) {
         float k = (t - 96.0F) / 12.0F;
         Vector3f at = landing(this.other(s, 95.9F), FreestyleCombo.Blow.HEEL);
         d.glow(at, 4.5F * (1.0F - k), 1.0F, 0.95F, 0.8F, 1.0F - k);
         d.sprite(Sprite.STAR, at, 1.5F + 2.5F * k, 0.4F, r(16767072), g(16767072), b(16767072), 1.0F - k, Draw.Blend.GLOW);
         d.sprite(Sprite.RING, at, 0.6F + 4.0F * Spectacle.out(k, 2.0F), 0.0F, 1.0F, 1.0F, 1.0F, 1.0F - k, Draw.Blend.GLOW);
         d.decal(
            FxTex.SHOCK,
            at(-0.85F, 0.0F, 0.0F),
            1.0F + 6.0F * Spectacle.out(k, 2.0F),
            0.0F,
            r(16767072),
            g(16767072),
            b(16767072),
            0.7F * (1.0F - k),
            Draw.Blend.GLOW
         );
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      if (t == 2) {
         fx.sound(Sfx.WHOOSH, s.victim.axis(1.0F), 0.7F, 0.8F);
      }

      for (int i = 0; i < TICKS.length; i++) {
         if (t == (int)TICKS[i] - 1) {
            fx.sound(Sfx.WHOOSH, s.actor.axis(1.4F), 0.5F, 1.3F + 0.02F * i);
         }

         if (t == (int)TICKS[i]) {
            Vector3f p = landing(s, BLOWS[i]);
            boolean big = big(i);
            fx.sound(Sfx.IMPACT, p, 1.0F, big ? 0.8F : 1.0F + 0.03F * (i % 5));
            fx.sound(Sfx.FLESH, p, 0.5F, 1.1F);
            if (big) {
               fx.sound(Sfx.CRIT, p, 0.6F, 1.1F);
               fx.shake(0.35F, 0.2F);
            }

            fx.sparks(p, big ? 18 : 8, big ? 0.4F : 0.25F, 16767072);
            fx.burst(Sprite.DROP, p, big ? 6 : 2, 0.15F, 0.08F, 20, 9043984, true);
         }
      }

      if (t == 36) {
         fx.sound(Sfx.WHOOSH_HEAVY, s.actor.axis(0.5F), 0.8F, 1.1F);
         fx.ring(Sprite.DUST, s.actor.axis(0.05F), 10, 0.06F, 0.3F, 16, 9075302, true);
      }

      if (t == 46) {
         Vector3f ground = at(bodyFwd(46.0F), 0.0F, 0.2F);
         fx.sound(Sfx.SLAM, ground, 1.0F, 1.0F);
         fx.sound(Sfx.BONE_CRACK, ground, 0.7F, 1.0F);
         fx.ring(Sprite.DUST, at(bodyFwd(46.0F), 0.0F, 0.05F), 20, 0.15F, 0.5F, 24, 9075302, true);
         fx.shake(0.6F, 0.4F);
      }

      if (t == 52) {
         fx.sound(Sfx.BODY_FALL, at(bodyFwd(52.0F), 0.0F, 0.2F), 0.6F, 1.1F);
      }

      if (t == 74) {
         fx.sound(Sfx.SCRATCH, s.actor.axis(1.5F), 0.6F, 1.3F);
      }

      if (t == 87) {
         fx.sound(Sfx.SCRATCH, s.actor.axis(1.5F), 1.0F, 0.8F);
      }

      if (t == 89) {
         fx.sound(Sfx.WHOOSH_HEAVY, s.actor.axis(1.0F), 0.9F, 0.8F);
         fx.ring(Sprite.DUST, s.actor.axis(0.05F), 12, 0.07F, 0.35F, 18, 9075302, true);
      }

      if (t == 92.0F) {
         fx.sound(Sfx.WHOOSH, s.actor.axis(1.0F), 1.0F, 0.7F);
      }

      if (t == 96.0F) {
         Vector3f p = landing(s, FreestyleCombo.Blow.HEEL);
         fx.sound(Sfx.IMPACT_HEAVY, p, 1.0F, 1.0F);
         fx.sound(Sfx.BONE_CRACK, p, 1.0F, 0.9F);
         fx.sound(Sfx.CRIT, p, 1.0F, 0.8F);
         fx.flash(p, 0.6F);
         fx.shake(1.0F, 0.9F);
         fx.sparks(p, 50, 0.7F, 16767072);
         fx.burst(Sprite.STAR, p, 14, 0.3F, 0.2F, 20, 16777215, false);
         fx.burst(Sprite.DROP, p, 16, 0.3F, 0.12F, 30, 9043984, true);
      }

      if (t == 104.0F) {
         fx.sound(Sfx.BODY_FALL, at(-1.6F, 0.0F, 0.2F));
         fx.ring(Sprite.DUST, at(-1.6F, 0.0F, 0.05F), 14, 0.08F, 0.4F, 22, 9075302, true);
      }

      if (t == 110.0F) {
         fx.sound(Sfx.SCRATCH, s.actor.axis(1.5F), 0.6F, 1.1F);
      }
   }

   enum Blow {
      SLAP,
      JAB,
      CROSS,
      BODY,
      HOOK,
      UPPER,
      AIR_KICK,
      AIR_PUNCH,
      SPIN_KICK,
      AXE,
      FLURRY,
      HEEL;
   }
}
