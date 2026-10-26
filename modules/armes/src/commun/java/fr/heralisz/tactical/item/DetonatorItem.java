package fr.heralisz.tactical.item;

import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class DetonatorItem extends Item {
   public DetonatorItem() {
      super(new Properties().stacksTo(1));
   }

   public void recordEntity(Entity entity, ItemStack detonatorStack) {
      UUID entityId = entity.getUUID();
      detonatorStack.getOrCreateTag().putUUID("linked_entity", entityId);
   }

   public UUID getLinkedEntityId(ItemStack detonatorStack) {
      return detonatorStack.hasTag() && detonatorStack.getTag().hasUUID("linked_entity") ? detonatorStack.getTag().getUUID("linked_entity") : null;
   }

   public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
      // commun (client) : l'original renvoyait toujours « pass » côté client (monde client : pas de détonation, pas de
      // message) ; la mise à feu du C4 et les messages : DetonatorItemServeur.use (code d'origine)
      return InteractionResultHolder.pass(pPlayer.getItemInHand(pUsedHand));
   }
}
