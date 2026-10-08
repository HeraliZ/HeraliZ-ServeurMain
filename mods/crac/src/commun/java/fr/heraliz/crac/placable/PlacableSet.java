package fr.heraliz.crac.placable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CPlacableSet;
import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.PacketDistributor;

public final class PlacableSet {
    /** Étiquette des PV gardés dans l'objet d'un placable abîmé ; reprise par PlacableHpManager (serveur). */
    public static final String ITEM_HP_TAG = "CracHp";

    private static final Set<Block> SERVER = new HashSet<>();
    private static final Set<Block> CLIENT = Collections.synchronizedSet(new HashSet<>());

    private PlacableSet() {
    }

    public static void setServerSide(Collection<Block> blocks) {
        SERVER.clear();
        SERVER.addAll(blocks);
    }

    public static Collection<Block> serverSide() {
        return Collections.unmodifiableSet(SERVER);
    }

    public static void syncTo(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), S2CPlacableSet.of(SERVER));
    }

    public static void syncToAll(MinecraftServer server) {
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                syncTo(p);
            }
        }
    }

    public static void setClientSide(Collection<Block> blocks) {
        CLIENT.clear();
        CLIENT.addAll(blocks);
    }

    public static boolean containsClient(Block block) {
        return CLIENT.contains(block);
    }

    public static boolean contains(Level level, Block block) {
        return level != null && level.isClientSide() ? CLIENT.contains(block) : SERVER.contains(block);
    }

    public static boolean canPlace(Level level, Block block) {
        return isClaimAnchor(block) || contains(level, block);
    }

    public static boolean canPlaceClient(Block block) {
        return isClaimAnchor(block) || CLIENT.contains(block);
    }

    public static boolean isClaimAnchor(Block block) {
        return block == ModBlocks.CLAIM_BLOCK.get();
    }
}
