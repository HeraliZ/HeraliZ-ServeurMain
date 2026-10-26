package fr.heraliz.crac.build;

import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.world.Costs;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Logs every cost entry of crac-building.toml that names an unknown item or an empty tag. Such an entry blocks its
 * action (see {@code BuildCost}), so the admin must hear about it: at server start, when the file is reloaded and
 * when the tags change ({@code /reload}).
 */
@Mod.EventBusSubscriber(modid = "crac", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BuildConfigCheck {
    private BuildConfigCheck() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        report();
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD && server != null && server.isRunning()) {
            report();
        }
    }

    /** Mod bus: crac-building.toml edited on disk. */
    @Mod.EventBusSubscriber(modid = "crac", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        private Reload() {
        }

        @SubscribeEvent
        public static void onReload(ModConfigEvent.Reloading event) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (event.getConfig().getSpec() == BuildConfig.SPEC && server != null) {
                server.execute(BuildConfigCheck::report);
            }
        }
    }

    private static void report() {
        if (!BuildConfig.SPEC.isLoaded()) {
            return;
        }
        for (String problem : Costs.problems()) {
            Crac.LOGGER.warn("[CRac building] crac-building.toml {}: this action is blocked until the entry is fixed", problem);
        }
    }
}
