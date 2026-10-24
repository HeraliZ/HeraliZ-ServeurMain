package com.mrcrayfish.backpacked.client;

import com.mrcrayfish.backpacked.Config;
import com.mrcrayfish.backpacked.common.backpack.ModelProperty;
import com.mrcrayfish.backpacked.data.pickpocket.TraderPickpocketing;
import com.mrcrayfish.backpacked.network.Network;
import com.mrcrayfish.backpacked.network.message.MessageEntityBackpack;
import com.mrcrayfish.backpacked.network.message.MessageOpenBackpack;
import com.mrcrayfish.backpacked.platform.Services;
import com.mrcrayfish.backpacked.util.PickpocketUtil;
import com.mrcrayfish.framework.api.event.ClientConnectionEvents;
import com.mrcrayfish.framework.api.event.InputEvents;
import com.mrcrayfish.framework.api.event.TickEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ClientEvents {
   public static void init() {
      TickEvents.END_CLIENT.register(ClientEvents::onClientTickEnd);
      ClientConnectionEvents.LOGGING_IN.register(ClientEvents::onPlayerLogin);
      InputEvents.KEY.register(ClientEvents::onKeyInput);
      InputEvents.CLICK.register(ClientEvents::onInteraction);
   }

   private static void onPlayerLogin(LocalPlayer player, MultiPlayerGameMode gameMode, Connection connection) {
      Config.updateBannedItemsList();
   }

   public static void onKeyInput(int key, int scanCode, int action, int modifiers) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.screen == null) {
         LocalPlayer player = mc.player;
         if (Keys.KEY_BACKPACK.isDown() && Keys.KEY_BACKPACK.consumeClick() && !Services.BACKPACK.getBackpackStack(player).isEmpty()) {
            Network.getPlay().sendToServer(new MessageOpenBackpack());
         }
      }
   }

   private static void onClientTickEnd() {
      // HeraliZ : les effets des sacs (particules) sont retires ; la boucle sur les joueurs a 16 blocs, faite a chaque
      // tick, ne faisait plus rien (corps vide) : retiree.
   }

   public static boolean canShowBackpackEffects(ItemStack stack) {
      CompoundTag tag = stack.getOrCreateTag();
      return tag.contains(ModelProperty.SHOW_EFFECTS.getTagName(), 1) ? tag.getBoolean(ModelProperty.SHOW_EFFECTS.getTagName()) : true;
   }

   private static boolean onInteraction(boolean attack, boolean use, boolean pick, InteractionHand hand) {
      if (use && hand == InteractionHand.MAIN_HAND) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null && mc.gameMode != null) {
            double range = (Double)Config.SERVER.pickpocketing.maxReachDistance.get();
            List<LivingEntity> entities = new ArrayList<>();
            if ((Boolean)Config.SERVER.pickpocketing.enabled.get()) {
               entities.addAll(
                  mc.level
                     .getEntities(
                        EntityType.PLAYER,
                        mc.player.getBoundingBox().inflate(range),
                        player -> !Services.BACKPACK.getBackpackStack(player).isEmpty()
                           && !player.equals(mc.player)
                           && PickpocketUtil.canPickpocketEntity(player, mc.player)
                     )
               );
            }

            entities.addAll(
               mc.level
                  .getEntities(
                     EntityType.WANDERING_TRADER,
                     mc.player.getBoundingBox().inflate(mc.gameMode.getPickRange()),
                     entityx -> TraderPickpocketing.get(entityx).map(TraderPickpocketing::isBackpackEquipped).orElse(false)
                        && PickpocketUtil.canPickpocketEntity(entityx, mc.player, mc.gameMode.getPickRange())
                  )
            );
            if (entities.isEmpty()) {
               return false;
            }

            Vec3 start = mc.player.getEyePosition(1.0F);
            Vec3 end = mc.player.getViewVector(1.0F).scale(mc.gameMode.getPickRange()).add(start);
            double closestDistance = Double.MAX_VALUE;
            LivingEntity hitEntity = null;

            for (LivingEntity entity : entities) {
               AABB box = PickpocketUtil.getBackpackBox(entity, 1.0F);
               Optional<Vec3> optionalHitVec = box.clip(start, end);
               if (!optionalHitVec.isEmpty()) {
                  double distance = start.distanceTo(optionalHitVec.get());
                  if (distance < closestDistance) {
                     closestDistance = distance;
                     hitEntity = entity;
                  }
               }
            }

            if (hitEntity != null) {
               if (PickpocketUtil.canSeeBackpack(hitEntity, mc.player)) {
                  Network.getPlay().sendToServer(new MessageEntityBackpack(hitEntity.getId()));
                  mc.player.swing(hand);
               }

               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}
