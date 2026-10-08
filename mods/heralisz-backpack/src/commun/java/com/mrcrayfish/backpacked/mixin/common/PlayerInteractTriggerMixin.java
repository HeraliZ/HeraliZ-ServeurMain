package com.mrcrayfish.backpacked.mixin.common;

import com.mrcrayfish.backpacked.event.BackpackedEvents;
import com.mrcrayfish.backpacked.event.BackpackedInteractAccess;
import com.mrcrayfish.backpacked.event.entity.InteractedWithEntity;
import java.util.List;
import net.minecraft.advancements.critereon.PlayerInteractTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInteractTrigger.class)
public class PlayerInteractTriggerMixin {
   @Inject(method = "trigger", at = @At("HEAD"))
   private void backpacked$OnTriggerHead(ServerPlayer player, ItemStack stack, Entity entity, CallbackInfo ci) {
      BackpackedInteractAccess access = (BackpackedInteractAccess)player;
      List<ResourceLocation> capturedIds = access.getBackpacked$CapturedInteractIds();
      if (!capturedIds.isEmpty()) {
         ((InteractedWithEntity)BackpackedEvents.INTERACTED_WITH_ENTITY.post()).handle(player, stack, entity, capturedIds);
         capturedIds.clear();
      }
   }
}
