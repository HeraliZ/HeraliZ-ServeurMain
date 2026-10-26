package com.mrcrayfish.backpacked.core;

import com.mrcrayfish.framework.api.registry.RegistryContainer;
import com.mrcrayfish.framework.api.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

@RegistryContainer
public class ModSounds {
   public static final RegistryEntry<SoundEvent> ITEM_BACKPACK_PLACE = RegistryEntry.soundEvent(
      new ResourceLocation("backpacked", "item.backpack.place"), id -> () -> SoundEvent.createVariableRangeEvent(id)
   );
}
