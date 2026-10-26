package fr.heralisz.sylvorn.entity;

import fr.heralisz.sylvorn.sound.Sounds;
import fr.heralisz.sylvorn.SylvornConfig;
import fr.heralisz.sylvorn.particle.ModParticles;
import fr.heralisz.sylvorn.fvfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link SeedPod} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new SeedPod(...) » côté serveur.
 */
public class SeedPodServeur extends SeedPod {
   public SeedPodServeur(EntityType<? extends SeedPod> type, Level level) {
      super(type, level);
   }

   @Override
   protected void onHit(HitResult r) {
      super.onHit(r);
      if (this.level().isClientSide || landedAt() >= 0) return;
      this.setDeltaMovement(Vec3.ZERO);
      this.setNoGravity(true);
      this.entityData.set(LANDED, this.tickCount);
      this.level().playSound(null, getX(), getY(), getZ(), Sounds.FX_EARTH.get(), SoundSource.HOSTILE, 1.5F, 0.6F);
   }

   @Override
   void burst(ServerLevel level) {
      Vec3 g = this.position();
      float mult = SylvornConfig.DAMAGE_MULT.get().floatValue();
      for (Player p : level.getEntitiesOfClass(Player.class, new AABB(g, g).inflate(3.5, 3, 3.5))) {
         if (p.isCreative() || p.isSpectator() || p.distanceToSqr(g) > 3.5 * 3.5) continue;
         p.hurt(this.damageSources().thrown(this, this.getOwner()), 11F * mult);
         p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false));
         p.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, false, false));
         p.setDeltaMovement(p.getDeltaMovement().x, 0.55, p.getDeltaMovement().z);
         p.hurtMarked = true;
      }
      Vfx.debris(level, g, 0.6F, 4F, 10, net.minecraft.world.level.block.Blocks.ROOTED_DIRT.defaultBlockState(), Vec3.ZERO);
      level.sendParticles(ModParticles.DUST.get(), g.x, g.y + 0.4, g.z, 14, 1.2, 0.3, 1.2, 0.04);
      level.sendParticles(ModParticles.SPORE.get(), g.x, g.y + 0.6, g.z, 30, 1.4, 0.6, 1.4, 0.06);
      level.playSound(null, g.x, g.y, g.z, Sounds.FX_EARTH.get(), SoundSource.HOSTILE, 2F, 0.6F);
      level.playSound(null, g.x, g.y, g.z, Sounds.FX_GOO.get(), SoundSource.HOSTILE, 1.5F, 0.5F);
      this.discard();
   }
}
