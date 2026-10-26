package com.dayzhotbar;

import com.dayzhotbar.client.HotbarClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Replaces the vanilla hotbar with DayZ-style slots: slanted, spaced out,
 * dark glass with a thin bone-white frame. Client only.
 */
@Mod(DayzHotbar.MOD_ID)
public class DayzHotbar {
    public static final String MOD_ID = "dayzhotbar";

    /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
    public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";
    public DayzHotbar() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            HotbarClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
