package fr.heralisz.tactical.api.item;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.api.melee.AttackResult;
import fr.heralisz.tactical.api.melee.MeleeAction;
import fr.heralisz.tactical.config.CommonConfig;
import fr.heralisz.tactical.item.index.MeleeWeaponIndex;
import fr.heralisz.tactical.item.melee.CombatData;
import fr.heralisz.tactical.network.NetworkHandler;
import fr.heralisz.tactical.network.message.SCustomSound;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.PacketDistributor.TargetPoint;
import org.jetbrains.annotations.Nullable;

public interface IMeleeWeapon extends ICustomItem {
   String ID_TAG = "MeleeWeaponId";
   String OVERRIDE_DISPLAY_ID = "DisplayId";
   ResourceLocation EMPTY = new ResourceLocation("heralisz_tactical", "empty");

   static IMeleeWeapon of(ItemStack stack) {
      return stack.getItem() instanceof IMeleeWeapon item ? item : null;
   }

   @Override
   default ResourceLocation getId(ItemStack stack) {
      CompoundTag nbt = stack.getOrCreateTag();
      if (nbt.contains("MeleeWeaponId", 8)) {
         ResourceLocation rl = ResourceLocation.tryParse(nbt.getString("MeleeWeaponId"));
         return Objects.requireNonNullElse(rl, EMPTY);
      } else {
         return EMPTY;
      }
   }

   @Override
   default ResourceLocation getDisplayId(ItemStack stack) {
      CompoundTag nbt = stack.getOrCreateTag();
      if (nbt.contains("DisplayId", 8)) {
         ResourceLocation rl = ResourceLocation.tryParse(nbt.getString("DisplayId"));
         return Objects.requireNonNullElse(rl, EMPTY);
      } else {
         return this.getId(stack);
      }
   }

   @Override
   default void setId(ItemStack stack, ResourceLocation id) {
      stack.getOrCreateTag().putString("MeleeWeaponId", id.toString());
   }

   @Override
   default boolean shouldBlockAttack() {
      return true;
   }

   @Override
   default boolean shouldBlockUse() {
      return true;
   }

   default int getAttackDelay(Player attacker, ItemStack stack, MeleeAction action) {
      return this.getAttackDelay(attacker, stack, action, 0);
   }

   default int getAttackDelay(Player attacker, ItemStack stack, MeleeAction action, int cnt) {
      return 0;
   }

   @Nullable
   default CombatData.MeleeMovement getAttackMovement(Player entity, ItemStack stack, MeleeAction action) {
      return null;
   }

   default boolean canAttack(Player attacker, ItemStack stack, MeleeAction action) {
      return true;
   }

   default Optional<MeleeWeaponIndex<?>> getMeleeIndex(ItemStack stack) {
      return HeraliszTacticalAPI.getMeleeIndex(stack);
   }

   List<Entity> collectTargets(Player var1, ItemStack var2, MeleeAction var3, Vec3 var4, Vec3 var5);

   default void attack(Player attacker, ItemStack stack, MeleeAction action, List<Entity> targets) {
      this.attack(attacker, stack, action, targets, 0);
   }

   void attack(Player var1, ItemStack var2, MeleeAction var3, List<Entity> var4, int var5);

   @Deprecated
   default void attack(Player attacker, ItemStack stack, MeleeAction action, Vec3 origin, Vec3 direction) {
   }

   default AttackResult performAttack(Player attacker, Entity target, ItemStack stack, float base, float knockback) {
      if (!ForgeHooks.onPlayerAttackTarget(attacker, target)) {
         return AttackResult.MISS;
      }

      if (!target.isAttackable()) {
         return AttackResult.MISS;
      }

      if (target.skipAttackInteraction(attacker)) {
         return AttackResult.MISS;
      }

      float modifier;
      if (target instanceof LivingEntity living) {
         modifier = EnchantmentHelper.getDamageBonus(stack, living.getMobType());
      } else {
         modifier = EnchantmentHelper.getDamageBonus(stack, MobType.UNDEFINED);
      }

      boolean flag2 = attacker.fallDistance > 0.0F
         && !attacker.onGround()
         && !attacker.onClimbable()
         && !attacker.isInWater()
         && !attacker.hasEffect(MobEffects.BLINDNESS)
         && !attacker.isPassenger()
         && target instanceof LivingEntity;
      CriticalHitEvent hitResult = ForgeHooks.getCriticalHit(attacker, target, flag2, flag2 ? 1.5F : 1.0F);
      if (hitResult != null) {
         base *= hitResult.getDamageModifier();
         flag2 = true;
      }

      int j = EnchantmentHelper.getFireAspect(attacker);
      if (target instanceof LivingEntity living && j > 0) {
         living.setSecondsOnFire(j * 4);
      }

      if (target.invulnerableTime < (Integer)CommonConfig.MELEE_IGNORE_INVULNERABLE_TICK_THRESHOLD.get()) {
         target.invulnerableTime = 0;
      }

      boolean result = target.hurt(attacker.damageSources().playerAttack(attacker), base + modifier);
      if (!result) {
         return AttackResult.MISS;
      }

      if (target instanceof LivingEntity living) {
         living.knockback(knockback, Mth.sin(attacker.getYRot() * (float) (Math.PI / 180.0)), -Mth.cos(attacker.getYRot() * (float) (Math.PI / 180.0)));
         EnchantmentHelper.doPostHurtEffects(living, attacker);
      }

      EnchantmentHelper.doPostDamageEffects(attacker, target);
      if (flag2) {
         attacker.crit(target);
      }

      boolean killed = target instanceof LivingEntity living && living.isDeadOrDying();
      if (killed) {
         return flag2 ? AttackResult.CRIT_KILL : AttackResult.KILL;
      } else {
         return flag2 ? AttackResult.CRIT : AttackResult.HIT;
      }
   }

   static void playMeleeSound(Player entity, ResourceLocation id, String key, float volume, float pitch) {
      playMeleeSound(entity, id, key, volume, pitch, false);
   }

   static void playMeleeSoundToAttacker(Player entity, ResourceLocation id, String key, float volume, float pitch) {
      if (entity instanceof ServerPlayer player) {
         NetworkHandler.sendToClientPlayer(new SCustomSound(SCustomSound.SoundType.MELEE, id, key, entity.position(), volume, pitch), player);
      }
   }

   static void playMeleeSound(Player entity, ResourceLocation id, String key, float volume, float pitch, boolean exceptSelf) {
      SCustomSound packet = new SCustomSound(SCustomSound.SoundType.MELEE, id, key, entity.position(), volume, pitch);
      ServerPlayer p;
      if (exceptSelf && entity instanceof ServerPlayer player) {
         p = player;
      } else {
         p = null;
      }

      NetworkHandler.CHANNEL
         .send(PacketDistributor.NEAR.with(() -> new TargetPoint(p, entity.getX(), entity.getY(), entity.getZ(), 64.0, entity.level().dimension())), packet);
   }

   default boolean canSprintingAttack() {
      return true;
   }

   @Override
   default boolean isSame(ItemStack i, ItemStack j) {
      IMeleeWeapon w1 = of(i);
      IMeleeWeapon w2 = of(j);
      if (w1 != null && w2 != null) {
         return w1.getId(i).equals(w2.getId(j));
      } else {
         return !i.isEmpty() && !j.isEmpty() ? false : i.isEmpty() && j.isEmpty();
      }
   }
}
