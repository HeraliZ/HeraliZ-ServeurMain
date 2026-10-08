package fr.heralisz.tactical.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.api.item.IConsumable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MinecraftMixin {
   @WrapWithCondition(
      method = "handleKeybinds",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V"
      )
   )
   private boolean heralisz$shouldReleaseUsingItem(MultiPlayerGameMode gameMode, Player player) {
      if (player != null && player.isUsingItem()) {
         ItemStack useItem = player.getUseItem();
         return !(useItem.getItem() instanceof IConsumable)
            ? true
            : HeraliszTacticalAPI.getConsumableIndex(useItem).map(index -> !index.getData().isToggleUse()).orElse(true);
      } else {
         return true;
      }
   }
}
