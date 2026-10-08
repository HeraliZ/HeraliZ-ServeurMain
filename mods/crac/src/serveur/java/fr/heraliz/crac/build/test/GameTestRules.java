package fr.heraliz.crac.build.test;

import fr.heraliz.crac.Crac;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;

/**
 * The GameTests were written for CRac's original rules outside claims (no world block broken by blasts, placables
 * with HP and adventure pickup everywhere). On the GameTest server only, {@code heraliz.cracRulesOutsideClaims} is
 * turned on so they keep testing those rules; HeraliZ servers keep the default (off).
 */
@Mod.EventBusSubscriber(modid = "crac", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GameTestRules {
    private GameTestRules() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (ForgeGameTestHooks.isGametestServer()) {
            // a flag rather than the config value: the tests save and reload crac-server.toml as they go
            fr.heraliz.crac.heraliz.Heraliz.gameTestOriginalRules = true;
            Crac.LOGGER.info("[CRac] GameTest server: CRac's original rules outside claims are on");
        }
    }
}
