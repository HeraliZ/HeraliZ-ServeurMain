package fr.heralisz.mere.entity;

import fr.heralisz.mere.MereConfig;
import fr.heralisz.mere.sound.Sounds;
import fr.heralisz.mere.vfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link WebGlob} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new WebGlob(...) » côté serveur.
 */
public class WebGlobServeur extends WebGlob {
   public WebGlobServeur(EntityType<? extends WebGlob> type, Level level) {
      super(type, level);
   }

   /** Globe lancé par la Mère (même construction que le mod d'origine : au niveau des yeux du tireur, qui en est le propriétaire). */
   public WebGlobServeur(Level level, LivingEntity owner, int kind) {
      super(level, owner, kind);
   }

   /** Tir en cloche qui retombe sur aim en flight ticks au plus (cloche abaissée tant qu'elle touche la voûte). @return durée de vol */
   public int launch(Vec3 from, Vec3 aim, int flight) {
      int f = flight;
      while (f > 8 && !clear(this.level(), from, aim, f)) f -= f > 20 ? 2 : 1;
      this.setPos(from.x, from.y, from.z);
      this.setDeltaMovement(velocity(from, aim, f));
      return f;
   }

   private static Vec3 velocity(Vec3 from, Vec3 aim, int flight) {
      double f = (1 - Math.pow(DRAG, flight)) / (1 - DRAG);
      return new Vec3((aim.x - from.x) / f, (aim.y - from.y + 0.5 * G * flight * flight) / f, (aim.z - from.z) / f);
   }

   private static boolean clear(Level level, Vec3 from, Vec3 aim, int flight) {
      Vec3 v = velocity(from, aim, flight);
      double x = from.x, y = from.y, z = from.z, vx = v.x, vy = v.y, vz = v.z;
      BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
      for (int t = 0; t < flight - 2; t++) {
         x += vx;
         y += vy;
         z += vz;
         vx *= DRAG;
         vy = vy * DRAG - G;
         vz *= DRAG;
         if (y < aim.y + 1.5) continue;
         p.set(Math.floor(x), Math.floor(y + 0.6), Math.floor(z));
         if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
      }
      return true;
   }

   @Override
   protected void onHit(HitResult hit) {
      super.onHit(hit);
      if (this.level().isClientSide) return;
      ServerLevel level = (ServerLevel) this.level();
      Vec3 p = this.position();
      double gy = RuinMotherServeur.groundY(level, p.x, p.y + 0.5, p.z);
      Vec3 g = new Vec3(p.x, Math.min(p.y, gy), p.z);
      float mult = MereConfig.DAMAGE_MULT.get().floatValue();
      RuinMotherServeur mother = this.getOwner() instanceof RuinMotherServeur m ? m : null;
      boolean hitSomeone = false;
      if (kind() == 2) {
         for (Player pl : level.getEntitiesOfClass(Player.class, new AABB(p, p).inflate(1.8))) {
            if (pl.isCreative() || pl.isSpectator()) continue;
            if (mother != null) mother.wrap(level, pl);
         }
         level.sendParticles(RuinMother.WEB_FX, p.x, p.y, p.z, 20, 0.5, 0.5, 0.5, 0.1);
         level.playSound(null, p.x, p.y, p.z, Sounds.COCOON_TEAR.get(), SoundSource.HOSTILE, 1.8F, 0.5F);
         this.discard();
         return;
      }
      float r = kind() == 0 ? 3.2F : 2.8F;
      for (Player pl : level.getEntitiesOfClass(Player.class, new AABB(g, g).inflate(r, 2.5, r))) {
         if (pl.isCreative() || pl.isSpectator() || pl.position().distanceToSqr(g.x, pl.getY(), g.z) > r * r) continue;
         hitSomeone = true;
         if (kind() == 0) {
            pl.hurt(this.damageSources().thrown(this, this.getOwner()), 6F * mult);
         } else {
            pl.hurt(this.damageSources().thrown(this, this.getOwner()), 10F * mult);
            pl.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1, false, false));
         }
         Vfx.hit(pl, 0.35F);
      }
      level.sendParticles(RuinMother.WEB_FX, g.x, g.y + 0.4, g.z, 16, 0.6, 0.3, 0.6, 0.1);
      if (kind() == 0) {
         if (mother != null) mother.addWeb(g, 3.2F, 160);
         level.playSound(null, g.x, g.y, g.z, Sounds.COCOON_TEAR.get(), SoundSource.HOSTILE, 1.6F, 0.6F);
      } else {
         level.sendParticles(RuinMother.GOO_FX, g.x, g.y + 0.4, g.z, 20, 0.8, 0.3, 0.8, 0.15);
         level.playSound(null, g.x, g.y, g.z, Sounds.GOO.get(), SoundSource.HOSTILE, 1.8F, 0.5F);
         Vfx.web(level, g, 1.6F, 100);
         if (!hitSomeone && mother != null && mother.isAlive()) mother.hatch(level, g, 2);          // personne dessous : les œufs éclosent
      }
      this.discard();
   }
}
