package com.mrcrayfish.backpacked.core;

import com.mrcrayfish.backpacked.inventory.container.BackpackContainerMenu;
import com.mrcrayfish.framework.api.registry.RegistryContainer;
import com.mrcrayfish.framework.api.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

@RegistryContainer
public class ModContainers {
   public static final RegistryEntry<MenuType<BackpackContainerMenu>> BACKPACK = RegistryEntry.menuTypeWithData(
      new ResourceLocation("backpacked", "backpack"),
      (windowId, playerInventory, data) -> new BackpackContainerMenu(windowId, playerInventory, data.readVarInt(), data.readVarInt(), data.readBoolean())
   );
}
