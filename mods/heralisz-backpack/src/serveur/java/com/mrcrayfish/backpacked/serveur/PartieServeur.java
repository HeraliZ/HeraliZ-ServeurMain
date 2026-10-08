package com.mrcrayfish.backpacked.serveur;

import com.mrcrayfish.backpacked.Bootstrap;
import com.mrcrayfish.backpacked.common.BackpackEvents;
import com.mrcrayfish.backpacked.common.WanderingTraderEvents;
import com.mrcrayfish.backpacked.common.backpack.loader.BackpackLoader;
import com.mrcrayfish.backpacked.core.ModCommands;
import com.mrcrayfish.backpacked.data.unlock.UnlockManagerServeur;
import com.mrcrayfish.backpacked.datagen.BlockTagGen;
import com.mrcrayfish.backpacked.datagen.LootTableGen;
import com.mrcrayfish.backpacked.datagen.RecipeGen;
import com.mrcrayfish.backpacked.enchantment.LootedEnchantmentServeur;
import com.mrcrayfish.backpacked.enchantment.RepairmanEnchantmentServeur;
import com.mrcrayfish.backpacked.network.message.MessageBackpackCosmetics;
import com.mrcrayfish.backpacked.network.message.MessageEntityBackpack;
import com.mrcrayfish.backpacked.network.message.MessageOpenBackpack;
import com.mrcrayfish.backpacked.network.message.MessageRequestCustomisation;
import com.mrcrayfish.backpacked.network.play.ServerPlayHandler;
import com.mrcrayfish.backpacked.pont.Partie;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie SERVEUR de HeraliZ Backpack (separation client / serveur HeraliZ, docs/METHODE_CLIENT_SERVEUR.md §4), chargee par
 * Backpacked si elle est dans le jar (-serveur, -complet). Code d'origine de Backpacked / Bootstrap, deplace tel quel :
 * sacs definis par les donnees (BackpackLoader), butin Pillard, refus du marchand ambulant, generation de donnees,
 * paquets recus par le serveur (ServerPlayHandler) et initialisation serveur de Bootstrap.
 */
public final class PartieServeur implements Partie {
   @Override
   public void init(IEventBus bus) {
      bus.addListener(PartieServeur::onGatherData);
      MinecraftForge.EVENT_BUS.addListener(PartieServeur::addReloadListener);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, PartieServeur::onDropLoot);
      MinecraftForge.EVENT_BUS.addListener(PartieServeur::onInteract);
      // paquets recus du client
      MessageOpenBackpack.RECEPTION_SERVEUR = ServerPlayHandler::handleOpenBackpack;
      MessageEntityBackpack.RECEPTION_SERVEUR = ServerPlayHandler::handleEntityBackpack;
      MessageBackpackCosmetics.RECEPTION_SERVEUR = ServerPlayHandler::handleCustomiseBackpack;
      MessageRequestCustomisation.RECEPTION_SERVEUR = ServerPlayHandler::handleRequestCustomisation;
      // Bootstrap.init (FMLCommonSetupEvent) : partie serveur, dans l'ordre d'origine
      Bootstrap.INIT_SERVEUR = () -> {
         UnlockManagerServeur.init();
         BackpackEvents.init();
         RepairmanEnchantmentServeur.init();
         WanderingTraderEvents.init();
         ModCommands.init();
      };
   }

   private static void onGatherData(GatherDataEvent event) {
      ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
      DataGenerator generator = event.getGenerator();
      PackOutput packOutput = generator.getPackOutput();
      CompletableFuture lookupProvider = event.getLookupProvider();
      generator.addProvider(event.includeServer(), new LootTableGen(packOutput));
      generator.addProvider(event.includeServer(), new RecipeGen(packOutput));
      generator.addProvider(event.includeServer(), new BlockTagGen(packOutput, lookupProvider, existingFileHelper));
   }

   private static void addReloadListener(AddReloadListenerEvent event) {
      event.addListener(new BackpackLoader());
   }

   private static void onDropLoot(LivingDropsEvent event) {
      if (LootedEnchantmentServeur.onDropLoot(event.getDrops(), event.getSource())) {
         event.setCanceled(true);
      }
   }

   private static void onInteract(EntityInteract event) {
      if (WanderingTraderEvents.onInteract(event.getTarget(), event.getEntity())) {
         event.setCancellationResult(InteractionResult.SUCCESS);
         event.setCanceled(true);
      }
   }
}
