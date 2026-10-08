package fr.heraliz.crac.claim;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Cross-checks the claim settings, which Forge only range-checks one by one: Y bounds, radii per tier, upkeep items,
 * and existing claims whose zones overlap since a radius was raised (spacing is only enforced when a claim is placed).
 * Problems are logged at server start and whenever crac-server.toml is reloaded.
 */
@EventBusSubscriber(
    modid = "crac"
)
public final class ClaimConfigCheck {
    private ClaimConfigCheck() {
    }

    /** Problems in the claim geometry settings, empty when they are consistent. */
    public static List<String> geometryProblems(int minY, int maxY, int[] radii, int[] yRadii) {
        List<String> out = new ArrayList<>();
        if (minY > maxY) {
            out.add("claims.minY (" + minY + ") is above claims.maxY (" + maxY + "): no Claim Block can be placed anywhere");
        }
        for (int i = 1; i < radii.length; i++) {
            if (radii[i] < radii[i - 1]) {
                out.add("radiusL" + (i + 1) + " (" + radii[i] + ") is smaller than radiusL" + i + " (" + radii[i - 1] + "): upgrading shrinks the claim");
            }
        }
        for (int i = 1; i < yRadii.length; i++) {
            if (yRadii[i] < yRadii[i - 1]) {
                out.add("yRadiusL" + (i + 1) + " (" + yRadii[i] + ") is smaller than yRadiusL" + i + " (" + yRadii[i - 1] + "): upgrading shrinks the claim");
            }
        }
        return out;
    }

    /** Whether two claim boxes share a block. */
    public static boolean overlap(BlockPos a, int ra, int yra, BlockPos b, int rb, int yrb) {
        return Math.abs(a.getX() - b.getX()) <= ra + rb && Math.abs(a.getZ() - b.getZ()) <= ra + rb && Math.abs(a.getY() - b.getY()) <= yra + yrb;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        report(event.getServer());
    }

    /** Mod bus: crac-server.toml edited on disk or from the admin console. */
    @EventBusSubscriber(
        modid = "crac",
        bus = EventBusSubscriber.Bus.MOD
    )
    public static final class Reload {
        private Reload() {
        }

        @SubscribeEvent
        public static void onReload(ModConfigEvent.Reloading event) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (event.getConfig().getSpec() == Config.SPEC && server != null) {
                server.execute(() -> report(server));
            }
        }
    }

    private static void report(MinecraftServer server) {
        if (!Config.SPEC.isLoaded()) {
            return;
        }
        List<String> problems = geometryProblems(
            Config.claimMinY.get(), Config.claimMaxY.get(),
            new int[]{Config.claimRadiusL1.get(), Config.claimRadiusL2.get(), Config.claimRadiusL3.get()},
            new int[]{Config.claimYRadiusL1.get(), Config.claimYRadiusL2.get(), Config.claimYRadiusL3.get()}
        );
        for (UpkeepResource res : UpkeepResource.values()) {
            if (res.item() == Items.AIR) {
                problems.add("the " + res.displayName + " upkeep item does not exist: pieces of that tier cost no upkeep");
            }
        }
        for (String p : problems) {
            Crac.LOGGER.warn("[CRac claims] {}", p);
        }
        for (ServerLevel level : server.getAllLevels()) {
            List<Claim> claims = new ArrayList<>(ClaimData.get(level).all());
            for (int i = 0; i < claims.size(); i++) {
                for (int j = i + 1; j < claims.size(); j++) {
                    Claim a = claims.get(i);
                    Claim b = claims.get(j);
                    if (overlap(a.anchor(), a.radius(), a.yRadius(), b.anchor(), b.radius(), b.yRadius())) {
                        Crac.LOGGER.warn("[CRac claims] The claims at {} and {} in {} overlap (a radius was raised): the nearest Claim Block owns the shared blocks",
                            a.anchor().toShortString(), b.anchor().toShortString(), level.dimension().location());
                    }
                }
            }
        }
    }
}
