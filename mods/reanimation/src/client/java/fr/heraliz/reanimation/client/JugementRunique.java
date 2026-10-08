package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class JugementRunique extends Finisher {
   static final JugementRunique INSTANCE = new JugementRunique();
   static final float[] SIGILS = new float[]{8.0F, 16.0F, 24.0F, 32.0F};
   static final float[] SIGIL_SIZE = new float[]{2.2F, 3.2F, 4.3F, 5.6F};
   static final float BIND = 34.0F;
   static final float SWORD = 62.0F;
   static final float FALL = 90.0F;
   static final float BLADE = 6.4F;
   static final float HIGH = 2.05F;
   static final float[] ANCHORS = new float[]{50.0F, 130.0F, 230.0F, 310.0F};
   static final float ANCHOR_R = 2.3F;
   static final int GOLD = 16766570;
   static final int WHITE = 16775398;
   private static final HumanoidModel<?> PROBE = Script.probe();

   private JugementRunique() {
      super(ExecutionType.JUGEMENT_RUNIQUE);
   }

   @Override
   float endFwd() {
      return 1.8F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 1.6F;
   }

   static float tip(float t) {
      if (t < 90.0F) {
         return Mth.lerp(ease(t, 62.0F, 90.0F), 9.5F, 5.2F);
      }

      float u = phase(t, 90.0F, 96.0F);
      float chest = 2.5F;
      return t < 96.0F ? Mth.lerp(u * u, 5.2F, chest) : Mth.lerp(out(phase(t, 96.0F, 98.5F), 2.0F), chest, -0.9F);
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float step = ease(t, 102.0F, 110.0F);
      s.actorPoint.set(at(1.6F + 0.2F * step, 0.0F, 0.0F));
      s.actorYaw = 180.0F;
      s.lean = -0.15F * Keys.window(t, 62.0F, 92.0F, 6.0F) + 0.2F * Keys.window(t, 90.0F, 104.0F, 2.0F);
      CoupDeGrace.Body v = s.victim;
      if (t < 38.0F) {
         prone(v, t, victimYaw, s.side);
      } else if (t < 96.0F) {
         float k = ease(t, 38.0F, 56.0F);
         float strain = 0.03F * Mth.sin(t * 0.9F) * ease(t, 56.0F, 64.0F);
         rise(v, k, s.side, 0.0F, at(0.0F, strain, 1.0556251F + 0.9943749F * k + 0.05F * Mth.sin(t * 0.13F) * k));
      } else {
         float u = smooth(phase(t, 96.0F, 99.0F));
         Vector3f upright = at(0.0F, 0.0F, 2.05F + 0.05F * Mth.sin(12.48F));
         Vector3f lying = at(0.38F, 0.0F, 0.15F);
         v.turn(0.0F, 90.0F * u, 0.0F, 0.0F).anchor(1.0556251F, upright.lerp(lying, u));
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float call = Keys.window(t, 4.0F, 40.0F, 4.0F);
      pose(m.rightArm, call, -2.4F, 0.0F, 0.2F);
      pose(m.leftArm, call, -1.0F, 0.0F, 0.75F);
      float bind = Keys.window(t, 34.0F, 64.0F, 4.0F);
      pose(m.rightArm, bind, -1.0F, 0.0F, 1.2F);
      pose(m.leftArm, bind, -1.0F, 0.0F, -1.2F);
      float sword = Keys.window(t, 62.0F, 91.0F, 4.0F);
      armsUp(m, sword);
      float down = Keys.window(t, 90.0F, 108.0F, 2.0F);
      pose(m.rightArm, down, -0.8F, 0.0F, 0.15F);
      pose(m.leftArm, down, -0.8F, 0.0F, -0.15F);
      m.head.xRot = 0.0F;
      if (sword > 0.5F) {
         look(m.head, s.actor.model(at(0.0F, 0.0F, tip(t))), 0.6F, 1.2F);
      } else {
         look(m.head, s.actor.model(s.victim.axis(1.3F)), 0.8F, 1.0F);
      }
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < 42.0F) {
         lyingPose(m);
      }

      if (t >= 38.0F && t < 96.0F) {
         spread(m, ease(t, 38.0F, 54.0F));
         float pull = Mth.sin(age * 0.7F) * ease(t, 54.0F, 64.0F);
         m.rightArm.zRot -= 0.12F * pull;
         m.leftArm.zRot -= 0.12F * pull;
         m.head.xRot = Mth.lerp(ease(t, 62.0F, 90.0F), m.head.xRot, -0.8F);
      } else if (t >= 96.0F) {
         spread(m, 1.0F);
         float k = ease(t, 98.0F, 102.0F);
         pose(m.rightArm, k, -0.2F, 0.0F, 1.25F);
         pose(m.leftArm, k, -0.2F, 0.0F, -1.25F);
         pose(m.head, k, 0.1F, 0.6F * s.side, 0.0F);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      Vector3f body = s.victim.axis(1.0556251F);
      if (t < 34.0F) {
         float k = ease(t, 0.0F, 34.0F);
         return new CoupDeGrace.Shot(at(2.4F - 0.4F * k, -2.6F, 2.9F + 0.8F * k), at(0.0F, 0.0F, 0.2F), 0, true, 3.0F, 62.0F);
      }

      if (t < 62.0F) {
         return new CoupDeGrace.Shot(at(-1.3F, -3.1F, 0.5F), at(0.0F, 0.0F, 1.7F), 1, true, 3.0F, 66.0F);
      }

      if (t < 90.0F) {
         float k = ease(t, 62.0F, 90.0F);
         return new CoupDeGrace.Shot(at(-4.4F, -4.4F, 1.4F), at(0.0F, 0.0F, Mth.lerp(k, 9.5F, 6.2F)), 2, true, 3.0F, 66.0F);
      }

      if (t < 102.0F) {
         return new CoupDeGrace.Shot(at(0.8F, -7.2F, 2.6F), at(0.0F, 0.0F, 3.6F), 3, true, 3.0F, 72.0F);
      }

      float k = ease(t, 102.0F, 128.0F);
      return new CoupDeGrace.Shot(at(0.8F, -7.2F, 2.6F).lerp(at(1.8F, -2.9F, 1.9F), k), at(0.2F, 0.0F, Mth.lerp(k, 2.0F, 1.0F)), 3, false, 2.5F, 64.0F);
   }

   Vector3f[] binds(Spectacle.Frame s) {
      HumanoidModel<?> m = PROBE;
      this.poseVictim(m, s, s.t);
      ModelPart[] parts = new ModelPart[]{m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
      Vector3f[] out = new Vector3f[4];

      for (int i = 0; i < 4; i++) {
         out[i] = i < 2 ? hand(s.victim, parts[i]) : foot(s.victim, parts[i]);
      }

      return out;
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      float flare = Keys.window(t, 96.0F, 106.0F, 1.0F);
      float fade = 1.0F - ease(t, 106.0F, 138.0F);

      for (int i = 0; i < SIGILS.length; i++) {
         float k = out(phase(t, SIGILS[i], SIGILS[i] + 4.0F), 3.0F);
         if (!(k <= 0.0F)) {
            float spin = age * 0.015F * (i % 2 == 0 ? 1.0F : -1.0F) * (1.0F + i * 0.4F);
            float a = (0.75F + 0.25F * pulse(t, 6.0F) + flare) * fade;
            d.decal(
               FxTex.SIGIL[i], at(0.0F, 0.0F, 0.01F * i), SIGIL_SIZE[i] * k, spin, r(16766570), g(16766570), b(16766570), Math.min(1.0F, a), Draw.Blend.GLOW
            );
         }
      }

      if (t > SIGILS[0] && t < 116.0F) {
         for (int i = 0; i < 16; i++) {
            float u = (age * 0.012F + i * 0.0625F) % 1.0F;
            Vector3f p = orbit(i * 67.0F + age * 0.6F, 0.6F + 2.2F * (i * 5 % 7) / 6.0F, 0.1F + 3.2F * u);
            d.sprite(Sprite.rune(i), p, 0.28F, 0.0F, r(16766570), g(16766570), b(16766570), 0.8F * Mth.sin((float) Math.PI * u) * fade, Draw.Blend.GLOW);
         }
      }

      float chains = ease(t, 34.0F, 38.0F);
      if (chains > 0.0F && t < 104.0F) {
         Vector3f[] ends = this.binds(t < 96.0F ? s : this.other(s, 95.9F));

         for (int i = 0; i < 4; i++) {
            Vector3f a = orbit(ANCHORS[i], 2.3F, 0.05F);
            Vector3f b = ends[i];
            if (t < 96.0F) {
               Vector3f to = new Vector3f(a).lerp(b, out(phase(t, 34.0F + i, 34.0F + i + 3.0F), 2.0F));
               float len = a.distance(to);
               d.band(FxTex.CHAIN, 0.0F, 0.0F, len / 0.3F, 1.0F, false, a, to, 0.16F, r(16766570), g(16766570), b(16766570), 0.95F, Draw.Blend.GLOW);
               d.streak(a, to, 0.35F, r(16766570), g(16766570), b(16766570), 0.3F);
               d.glow(to, 0.4F, r(16775398), g(16775398), b(16775398), 0.8F);
            } else {
               float k = phase(t, 96.0F, 104.0F);
               Vector3f mid = new Vector3f(a).lerp(b, 0.5F);
               Vector3f lower = new Vector3f(mid).lerp(a, k);
               d.band(
                  FxTex.CHAIN,
                  0.0F,
                  0.0F,
                  a.distance(lower) / 0.3F,
                  1.0F,
                  false,
                  a,
                  lower,
                  0.16F,
                  r(16766570),
                  g(16766570),
                  b(16766570),
                  1.0F - k,
                  Draw.Blend.GLOW
               );
            }
         }
      }

      if (t >= 62.0F && t < 118.0F) {
         float form = ease(t, 62.0F, 72.0F);
         float gone = 1.0F - ease(t, 108.0F, 118.0F);
         float a = form * gone;
         float y = tip(t);
         Vector3f tipAt = at(0.0F, 0.0F, y);
         Vector3f guard = at(0.0F, 0.0F, y + 6.4F);
         Matrix4f blade = new Matrix4f().translate(at(0.0F, 0.0F, y + 3.2F)).rotateY((float) (-Math.PI / 4)).scale(0.95F, 6.4F, 0.3F);
         d.box(FxTex.SWORD_LIGHT, blade, 1.0F, 0.95F, 0.8F, a, Draw.Blend.GLOW);
         Matrix4f cross = new Matrix4f().translate(new Vector3f(guard).add(0.0F, 0.08F, 0.0F)).rotateY((float) (-Math.PI / 4)).scale(3.4F, 0.36F, 0.4F);
         d.box(FxTex.SWORD_LIGHT, cross, 1.0F, 0.9F, 0.6F, a, Draw.Blend.GLOW);
         Matrix4f grip = new Matrix4f().translate(new Vector3f(guard).add(0.0F, 0.95F, 0.0F)).scale(0.3F, 1.7F, 0.3F);
         d.box(FxTex.SWORD_LIGHT, grip, 1.0F, 0.85F, 0.5F, a, Draw.Blend.GLOW);
         d.glow(new Vector3f(guard).add(0.0F, 1.95F, 0.0F), 1.8F, r(16766570), g(16766570), b(16766570), a);
         d.streak(tipAt, guard, 3.2F, r(16766570), g(16766570), b(16766570), 0.45F * a);
         d.sprite(
            Sprite.RING,
            guard,
            2.8F + 0.3F * Mth.sin(age * 0.3F),
            age * 0.05F,
            r(16775398),
            g(16775398),
            b(16775398),
            0.5F * a * (1.0F - flare),
            Draw.Blend.GLOW
         );
         if (t >= 90.0F && t < 96.0F) {
            d.streak(new Vector3f(guard).add(0.0F, 3.0F, 0.0F), guard, 0.9F, r(16775398), g(16775398), b(16775398), 0.5F);
         }
      }

      if (t >= 96.0F && t < 110.0F) {
         float k = (t - 96.0F) / 14.0F;
         Vector3f c = at(0.0F, 0.0F, 0.8F);
         d.glow(c, 8.0F * (1.0F - k), 1.0F, 0.95F, 0.8F, 1.0F - k);
         d.decal(
            FxTex.SHOCK, at(0.0F, 0.0F, 0.0F), 1.0F + 10.0F * out(k, 2.0F), 0.0F, r(16766570), g(16766570), b(16766570), 0.9F * (1.0F - k), Draw.Blend.GLOW
         );
         d.beam(at(0.0F, 0.0F, 0.0F), at(0.0F, 0.0F, 30.0F), 3.0F * (1.0F - k), age * 0.2F, r(16766570), g(16766570), b(16766570), 0.6F * (1.0F - k));
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;

      for (int i = 0; i < SIGILS.length; i++) {
         if (t == (int)SIGILS[i]) {
            fx.sound(Sfx.RUNE, at(0.0F, 0.0F, 0.3F), 1.0F, 0.75F + 0.12F * i);
            fx.ring(Sprite.rune(i), at(0.0F, 0.0F, 0.1F), 12, 0.06F, 0.25F, 20, 16766570, false);
         }
      }

      if (t == 4) {
         fx.sound(Sfx.CHIME, s.actor.axis(1.8F), 0.6F, 0.7F);
      }

      for (int i = 0; i < 4; i++) {
         if (t == 34 + i) {
            fx.sound(Sfx.CHAIN, orbit(ANCHORS[i], 2.3F, 0.3F), 1.0F, 0.9F + 0.08F * i);
            fx.sparks(orbit(ANCHORS[i], 2.3F, 0.1F), 8, 0.2F, 16766570);
         }
      }

      if (t == 40) {
         fx.sound(Sfx.CHAIN, s.victim.axis(1.0F), 1.0F, 0.7F);
      }

      if (t == 62) {
         fx.sound(Sfx.CHOIR, at(0.0F, 0.0F, 6.0F), 1.0F, 0.9F);
         fx.sound(Sfx.HUM, at(0.0F, 0.0F, 6.0F), 0.7F, 0.5F);
      }

      if (t == 78) {
         fx.sound(Sfx.CHOIR, at(0.0F, 0.0F, 6.0F), 1.0F, 1.0F);
      }

      if (t == 89) {
         fx.sound(Sfx.SWORD_FALL, at(0.0F, 0.0F, 5.0F), 1.0F, 1.1F);
      }

      if (t == 96.0F) {
         Vector3f c = at(0.0F, 0.0F, 2.45F);
         fx.sound(Sfx.HOLY_IMPACT, c, 1.0F, 1.0F);
         fx.sound(Sfx.IMPACT_HEAVY, c, 1.0F, 0.9F);
         fx.sound(Sfx.FLESH, c, 1.0F, 0.7F);
         fx.sound(Sfx.CHAIN, c, 1.0F, 0.6F);
         fx.flash(c, 1.0F);
         fx.shake(1.0F, 0.9F);
         fx.sparks(c, 60, 0.8F, 16775398);
         fx.burst(Sprite.FEATHER, c, 24, 0.25F, 0.25F, 60, 16775398, true);
         fx.burst(Sprite.DROP, c, 20, 0.3F, 0.12F, 30, 9043984, true);

         for (float a : ANCHORS) {
            Vector3f mid = new Vector3f(orbit(a, 2.3F, 0.05F)).lerp(c, 0.5F);
            fx.burst(Sprite.SHARD_B, mid, 6, 0.2F, 0.12F, 24, 16766570, false);
         }
      }

      if (t == 99.0F) {
         fx.sound(Sfx.SLAM, at(0.0F, 0.0F, 0.2F), 1.0F, 0.8F);
         fx.ring(Sprite.DUST, at(0.0F, 0.0F, 0.05F), 26, 0.25F, 0.6F, 30, 10129008, true);
      }

      if (t == 108.0F) {
         fx.sound(Sfx.SPARKLE, at(0.0F, 0.0F, 1.5F), 0.8F, 0.9F);
      }

      if (t > 108.0F && t < 118.0F) {
         Vector3f p = at(0.0F, 0.0F, tip(t) + 6.4F * Vfx.RANDOM.nextFloat());
         fx.p(Sprite.TWINKLE, p).vel(fx.dir(0.0F, 0.04F, 0.0F)).size(0.2F, 0.05F).life(30).color(16766570);
         fx.p(Sprite.TWINKLE, p)
            .vel(fx.dir((float)Vfx.random(0.03).x, 0.05F, (float)Vfx.random(0.03).z))
            .size(0.15F, 0.05F)
            .life(30)
            .color(16775398);
      }
   }
}
