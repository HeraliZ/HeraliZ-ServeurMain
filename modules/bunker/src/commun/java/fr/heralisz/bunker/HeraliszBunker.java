package fr.heralisz.bunker;

import fr.heralisz.bunker.pont.Parties;
import com.hbm_m.block.ModBlocks;
import com.hbm_m.block.entity.doors.DoorDeclRegistry;
import com.hbm_m.blockentity.ModBlockEntities;
import com.hbm_m.item.ModItems;
import com.hbm_m.sound.ModSounds;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import fr.heralisz.bunker.barricade.BarricadeRegistry;
import fr.heralisz.bunker.keycard.KeycardConfig;
import fr.heralisz.bunker.keycard.KeycardNetwork;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import fr.heralisz.bunker.keycard.KeycardRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** HeralisZ Bunker : portes animées reprises de HBM's Nuclear Tech Modernized (GPL-3.0), cartes d'accès et lecteurs façon Rust. */
@Mod(HeraliszBunker.MODID)
public final class HeraliszBunker {
   public static final String MODID = "heralisz_bunker";
   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
   public static final RegistryObject<CreativeModeTab> TAB = TABS.register("doors", () -> CreativeModeTab.builder()
      .title(Component.translatable("itemGroup.heralisz_bunker.doors"))
      .icon(() -> new ItemStack(ModItems.VAULT_DOOR.get()))
      .displayItems((params, out) -> {
         ModItems.ITEMS.getEntries().forEach(i -> {
            if (i != ModItems.SCREWDRIVER && i != ModItems.SCREWDRIVER_DESH) out.accept(i.get());
         });
         KeycardRegistry.ITEMS.getEntries().forEach(i -> out.accept(i.get()));
         out.accept(BarricadeRegistry.BARRICADE_ITEM.get());
      })
      .build());

   public HeraliszBunker() {
      DoorDeclRegistry.init();
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      ModBlocks.BLOCKS.register(bus);
      ModItems.ITEMS.register(bus);
      ModBlockEntities.BLOCK_ENTITIES.register(bus);
      ModSounds.SOUND_EVENTS.register(bus);
      KeycardRegistry.register(bus);
      BarricadeRegistry.register(bus);
      ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, KeycardConfig.SPEC);
      bus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(KeycardNetwork::init));
      TABS.register(bus);
      // partie serveur : salles à carte (porte tenue ouverte, loot de la salle, commande /salle et baguette)
      Parties.serveur(MODID, "fr.heralisz.bunker.serveur.PartieServeur", bus);
      // partie client coupée (docs/METHODE_CLIENT_SERVEUR.md) : portes animées, passage de carte, blocs fantômes
      Parties.client(MODID, "fr.heralisz.bunker.client.PartieClient", bus);
   }
}
