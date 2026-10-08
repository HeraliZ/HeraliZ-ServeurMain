package fr.heralisz.tactical.item;

import java.util.UUID;
import fr.heralisz.tactical.entity.GrenadeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Partie SERVEUR de {@link DetonatorItem} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun (ModItems) dès que la partie serveur est dans le jar (objet unique, partagé par les
 * deux côtés avec -complet : le code d'origine garde ses tests isClientSide). Ne jamais faire « new DetonatorItem(...) ».
 */
public class DetonatorItemServeur extends DetonatorItem {
   public DetonatorItemServeur() {
      super();
   }

   public boolean detonate(ItemStack detonatorStack, Entity detonator) {
      UUID linkedId = this.getLinkedEntityId(detonatorStack);
      if (linkedId == null) {
         return false;
      } else if (detonator.level() instanceof ServerLevel serverLevel && serverLevel.getEntity(linkedId) instanceof GrenadeEntity grenadeEntity) {
         grenadeEntity.onDeath(null);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
      ItemStack detonatorStack = pPlayer.getItemInHand(pUsedHand);
      if (this.detonate(detonatorStack, pPlayer)) {
         pPlayer.setItemInHand(pUsedHand, ItemStack.EMPTY);
         if (pPlayer instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("BOOM!").withStyle(ChatFormatting.RED), true);
         }

         return InteractionResultHolder.sidedSuccess(detonatorStack, pLevel.isClientSide());
      } else {
         if (pPlayer instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("- NO SIGNAL -").withStyle(ChatFormatting.RED), true);
         }

         return InteractionResultHolder.pass(detonatorStack);
      }
   }
}
