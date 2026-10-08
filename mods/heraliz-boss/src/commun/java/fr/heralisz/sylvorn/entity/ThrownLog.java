package fr.heralisz.sylvorn.entity;

import fr.heralisz.sylvorn.particle.ModParticles;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Tronc arraché et lancé par Sylvorn : tournoie en cloche et s'écrase en zone (le cercle d'alerte est posé à l'avance). */
public class ThrownLog extends ThrowableItemProjectile {
   public ThrownLog(EntityType<? extends ThrownLog> type, Level level) {
      super(type, level);
   }

   @Override
   protected Item getDefaultItem() {
      return Items.SPRUCE_LOG;
   }

   @Override
   protected float getGravity() {
      return 0.06F;
   }

   @Override
   protected boolean canHitEntity(Entity e) {
      return !(e instanceof Sylvorn) && !(e instanceof SapNode) && super.canHitEntity(e);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide && this.tickCount % 2 == 0) {
         this.level().addParticle(ModParticles.LEAF.get(), getX(), getY(), getZ(), 0, -0.02, 0);
      }
      if (!this.level().isClientSide && this.tickCount > 100) this.discard();
   }
}
