package fr.heraliz.crac.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.Config;
import net.minecraft.Util;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Server-side rate limit of the adventure placable packets (place and pick up share it, like the client cooldown).
 * The client waits {@code adventureCooldownMs}; the server accepts a little less for network jitter.
 */
@EventBusSubscriber(
    modid = "crac"
)
public final class ActionCooldown {
    /** Server-thread only (packets are handled through enqueueWork). */
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private ActionCooldown() {
    }

    /** True when the player may act now; records the action. */
    public static boolean allow(ServerPlayer player) {
        long now = Util.getMillis();
        long min = Math.round(Config.adventureCooldownMs.get() * 0.75);
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < min) {
            return false;
        }
        LAST.put(player.getUUID(), now);
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }
}
