package com.hbm_m.compat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;
/** HeralisZ : pas de compat Create. */
public final class ContraptionDoorState {
   public static boolean isContraptionWorld(Level level) { return false; }
   public static VoxelShape getShape(Level level, BlockPos pos) { return null; }
   public static BlockPos getControllerForPart(Level level, BlockPos partPos) { return null; }
}
