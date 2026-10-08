package com.mrcrayfish.backpacked.mixin.common;

import com.mrcrayfish.backpacked.event.BackpackedEvents;
import com.mrcrayfish.backpacked.event.block.InteractedWithBlock;
import com.mrcrayfish.backpacked.event.block.MinedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {
   @Shadow
   @Final
   protected ServerPlayer player;
   @Unique
   private BlockState backpacked$capturedMinedBlock;
   @Unique
   private ItemStack backpacked$capturedMinedItem;
   @Unique
   private CompoundTag backpacked$capturedMinedTag;
   @Unique
   private BlockState backpacked$capturedUseState;
   @Unique
   private ItemStack backpacked$capturedUseItem;
   @Unique
   private CompoundTag backpacked$capturedUseTag;

   @Inject(
      method = "destroyBlock",
      at = @At(
         value = "INVOKE_ASSIGN",
         target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
         ordinal = 0,
         shift = Shift.BY,
         by = 1
      ),
      locals = LocalCapture.CAPTURE_FAILHARD
   )
   private void backpacked$OnBlockMined(BlockPos pos, CallbackInfoReturnable<Boolean> cir, BlockState state) {
      this.backpacked$capturedMinedBlock = state;
      this.backpacked$capturedMinedItem = this.player.getMainHandItem();
      if (((MinedBlock.CaptureTag)BackpackedEvents.MINED_BLOCK_CAPTURE_TAG.post())
         .handle(this.backpacked$capturedMinedBlock, this.backpacked$capturedMinedItem, this.player)) {
         BlockEntity entity = this.player.level().getBlockEntity(pos);
         if (entity != null) {
            this.backpacked$capturedMinedTag = entity.saveWithFullMetadata();
         }
      }
   }

   @Inject(
      method = "destroyAndAck",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/level/ServerPlayerGameMode;debugLogging(Lnet/minecraft/core/BlockPos;ZILjava/lang/String;)V",
         ordinal = 0
      )
   )
   private void backpacked$AfterSuccessfulDestroy(BlockPos pos, int action, String message, CallbackInfo ci) {
      if (this.backpacked$capturedMinedBlock != null && this.backpacked$capturedMinedItem != null) {
         ((MinedBlock)BackpackedEvents.MINED_BLOCK.post())
            .handle(this.backpacked$capturedMinedBlock, this.backpacked$capturedMinedItem, this.backpacked$capturedMinedTag, this.player);
      }
   }

   @Inject(method = "destroyAndAck", at = @At("TAIL"))
   private void backpacked$DestroyTail(BlockPos pos, int action, String message, CallbackInfo ci) {
      this.backpacked$capturedMinedBlock = null;
      this.backpacked$capturedMinedItem = null;
      this.backpacked$capturedMinedTag = null;
   }

   @Inject(method = "useItemOn", at = @At("HEAD"))
   private void backpacked$OnUse(
      ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult result, CallbackInfoReturnable<InteractionResult> cir
   ) {
      this.backpacked$capturedUseState = level.getBlockState(result.getBlockPos());
      this.backpacked$capturedUseItem = stack.copy();
      if (((InteractedWithBlock.CaptureTag)BackpackedEvents.INTERACTED_WITH_BLOCK_CAPTURE_TAG.post())
         .handle(this.backpacked$capturedUseState, this.backpacked$capturedUseItem, player)) {
         BlockEntity entity = level.getBlockEntity(result.getBlockPos());
         if (entity != null) {
            this.backpacked$capturedUseTag = entity.saveWithFullMetadata();
         }
      }
   }

   @Inject(
      method = "useItemOn",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/advancements/critereon/ItemUsedOnLocationTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"
      )
   )
   private void backpacked$BeforeTriggers(
      ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult result, CallbackInfoReturnable<InteractionResult> cir
   ) {
      if (this.backpacked$capturedUseState != null && this.backpacked$capturedUseItem != null) {
         ((InteractedWithBlock)BackpackedEvents.INTERACTED_WITH_BLOCK.post())
            .handle(this.backpacked$capturedUseState, this.backpacked$capturedUseItem, this.backpacked$capturedUseTag, player);
         this.backpacked$capturedUseState = null;
         this.backpacked$capturedUseItem = null;
         this.backpacked$capturedUseTag = null;
      }
   }
}
