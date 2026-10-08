package fr.heralisz.sylvorn.entity;

import fr.heralisz.sylvorn.particle.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link SylvornLegs} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new SylvornLegs(...) » côté serveur.
 */
public class SylvornLegsServeur extends SylvornLegs {
   public SylvornLegsServeur(EntityType<? extends SylvornLegs> type, Level level) {
      super(type, level);
   }

   private Vec3 from;

   public void setup(Vec3 ankles, float yaw, int owner) {
      from = ankles;
      this.moveTo(ankles.x, ankles.y, ankles.z, yaw, 0);
      this.entityData.set(YAW, yaw);
      this.entityData.set(OWNER, owner);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) return;
      if (from == null) from = this.position();
      int t = this.tickCount;
      // elles pendent en se balançant, puis remontent de plus en plus vite
      double rise = t < 30 ? 0 : 0.5 * Math.pow((t - 30) / 20.0, 2);
      Vec3 f = Vec3.directionFromRotation(0, legsYaw());
      double sw = Math.sin(t * 0.16) * 0.5 * Math.max(0, 1 - t / 120.0);
      this.setPos(from.x + f.x * sw, from.y + rise, from.z + f.z * sw);
      if (t < 90 && t % 2 == 0 && this.level() instanceof ServerLevel sl) {
         double y = this.getY() - 5.2;
         sl.sendParticles(ModParticles.BLOOD.get(), this.getX(), y, this.getZ(), 2, 0.35, 0.1, 0.35, 0.02);
      }
      if (t > LIFE) this.discard();
   }
}
