package fr.heralisz.sylvorn.entity;

import fr.heralisz.sylvorn.SylvornConfig;
import fr.heralisz.sylvorn.entity.Ground;
import fr.heralisz.sylvorn.particle.ModParticles;
import fr.heralisz.sylvorn.sound.Sounds;
import fr.heralisz.sylvorn.fvfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link ThrownLog} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new ThrownLog(...) » côté serveur.
 */
public class ThrownLogServeur extends ThrownLog {
   public ThrownLogServeur(EntityType<? extends ThrownLog> type, Level level) {
      super(type, level);
   }

   @Override
   protected void onHit(HitResult r) {
      super.onHit(r);
      if (this.level().isClientSide) return;
      ServerLevel level = (ServerLevel) this.level();
      Vec3 p = this.position();
      double gy = Ground.y(level, p.x, p.y + 0.5, p.z);
      Vec3 g = new Vec3(p.x, Math.min(p.y, gy), p.z);
      float mult = SylvornConfig.DAMAGE_MULT.get().floatValue();
      for (Player pl : level.getEntitiesOfClass(Player.class, new AABB(g, g).inflate(3.8, 3, 3.8))) {
         if (pl.isCreative() || pl.isSpectator() || pl.distanceToSqr(g) > 3.8 * 3.8) continue;
         pl.hurt(this.damageSources().thrown(this, this.getOwner()), 20F * mult);
         Vec3 push = pl.position().subtract(g).normalize();
         pl.setDeltaMovement(push.x * 0.9, 0.6, push.z * 0.9);
         pl.hurtMarked = true;
      }
      var ground = level.getBlockState(BlockPos.containing(g.x, g.y - 1, g.z));
      Vfx.chips(level, g, 1.5F, 7F, 16, ModParticles.WOOD_CHIP.get());
      if (!ground.isAir()) Vfx.debris(level, g, 1.5F, 6F, 14, ground, Vec3.ZERO);
      Vfx.shake(level, g, 0.9F, 24, 10);
      level.sendParticles(ModParticles.DUST.get(), g.x, g.y + 0.5, g.z, 22, 1.6, 0.4, 1.6, 0.05);
      level.sendParticles(ModParticles.LEAF.get(), g.x, g.y + 1, g.z, 24, 1.5, 0.8, 1.5, 0.15);
      level.playSound(null, g.x, g.y, g.z, Sounds.FX_EXPLODE.get(), SoundSource.HOSTILE, 1.6F, 0.7F);
      level.playSound(null, g.x, g.y, g.z, Sounds.FX_WOOD.get(), SoundSource.HOSTILE, 2F, 0.5F);
      this.discard();
   }
}
