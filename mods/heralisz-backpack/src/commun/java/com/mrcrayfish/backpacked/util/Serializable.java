package com.mrcrayfish.backpacked.util;

import net.minecraft.nbt.CompoundTag;

public interface Serializable {
   CompoundTag serialize();

   void deserialize(CompoundTag var1);
}
