package com.hbm_m.blockentity.machines;

import com.hbm_m.block.UniversalMachinePartBlock;
import com.hbm_m.block.machines.TransitionSealBlock;
import com.hbm_m.blockentity.ModBlockEntities;
import com.hbm_m.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class TransitionSealBlockEntity extends BlockEntity {
   public static final float DURATION_TICKS = 480.0F;
   private static final int RING_HEIGHT = 23;
   private static final int RING_WIDTH = 13;
   public float animTicks = 0.0F;
   public boolean moving = false;
   public boolean opening = false;
   public boolean prevPowered = false;

   private void updatePartStates() {
      BlockState state = this.getBlockState();
      if (state.getBlock() instanceof TransitionSealBlock block) {
         Direction var8 = (Direction)state.getValue(TransitionSealBlock.FACING);
         boolean open = this.isOpen();

         for (BlockPos partPos : block.getStructureHelper().getAllPartPositions(this.worldPosition, var8)) {
            BlockState partState = this.level.getBlockState(partPos);
            if (partState.hasProperty(UniversalMachinePartBlock.PASSABLE) && (Boolean)partState.getValue(UniversalMachinePartBlock.PASSABLE) != open) {
               this.level.setBlock(partPos, (BlockState)partState.setValue(UniversalMachinePartBlock.PASSABLE, open), 2);
            }
         }
      }
   }

   public float getAnimationTime(float partialTicks) {
      float time = this.animTicks;
      if (this.moving) {
         time += this.opening ? partialTicks : -partialTicks;
      }

      return Math.max(0.0F, Math.min(480.0F, time)) / 20.0F;
   }

   public boolean isOpen() {
      return this.animTicks >= 480.0F;
   }

   public boolean isPowered() {
      if (this.level == null) {
         return false;
      }

      if (this.level.hasNeighborSignal(this.worldPosition)) {
         return true;
      }

      boolean zAxis = this.isZAxis();

      for (int dy = 0; dy <= 23; dy++) {
         for (int dz : new int[]{-13, 13}) {
            if (this.hasSignalAt(dy, dz, zAxis)) {
               return true;
            }
         }
      }

      for (int dz = -13; dz <= 13; dz++) {
         if (this.hasSignalAt(0, dz, zAxis) || this.hasSignalAt(23, dz, zAxis)) {
            return true;
         }
      }

      return false;
   }

   private boolean isZAxis() {
      if (this.level == null) {
         return false;
      }

      Direction facing = (Direction)this.level.getBlockState(this.worldPosition).getValue(TransitionSealBlock.FACING);
      return facing == Direction.EAST || facing == Direction.WEST;
   }

   private boolean hasSignalAt(int dy, int dz, boolean zAxis) {
      BlockPos pos = zAxis ? this.worldPosition.offset(0, dy, dz) : this.worldPosition.offset(dz, dy, 0);
      return this.level.hasNeighborSignal(pos);
   }

   public AABB getRenderBoundingBox() {
      return new AABB(this.worldPosition, this.worldPosition.offset(0, 24, 0)).inflate(13.0);
   }

   public TransitionSealBlockEntity(BlockPos pos, BlockState blockState) {
      super((BlockEntityType)ModBlockEntities.TRANSITION_SEAL_BE.get(), pos, blockState);
   }

   public static void tick(Level level, BlockPos pos, BlockState state, TransitionSealBlockEntity be) {
      be.updateEntity();
   }

   public void updateEntity() {
      if (this.level != null) {
         boolean powered = this.isPowered();
         float prev = this.animTicks;
         boolean prevOpen = prev >= 480.0F;
         boolean wasMoving = this.moving;
         if (powered && !this.prevPowered) {
            this.opening = !this.isOpen();
            this.moving = true;
         }

         this.prevPowered = powered;
         if (this.moving) {
            if (this.opening && this.animTicks < 480.0F) {
               this.animTicks = Math.min(480.0F, this.animTicks + 1.0F);
            } else if (!this.opening && this.animTicks > 0.0F) {
               this.animTicks = Math.max(0.0F, this.animTicks - 1.0F);
            }
         }

         this.moving = this.animTicks > 0.0F && this.animTicks < 480.0F;
         if (!this.level.isClientSide) {
            if (this.moving && !wasMoving) {
               this.level
                  .playSound(
                     null,
                     this.worldPosition,
                     this.opening ? (SoundEvent)ModSounds.TRANSITION_SEAL_OPEN.get() : (SoundEvent)ModSounds.TRANSITION_SEAL_CLOSE.get(),
                     SoundSource.BLOCKS,
                     2.0F,
                     1.0F
                  );
            }

            if (!this.moving && wasMoving) {
               this.level.playSound(null, this.worldPosition, (SoundEvent)ModSounds.METAL_STOP_1.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
            }

            if (this.animTicks != prev) {
               this.setChanged();
            }

            if (this.isOpen() != prevOpen) {
               this.updatePartStates();
               this.setChanged();
            }
         }
      }
   }

   protected void saveAdditional(CompoundTag tag) {
      super.saveAdditional(tag);
      tag.putFloat("animTicks", this.animTicks);
      tag.putBoolean("prevPowered", this.prevPowered);
   }

   public void load(CompoundTag tag) {
      super.load(tag);
      this.animTicks = tag.getFloat("animTicks");
      this.prevPowered = tag.getBoolean("prevPowered");
   }
}
