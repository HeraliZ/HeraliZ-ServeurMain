package com.hbm_m.interfaces;

import com.hbm_m.multiblock.PartRole;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public interface IMultiblockPart {
   @Nullable
   BlockPos getControllerPos();

   void setControllerPos(BlockPos var1);

   void setPartRole(PartRole var1);

   PartRole getPartRole();

   void setAllowedClimbSides(Set<Direction> var1);

   Set<Direction> getAllowedClimbSides();

   default void setAllowedEnergySides(Set<Direction> sides) {
   }

   default Set<Direction> getAllowedEnergySides() {
      return EnumSet.allOf(Direction.class);
   }

   default void setAllowedFluidSides(Set<Direction> sides) {
   }

   default Set<Direction> getAllowedFluidSides() {
      return EnumSet.allOf(Direction.class);
   }
}
