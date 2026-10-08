package fr.heraliz.crac.client;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.network.C2SRequestNearbyClaims;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CNearbyClaims;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** Claims near the player as the server last sent them (login, dimension change, claim and group changes, travel). */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientClaimCache {
    /** Ticks between two refresh requests; the server answers at most one per second. */
    private static final int REFRESH_TICKS = 40;
    private static final List<S2CNearbyClaims.ClaimEntry> CLAIMS = new CopyOnWriteArrayList<>();
    /** Dimension the claims were received in; they say nothing about another one. */
    @Nullable
    private static volatile ResourceKey<Level> dimension;
    /** Where the player stood when the claims arrived: the server only sends the claims within its view radius of it. */
    @Nullable
    private static volatile BlockPos receivedAt;
    private static int refreshCooldown;

    public enum ClaimHere {
        NONE,
        OWN,
        FOREIGN
    }

    private ClientClaimCache() {
    }

    public static void set(List<S2CNearbyClaims.ClaimEntry> claims) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        dimension = level == null ? null : level.dimension();
        receivedAt = mc.player == null ? null : mc.player.blockPosition();
        CLAIMS.clear();
        CLAIMS.addAll(claims);
    }

    public static List<S2CNearbyClaims.ClaimEntry> get() {
        return current() ? Collections.unmodifiableList(CLAIMS) : List.of();
    }

    public static void clear() {
        CLAIMS.clear();
        dimension = null;
        receivedAt = null;
    }

    /** False while the player is in another dimension than the claims came from (until the server sends new ones). */
    private static boolean current() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.dimension().equals(dimension);
    }

    /** Claims of the last server must not show on the next one. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    /**
     * The server pushes claim changes itself, but only the claims around where the player stood: after a long walk the
     * list no longer covers the player's surroundings and the placement ghosts would trust it. Ask again once the player
     * is {@link C2SRequestNearbyClaims#refreshDistance()} away from that spot (other groups' claims are only sent that far).
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (refreshCooldown > 0) {
            refreshCooldown--;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || !Config.SPEC.isLoaded()) {
            return;
        }
        BlockPos at = receivedAt;
        int limit = C2SRequestNearbyClaims.refreshDistance();
        if (at != null && current() && Math.abs(player.getBlockX() - at.getX()) <= limit && Math.abs(player.getBlockZ() - at.getZ()) <= limit) {
            return;
        }
        refreshCooldown = REFRESH_TICKS;
        NetworkHandler.CHANNEL.sendToServer(new C2SRequestNearbyClaims());
    }

    /** Whose claim covers a block, as far as the client knows; overlapping zones resolve as on the server. */
    public static ClaimHere claimAt(BlockPos pos) {
        S2CNearbyClaims.ClaimEntry e = current() ? S2CNearbyClaims.claimAt(CLAIMS, pos) : null;
        if (e == null) {
            return ClaimHere.NONE;
        }
        return e.ownedByMe() ? ClaimHere.OWN : ClaimHere.FOREIGN;
    }
}
