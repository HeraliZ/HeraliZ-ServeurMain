package fr.heraliz.casino;

import com.mojang.logging.LogUtils;
import fr.heraliz.casino.config.ClientConfig;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.pont.Parties;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Heraliz Casino : machines de casino 3D animees pour le serveur HERALIZ.
 * Monnaie : les jetons (objets). Toutes les parties sont tirees par le serveur ; la maison garde l'avantage.
 */
@Mod(HeralizCasino.MODID)
public class HeralizCasino {
    public static final String MODID = "heralizcasino";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HeralizCasino() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::entityAttributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // parties coupées (docs/METHODE_CLIENT_SERVEUR.md) : logique de jeu (serveur), affichage (client)
        Parties.serveur(MODID, "fr.heraliz.casino.serveur.PartieServeur", modBus);
        Parties.client(MODID, "fr.heraliz.casino.client.PartieClient", modBus);
    }

    private void entityAttributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(ModRegistry.GUARD.get(), fr.heraliz.casino.entity.GuardEntity.createAttributes().build());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CasinoNet::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
