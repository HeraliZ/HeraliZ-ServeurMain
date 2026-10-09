package com.heraliz.extraction;

import com.heraliz.extraction.pont.Parties;
import com.heraliz.extraction.reseau.Reseau;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * HeraliZ Extraction : terminal d'extraction (PC + fumigène) et zones fixes de la carte, ouvertes à tour de rôle.
 */
@Mod(HeralizExtraction.MODID)
public class HeralizExtraction {
    public static final String MODID = "heraliz_extraction";

    public HeralizExtraction() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(modBus);
        modBus.addListener(this::addToCreativeTab);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ExtractionConfig.SPEC);
        Reseau.enregistrer();
        // parties coupées (docs/METHODE_CLIENT_SERVEUR.md) : chronos, téléportation, zones fixes, rotation, commandes (serveur) ;
        // rendu du PC et de la zone, écran, poses, particules (client)
        Parties.serveur(MODID, "com.heraliz.extraction.serveur.PartieServeur", modBus);
        Parties.client(MODID, "com.heraliz.extraction.client.PartieClient", modBus);
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModRegistry.EXTRACTION_TERMINAL);
        }
    }
}
