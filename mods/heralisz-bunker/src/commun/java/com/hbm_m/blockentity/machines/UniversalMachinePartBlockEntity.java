package com.hbm_m.blockentity.machines;

import com.hbm_m.blockentity.ModBlockEntities;
import com.hbm_m.interfaces.IMultiblockPart;
import com.hbm_m.multiblock.PartRole;
import com.hbm_m.platform.PlatformHooks;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** HeralisZ : partie de multibloc des portes (sans énergie ni fluides). */
public class UniversalMachinePartBlockEntity extends BlockEntity implements IMultiblockPart {
   private BlockPos controllerPos;
   private PartRole role = PartRole.DEFAULT;
   private Set<Direction> allowedClimbSides = EnumSet.noneOf(Direction.class);
   private Set<Direction> allowedEnergySides = EnumSet.noneOf(Direction.class);
   private Set<Direction> allowedFluidSides = EnumSet.noneOf(Direction.class);

   public UniversalMachinePartBlockEntity(BlockPos pPos, BlockState pBlockState) {
      super((BlockEntityType)ModBlockEntities.UNIVERSAL_MACHINE_PART_BE.get(), pPos, pBlockState);
   }

   @Override
   public synchronized void setControllerPos(BlockPos pos) {
      this.controllerPos = pos;
      this.setChanged();
      if (this.level != null && !this.level.isClientSide()) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
      }
   }

   @Override
   public void setPartRole(PartRole role) {
      if (this.role != role) {
         this.role = role;
         this.setChanged();
         if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
         }
      }
   }

   public static void tick(Level level, BlockPos pos, BlockState state, UniversalMachinePartBlockEntity be) {
   }

   @Override
   public BlockPos getControllerPos() {
      return this.controllerPos;
   }

   @Override
   public PartRole getPartRole() {
      return this.role;
   }

   @Override
   public void setAllowedClimbSides(Set<Direction> sides) {
      this.allowedClimbSides = EnumSet.copyOf(sides);
      this.setChanged();
      if (this.level != null && !this.level.isClientSide()) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
      }
   }

   @Override
   public Set<Direction> getAllowedClimbSides() {
      return this.allowedClimbSides;
   }

   @Override
   public void setAllowedEnergySides(Set<Direction> sides) {
      this.allowedEnergySides = EnumSet.copyOf(sides);
      this.setChanged();
      if (this.level != null && !this.level.isClientSide()) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
         this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
      }
   }

   @Override
   public Set<Direction> getAllowedEnergySides() {
      return this.allowedEnergySides;
   }

   @Override
   public void setAllowedFluidSides(Set<Direction> sides) {
      this.allowedFluidSides = EnumSet.copyOf(sides);
      this.setChanged();
      if (this.level != null && !this.level.isClientSide()) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
         this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
      }
   }

   @Override
   public Set<Direction> getAllowedFluidSides() {
      return this.allowedFluidSides;
   }

   protected void saveAdditional(CompoundTag pTag) {
      super.saveAdditional(pTag);
      if (this.controllerPos != null) {
         pTag.put("ControllerPos", NbtUtils.writeBlockPos(this.controllerPos));
      }

      pTag.putString("PartRole", this.role.name());
      if (!this.allowedClimbSides.isEmpty()) {
         int mask = 0;

         for (Direction dir : this.allowedClimbSides) {
            mask |= 1 << dir.get3DDataValue();
         }

         pTag.putInt("ClimbSides", mask);
      }

      if (!this.allowedEnergySides.isEmpty()) {
         int mask = 0;

         for (Direction dir : this.allowedEnergySides) {
            mask |= 1 << dir.get3DDataValue();
         }

         pTag.putInt("EnergySides", mask);
      }

      if (!this.allowedFluidSides.isEmpty()) {
         int mask = 0;

         for (Direction dir : this.allowedFluidSides) {
            mask |= 1 << dir.get3DDataValue();
         }

         pTag.putInt("FluidSides", mask);
      }
   }

   public void load(CompoundTag pTag) {
      super.load(pTag);
      if (pTag.contains("ControllerPos")) {
         this.controllerPos = NbtUtils.readBlockPos(pTag.getCompound("ControllerPos"));
      }

      if (pTag.contains("PartRole")) {
         try {
            this.role = PartRole.valueOf(pTag.getString("PartRole"));
         } catch (IllegalArgumentException e) {
            this.role = PartRole.DEFAULT;
         }
      }

      if (pTag.contains("ClimbSides")) {
         int mask = pTag.getInt("ClimbSides");
         this.allowedClimbSides.clear();

         for (Direction dir : Direction.values()) {
            if ((mask & 1 << dir.get3DDataValue()) != 0) {
               this.allowedClimbSides.add(dir);
            }
         }
      }

      if (pTag.contains("EnergySides")) {
         int mask = pTag.getInt("EnergySides");
         this.allowedEnergySides.clear();

         for (Direction dir : Direction.values()) {
            if ((mask & 1 << dir.get3DDataValue()) != 0) {
               this.allowedEnergySides.add(dir);
            }
         }
      }

      if (pTag.contains("FluidSides")) {
         int mask = pTag.getInt("FluidSides");
         this.allowedFluidSides.clear();

         for (Direction dir : Direction.values()) {
            if ((mask & 1 << dir.get3DDataValue()) != 0) {
               this.allowedFluidSides.add(dir);
            }
         }
      }
   }

   @Nullable
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public CompoundTag getUpdateTag() {
      return this.saveWithoutMetadata();
   }

   public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
      CompoundTag tag = PlatformHooks.getItemTag(pkt);
      if (tag != null) {
         this.handleUpdateTag(tag);
         if (this.level != null && this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
         }
      }
   }
}
