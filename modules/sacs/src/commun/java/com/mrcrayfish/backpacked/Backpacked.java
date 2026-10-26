package com.mrcrayfish.backpacked;

import com.mrcrayfish.backpacked.core.ModEnchantments;
import com.mrcrayfish.backpacked.inventory.BackpackInventory;
import com.mrcrayfish.backpacked.inventory.BackpackedInventoryAccess;
import com.mrcrayfish.backpacked.item.BackpackItem;
import com.mrcrayfish.backpacked.platform.Services;
import com.mrcrayfish.backpacked.pont.Parties;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingGetProjectileEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("backpacked")
public class Backpacked {
   public static final EnchantmentCategory ENCHANTMENT_TYPE = EnchantmentCategory.create("backpack", item -> item instanceof BackpackItem);
   private static boolean controllableLoaded = false;
   private static final String EXPECTED_SERVER_KEY = "e35X9xKSmfqK9mQW6852ec6JQx";
   private static final String KEY_URL = "https://launcher.arkaniaz.fr/server/server_key.php";

   public Backpacked() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      bus.addListener(this::onCommonSetup);
      MinecraftForge.EVENT_BUS.register(this);
      MinecraftForge.EVENT_BUS.addListener(this::onGetProjectile);
      controllableLoaded = ModList.get().isLoaded("controllable");
      // Separation client / serveur (HeraliZ, docs/METHODE_CLIENT_SERVEUR.md) : parties chargees si elles sont dans le jar.
      //  - serveur : sacs du serveur (data), butin Pillard, marchand ambulant, generation de donnees, reseau serveur ;
      //  - client  : chargeurs de modeles, rendus, couches, touches, ecrans, reseau client.
      Parties.serveur("backpacked", "com.mrcrayfish.backpacked.serveur.PartieServeur", bus);
      Parties.client("backpacked", "com.mrcrayfish.backpacked.client.PartieClient", bus);
   }

   private void onCommonSetup(FMLCommonSetupEvent event) {
      event.enqueueWork(Bootstrap::init);
   }

   private void onGetProjectile(LivingGetProjectileEvent event) {
      if (event.getProjectileItemStack().isEmpty() && event.getEntity() instanceof Player player) {
         ItemStack backpack = Services.BACKPACK.getBackpackStack(player);
         if (backpack.isEmpty()) {
            return;
         }

         if (EnchantmentHelper.getItemEnchantmentLevel((Enchantment)ModEnchantments.MARKSMAN.get(), backpack) <= 0) {
            return;
         }

         BackpackInventory inventory = ((BackpackedInventoryAccess)player).backpacked$GetBackpackInventory();
         if (inventory == null) {
            return;
         }

         Predicate<ItemStack> predicate = ((ProjectileWeaponItem)event.getProjectileWeaponItemStack().getItem()).getSupportedHeldProjectiles();
         ItemStack projectile = IntStream.range(0, inventory.getContainerSize())
            .<ItemStack>mapToObj(inventory::getItem)
            .filter(predicate)
            .findFirst()
            .orElse(ItemStack.EMPTY);
         if (!projectile.isEmpty()) {
            event.setProjectileItemStack(projectile);
         }
      }
   }

   public static boolean isControllableLoaded() {
      return controllableLoaded;
   }
}
