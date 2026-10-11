package fr.heraliz.airstrike;

import com.mojang.logging.LogUtils;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.pont.Parties;
import fr.heraliz.airstrike.registry.ModBlockEntities;
import fr.heraliz.airstrike.registry.ModBlocks;
import fr.heraliz.airstrike.registry.ModCreativeTab;
import fr.heraliz.airstrike.registry.ModEntities;
import fr.heraliz.airstrike.registry.ModItems;
import fr.heraliz.airstrike.registry.ModParticles;
import fr.heraliz.airstrike.registry.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AirstrikeMod.MODID)
public class AirstrikeMod {
    public static final String MODID = "airstrike";
    /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
    public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AirstrikeMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        modBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AirstrikeConfig.SPEC);
        // server part (strikes, bombs, explosions, drops, commands: its ServerEvents are registered there, at the same
        // moment as before) and client part (binoculars, rendering, effects, sounds), if they are in the jar
        Parties.serveur(MODID, "fr.heraliz.airstrike.serveur.PartieServeur", modBus);
        Parties.client(MODID, "fr.heraliz.airstrike.client.PartieClient", modBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Network::register);
    }
}
