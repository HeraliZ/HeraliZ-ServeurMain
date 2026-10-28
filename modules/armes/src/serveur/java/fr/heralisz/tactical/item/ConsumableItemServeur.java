package fr.heralisz.tactical.item;

import javax.annotation.ParametersAreNonnullByDefault;
import fr.heralisz.tactical.api.event.ConsumableUseEvent;
import fr.heralisz.tactical.capability.CustomItemCoolDownsProvider;
import fr.heralisz.tactical.item.consumable.ConsumableData;
import fr.heralisz.tactical.item.index.ConsumableIndex;
import fr.heralisz.tactical.network.NetworkHandler;
import fr.heralisz.tactical.network.message.SStopConsumableUse;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Partie SERVEUR de {@link ConsumableItem} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun (ModItems.CONSUMABLE_USINE) dès que la partie serveur est dans le jar ; soins, effets,
 * consommation et temps de recharge (code d'origine). Ne jamais faire « new ConsumableItem(...) » côté serveur.
 */
public class ConsumableItemServeur extends ConsumableItem {
   public ConsumableItemServeur() {
      super();
   }

   /**
    * Sortie de l'objet : le serveur apprend le changement de case au moins un tick après le joueur (le paquet de case part
    * au tick suivant, plus le ping), son compte de sortie finit donc après celui du joueur. Un clic juste à la fin de la
    * sortie était refusé ici alors que le joueur commençait le soin chez lui : barre pleine, rien soigné ni consommé
    * (trousse AI-2, 28/10). 3 ticks (0,15 s) de marge.
    */
   private static final int TOLERANCE_SORTIE = 3;

   @Override
   protected int toleranceSortie(Level level) {
      // jar « complet » (solo) : cette classe sert aussi côté joueur, où aucune marge n'est donnée
      return level.isClientSide() ? 0 : TOLERANCE_SORTIE;
   }

   @Override
   @ParametersAreNonnullByDefault
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
      InteractionResultHolder<ItemStack> r = super.use(level, player, usedHand);
      // refusé ici (sortie, temps de recharge, main gauche) : le joueur, qui a pu commencer chez lui, doit s'arrêter
      if (!level.isClientSide() && r.getResult() == InteractionResult.FAIL && player instanceof ServerPlayer sp) {
         NetworkHandler.sendToClientPlayer(new SStopConsumableUse(), sp);
      }
      return r;
   }

   @Override
   @ParametersAreNonnullByDefault
   public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
      return this.finishConsumableUse(stack, level, entity);
   }

   @ParametersAreNonnullByDefault
   public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
      this.getConsumableIndex(stack).ifPresent(index -> {
         if (index.getData().isToggleUse() && entity.getTicksUsingItem() >= index.getData().getUseDuration() && !level.isClientSide()) {
            this.finishConsumableUse(stack, level, entity);
            entity.stopUsingItem();
         }
      });
   }

   private ItemStack finishConsumableUse(ItemStack stack, Level level, LivingEntity entity) {
      this.getConsumableIndex(stack).ifPresent(index -> {
         if (!level.isClientSide()) {
            this.applyEffects(entity, stack, index);
            if (entity instanceof Player player) {
               player.awardStat(Stats.ITEM_USED.get(this));
               if (!player.getAbilities().instabuild) {
                  this.consumeAfterUse(stack, entity, index);
               }
            } else {
               this.consumeAfterUse(stack, entity, index);
            }
         }
      });
      return stack;
   }

   private void consumeAfterUse(ItemStack stack, LivingEntity entity, ConsumableIndex index) {
      ConsumableData data = index.getData();
      if (data.hasDurability()) {
         stack.hurtAndBreak(data.getDurabilityDamage(), entity, living -> living.broadcastBreakEvent(EquipmentSlot.MAINHAND));
      } else {
         stack.shrink(1);
      }
   }

   private void removeEffect(LivingEntity entity, ConsumableData.RemoveEffectSelector selector) {
      if (selector.isCategory()) {
         this.removeEffectsByCategory(entity, selector.getCategory());
      } else {
         MobEffect effect = (MobEffect)ForgeRegistries.MOB_EFFECTS.getValue(selector.getEffect());
         if (effect != null) {
            entity.removeEffect(effect);
         }
      }
   }

   private void removeEffectsByCategory(LivingEntity entity, MobEffectCategory category) {
      for (MobEffect effect : entity.getActiveEffects()
         .stream()
         .<MobEffect>map(MobEffectInstance::getEffect)
         .filter(effectx -> effectx.getCategory() == category)
         .toList()) {
         entity.removeEffect(effect);
      }
   }

   private void applyEffects(LivingEntity entity, ItemStack stack, ConsumableIndex index) {
      ConsumableData data = index.getData();
      if (data.getHeal() > 0.0F) {
         entity.heal(data.getHeal());
      }

      for (ConsumableData.RemoveEffectSelector selector : data.getRemoveEffects()) {
         this.removeEffect(entity, selector);
      }

      for (ConsumableData.EffectData effectData : data.getEffects()) {
         if (!(entity.getRandom().nextFloat() > effectData.getChance())) {
            MobEffectInstance effect = effectData.createInstance();
            if (effect != null) {
               entity.addEffect(effect);
            }
         }
      }

      if (entity instanceof Player player && (data.getFood() > 0 || data.getSaturation() > 0.0F)) {
         FoodData foodData = player.getFoodData();
         foodData.eat(data.getFood(), data.getSaturation());
      }

      MinecraftForge.EVENT_BUS.post(new ConsumableUseEvent(entity, stack.copy(), index.getId(), index));
      ResourceLocation cooldownId = data.getCooldownCategory();
      if (cooldownId != null && data.getCooldown() > 0) {
         entity.getCapability(CustomItemCoolDownsProvider.CAPABILITY).ifPresent(cap -> cap.addCooldown(cooldownId, data.getCooldown()));
      }
   }
}
