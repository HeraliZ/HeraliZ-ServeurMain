package fr.heralisz.tactical;

import fr.heralisz.tactical.config.ClientConfig;
import fr.heralisz.tactical.config.CommonConfig;
import fr.heralisz.tactical.config.ServerConfig;
import fr.heralisz.tactical.init.ModCustomTypes;
import fr.heralisz.tactical.init.ModEffects;
import fr.heralisz.tactical.init.ModEnchantment;
import fr.heralisz.tactical.init.ModEntities;
import fr.heralisz.tactical.init.ModItems;
import fr.heralisz.tactical.init.ModParticleTypes;
import fr.heralisz.tactical.init.ModSounds;
import fr.heralisz.tactical.network.NetworkHandler;
import fr.heraliz.tactique.pont.Parties;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("heralisz_tactical")
public class HeraliszTactical {
   public static final String MOD_ID = "heralisz_tactical";
   public static final Logger LOGGER = LogManager.getLogger("heralisz_tactical");

   public HeraliszTactical() {
      ModLoadingContext.get().registerConfig(Type.CLIENT, ClientConfig.init());
      ModLoadingContext.get().registerConfig(Type.COMMON, CommonConfig.init());
      ModLoadingContext.get().registerConfig(Type.SERVER, ServerConfig.init());
      IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
      ModItems.ITEMS.register(modEventBus);
      ModEntities.ENTITY_TYPES.register(modEventBus);
      ModEnchantment.ENCHANTMENTS.register(modEventBus);
      ModParticleTypes.PARTICLE_TYPES.register(modEventBus);
      ModEffects.EFFECTS.register(modEventBus);
      ModCustomTypes.THROWABLE_TYPES.register(modEventBus);
      ModCustomTypes.MELEE_WEAPON_TYPES.register(modEventBus);
      ModSounds.SOUNDS.register(modEventBus);
      NetworkHandler.init();
      // Fusion (heraliz_tactique) : onglets remplacés par les groupes Mêlée / Soins / Grenades de l'onglet unique (voir
      // HeralizTactique) ; parties serveur (coups, soins, lancers, explosions) et client (rendus, animations, HUD) du jar.
      Parties.serveur(MOD_ID, "fr.heralisz.tactical.serveur.PartieServeur", modEventBus);
      Parties.client(MOD_ID, "fr.heralisz.tactical.client.PartieClient", modEventBus);
   }
}
