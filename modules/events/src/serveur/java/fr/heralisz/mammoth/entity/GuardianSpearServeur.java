package fr.heralisz.mammoth.entity;

import fr.heralisz.mammoth.MammothConfig;
import fr.heralisz.mammoth.fx.Particles;
import fr.heralisz.mammoth.sound.Sounds;
import fr.heralisz.mammoth.vfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link GuardianSpear} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new GuardianSpear(...) » côté serveur.
 */
public class GuardianSpearServeur extends GuardianSpear {
   public GuardianSpearServeur(EntityType<? extends GuardianSpear> type, Level level) {
      super(type, level);
   }

   private int life;

   /** Un pas de vol (appelé par GuardianSpear.tick après Projectile.tick) : le code d'origine, coups et atterrissage compris. */
   @Override
   protected void vol() {
      Vec3 v = this.getDeltaMovement();
      Vec3 from = this.position(), to = from.add(v);
      if (!this.level().isClientSide && this.level() instanceof ServerLevel level) {
         if (++life > 200) {
            land(level, this.blockPosition());
            return;
         }
         for (Player p : level.getEntitiesOfClass(Player.class, new AABB(from, to).inflate(0.8))) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            Entity owner = this.getOwner();
            p.hurt(this.damageSources().mobProjectile(this, owner instanceof net.minecraft.world.entity.LivingEntity le ? le : null),
               9F * MammothConfig.DAMAGE_MULT.get().floatValue());
            p.setDeltaMovement(p.getDeltaMovement().add(v.normalize().scale(0.6)).add(0, 0.2, 0));
            p.hurtMarked = true;
            Vfx.hit(p, 0.5F);
            level.playSound(null, p.blockPosition(), Sounds.BONE_HIT.get(), net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 1.1F);
            this.setDeltaMovement(v.scale(0.2));
            v = this.getDeltaMovement();
            to = from.add(v);
            break;
         }
         HitResult hit = level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, this));
         if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bh = (BlockHitResult) hit;
            BlockPos at = bh.getBlockPos().relative(bh.getDirection());
            this.setPos(bh.getLocation());
            land(level, at);
            return;
         }
         if (life % 2 == 0) level.sendParticles(Particles.MIST.get(), getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
      }
      this.setPos(to);
      this.setDeltaMovement(v.scale(0.99).add(0, -0.035, 0));
   }

   private void land(ServerLevel level, BlockPos at) {
      level.playSound(null, at, Sounds.BONE_HIT.get(), net.minecraft.sounds.SoundSource.HOSTILE, 2F, 0.6F);
      level.sendParticles(Particles.SHARD.get(), getX(), getY(), getZ(), 12, 0.3, 0.2, 0.3, 0.1);
      while (!level.getBlockState(at).isAir() && at.getY() < this.getBlockY() + 3) at = at.above();
      while (level.getBlockState(at.below()).isAir() && at.getY() > this.getBlockY() - 8) at = at.below();
      if (this.getOwner() instanceof DraugrServeur d) d.spearLanded(level, at, this.getYRot());
      this.discard();
   }
}
