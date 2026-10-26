package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class Pantin extends KnifeFinisher {
   static final float SHOW = 2.0F;
   static final float THROW = 10.0F;
   static final float APEX = 22.0F;
   static final float STRINGS = 14.0F;
   static final float UP = 18.0F;
   static final float DANCE = 28.0F;
   static final float CROSS = 80.0F;
   static final float SKY = 9.5F;
   static final float STAND = 1.9F;
   static final Puppet.Move[] MOVES = new Puppet.Move[]{
      Puppet.Move.SLAP, Puppet.Move.BOW, Puppet.Move.PUNCH, Puppet.Move.KICK, Puppet.Move.SLAP_BOTH, Puppet.Move.TWIRL, Puppet.Move.SLAP
   };
   static final int GOLD = 16769162;
   static final int RED = 12584984;
   private final HumanoidModel<LivingEntity> actorProbe = Script.probe();
   private final HumanoidModel<LivingEntity> victimProbe = Script.probe();
   static final float BEAT = 8.0F;

   Pantin(Blade blade) {
      super(ExecutionType.PANTIN, blade);
   }

   @Override
   float spin(float t) {
      return Keys.key(t, 2.0F, 0.0F, 8.0F, (float) (Math.PI * 2));
   }

   @Override
   float scale(float t) {
      return Keys.key(t, 0.0F, 1.0F, 6.0F, 1.35F, 22.0F, 1.8F, 118.0F, 1.8F);
   }

   @Override
   float slowmo(float t) {
      return t >= 96.0F && t < 110.0F ? 1.0F : 0.0F;
   }

   @Nullable
   static Puppet.Move move(float t) {
      if (!(t < 28.0F) && !(t >= 80.0F)) {
         int i = (int)((t - 28.0F) / 8.0F);
         return i < MOVES.length ? MOVES[i] : null;
      } else {
         return null;
      }
   }

   static float beat(float t) {
      return (t - 28.0F) % 8.0F / 8.0F;
   }

   static float jerk(float t) {
      return move(t) == null ? 0.0F : CoupDeGrace.smooth((t - 28.0F) % 8.0F / 2.0F);
   }

   Vector3f target(Script.Frame f) {
      Script.Frame end = this.other(f, 95.99F);
      return this.victimPoint(end, Knife.Part.HEAD, new Vector3f(0.0F, -8.6F, 0.0F));
   }

   Matrix4f falling(Script.Frame f, float t) {
      Vector3f head = this.target(f);
      float u = CoupDeGrace.phase(t, 22.0F, 96.0F);
      float y = Mth.lerp(u * u * u, 9.5F, head.y - 0.3F);
      Vector3f tip = new Vector3f(head.x, y, head.z);
      float phi = 720.0F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 22.0F, 88.0F)) * (float) (Math.PI / 180.0);
      Vector3f dir = new Vector3f(0.0F, -Mth.cos(phi), Mth.sin(phi));
      float psi = Math.max(0.0F, t - 88.0F) * 0.45F;
      Vector3f axis = t < 88.0F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(Mth.cos(psi), 0.0F, Mth.sin(psi));
      return Knife.placedMatrix(this.blade, tip, dir, axis, this.scale(t));
   }

   Matrix4f inHead(Script.Frame f) {
      Script.Frame end = this.other(f, 95.99F);
      return this.victimPart(end, Knife.Part.HEAD).invert().mul(this.falling(f, 95.99F));
   }

   static float hang(float t) {
      Puppet.Move mv = move(t);
      float h = 0.04F * ease(t, 18.0F, 26.0F);
      if (mv == Puppet.Move.TWIRL) {
         h += 0.35F * Mth.sin((float) Math.PI * beat(t));
      }

      return h + 0.18F * ease(t, 86.0F, 95.0F) * (t < 96.0F ? 1.0F : 0.0F);
   }

   @Override
   void victim(Script.Frame f, float t) {
      if (t < 18.0F) {
         prone(f, t, 0.0F, new Vector3f());
      } else if (t < 96.0F) {
         float k = ease(t, 18.0F, 26.0F);
         float yaw = move(t) == Puppet.Move.TWIRL ? 360.0F * CoupDeGrace.smooth(beat(t) * 1.4F) : 0.0F;
         float bow = move(t) == Puppet.Move.BOW ? -45.0F * jerk(t) : 0.0F;
         float sway = 3.0F * Mth.sin(t * 0.3F) * (1.0F - ease(t, 80.0F, 84.0F));
         Vector3f mid = at(0.0F, 0.0F, 1.0556251F + hang(t));
         Finisher.rise(f.victim, k, 1, 0.0F, mid);
         if (k >= 1.0F) {
            f.victim.turn(yaw, bow + sway, 0.0F, 0.0F).feet(at(0.0F, 0.0F, hang(t)));
         }
      } else {
         float fall = (float)Math.pow(CoupDeGrace.phase(t, 97.0F, 110.0F), 1.6);
         float y = hang(95.99F) * (1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 96.0F, 100.0F)));
         float bounce = 0.06F * Mth.sin((float) Math.PI * CoupDeGrace.phase(t, 110.0F, 114.0F));
         f.victim.turn(0.0F, 90.0F * fall, 0.0F, 0.0F).feet(at(0.0F, 0.0F, y + 0.15F * fall + bounce));
      }
   }

   @Override
   void actor(Script.Frame f, float t) {
      float back = ease(t, 12.0F, 22.0F);
      Vector3f pos = at(Mth.lerp(back, f.actorStart.z, 1.9F), 0.0F, 0.0F);
      float in = CoupDeGrace.smooth(t / 5.0F);
      pos.x = Mth.lerp(in, f.actorStart.x, pos.x);
      f.lean = -0.15F * Keys.window(t, 8.0F, 22.0F, 3.0F) - 0.1F * Keys.window(t, 80.0F, 102.0F, 3.0F);
      f.walk = Keys.window(t, 12.0F, 22.0F, 2.0F) * 0.5F;
      f.stride = t * 0.8F;
      f.actor.turn(Mth.rotLerp(in, f.actorYaw, 180.0F), 0.0F, 0.0F, 0.0F).feet(pos);
      f.actorFwd = pos.z;
      f.actorSide = -pos.x;
   }

   @Override
   void knife(Script.Frame f, float t) {
      Knife k = f.knife;
      k.trail = Keys.window(t, 9.0F, 22.0F, 2.0F) + Keys.window(t, 88.0F, 97.0F, 1.0F) * 1.5F;
      k.blood = Keys.key(t, 95.5F, 0.0F, 96.0F, 1.0F);
      if (t >= 10.0F && t < 22.0F) {
         k.scene(Knife.flight(this.blade, this.heldAt(f, 10.0F), this.falling(f, 22.0F), CoupDeGrace.smooth(CoupDeGrace.phase(t, 10.0F, 22.0F)), 0.0F, 4.0F));
      } else if (t >= 22.0F && t < 96.0F) {
         k.scene(this.falling(f, t));
      } else if (t >= 96.0F) {
         k.stuck(Knife.Part.HEAD, this.inHead(f));
      }
   }

   @Override
   public void poseActor(HumanoidModel<?> m, CoupDeGrace.State state, float age) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      CoupDeGrace.stand(m);
      CoupDeGrace.bend(m, f.lean);
      Rig.walk(m.rightLeg, m.leftLeg, m.rightArm, m.leftArm, f.stride, f.walk, true);
      float show = Keys.window(t, 0.0F, 11.0F, 2.0F);
      Finisher.pose(m.rightArm, show, Mth.lerp(ease(t, 7.0F, 10.0F), -1.6F, -3.0F), 0.0F, 0.25F);
      float work = Keys.window(t, 12.0F, 97.0F, 2.5F);
      Puppet.Move mv = move(t);
      float j = jerk(t);
      float rx = -1.75F;
      float lx = -1.75F;
      if (mv == Puppet.Move.SLAP || mv == Puppet.Move.SLAP_BOTH) {
         rx -= 0.5F * j;
      }

      if (mv == Puppet.Move.PUNCH || mv == Puppet.Move.SLAP_BOTH) {
         lx -= 0.5F * j;
      }

      if (mv == Puppet.Move.BOW) {
         rx += 0.4F * j;
         lx += 0.4F * j;
      }

      float cross = ease(t, 80.0F, 84.0F);
      Finisher.pose(m.rightArm, work, rx + 0.03F * Mth.sin(age * 1.7F), 0.0F, Mth.lerp(cross, 0.25F, 1.45F));
      Finisher.pose(m.leftArm, work, lx + 0.03F * Mth.sin(age * 1.9F), 0.0F, Mth.lerp(cross, -0.25F, -1.45F));
      float drop = ease(t, 96.0F, 102.0F);
      Finisher.pose(m.rightArm, drop, -0.3F, 0.0F, 0.5F);
      Finisher.pose(m.leftArm, drop, -0.3F, 0.0F, -0.5F);
      if (t >= 10.0F && t < 18.0F) {
         m.head.xRot = -0.9F;
      } else {
         CoupDeGrace.look(m.head, f.actor.model(f.victim.scene(0.0F, -2.0F, 0.0F)), 1.0F, 1.0F);
      }
   }

   @Override
   public void poseVictim(HumanoidModel<?> m, CoupDeGrace.State state, float age) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      CoupDeGrace.stand(m);
      if (t < 22.0F) {
         proneLimbs(m, f, age, Keys.window(t, 4.0F, 12.0F, 2.5F), Keys.window(t, 10.0F, 18.0F, 2.5F), 0.0F);
         if (t >= 18.0F) {
            Finisher.limp(m, age, ease(t, 18.0F, 22.0F));
         }
      } else if (t < 96.0F) {
         Finisher.limp(m, age, 1.0F);
         Puppet.Move mv = move(t);
         if (mv != null) {
            Puppet.pose(m, mv, jerk(t), beat(t), age);
         }

         float cross = ease(t, 80.0F, 84.0F);
         Puppet.pose(m, Puppet.Move.CROSS, cross, 0.0F, age);
         Finisher.pose(m.head, ease(t, 82.0F, 88.0F), -0.85F, 0.0F, 0.0F);
         m.head.yRot = m.head.yRot + 0.05F * Mth.sin(age * 4.0F) * cross;
      } else {
         Finisher.spread(m, 1.0F - ease(t, 104.0F, 112.0F));
         Finisher.pose(m.head, 1.0F, -0.85F, 0.0F, 0.0F);
         float limp = ease(t, 104.0F, 112.0F);
         Finisher.pose(m.rightArm, limp, -0.2F, 0.0F, 1.1F);
         Finisher.pose(m.leftArm, limp, -0.2F, 0.0F, -1.1F);
         Finisher.pose(m.rightLeg, limp, 0.0F, 0.0F, 0.15F);
         Finisher.pose(m.leftLeg, limp, 0.0F, 0.0F, -0.15F);
      }
   }

   @Override
   public CoupDeGrace.Shot shot(CoupDeGrace.State state) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      Vector3f body = f.victim.axis(1.1F);
      if (t < 10.0F) {
         return new CoupDeGrace.Shot(new Vector3f(f.actor.pos).add(at(-2.4F, -1.0F, 1.5F)), f.actor.axis(1.6F), 0, true, 4.0F, 45.0F);
      } else if (t < 22.0F) {
         Vector3f[] k = this.knifePoints(f);
         Vector3f look = k != null ? new Vector3f(k[0]) : at(0.0F, 0.0F, 9.5F);
         return new CoupDeGrace.Shot(new Vector3f(f.actor.pos).add(at(1.6F, -1.2F, 0.4F)), look, 1, true, 6.0F, 62.0F);
      } else if (t < 52.0F) {
         return new CoupDeGrace.Shot(at(1.0F, -4.4F, 1.4F), at(0.95F, 0.0F, 1.2F), 2, true, 3.0F, 58.0F);
      } else if (t < 80.0F) {
         return new CoupDeGrace.Shot(at(2.0F, -2.3F, 1.6F), new Vector3f(body).add(0.0F, 0.35F, 0.0F), 3, true, 4.0F, 50.0F);
      } else if (t < 93.0F) {
         Vector3f head = f.victim.scene(0.0F, -4.0F, 0.0F);
         return new CoupDeGrace.Shot(new Vector3f(head).add(at(-1.3F, -1.6F, -1.05F)), new Vector3f(head).add(0.0F, 2.2F, 0.0F), 4, true, 4.0F, 72.0F);
      } else if (t < 100.0F) {
         Vector3f head = this.target(f);
         return new CoupDeGrace.Shot(new Vector3f(head).add(at(0.3F, -1.9F, 0.3F)), new Vector3f(head).add(0.0F, 0.2F, 0.0F), 5, true, 4.0F, 50.0F);
      } else {
         float k = ease(t, 100.0F, 128.0F);
         return new CoupDeGrace.Shot(at(0.6F, -3.6F, 1.0F).lerp(at(1.2F, -3.2F, 2.2F), k), at(0.0F, 0.0F, 0.9F - 0.4F * k), 6, true, 2.0F, 58.0F);
      }
   }

   @Override
   public Vector3f end(int side) {
      return at(1.9F, 0.0F, 0.0F);
   }

   @Override
   public Vector3f corpse(int side) {
      return at(-0.9F, 0.0F, 0.15F);
   }

   @Override
   public float reach() {
      return 1.6F;
   }

   @Override
   void draw(Draw d, Script.Frame f, float age, Choreography.Extras ex) {
      float t = f.t;
      float out = ease(t, 14.0F, 18.0F);
      float snap = CoupDeGrace.phase(t, 96.0F, 106.0F);
      if (out > 0.0F && snap < 1.0F) {
         HumanoidModel<?> am = this.actorProbe;
         this.poseActor(am, f, age);
         Vector3f rh = Finisher.hand(f.actor, am.rightArm);
         Vector3f lh = Finisher.hand(f.actor, am.leftArm);
         HumanoidModel<?> vm = this.victimProbe;
         this.poseVictim(vm, f, age);
         ModelPart[] parts = new ModelPart[]{vm.rightArm, vm.leftArm, vm.rightLeg, vm.leftLeg};
         Vector3f[] ends = new Vector3f[5];

         for (int i = 0; i < 4; i++) {
            ends[i] = i < 2 ? Finisher.hand(f.victim, parts[i]) : Finisher.foot(f.victim, parts[i]);
         }

         ends[4] = f.victim.scene(0.0F, -8.0F, 0.0F);
         float pluck = 0.0F;
         if (move(t) != null) {
            pluck = Keys.window(t, t - beat(t) * 8.0F, t - beat(t) * 8.0F + 4.0F, 1.0F);
         }

         float taut = 0.5F + 0.5F * ease(t, 80.0F, 84.0F);

         for (int i = 0; i < 5; i++) {
            Vector3f from = i == 4 ? new Vector3f(rh).lerp(lh, 0.5F) : (i % 2 == 0 ? rh : lh);
            Vector3f to = new Vector3f(from).lerp(ends[i], out);
            if (t < 96.0F) {
               Puppet.string(d, from, to, taut, pluck, 16769162, 1.0F, age, i);
            } else {
               float fade = 1.0F - snap;
               Vector3f mid = new Vector3f(from).lerp(to, 0.5F);
               Puppet.string(
                  d, from, new Vector3f(mid).lerp(from, Math.min(1.0F, snap * 3.0F)).add(0.0F, 0.3F * snap, 0.0F), 0.0F, 1.0F, 16769162, fade, age, i
               );
               Puppet.string(d, new Vector3f(mid).lerp(to, Math.min(1.0F, snap * 3.0F)).add(0.0F, -0.4F * snap, 0.0F), to, 0.0F, 1.0F, 16769162, fade, age, i);
            }
         }
      }

      if (t >= 18.0F && t < 96.0F) {
         Vector3f[] k = this.knifePoints(f);
         if (k != null) {
            float speed = t > 86.0F ? ease(t, 86.0F, 96.0F) : 0.2F;
            d.glow(k[0], 0.8F + 0.3F * Mth.sin(age * 0.8F), 1.0F, 0.95F, 0.8F, 0.7F);
            d.sprite(Sprite.STAR4, k[1], 0.9F, age * 0.1F, 1.0F, 1.0F, 1.0F, 0.9F, Draw.Blend.GLOW);
            d.streak(new Vector3f(k[0]).add(0.0F, 0.6F + 3.0F * speed, 0.0F), k[0], 0.25F, 1.0F, 0.95F, 0.8F, 0.3F + 0.5F * speed);
         }

         float aim = ease(t, 80.0F, 94.0F);
         if (aim > 0.0F) {
            Vector3f head = this.target(f);
            d.sprite(Sprite.RING, head, 0.5F + 0.2F * pulse(t, 4.0F), 0.0F, 1.0F, 0.85F, 0.5F, 0.7F * aim, Draw.Blend.GLOW);
         }
      }

      if (t >= 96.0F && t < 106.0F) {
         float k = (t - 96.0F) / 10.0F;
         Vector3f head = f.victim.scene(0.0F, -6.0F, 0.0F);
         d.glow(head, 2.5F * (1.0F - k), 1.0F, 0.5F, 0.5F, 1.0F - k);
         d.sprite(Sprite.RING, head, 0.5F + 3.0F * k, 0.0F, r(12584984), g(12584984), b(12584984), 1.0F - k, Draw.Blend.GLOW);
      }
   }

   @Override
   void events(KnifeFinisher.Fx fx, int t) {
      Script.Frame f = fx.f;
      if (t == 4) {
         fx.sound(Sfx.BLADE_DRAW, f.actor.axis(1.6F), 0.7F, 1.2F);
      }

      if (t == 10) {
         fx.sound(Sfx.WHOOSH_HEAVY, f.actor.axis(1.8F), 1.0F, 1.2F);
         fx.sparks(f.actor.axis(2.0F), 8, 0.2F, 16769162);
      }

      if (t == 22) {
         fx.sound(Sfx.SPARKLE, at(0.0F, 0.0F, 9.5F), 0.8F, 1.3F);
         fx.sound(Sfx.CHIME, at(0.0F, 0.0F, 9.5F), 0.6F, 1.5F);
      }

      if (t == 14) {
         fx.sound(Sfx.STRING_PLUCK, f.actor.axis(1.6F), 1.0F, 1.0F);
         fx.sound(Sfx.SPARKLE, f.victim.axis(0.8F), 0.6F, 1.2F);
      }

      if (t == 18) {
         fx.sound(Sfx.STRING_PLUCK, f.victim.axis(1.0F), 0.9F, 0.75F);
      }

      Puppet.Move mv = move(t);
      if (mv != null) {
         float start = t - beat(t) * 8.0F;
         float[] pitch = new float[]{1.0F, 0.89F, 1.12F, 1.33F, 1.19F, 1.5F, 1.68F};
         int i = (int)((t - 28.0F) / 8.0F);
         if (t == (int)start) {
            fx.sound(Sfx.STRING_PLUCK, f.victim.axis(1.6F), 0.9F, pitch[Math.min(i, pitch.length - 1)]);
         }

         if (Puppet.hurts(mv) && t == (int)(start + 3.6F)) {
            Vector3f face = f.victim.scene(0.0F, -4.0F, -4.0F);
            fx.sound(Sfx.IMPACT, face, 1.0F, mv == Puppet.Move.PUNCH ? 0.8F : 1.3F);
            fx.sound(Sfx.FLESH, face, 0.7F, 1.4F);
            fx.burst(Sprite.STAR, face, 5, 0.1F, 0.15F, 12, 16777215, false);
            fx.burst(Sprite.DROP, face, 3, 0.12F, 0.07F, 18, 9043984, true);
            fx.shake(0.12F, 0.05F);
         }

         if (mv == Puppet.Move.SLAP_BOTH && (t - (int)start) % 2 == 1) {
            fx.sound(Sfx.IMPACT, f.victim.scene(0.0F, -4.0F, -4.0F), 0.5F, 1.6F);
         }
      }

      if (t == 68) {
         fx.sound(Sfx.REVERSE_SWELL, f.victim.axis(1.6F), 0.7F, 1.0F);
      }

      if (t == 80) {
         fx.sound(Sfx.STRING_PLUCK, f.victim.axis(1.6F), 1.0F, 0.6F);
         fx.sound(Sfx.CHOIR, f.victim.axis(2.0F), 0.6F, 1.0F);
      }

      if (t == 89) {
         fx.sound(Sfx.SWORD_FALL, this.target(f).add(0.0F, 3.0F, 0.0F), 1.0F, 1.4F);
      }

      if (t == 96.0F) {
         Vector3f head = this.target(f);
         fx.sound(Sfx.FLESH, head, 1.0F, 0.7F);
         fx.sound(Sfx.BONE_CRACK, head, 1.0F, 0.8F);
         fx.sound(Sfx.IMPACT_HEAVY, head, 1.0F, 1.1F);
         fx.sound(Sfx.STRING_SNAP, head, 1.0F, 1.0F);
         fx.flash(head, 0.4F);
         fx.shake(0.8F, 0.6F);
         fx.burst(Sprite.DROP, head, 26, 0.25F, 0.12F, 30, 9043984, true);
         fx.sparks(head, 30, 0.4F, 16769162);
      }

      if (t == 100.0F || t == 106.0F) {
         fx.heartbeat(1.0F);
         fx.sound(Sfx.HEARTBEAT, f.victim.axis(1.0F), 0.8F, 0.6F);
      }

      if (t == 110.0F) {
         fx.sound(Sfx.BODY_FALL, at(-0.9F, 0.0F, 0.2F), 1.0F, 0.8F);
         fx.ring(Sprite.DUST, at(-0.8F, 0.0F, 0.05F), 14, 0.08F, 0.4F, 22, 9075302, true);
      }
   }
}
