package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

final class CourrouxCiel extends Finisher {
   static final CourrouxCiel INSTANCE = new CourrouxCiel();
   static final float CALL = 2.0F;
   static final float LIFT = 24.0F;
   static final float SUMMON = 64.0F;
   static final float DOWN = 92.0F;
   static final float SKY = 9.0F;
   static final float[][] BOLTS = new float[][]{
      {16.0F, 30.0F, 1.6F},
      {24.0F, 200.0F, 1.9F},
      {32.0F, 110.0F, 1.4F},
      {40.0F, 300.0F, 2.1F},
      {48.0F, 160.0F, 1.7F},
      {56.0F, 250.0F, 1.5F},
      {64.0F, 60.0F, 2.2F},
      {68.0F, 330.0F, 1.8F},
      {72.0F, 140.0F, 2.0F},
      {76.0F, 230.0F, 1.6F},
      {80.0F, 20.0F, 1.9F},
      {84.0F, 280.0F, 1.5F},
      {86.0F, 180.0F, 2.3F},
      {88.0F, 90.0F, 1.7F},
      {90.0F, 310.0F, 2.0F}
   };
   static final int BLUE = 8370431;
   static final int WHITE = 15398143;

   private CourrouxCiel() {
      super(ExecutionType.COURROUX_CIEL);
   }

   @Override
   float endFwd() {
      return 1.4F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 1.4F;
   }

