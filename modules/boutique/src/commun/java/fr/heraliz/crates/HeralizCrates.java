package fr.heraliz.crates;

import fr.heraliz.crates.pont.Parties;
import com.mojang.logging.LogUtils;
import fr.heraliz.crates.config.ClientConfig;
import fr.heraliz.crates.network.HzNet;
import fr.heraliz.crates.registry.ModRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Heraliz Crates : coffres animes HD pour le serveur HERALIZ.
 */
@Mod(HeralizCrates.MODID)
public class HeralizCrates {
    public static final String MODID = "heralizcrates";
    /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
    public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HeralizCrates() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(modBus);
        HzNet.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // parties coupées (docs/METHODE_CLIENT_SERVEUR.md) : butin, tirages, ouvertures, éditeur, commandes (serveur) ;
        // rendus, interfaces, effets (client)
        Parties.serveur(MODID, "fr.heraliz.crates.serveur.PartieServeur", modBus);
        Parties.client(MODID, "fr.heraliz.crates.client.PartieClient", modBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
