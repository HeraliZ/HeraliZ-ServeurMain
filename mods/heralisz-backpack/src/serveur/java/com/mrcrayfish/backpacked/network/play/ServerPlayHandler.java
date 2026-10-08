package com.mrcrayfish.backpacked.network.play;

import com.mrcrayfish.backpacked.Config;
import com.mrcrayfish.backpacked.common.WanderingTraderEvents;
import com.mrcrayfish.backpacked.common.backpack.Backpack;
import com.mrcrayfish.backpacked.common.backpack.BackpackManager;
import com.mrcrayfish.backpacked.common.backpack.ModelProperty;
import com.mrcrayfish.backpacked.data.unlock.UnlockManager;
import com.mrcrayfish.backpacked.item.BackpackItem;
import com.mrcrayfish.backpacked.item.BackpackItem_expand1;
import com.mrcrayfish.backpacked.item.BackpackItem_expand2;
import com.mrcrayfish.backpacked.item.Backpack_Mountain;
import com.mrcrayfish.backpacked.network.Network;
import com.mrcrayfish.backpacked.network.message.MessageBackpackCosmetics;
import com.mrcrayfish.backpacked.network.message.MessageEntityBackpack;
import com.mrcrayfish.backpacked.network.message.MessageOpenBackpack;
import com.mrcrayfish.backpacked.network.message.MessageOpenCustomisation;
import com.mrcrayfish.backpacked.network.message.MessageRequestCustomisation;
import com.mrcrayfish.backpacked.platform.Services;
import com.mrcrayfish.backpacked.util.PickpocketUtil;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;

public class ServerPlayHandler {
   public static void handleCustomiseBackpack(MessageBackpackCosmetics message, ServerPlayer player) {
      if (true) {
         return; // HeralisZ : l'apparence des sacs n'est pas modifiable par les joueurs
      }

      ItemStack stack = Services.BACKPACK.getBackpackStack(player);
      if (!stack.isEmpty()) {
         ResourceLocation id = message.getBackpackId();
         Backpack backpack = BackpackManager.instance().getBackpack(id);
         if (backpack == null) {
            return;
         }

         if (!backpack.isUnlocked(player) && !(Boolean)Config.SERVER.backpack.unlockAllCosmetics.get()) {
            return;
         }

         CompoundTag tag = stack.getOrCreateTag();
         tag.putString("BackpackModel", id.toString());
         tag.putBoolean(ModelProperty.SHOW_GLINT.getTagName(), message.isShowGlint());
         tag.putBoolean(ModelProperty.SHOW_WITH_ELYTRA.getTagName(), message.isShowWithElytra());
         tag.putBoolean(ModelProperty.SHOW_EFFECTS.getTagName(), message.isShowEffects());
      }
   }

   public static void handleOpenBackpack(MessageOpenBackpack message, ServerPlayer player) {
      ItemStack stack = Services.BACKPACK.getBackpackStack(player);
      if (stack.getItem() instanceof BackpackItem) {
         BackpackItem.openBackpack(player, player);
      } else if (stack.getItem() instanceof BackpackItem_expand1) {
         BackpackItem_expand1.openBackpack(player, player);
      } else if (stack.getItem() instanceof BackpackItem_expand2) {
         BackpackItem_expand2.openBackpack(player, player);
      } else if (stack.getItem() instanceof Backpack_Mountain) {
         Backpack_Mountain.openBackpack(player, player);
      }
   }

   public static void handleEntityBackpack(MessageEntityBackpack message, ServerPlayer player) {
      if (player.level().getEntity(message.getEntityId()) instanceof LivingEntity otherEntity) {
         if (!(otherEntity instanceof ServerPlayer) || (Boolean)Config.SERVER.pickpocketing.enabled.get()) {
            if (PickpocketUtil.canSeeBackpack(otherEntity, player)) {
               if (otherEntity instanceof ServerPlayer otherPlayer) {
                  if (BackpackItem.openBackpack(otherPlayer, player)) {
                     otherPlayer.displayClientMessage(Component.translatable("message.backpacked.player_opened"), true);
                     player.level()
                        .playSound(
                           player,
                           otherPlayer.getX(),
                           otherPlayer.getY() + 1.0,
                           otherPlayer.getZ(),
                           SoundEvents.ARMOR_EQUIP_LEATHER,
                           SoundSource.PLAYERS,
                           0.75F,
                           1.0F
                        );
                  } else if (BackpackItem_expand1.openBackpack(otherPlayer, player)) {
                     otherPlayer.displayClientMessage(Component.translatable("message.backpacked.player_opened"), true);
                     player.level()
                        .playSound(
                           player,
                           otherPlayer.getX(),
                           otherPlayer.getY() + 1.0,
                           otherPlayer.getZ(),
                           SoundEvents.ARMOR_EQUIP_LEATHER,
                           SoundSource.PLAYERS,
                           0.75F,
                           1.0F
                        );
                  } else if (BackpackItem_expand2.openBackpack(otherPlayer, player)) {
                     otherPlayer.displayClientMessage(Component.translatable("message.backpacked.player_opened"), true);
                     player.level()
                        .playSound(
                           player,
                           otherPlayer.getX(),
                           otherPlayer.getY() + 1.0,
                           otherPlayer.getZ(),
                           SoundEvents.ARMOR_EQUIP_LEATHER,
                           SoundSource.PLAYERS,
                           0.75F,
                           1.0F
                        );
                  }
               } else if (otherEntity instanceof WanderingTrader trader) {
                  WanderingTraderEvents.openBackpack(trader, player);
               }
            }
         }
      }
   }

   public static void handleRequestCustomisation(MessageRequestCustomisation message, ServerPlayer player) {
      if (true) {
         return; // HeralisZ : l'écran de personnalisation n'est plus accessible
      }

      if (!(Boolean)Config.SERVER.backpack.disableCustomisation.get()) {
         if (!Services.BACKPACK.getBackpackStack(player).isEmpty()) {
            UnlockManager.getTracker(player)
               .ifPresent(
                  unlockTracker -> {
                     Map<ResourceLocation, Component> map = new HashMap<>();

                     for (Backpack backpack : BackpackManager.instance().getBackpacks()) {
                        if (!unlockTracker.isUnlocked(backpack.getId())) {
                           unlockTracker.getProgressTracker(backpack.getId())
                              .ifPresent(progressTracker -> map.put(backpack.getId(), progressTracker.getDisplayComponent()));
                        }
                     }

                     Network.getPlay().sendToPlayer(() -> player, new MessageOpenCustomisation(map));
                  }
               );
         }
      }
   }
}
