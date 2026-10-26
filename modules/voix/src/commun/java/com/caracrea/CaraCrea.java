package com.caracrea;

import com.caracrea.pont.Parties;
import com.caracrea.network.CaraCreaNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * CaraCrea: a character creator. Players build their look from presets
 * (body type, face, hair, clothes); every client paints a high-resolution
 * skin from those few bytes, so the server only stores and relays a tiny
 * appearance record while 3D or custom skins are replaced for everyone.
 */
@Mod(CaraCrea.MOD_ID)
public class CaraCrea {
    public static final String MOD_ID = "caracrea";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CaraCrea() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CaraCreaConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CaraCreaConfig.CLIENT_SPEC);
        // parties coupées (docs/METHODE_CLIENT_SERVEUR.md) : apparences, relais vocal, sourdines, commandes (serveur) ;
        // créateur, peinture des skins, micro, codec, lecture des voix (client)
        Parties.serveur(MOD_ID, "com.caracrea.serveur.PartieServeur", modBus);
        Parties.client(MOD_ID, "com.caracrea.client.PartieClient", modBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CaraCreaNetwork::register);
    }
}
