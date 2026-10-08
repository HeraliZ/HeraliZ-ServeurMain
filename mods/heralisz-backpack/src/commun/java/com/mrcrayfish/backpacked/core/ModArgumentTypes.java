package com.mrcrayfish.backpacked.core;

import com.mrcrayfish.backpacked.common.command.arguments.BackpackArgument;
import com.mrcrayfish.framework.api.registry.RegistryContainer;
import com.mrcrayfish.framework.api.registry.RegistryEntry;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.resources.ResourceLocation;

@RegistryContainer
public class ModArgumentTypes {
   public static final RegistryEntry<SingletonArgumentInfo<BackpackArgument>> BACKPACK = RegistryEntry.commandArgumentType(
      new ResourceLocation("backpacked", "backpack"), BackpackArgument.class, () -> SingletonArgumentInfo.contextFree(BackpackArgument::backpacks)
   );
}
