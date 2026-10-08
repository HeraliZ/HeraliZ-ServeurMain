package com.mrcrayfish.backpacked.platform;

import com.mrcrayfish.backpacked.platform.services.IPlatformHelper;
import java.util.Optional;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ForgePlatformHelper implements IPlatformHelper {
   @Override
   public boolean testPredicate(Optional<BlockPredicate> optional, BlockState state, @Nullable CompoundTag tag) {
      if (optional.isEmpty()) {
         return true;
      } else {
         BlockPredicate predicate = optional.get();
         if (predicate.tag != null && !state.is(predicate.tag)) {
            return false;
         } else if (predicate.blocks != null && !predicate.blocks.contains(state.getBlock())) {
            return false;
         } else {
            return tag != null && predicate.nbt.matches(tag) ? false : predicate.properties.matches(state);
         }
      }
   }
}
