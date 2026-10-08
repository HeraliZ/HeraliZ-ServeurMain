package fr.heraliz.reanimation;

import com.mojang.logging.LogUtils;
import fr.heraliz.reanimation.network.Network;
import fr.heraliz.reanimation.pont.Parties;
import fr.heraliz.reanimation.registry.ModEntities;
import fr.heraliz.reanimation.registry.ModItems;
import fr.heraliz.reanimation.registry.ModMenus;
import fr.heraliz.reanimation.registry.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod("reanimation")
public class ReanimationMod {
   public static final String MODID = "reanimation";
   public static final Logger LOGGER = LogUtils.getLogger();
   public static final boolean TRACE = Boolean.getBoolean("reanimation.selftest") || Boolean.getBoolean("reanimation.selftest.server");

   public ReanimationMod() {
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
      ModEntities.ENTITIES.register(modBus);
      ModItems.ITEMS.register(modBus);
      ModMenus.MENUS.register(modBus);
      ModSounds.SOUNDS.register(modBus);
      modBus.addListener(this::commonSetup);
      ModLoadingContext.get().registerConfig(Type.SERVER, ReanimationConfig.SPEC);
      ModLoadingContext.get().registerConfig(Type.CLIENT, ClientConfig.SPEC);
      // parties serveur (état à terre, exécutions, commandes) et client (poses, HUD, écrans), si elles sont dans le jar
      Parties.serveur(MODID, "fr.heraliz.reanimation.serveur.PartieServeur", modBus);
      Parties.client(MODID, "fr.heraliz.reanimation.client.PartieClient", modBus);
   }

   private void commonSetup(FMLCommonSetupEvent event) {
      event.enqueueWork(Network::register);
   }
}
