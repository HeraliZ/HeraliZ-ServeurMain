package fr.heralisz.tactical.item;

import fr.heraliz.tactique.pont.Crochet;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.tacz.guns.api.item.IAnimationItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import fr.heralisz.tactical.api.collision.ITargetFilter;
import fr.heralisz.tactical.api.item.IMeleeWeapon;
import fr.heralisz.tactical.api.melee.MeleeAction;
import fr.heralisz.tactical.inventory.tooltip.MeleeTooltip;
import fr.heralisz.tactical.item.index.MeleeWeaponIndex;
import fr.heralisz.tactical.item.melee.CombatData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.NotNull;

public class MeleeItem extends Item implements IAnimationItem, IMeleeWeapon {
   public MeleeItem() {
      super(new Properties().stacksTo(1).setNoRepair());
   }

   public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
      return (Multimap<Attribute, AttributeModifier>)(slot == EquipmentSlot.MAINHAND
         ? this.getMeleeIndex(stack).map(MeleeWeaponIndex::getDefaultModifiers).orElse(ImmutableMultimap.of())
         : ImmutableMultimap.of());
   }

   /** Infobulle (19/10) : ni « Dans la main principale : … points d'attaque » ni vitesse ; nom, rareté et description seulement. */
   @Override
   public int getDefaultTooltipHideFlags(@NotNull ItemStack stack) {
      return ItemStack.TooltipPart.MODIFIERS.getMask();
   }

   @Override
   public boolean isSame(ItemStack stack1, ItemStack stack2) {
      return IMeleeWeapon.super.isSame(stack1, stack2);
   }

   /** Fusion (heraliz_tactique) : rendu en main (code client d'origine), branché par fr.heralisz.tactical.client.PartieClient. */
   public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

   public void initializeClient(Consumer<IClientItemExtensions> consumer) {
      RENDU_CLIENT.accept(consumer);
   }

   public boolean isEnchantable(@NotNull ItemStack pStack) {
      return true;
   }

   public int getEnchantmentValue(ItemStack stack) {
      return 5;
   }

   @NotNull
   public String getDescriptionId(@NotNull ItemStack stack) {
      return this.getMeleeIndex(stack).map(MeleeWeaponIndex::getDescriptionId).orElse(super.getDescriptionId(stack));
   }

   public Optional<TooltipComponent> getTooltipImage(ItemStack pStack) {
      return this.getMeleeIndex(pStack).isEmpty() ? Optional.empty() : Optional.of(new MeleeTooltip(pStack));
   }

   @Override
   public int getAttackCoolDown(ItemStack stack, MeleeAction action, int cnt) {
      return this.getMeleeIndex(stack)
         .map(index -> index.getData().getAttackInfo())
         .map(attackInfos -> attackInfos.getAttackInfo(action, cnt))
         .map(CombatData.MeleeAttackInfo::getCooldown)
         .orElse(0);
   }

   public int getMaxDamage(ItemStack stack) {
      return this.getMeleeIndex(stack).map(MeleeWeaponIndex::getMaxDurability).orElse(0);
   }

   public boolean isDamageable(ItemStack stack) {
      return this.getMaxDamage(stack) > 0;
   }

   @Override
   public int getDrawTime(ItemStack stack) {
      return this.getMeleeIndex(stack).map(index -> index.getData().getDrawTime()).orElse(0);
   }

   @Override
   public int getPutAwayTime(ItemStack stack) {
      return this.getMeleeIndex(stack).map(index -> index.getData().getPutAwayTime()).orElse(0);
   }

   @Override
   public int getAttackDelay(Player attacker, ItemStack stack, MeleeAction action, int cnt) {
      return this.getMeleeIndex(stack)
         .map(index -> index.getData().getAttackInfo())
         .map(attackInfos -> attackInfos.getAttackInfo(action, cnt))
         .map(CombatData.MeleeAttackInfo::getDelay)
         .orElse(0);
   }

   @Override
   public CombatData.MeleeMovement getAttackMovement(Player entity, ItemStack stack, MeleeAction action) {
      return this.getMeleeIndex(stack)
         .map(index -> index.getData().getAttackInfo())
         .map(attackInfos -> attackInfos.getAttackInfo(action))
         .map(CombatData.MeleeAttackInfo::getMovement)
         .orElse(null);
   }

   @Override
   public List<Entity> collectTargets(Player attacker, ItemStack stack, MeleeAction action, Vec3 origin, Vec3 direction) {
      List<Entity> entities = new ArrayList<>();
      this.getMeleeIndex(stack).ifPresent(index -> {
         CombatData combatData = index.getData().getAttackInfo();
         if (combatData != null) {
            CombatData.MeleeAttackInfo attackInfo = combatData.getAttackInfo(action);
            if (attackInfo != null) {
               ITargetFilter filter = attackInfo.getHitbox();
               entities.addAll(filter.filterTargets(attacker, origin, direction));
            }
         }
      });
      return entities;
   }

   @Override
   public void attack(Player attacker, ItemStack stack, MeleeAction action, List<Entity> targets, int combo) {
      // commun : neutre. Les coups (dégâts, usure, sons) sont donnés par le serveur : MeleeItemServeur.attack (code d'origine),
      // appelé par la capacité CombatProperties.postAttack à la réception du paquet CMeleeAttackRequest.
   }

   public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
      return enchantment.category == EnchantmentCategory.WEAPON;
   }

   public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
      return toolAction == ToolActions.SWORD_SWEEP;
   }
}
