package fr.heralisz.n07.entity;

import fr.heralisz.n07.particle.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link N07Shell} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par SujetN07Serveur (new N07ShellServeur, même constructeur qu'à l'origine) ou par l'usine du code commun (mondes serveur) ;
 * ne jamais faire « new N07Shell(...) » côté serveur.
 */
public class N07ShellServeur extends N07Shell {
   public N07ShellServeur(EntityType<? extends N07Shell> type, Level level) {
      super(type, level);
   }

   public N07ShellServeur(Level level, LivingEntity owner, int kind) {
      super(level, owner, kind);
   }


   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         if (kind() == SHELL) {
            this.level().addParticle(ModParticles.TOXIC_SMOKE.get(), getX(), getY(), getZ(), 0, 0.02, 0);
            this.level().addParticle(ModParticles.ACID_DROP.get(), getX(), getY(), getZ(), 0, 0, 0);
         } else if (this.tickCount % 2 == 0) {
            this.level().addParticle(ModParticles.ACID_DROP.get(), getX(), getY(), getZ(), 0, 0, 0);
         }
      } else if (this.tickCount > 120) {
         this.discard();
      }
   }

   @Override
   protected void onHit(HitResult r) {
      super.onHit(r);
      if (this.level().isClientSide) return;
      ServerLevel level = (ServerLevel) this.level();
      Vec3 p = this.position();
      // sol sous le point d'impact (et non le haut de la colonne : salles couvertes)
      double gy = Ground.floorY(level, p.x, p.y + 0.3, p.z);
      Vec3 g = new Vec3(p.x, Math.min(p.y, gy), p.z);
      if (this.getOwner() instanceof SujetN07Serveur b) b.shellImpact(level, g, kind());
      this.discard();
   }
}
