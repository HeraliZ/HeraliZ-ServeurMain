package com.hbm_m.interfaces;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.Direction;

public interface IMultiblockSidedIO {
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

   default void setAllowedFluidSidesFromMultiblockStructure(Set<Direction> worldSides) {
      this.setAllowedFluidSides(worldSides);
   }
}
