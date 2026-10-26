package com.mrcrayfish.backpacked.mixin.common;

import com.mrcrayfish.backpacked.event.BackpackedEvents;
import com.mrcrayfish.backpacked.event.entity.BredAnimal;
import net.minecraft.advancements.critereon.BredAnimalsTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BredAnimalsTrigger.class)
public class BredAnimalsTriggerMixin {
   @Inject(method = "trigger", at = @At("HEAD"))
   public void backpacked$OnPlayerBreedTurtle(ServerPlayer player, Animal animal, Animal partner, AgeableMob ageableEntity, CallbackInfo ci) {
      ((BredAnimal)BackpackedEvents.BRED_ANIMAL.post()).handle(animal, partner, player);
   }
}
