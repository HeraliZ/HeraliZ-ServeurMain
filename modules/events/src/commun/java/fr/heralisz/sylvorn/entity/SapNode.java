package fr.heralisz.sylvorn.entity;

import fr.heralisz.sylvorn.particle.ModParticles;
import fr.heralisz.sylvorn.fvfx.Vfx;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Nœud de sève : cœur ambré pulsant, planté dans le sol pendant l'enracinement de Sylvorn.
 * Tant qu'il vit, le boss se soigne ; à abattre au fusil. Jamais sauvegardé.
 */
public class SapNode extends LivingEntity {
   int ownerId = -1;

   public SapNode(EntityType<? extends SapNode> type, Level level) {
      super(type, level);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
   }

   @Override
   public void tick() {
      super.tick();
      this.setDeltaMovement(0, Math.min(0, this.getDeltaMovement().y), 0);
      if (this.level().isClientSide) {
         if (this.tickCount % 2 == 0)
            this.level().addParticle(ModParticles.SPORE.get(), getX() + (random.nextDouble() - 0.5), getY() + 1.4 + random.nextDouble(), getZ() + (random.nextDouble() - 0.5), 0, 0.02, 0);
         return;
      }
      if (ownerId < 0 || !(this.level().getEntity(ownerId) instanceof Sylvorn s) || !s.isAlive() || this.tickCount > 400) withdraw();
   }

   /** Rentre dans le sol sans éclat (fin de l'enracinement). */
   public void withdraw() {
      if (this.level() instanceof ServerLevel sl) {
         Vfx.chips(sl, this.position(), 0.8F, 3F, 6, ModParticles.DUST.get());
      }
      this.discard();
   }

   @Override
   public void die(DamageSource s) {
      super.die(s);
      if (this.level() instanceof ServerLevel sl) effetsMort(sl);
      this.discard();
   }

   /** Éclat du nœud abattu (flash, éclats, sons) : dans SapNodeServeur ; neutre ici (et jamais appelé côté client). */
   void effetsMort(ServerLevel sl) {
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean isPickable() {
      return true;
   }

   @Override
   public Iterable<ItemStack> getArmorSlots() {
      return NonNullList.withSize(4, ItemStack.EMPTY);
   }

   @Override
   public ItemStack getItemBySlot(EquipmentSlot slot) {
      return ItemStack.EMPTY;
   }

   @Override
   public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}

   @Override
   public HumanoidArm getMainArm() {
      return HumanoidArm.RIGHT;
   }
}
