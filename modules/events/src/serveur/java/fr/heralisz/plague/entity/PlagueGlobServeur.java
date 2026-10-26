package fr.heralisz.plague.entity;

import fr.heralisz.plague.particle.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link PlagueGlob} (docs/METHODE_CLIENT_SERVEUR.md §5.4) : durée de vie et impact (le Porteur de Peste
 * applique les dégâts et le gaz). Le vol en cloche reste commun. Créée par l'usine du code commun dans les mondes serveur, ou
 * directement par le Porteur (constructeur (niveau, lanceur, genre)) ; ne jamais faire « new PlagueGlob(...) » côté serveur.
 */
public class PlagueGlobServeur extends PlagueGlob {
   public PlagueGlobServeur(EntityType<? extends PlagueGlob> type, Level level) {
      super(type, level);
   }

   public PlagueGlobServeur(Level level, LivingEntity owner, int kind) {
      super(level, owner, kind);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         if (kind() == CANISTER) this.level().addParticle(ModParticles.TOXIC_SMOKE.get(), getX(), getY(), getZ(), 0, 0.02, 0);
         else if (this.tickCount % 2 == 0) this.level().addParticle(ModParticles.ACID_DROP.get(), getX(), getY(), getZ(), 0, 0, 0);
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
      double gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.x), (int) Math.floor(p.z));
      Vec3 g = new Vec3(p.x, Math.min(p.y, gy), p.z);
      if (this.getOwner() instanceof PlagueBearerServeur b) b.globImpact(level, g, kind());
      this.discard();
   }
}
