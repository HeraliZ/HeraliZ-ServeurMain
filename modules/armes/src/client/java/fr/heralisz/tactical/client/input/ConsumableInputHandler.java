package fr.heralisz.tactical.client.input;

import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.api.item.IConsumable;
import fr.heralisz.tactical.item.index.ConsumableIndex;
import fr.heralisz.tactical.network.NetworkHandler;
import fr.heralisz.tactical.network.message.CCancelToggleConsumableUse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent.Key;
import net.minecraftforge.client.event.InputEvent.MouseButton.Pre;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(value = Dist.CLIENT, modid = "heralisz_tactical")
public class ConsumableInputHandler {
   @SubscribeEvent
   public static void onKeyInput(Key event) {
      if (event.getAction() == 1) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.options.keyUse.matches(event.getKey(), event.getScanCode())) {
            tryCancelToggleUse(mc);
         }
      }
   }

   @SubscribeEvent
   public static void onMouseInput(Pre event) {
      if (event.getAction() == 1) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.options.keyUse.matchesMouse(event.getButton()) && tryCancelToggleUse(mc)) {
            event.setCanceled(true);
         }
      }
   }

   private static boolean tryCancelToggleUse(Minecraft mc) {
      LocalPlayer player = mc.player;
      if (player != null && !player.isSpectator() && player.isUsingItem()) {
         ItemStack useItem = player.getUseItem();
         if (!(useItem.getItem() instanceof IConsumable)) {
            return false;
         }

         ConsumableIndex index = HeraliszTacticalAPI.getConsumableIndex(useItem).orElse(null);
         if (index != null && index.getData().isToggleUse()) {
            ItemStack mainHandItem = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (mainHandItem != useItem) {
               return false;
            }

            player.stopUsingItem();
            NetworkHandler.sendToServer(new CCancelToggleConsumableUse());

            while (mc.options.keyUse.consumeClick()) {
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}
