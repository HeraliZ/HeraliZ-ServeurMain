package com.mrcrayfish.backpacked.platform.services;

import java.util.Optional;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public interface IPlatformHelper {
   boolean testPredicate(Optional<BlockPredicate> var1, BlockState var2, @Nullable CompoundTag var3);
}
