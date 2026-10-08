package com.mrcrayfish.backpacked.core;

import com.mrcrayfish.backpacked.platform.Services;
import com.mrcrayfish.framework.Registration;
import com.mrcrayfish.framework.api.registry.RegistryContainer;
import com.mrcrayfish.framework.api.registry.RegistryEntry;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ItemLike;

@RegistryContainer
public class ModCreativeTabs {
   public static final RegistryEntry<CreativeModeTab> MAIN = RegistryEntry.creativeModeTab(
      new ResourceLocation("backpacked", "creative_tab"),
      builder -> {
         builder.title(Component.translatable("itemGroup.backpacked"));
         builder.icon(() -> new ItemStack((ItemLike)ArkaniaZModItems.BACKPACK.get()));
         builder.displayItems(
            (params, output) -> {
               Registration.get(Registries.BLOCK)
                  .stream()
                  .filter(entry -> entry.getId().getNamespace().equals("backpacked"))
                  .forEach(entry -> output.accept((ItemLike)entry.get()));
               Registration.get(Registries.ITEM)
                  .stream()
                  .filter(entry -> entry.getId().getNamespace().equals("backpacked"))
                  .forEach(entry -> output.accept((ItemLike)entry.get()));

               for (Enchantment enchantment : BuiltInRegistries.ENCHANTMENT) {
                  Optional.ofNullable(BuiltInRegistries.ENCHANTMENT.getKey(enchantment)).ifPresent(id -> {
                     if (id.getNamespace().equals("backpacked") && enchantment.category == Services.BACKPACK.getEnchantmentCategory()) {
                        Services.REGISTRATION.addEnchantedBookToCreativeTab(output, enchantment);
                     }
                  });
               }
            }
         );
      }
   );
}
