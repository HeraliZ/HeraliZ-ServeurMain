package fr.heralisz.tactical.item;

import java.util.List;
import fr.heralisz.tactical.api.melee.MeleeAction;
import fr.heralisz.tactical.config.ServerConfig;
import fr.heralisz.tactical.init.ModEffects;
import fr.heralisz.tactical.item.throwable.flash.StunThrowableData;
import fr.heralisz.tactical.util.SightTraceUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link FlashShieldItem} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun (ModItems) dès que la partie serveur est dans le jar (objet unique, partagé par les
 * deux côtés avec -complet : le code d'origine garde ses tests isClientSide). Ne jamais faire « new FlashShieldItem(...) ».
 */
public class FlashShieldItemServeur extends FlashShieldItem {
   public FlashShieldItemServeur() {
      super();
   }

   @Override
   public void attack(Player attacker, ItemStack stack, MeleeAction action, List<Entity> targets, int cnt) {
      float base = (float)attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);

      for (Entity livingentity : targets) {
         boolean flag = !(livingentity instanceof ArmorStand armorStand && armorStand.isMarker());
         boolean inRange = livingentity.distanceToSqr(attacker) <= 6.25;
         if (livingentity != attacker && flag && inRange) {
            this.performAttack(attacker, livingentity, stack, base, 1.2F);
         }
      }
   }

   @Override
   public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity entity) {
      if (entity.getTicksUsingItem() >= this.getMaxUsingTick(stack) && !world.isClientSide()) {
         if (entity instanceof Player player) {
            player.getCooldowns().addCooldown(stack.getItem(), (Integer)ServerConfig.FLASH_SHIELD_COOLDOWN.get());
            player.addEffect(new MobEffectInstance((MobEffect)ModEffects.BLIND.get(), 45, 0, false, false));
            player.addEffect(new MobEffectInstance((MobEffect)ModEffects.DEAFENED.get(), 60, 0, false, false));
         }

         AABB aabb = entity.getBoundingBox().inflate(12.0);

         for (Entity target : world.getEntities(entity, aabb, EntitySelector.NO_SPECTATORS)) {
            if (target instanceof LivingEntity living) {
               calculateAndApplyEffect(entity, living, data);
            }
         }
      }

      return stack;
   }

   public static void calculateAndApplyEffect(Entity starter, LivingEntity target, StunThrowableData.StunData data) {
      Vec3 p = starter.position().add(0.0, 1.0, 0.0);
      Vec3 eyes = target.getEyePosition(1.0F);
      Vec3 d1 = p.subtract(eyes);
      Vec3 d2 = eyes.subtract(p);
      double distanceMax = data.getRadius();
      double distance = d1.length();
      if (!(distance > distanceMax)) {
         double a1 = Math.toDegrees(Math.acos(target.getViewVector(1.0F).dot(d1.normalize())));
         double a2 = Math.toDegrees(Math.acos(starter.getViewVector(1.0F).dot(d2.normalize())));
         double angleMax = data.getBlind().getMaxAngle();
         if (a1 > 0.0
            && a1 < angleMax
            && a2 > 0.0
            && a2 <= 90.0
            && SightTraceUtil.rayTraceOpaqueBlocks(starter, target.level(), eyes, p, false, false, false) == null) {
            int durationBlinded = data.calcBlindDuration(distance, a1);
            if (durationBlinded > 0) {
               target.addEffect(new MobEffectInstance((MobEffect)ModEffects.BLIND.get(), durationBlinded, 0, false, false, true));
            }
         }

         int durationDeafened = data.calcDeafenedDuration(distance);
         if (durationDeafened > 0) {
            target.addEffect(new MobEffectInstance((MobEffect)ModEffects.DEAFENED.get(), durationDeafened, 0, false, false, true));
         }
      }
   }
}
