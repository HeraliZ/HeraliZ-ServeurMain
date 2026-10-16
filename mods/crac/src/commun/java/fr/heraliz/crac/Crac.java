package fr.heraliz.crac;

import fr.heraliz.crac.pont.Parties;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.registry.ModBlockEntities;
import fr.heraliz.crac.registry.ModBlocks;
import fr.heraliz.crac.registry.ModEntities;
import fr.heraliz.crac.registry.ModItems;
import fr.heraliz.crac.registry.ModMenus;
import fr.heraliz.crac.registry.ModParticles;
import fr.heraliz.crac.registry.ModSounds;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("crac")
public class Crac {
    public static final String MODID = "crac";
    /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
    public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";
    public static final Logger LOGGER = LogManager.getLogger();

    public Crac() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUND_EVENTS.register(modBus);
        ModParticles.PARTICLE_TYPES.register(modBus);
        ModMenus.MENUS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addCreative);
        // crac-server.toml, with old root keys moved into their section on load
        ConfigMigration.register(ModLoadingContext.get().getActiveContainer());
        ModLoadingContext.get().registerConfig(Type.SERVER, fr.heraliz.crac.build.BuildConfig.SPEC, "crac-building.toml");
        // monde des bases, /home, tutoriel, protection des nouveaux joueurs, carrières
        ModLoadingContext.get().registerConfig(Type.SERVER, fr.heraliz.crac.bases.BasesReglages.SPEC, "crac-bases.toml");
        // Not SERVER: Forge syncs SERVER config files to clients, and this one holds the admin console password.
        ModLoadingContext.get().registerConfig(Type.COMMON, fr.heraliz.crac.admin.AdminConfig.SPEC, "crac-admin.toml");
        // parties coupées (docs/METHODE_CLIENT_SERVEUR.md) : règles de construction, claims, raids, protections,
        // administration (serveur) ; aperçus, rendus, écrans, HUD (client)
        Parties.serveur(MODID, "fr.heraliz.crac.serveur.PartieServeur", modBus);
        Parties.client(MODID, "fr.heraliz.crac.client.PartieClient", modBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::register);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.BUILDING_HAMMER);
            event.accept(ModItems.BUILDING_PLAN);
            event.accept(ModItems.C4_ITEM);
            event.accept(ModItems.EXPLOSIVE_CHARGE);
            event.accept(ModItems.METAL_CRATE);
            event.accept(ModItems.CLAIM_BLOCK);
            event.accept(ModItems.GLASS_WINDOW);
            event.accept(ModItems.METAL_WINDOW);
            event.accept(ModItems.NAIL);
            event.accept(ModItems.SHEET_METAL_PANEL);
            event.accept(ModItems.REINFORCED_METAL_PANEL);
            event.accept(ModItems.ARMORED_METAL_PANEL);
        }
    }
}
