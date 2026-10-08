package com.mrcrayfish.backpacked.mixin.common;

import com.mrcrayfish.backpacked.common.MovementType;
import com.mrcrayfish.backpacked.event.BackpackedEvents;
import com.mrcrayfish.backpacked.event.BackpackedInteractAccess;
import com.mrcrayfish.backpacked.event.entity.InteractedWithEntity;
import com.mrcrayfish.backpacked.event.entity.PlayerTravel;
import com.mrcrayfish.backpacked.inventory.BackpackInventory;
import com.mrcrayfish.backpacked.inventory.BackpackedInventoryAccess;
import com.mrcrayfish.backpacked.item.BackpackItem;
import com.mrcrayfish.backpacked.item.BackpackItem_expand1;
import com.mrcrayfish.backpacked.item.BackpackItem_expand2;
import com.mrcrayfish.backpacked.item.Backpack_Mountain;
import com.mrcrayfish.backpacked.platform.Services;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin implements BackpackedInventoryAccess {
   @Unique
   public BackpackInventory backpacked$Inventory = null;

   @Nullable
   @Override
   public BackpackInventory backpacked$GetBackpackInventory() {
      Player player = (Player)(Object)this;
      ItemStack stack = Services.BACKPACK.getBackpackStack(player);
      if (stack.isEmpty()) {
         this.backpacked$Inventory = null;
         return null;
      }

      Item item = stack.getItem();
      if (item instanceof BackpackItem backpackItem) {
         if (this.backpacked$Inventory == null
            || !this.backpacked$Inventory.getBackpackStack().equals(stack)
            || this.backpacked$Inventory.getContainerSize() != backpackItem.getRowCount() * backpackItem.getColumnCount()) {
            this.backpacked$Inventory = new BackpackInventory(backpackItem.getColumnCount(), backpackItem.getRowCount(), player, stack);
         }

         return this.backpacked$Inventory;
      } else if (item instanceof BackpackItem_expand1 backpackItem1) {
         if (this.backpacked$Inventory == null
            || !this.backpacked$Inventory.getBackpackStack().equals(stack)
            || this.backpacked$Inventory.getContainerSize() != backpackItem1.getRowCount() * backpackItem1.getColumnCount()) {
            this.backpacked$Inventory = new BackpackInventory(backpackItem1.getColumnCount(), backpackItem1.getRowCount(), player, stack);
         }

         return this.backpacked$Inventory;
      } else if (item instanceof BackpackItem_expand2 backpackItem2) {
         if (this.backpacked$Inventory == null || !this.backpacked$Inventory.getBackpackStack().equals(stack) || this.backpacked$Inventory.getContainerSize() != 54) {
            this.backpacked$Inventory = new BackpackInventory(backpackItem2.getColumnCount(), backpackItem2.getRowCount(), player, stack);
         }

         return this.backpacked$Inventory;
      } else if (!(item instanceof Backpack_Mountain backpackItem2)) {
         return null;
      } else {
         if (this.backpacked$Inventory == null || !this.backpacked$Inventory.getBackpackStack().equals(stack) || this.backpacked$Inventory.getContainerSize() != 54) {
            this.backpacked$Inventory = new BackpackInventory(backpackItem2.getColumnCount(), backpackItem2.getRowCount(), player, stack);
         }

         return this.backpacked$Inventory;
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   public void backpacked$TickHead(CallbackInfo ci) {
      if (this.backpacked$Inventory != null) {
         this.backpacked$Inventory.tick();
      }
   }

   @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
   public void backpacked$AddAdditionalSaveData(CompoundTag tag, CallbackInfo ci) {
      if (this.backpacked$Inventory != null) {
         this.backpacked$Inventory.saveItemsToStack();
      }
   }

   @Inject(method = "interactOn", at = @At("HEAD"))
   public void backpacked$InteractHead(Entity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
      Player player = (Player)(Object)this;
      if (player instanceof ServerPlayer serverPlayer) {
         BackpackedInteractAccess access = (BackpackedInteractAccess)serverPlayer;
         List<ResourceLocation> capturedIds = access.getBackpacked$CapturedInteractIds();
         capturedIds.clear();
         ItemStack stack = serverPlayer.getItemInHand(hand);
         ((InteractedWithEntity.Capture)BackpackedEvents.INTERACTED_WITH_ENTITY_CAPTURE.post()).handle(serverPlayer, stack, entity, capturedIds::add);
      }
   }

   @Unique
   private void backpacked$PlayerTravelEvent(double dx, double dy, double dz, MovementType type) {
      Player player = (Player)(Object)this;
      if (!player.level().isClientSide()) {
         double distanceSquared = dx * dx + dy * dy + dz * dz;
         ((PlayerTravel)BackpackedEvents.PLAYER_TRAVEL.post()).handle((ServerPlayer)player, distanceSquared, type);
      }
   }

   @Inject(
      method = "checkMovementStatistics",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/ResourceLocation;I)V", ordinal = 0)
   )
   private void backpacked$MovementSwim(double dx, double dy, double dz, CallbackInfo ci) {
      this.backpacked$PlayerTravelEvent(dx, dy, dz, MovementType.SWIM);
   }

   @Inject(
      method = "checkMovementStatistics",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/ResourceLocation;I)V", ordinal = 1)
   )
   private void backpacked$MovementWalkUnderwater(double dx, double dy, double dz, CallbackInfo ci) {
      this.backpacked$PlayerTravelEvent(dx, dy, dz, MovementType.WALK_UNDERWATER);
   }

   @Inject(
      method = "checkMovementStatistics",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/ResourceLocation;I)V", ordinal = 2)
   )
   private void backpacked$MovementWalkOnWater(double dx, double dy, double dz, CallbackInfo ci) {
      this.backpacked$PlayerTravelEvent(dx, 0.0, dz, MovementType.WALK_ON_WATER);
   }

   @Inject(
      method = "checkMovementStatistics",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/ResourceLocation;I)V", ordinal = 3)
   )
   private void backpacked$MovementClimb(double dx, double dy, double dz, CallbackInfo ci) {
      this.backpacked$PlayerTravelEvent(0.0, dy, 0.0, MovementType.CLIMB);
   }

   @Inject(
      method = "checkMovementStatistics",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/ResourceLocation;I)V", ordinal = 4)
   )
   private void backpacked$MovementSprint(double dx, double dy, double dz, CallbackInfo ci) {
      this.backpacked$PlayerTravelEvent(dx, 0.0, dz, MovementType.SPRINT);
   }

   @Inject(
      method = "checkMovementStatistics",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/ResourceLocation;I)V", ordinal = 5)
   )
   private void backpacked$MovementSneak(double dx, double dy, double dz, CallbackInfo ci) {
      this.backpacked$PlayerTravelEvent(dx, 0.0, dz, MovementType.SNEAK);
   }

   @Inject(
      method = "checkMovementStatistics",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/ResourceLocation;I)V", ordinal = 6)
   )
   private void backpacked$MovementWalk(double dx, double dy, double dz, CallbackInfo ci) {
      this.backpacked$PlayerTravelEvent(dx, 0.0, dz, MovementType.WALK);
   }

   @Inject(
      method = "checkMovementStatistics",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/ResourceLocation;I)V", ordinal = 7)
   )
   private void backpacked$MovementElytraFlying(double dx, double dy, double dz, CallbackInfo ci) {
      this.backpacked$PlayerTravelEvent(dx, dy, dz, MovementType.ELYTRA_FLY);
   }

   @Inject(
      method = "checkMovementStatistics",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/ResourceLocation;I)V", ordinal = 8)
   )
   private void backpacked$MovementFlying(double dx, double dy, double dz, CallbackInfo ci) {
      this.backpacked$PlayerTravelEvent(dx, 0.0, dz, MovementType.FLY);
   }

   @Inject(
      method = "checkMovementStatistics",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/ResourceLocation;I)V", ordinal = 8)
   )
   private void backpacked$MovementFall(double dx, double dy, double dz, CallbackInfo ci) {
      this.backpacked$PlayerTravelEvent(0.0, dy, 0.0, MovementType.FALL);
   }
}
