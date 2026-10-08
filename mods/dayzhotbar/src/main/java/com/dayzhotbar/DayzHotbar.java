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

    public DayzHotbar() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            HotbarClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
