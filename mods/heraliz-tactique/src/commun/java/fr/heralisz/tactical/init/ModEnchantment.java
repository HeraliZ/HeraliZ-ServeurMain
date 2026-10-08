package fr.heralisz.tactical.init;

import fr.heralisz.tactical.enchantment.BackstabEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEnchantment {
   public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, "heralisz_tactical");
   public static RegistryObject<Enchantment> BACKSTAB = ENCHANTMENTS.register("backstab", BackstabEnchantment::new);
}