   static Vector3f mid(float t) {
      float up = Mth.lerp(ease(t, 24.0F, 50.0F), 0.3F, 1.9F) + 0.08F * Mth.sin(t * 0.3F) * ease(t, 40.0F, 50.0F);
      return at(0.0F, 0.0F, up);
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float kick = Keys.window(t, 95.0F, 106.0F, 2.0F);
      s.actorPoint.set(at(1.4F + 0.1F * kick, 0.0F, 0.0F));
      s.actorYaw = 180.0F;
      s.lean = 0.25F * ease(t, 92.0F, 96.0F) * (1.0F - ease(t, 102.0F, 112.0F)) - 0.12F * Keys.window(t, 2.0F, 24.0F, 6.0F);
      CoupDeGrace.Body v = s.victim;
      if (t < 24.0F) {
         prone(v, t, victimYaw, s.side);
      } else if (t < 96.0F) {
         float k = ease(t, 24.0F, 44.0F);
         Vector3f mid = mid(t).add(0.02F * Mth.sin(t * 5.3F) * k, 0.0F, 0.02F * Mth.sin(t * 4.1F) * k);
         rise(v, k, s.side, 0.0F, mid);
      } else {
         float u = phase(t, 96.0F, 99.0F);
         float bounce = 0.1F * Mth.sin((float) Math.PI * phase(t, 99.0F, 103.0F));
         Vector3f mid = mid(96.0F).lerp(at(-0.3F, 0.0F, 0.15F + bounce), u * u);
         v.turn(0.0F, 90.0F * smooth(u), 0.0F, 0.0F).anchor(1.0556251F, mid);
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float stance = ease(t, 0.0F, 8.0F);
      m.rightLeg.zRot = 0.12F * stance;
      m.leftLeg.zRot = -0.12F * stance;
      m.rightLeg.xRot = -0.25F * stance;
      m.leftLeg.xRot = 0.2F * stance;
      float up = Keys.window(t, 2.0F, 26.0F, 6.0F);
      armsUp(m, up);
      Vector3f chest = s.actor.model(s.victim.axis(1.2F));
      float push = ease(t, 22.0F, 28.0F);
      float call = ease(t, 60.0F, 68.0F) * (1.0F - ease(t, 92.0F, 95.0F));
      if (push > 0.0F && t < 98.0F) {
         hold(m.rightArm, chest, push * (1.0F - call));
         hold(m.leftArm, chest, push);
         m.rightArm.xRot = m.rightArm.xRot + 0.05F * Mth.sin(age * 4.0F) * push;
         m.leftArm.xRot = m.leftArm.xRot + 0.05F * Mth.sin(age * 4.7F) * push;
      }

      pose(m.rightArm, call, -3.0F + 0.05F * Mth.sin(age * 3.0F), 0.0F, 0.15F);
      float down = ease(t, 92.0F, 96.0F) * (1.0F - ease(t, 102.0F, 112.0F));
      pose(m.rightArm, down, -0.9F, 0.0F, 0.1F);
      float rest = ease(t, 102.0F, 112.0F);
      pose(m.rightArm, rest, 0.0F, 0.0F, 0.08F);
      pose(m.leftArm, rest, 0.0F, 0.0F, -0.08F);
      look(m.head, s.actor.model(!(t < 64.0F) && !(t > 92.0F) ? at(0.0F, 0.0F, 9.0F) : s.victim.axis(1.4F)), 0.9F, 1.1F);
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < 28.0F) {
         lyingPose(m);

         for (float[] b : BOLTS) {
            float j = Keys.jolt(t, b[0], 6.0F, 2.5F);
            m.rightArm.xRot += 0.25F * j;
            m.leftArm.xRot -= 0.25F * j;
         }
      }

      if (t >= 24.0F && t < 96.0F) {
         convulse(m, age, ease(t, 24.0F, 32.0F) * (0.6F + 0.4F * ease(t, 64.0F, 90.0F)));
      } else if (t >= 96.0F) {
         deadPose(m, 0.5F * s.side);
         float twitch = Keys.jolt(t, 104.0F, 10.0F, 3.0F);
         m.rightArm.zRot += 0.2F * twitch;
         m.leftLeg.xRot += 0.3F * twitch;
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      Vector3f body = s.victim.axis(1.0556251F);
      if (t < 16.0F) {
         Vector3f look = new Vector3f(s.actor.axis(1.5F)).add(0.0F, 0.6F * ease(t, 2.0F, 14.0F), 0.0F);
         return new CoupDeGrace.Shot(at(-0.4F, -3.4F, 0.5F), look, 0, true, 3.0F, 62.0F);
      }

      if (t < 60.0F) {
         float k = ease(t, 16.0F, 60.0F);
         return new CoupDeGrace.Shot(at(0.9F - 0.3F * k, -4.8F + 0.8F * k, 1.1F + 0.3F * k), at(0.4F, 0.0F, 1.3F), 1, true, 3.0F, 62.0F);
      }

      if (t < 90.0F) {
         float k = ease(t, 60.0F, 90.0F);
         Vector3f pos = at(-1.6F - 0.6F * k, 2.6F - 0.4F * k, 1.3F + 0.4F * k);
         return new CoupDeGrace.Shot(pos, new Vector3f(s.actor.axis(1.4F)).lerp(body, 0.35F).add(0.0F, 0.4F * k, 0.0F), 2, true, 3.0F, 52.0F);
      }

      if (t < 102.0F) {
         return new CoupDeGrace.Shot(at(1.2F, -6.2F, 0.6F), at(0.2F, 0.0F, 2.8F), 3, true, 3.0F, 68.0F);
      }

      float k = ease(t, 102.0F, 128.0F);
      Vector3f pos = at(1.2F, -6.2F, 0.6F).lerp(at(1.8F, -3.0F, 2.6F), k);
      return new CoupDeGrace.Shot(pos, new Vector3f(body).lerp(at(0.6F, 0.0F, 0.4F), 0.3F), 3, false, 2.5F, 60.0F);
   }

   static Vector3f boltSpot(float[] b) {
      return orbit(b[1], b[2], 0.0F);
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      long frame = (long)(age * 0.5F);
      Vector3f chest = s.victim.axis(1.2F);
      float storm = ease(t, 2.0F, 20.0F) * (1.0F - ease(t, 100.0F, 116.0F));
      if (storm > 0.0F) {
         Vector3f heart = at(0.0F, 0.0F, 9.0F);
         float call = ease(t, 64.0F, 96.0F);
         d.sprite(Sprite.SMOKE_DARK, heart, 9.0F * storm, age * 0.01F, 0.12F, 0.13F, 0.18F, 0.85F * storm, Draw.Blend.SOFT);
         d.glow(heart, (2.0F + 6.0F * call) * storm, r(8370431), g(8370431), b(8370431), (0.15F + 0.5F * call) * (0.7F + 0.3F * Mth.sin(age * 1.7F)));
         if (call > 0.0F) {
            for (int i = 0; i < 3; i++) {
               float a = (age * 23.0F + i * 120.0F) * (float) (Math.PI / 180.0);
               Vector3f end = new Vector3f(heart).add(2.2F * call * Mth.cos(a), -0.6F, 2.2F * call * Mth.sin(a));
               d.bolt(heart, end, frame * 7L + i, 0.4F, 0.04F, r(8370431), g(8370431), b(8370431), 0.8F * call);
            }
         }
      }

      if (t > 6.0F && t < 100.0F) {
         for (boolean right : new boolean[]{true, false}) {
            Vector3f hand = this.actorHand(s, right);
            Vector3f spark = new Vector3f(hand).add((float)Vfx.random(0.35).x, (float)Vfx.random(0.35).y, (float)Vfx.random(0.35).z);
            d.bolt(hand, spark, frame * 13L + (right ? 1 : 2), 0.12F, 0.02F, r(8370431), g(8370431), b(8370431), 0.9F);
            d.glow(hand, 0.35F + 0.1F * Mth.sin(age * 3.0F), r(8370431), g(8370431), b(8370431), 0.7F);
         }
      }

      float hold = ease(t, 22.0F, 26.0F) * (t < 96.0F ? 1.0F : 0.0F);
      if (hold > 0.0F) {
         float call = ease(t, 60.0F, 68.0F) * (1.0F - ease(t, 92.0F, 95.0F));

         for (boolean right : new boolean[]{true, false}) {
            float a = right ? hold * (1.0F - call) : hold;
            if (a > 0.05F) {
               d.bolt(this.actorHand(s, right), chest, frame * 31L + (right ? 3 : 4), 0.3F, 0.035F, r(8370431), g(8370431), b(8370431), a);
            }
         }

         d.glow(chest, 1.1F + 0.3F * Mth.sin(age * 2.0F), r(8370431), g(8370431), b(8370431), 0.6F * hold);

         for (int i = 0; i < 2; i++) {
            Vector3f p = s.victim.axis(0.3F + 1.4F * ((age * 0.37F + i * 0.5F) % 1.0F));
            d.bolt(
               p,
               new Vector3f(p).add((float)Vfx.random(0.5).x, (float)Vfx.random(0.3).y, (float)Vfx.random(0.5).z),
               frame * 17L + i,
               0.15F,
               0.02F,
               r(8370431),
               g(8370431),
               b(8370431),
               0.8F * hold
            );
         }
      }

      for (float[] b : BOLTS) {
         if (!(t < b[0])) {
            Vector3f ground = boltSpot(b);
            float scorch = 1.0F - ease(t, b[0] + 30.0F, b[0] + 70.0F);
            d.decal(FxTex.SCORCH, ground, 1.1F, b[1], 1.0F, 1.0F, 1.0F, 0.85F * scorch, Draw.Blend.SOFT);
            float live = t - b[0];
            if (live < 4.0F) {
               float a = (1.0F - live / 4.0F) * ((int)(live * 2.0F) % 2 == 0 ? 1.0F : 0.55F);
               d.bolt(new Vector3f(ground).add(0.8F, 9.0F, -0.5F), ground, (long)(b[0] * 101.0F + b[1]), 0.9F, 0.07F, r(8370431), g(8370431), b(8370431), a);
               d.glow(new Vector3f(ground).add(0.0F, 0.2F, 0.0F), 2.2F * a, r(15398143), g(15398143), b(15398143), a);
               d.decal(FxTex.SHOCK, ground, 0.6F + 2.5F * live / 4.0F, 0.0F, r(8370431), g(8370431), b(8370431), a, Draw.Blend.GLOW);
            }
         }
      }

      if (t >= 95.5F && t < 104.0F) {
         float live = t - 96.0F;
         float a = (1.0F - Math.max(0.0F, live) / 8.0F) * ((int)(Math.max(0.0F, live) * 2.0F) % 2 == 0 ? 1.0F : 0.6F);
         Vector3f top = at(0.4F, -0.6F, 11.0F);
         Vector3f ground = at(-0.3F, 0.0F, 0.0F);
         d.bolt(top, ground, 9601L + (long)live, 1.1F, 0.22F, r(8370431), g(8370431), b(8370431), a);
         d.bolt(top, at(1.6F, 1.4F, 0.0F), 9602L, 1.4F, 0.08F, r(8370431), g(8370431), b(8370431), 0.6F * a);
         d.bolt(top, at(-1.5F, -1.8F, 0.0F), 9603L, 1.4F, 0.08F, r(8370431), g(8370431), b(8370431), 0.6F * a);
         d.glow(at(-0.3F, 0.0F, 0.6F), 7.0F * a, r(15398143), g(15398143), b(15398143), a);
         float k = Math.max(0.0F, live) / 8.0F;
         d.decal(FxTex.SHOCK, ground, 1.0F + 10.0F * out(k, 2.0F), 0.0F, r(8370431), g(8370431), b(8370431), 0.9F * (1.0F - k), Draw.Blend.GLOW);
      }

      float burnt = ease(t, 96.0F, 98.0F);
      if (burnt > 0.0F) {
         d.decal(FxTex.SCORCH, at(-0.3F, 0.0F, 0.0F), 3.0F, 0.4F, 1.0F, 1.0F, 1.0F, 0.95F * burnt, Draw.Blend.SOFT);
         float embers = 1.0F - ease(t, 106.0F, 138.0F);
         d.decal(FxTex.FROST, at(-0.3F, 0.0F, 0.0F), 2.6F, 1.1F, r(8370431), g(8370431), b(8370431), 0.5F * embers * burnt, Draw.Blend.GLOW);
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      Vector3f chest = s.victim.axis(1.2F);
      if (t >= 2.0F && t < 100.0F) {
         fx.darken(0.25F + 0.35F * ease(t, 2.0F, 20.0F) + 0.15F * ease(t, 64.0F, 96.0F));
      }

      if (t == 4) {
         fx.sound(Sfx.THUNDER, at(0.0F, 0.0F, 9.0F), 0.8F, 0.7F);
      }

      if (t >= 2.0F && t < 96.0F && t % 2 == 0) {
         Vector3f p = orbit(Vfx.RANDOM.nextFloat() * 360.0F, 1.0F + 4.0F * Vfx.RANDOM.nextFloat(), 8.0F + 2.0F * Vfx.RANDOM.nextFloat());
         fx.p(Sprite.SMOKE_DARK, p).vel(fx.dir(0.01F, 0.0F, 0.02F)).size(2.4F, 3.4F).life(70).color(2237486).soft().fadeIn(0.3F).alpha(0.8F);
      }

      for (float[] b : BOLTS) {
         if (t == (int)b[0]) {
            Vector3f ground = boltSpot(b);
            fx.sound(b[0] < 64.0F ? Sfx.THUNDER : Sfx.ZAP, ground, b[0] < 64.0F ? 0.9F : 1.0F, 0.9F + 0.2F * Vfx.RANDOM.nextFloat());
            fx.sound(Sfx.ZAP, ground, 0.8F, 0.7F);
            fx.sparks(new Vector3f(ground).add(0.0F, 0.1F, 0.0F), 22, 0.4F, 8370431);
            fx.burst(Sprite.DUST, new Vector3f(ground).add(0.0F, 0.1F, 0.0F), 8, 0.12F, 0.3F, 20, 3816004, true);
            fx.flash(ground, 0.25F);
            fx.shake(0.2F, 0.06F);
         }
      }

      if (t == 24) {
         fx.sound(Sfx.CHARGE, chest, 0.8F, 0.8F);
      }

      if (t >= 24.0F && t < 96.0F && t % 6 == 0) {
         fx.sound(Sfx.CRACKLE, chest, 0.7F, 0.9F + 0.2F * Vfx.RANDOM.nextFloat());
         fx.sparks(chest, 6, 0.2F, 8370431);
      }

      if (t == 64) {
         fx.sound(Sfx.CHARGE, at(0.0F, 0.0F, 9.0F), 1.0F, 0.6F);
         fx.sound(Sfx.THUNDER, at(0.0F, 0.0F, 9.0F), 1.0F, 0.6F);
      }

      if (t == 92) {
         fx.sound(Sfx.WHOOSH_HEAVY, s.actor.axis(1.6F), 0.8F, 1.1F);
      }

      if (t == 96.0F) {
         Vector3f ground = at(-0.3F, 0.0F, 0.1F);
         fx.sound(Sfx.THUNDER, ground, 1.0F, 1.0F);
         fx.sound(Sfx.BLAST, ground, 1.0F, 0.9F);
         fx.sound(Sfx.IMPACT_HEAVY, ground, 1.0F, 0.8F);
         fx.flash(ground, 1.0F);
         fx.shake(1.0F, 0.9F);
         fx.sparks(chest, 60, 0.9F, 15398143);
         fx.burst(Sprite.SHARD_B, ground, 24, 0.4F, 0.18F, 30, 5921382, false);
         fx.ring(Sprite.SMOKE, ground, 30, 0.3F, 0.8F, 30, 2763315, true);
         fx.burst(Sprite.BOLT, chest, 10, 0.3F, 0.5F, 8, 8370431, false);
      }

      if (t == 99) {
         fx.sound(Sfx.BODY_FALL, at(-0.3F, 0.0F, 0.2F));
         fx.sound(Sfx.BONE_CRACK, at(-0.3F, 0.0F, 0.2F), 0.7F, 0.9F);
      }

      if (t > 96.0F && t < 118.0F && t % 2 == 0) {
         Vector3f p = s.victim.axis(0.4F + 1.2F * Vfx.RANDOM.nextFloat());
         fx.p(Sprite.SMOKE_DARK, p).vel(fx.dir(0.0F, 0.025F, 0.0F)).size(0.4F, 1.0F).life(40).color(2894896).soft().alpha(0.7F);
         if (t % 6 == 0) {
            fx.sparks(p, 3, 0.1F, 8370431);
         }
      }
   }
}
