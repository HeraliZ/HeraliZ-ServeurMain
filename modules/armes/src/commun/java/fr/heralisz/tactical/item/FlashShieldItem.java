package fr.heralisz.tactical.item;

import fr.heraliz.tactique.pont.Crochet;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.tacz.guns.api.item.IAnimationItem;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.ParametersAreNonnullByDefault;
import fr.heralisz.tactical.api.collision.ConeFilter;
import fr.heralisz.tactical.api.item.IMeleeWeapon;
import fr.heralisz.tactical.api.melee.MeleeAction;
import fr.heralisz.tactical.capability.CombatPropertiesProvider;
import fr.heralisz.tactical.capability.CustomItemCoolDownsProvider;
import fr.heralisz.tactical.config.ServerConfig;
import fr.heralisz.tactical.item.throwable.flash.StunThrowableData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;

public class FlashShieldItem extends Item implements IMeleeWeapon, IAnimationItem {
   private final Multimap<Attribute, AttributeModifier> defaultModifiers;
   public static StunThrowableData.StunData data = new StunThrowableData.StunData();

   public FlashShieldItem() {
      super(new Properties().stacksTo(1).durability(350));
      Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
      builder.put(Attributes.MOVEMENT_SPEED, new AttributeModifier("Shield modifier", -0.25, Operation.MULTIPLY_BASE));
      this.defaultModifiers = builder.build();
   }

   public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
      return (Multimap<Attribute, AttributeModifier>)(slot == EquipmentSlot.MAINHAND ? this.defaultModifiers : ImmutableMultimap.of());
   }

   public int getMaxDamage(ItemStack stack) {
      return (Integer)ServerConfig.FLASH_SHIELD_MAX_DURABILITY.get();
   }

   /** Fusion (heraliz_tactique) : rendu en main (code client d'origine), branché par fr.heralisz.tactical.client.PartieClient. */
   public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

   public void initializeClient(Consumer<IClientItemExtensions> consumer) {
      RENDU_CLIENT.accept(consumer);
   }

   @Override
   public int getDrawTime(ItemStack stack) {
      return 10;
   }

   @Override
   public void attack(Player attacker, ItemStack stack, MeleeAction action, List<Entity> targets, int cnt) {
      // commun : neutre ; coups du bouclier : FlashShieldItemServeur.attack (code d'origine)
   }

   @Override
   public List<Entity> collectTargets(Player attacker, ItemStack stack, MeleeAction action, Vec3 origin, Vec3 direction) {
      ConeFilter filter = new ConeFilter(2.5, 105.0);
      return filter.filterTargets(attacker, origin, direction);
   }

   @Override
   public boolean canSprintingAttack() {
      return false;
   }

   @Override
   public int getAttackDelay(Player attacker, ItemStack stack, MeleeAction action, int cnt) {
      return 5;
   }

   @Override
   public boolean canAttack(Player attacker, ItemStack stack, MeleeAction action) {
      boolean isDisabled = attacker.getCapability(CustomItemCoolDownsProvider.CAPABILITY)
         .map(cap -> cap.isOnCooldown(new ResourceLocation("shield_disabled")))
         .orElse(false);
      return action == MeleeAction.LEFT && !isDisabled;
   }

   @Override
   public boolean shouldBlockUse() {
      return false;
   }

   @Override
   public int getMaxUsingTick(ItemStack stack) {
      return 10;
   }

   @ParametersAreNonnullByDefault
   public int getUseDuration(ItemStack pStack) {
      return 10;
   }

   @ParametersAreNonnullByDefault
   @NotNull
   public InteractionResultHolder<ItemStack> use(Level pLevel, Player player, InteractionHand pUsedHand) {
      if (pUsedHand == InteractionHand.OFF_HAND) {
         return InteractionResultHolder.fail(player.getItemInHand(pUsedHand));
      } else {
         boolean coolDown = player.getCapability(CombatPropertiesProvider.CAPABILITY).map(cap -> cap.getCoolDownTick() > 0).orElse(false);
         boolean isDisabled = player.getCapability(CustomItemCoolDownsProvider.CAPABILITY)
            .map(cap -> cap.isOnCooldown(new ResourceLocation("shield_disabled")))
            .orElse(false);
         if (!coolDown && !isDisabled) {
            ItemStack stack = player.getItemInHand(pUsedHand);
            player.startUsingItem(pUsedHand);
            return InteractionResultHolder.sidedSuccess(stack, pLevel.isClientSide());
         } else {
            return InteractionResultHolder.fail(player.getItemInHand(pUsedHand));
         }
      }
   }

   @ParametersAreNonnullByDefault
   public void onUseTick(Level pLevel, LivingEntity pLivingEntity, ItemStack pStack, int pRemainingUseDuration) {
   }

   public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity entity) {
      // commun (client) : l'original ne faisait rien côté client ; flash, aveuglement, recharge : FlashShieldItemServeur
      return stack;
   }

   public boolean useOnRelease(@NotNull ItemStack pStack) {
      return false;
   }

   @Override
   public boolean isSame(ItemStack stack1, ItemStack stack2) {
      return ItemStack.isSameItem(stack1, stack2);
   }
}
