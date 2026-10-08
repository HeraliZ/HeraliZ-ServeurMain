package fr.heralisz.mere.entity;

import fr.heralisz.mere.HeraliszMere;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Projectile de la Mère des Ruines : globe de toile (kind 0, plaque gluante), cocon d'œufs (kind 1, venin ou éclosion),
 * paquet de soie (kind 2, emmaillote le joueur touché). */
public class WebGlob extends ThrowableItemProjectile {
   private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(WebGlob.class, EntityDataSerializers.INT);
   static final double G = 0.05, DRAG = 0.99;

   public WebGlob(EntityType<? extends WebGlob> type, Level level) {
      super(type, level);
   }

   public WebGlob(Level level, LivingEntity owner, int kind) {
      super(HeraliszMere.WEB_GLOB.get(), owner, level);
      this.entityData.set(KIND, kind);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(KIND, 0);
   }

   public int kind() {
      return this.entityData.get(KIND);
   }

   @Override
   protected Item getDefaultItem() {
      return Items.COBWEB;
   }

   @Override
   protected float getGravity() {
      return (float) G;
   }

   @Override
   protected boolean canHitEntity(Entity e) {
      return !(e instanceof RuinMother) && !(e instanceof WebGlob) && !(e instanceof CaveSpider) && super.canHitEntity(e);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide && this.tickCount % 2 == 0) this.level().addParticle(RuinMother.WEB_FX, getX(), getY(), getZ(), 0, 0, 0);
      if (!this.level().isClientSide && this.tickCount > 120) this.discard();
   }
}
