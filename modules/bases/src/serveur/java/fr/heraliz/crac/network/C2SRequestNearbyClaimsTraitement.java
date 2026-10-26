package fr.heraliz.crac.network;

import fr.heraliz.crac.heraliz.ClanAccess;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Traitement serveur du paquet C2SRequestNearbyClaims (corps d'origine, docs/METHODE_CLIENT_SERVEUR.md §6) : le paquet commun ne garde
 * que son format ; ce traitement est appelé par serveur/PartieServeur.
 */
public final class C2SRequestNearbyClaimsTraitement {
    private C2SRequestNearbyClaimsTraitement() {
    }

    /** Beyond the refresh distance, how far another group's zone may be and still matter (reach of building and ghosts). */
    static final int FOREIGN_MARGIN = 40;
    /** The server pushes claim changes itself; a client asking more often than this gets nothing. */
    static final long COOLDOWN_MS = 1000L;
    public static void traiter(C2SRequestNearbyClaims msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.level() instanceof ServerLevel level
                && PacketThrottle.allow(player, PacketThrottle.Kind.NEARBY_CLAIMS, COOLDOWN_MS)) {
                sendToPlayer(level, player);
            }
        });
        ctx.get().setPacketHandled(true);
    }    /** The settings a claim view is built from besides the claims: view radius, then zone sizes per tier. */
    public static int[] viewSettings() {
        return new int[]{
            Config.claimViewRadius.get(),
            Config.claimRadiusL1.get(), Config.claimRadiusL2.get(), Config.claimRadiusL3.get(), Config.claimRadiusL4.get(),
            Config.claimYRadiusL1.get(), Config.claimYRadiusL2.get(), Config.claimYRadiusL3.get(), Config.claimYRadiusL4.get()
        };
    }    /**
     * The player's own claims within the view radius, and other groups' claims only when their zone is close enough to
     * matter before the next refresh: the list does not reveal bases beyond what the player could reach or see.
     */
    public static void sendToPlayer(ServerLevel level, ServerPlayer player) {
        BlockPos pp = player.blockPosition();
        Group myGroup = player.getServer() == null ? null : GroupData.get(player.getServer()).getByMember(player.getUUID());
        int view = Config.claimViewRadius.get();
        int foreignView = Math.min(view, C2SRequestNearbyClaims.refreshDistance() + FOREIGN_MARGIN);
        List<S2CNearbyClaims.ClaimEntry> entries = new ArrayList<>();

        for (Claim c : ClaimData.get(level).all()) {
            Group g = GroupData.get(player.getServer()).getById(c.groupId());
            // HeraliZ : a player given access in the Claim Block builds there like a member (ghost and click)
            boolean own = myGroup != null && c.groupId().equals(myGroup.id())
                || g != null && fr.heraliz.crac.bases.AccesBase.a(g.id(), player.getUUID());
            int range = own ? view : foreignView + c.radius();
            if (Math.abs(c.center().getX() - pp.getX()) <= range && Math.abs(c.center().getZ() - pp.getZ()) <= range) {
                int color = g == null ? 16777215 : g.color() & 16777215;
                entries.add(new S2CNearbyClaims.ClaimEntry(c.center(), c.radius(), c.yRadius(), c.tier(), color, own));
            }
        }

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CNearbyClaims(entries));
    }    public static void pushAll(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            sendToPlayer(level, player);
        }
    }    /** Refreshes the claim view of just these players (those online), each in their own level. */
    public static void pushTo(MinecraftServer server, Collection<UUID> players) {
        for (UUID id : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                sendToPlayer(player.serverLevel(), player);
            }
        }
    }}
