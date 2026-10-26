package fr.heraliz.crac.group;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2COnlinePlayers;
import fr.heraliz.crac.network.S2COpenGroupManage;
import fr.heraliz.crac.network.S2COpenInvites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class GroupSync {
    private GroupSync() {
    }

    public static void sendManage(ServerPlayer viewer, Group g) {
        MinecraftServer server = viewer.getServer();
        if (server != null) {
            List<S2COpenGroupManage.MemberEntry> members = new ArrayList<>();

            for (UUID m : g.members()) {
                boolean online = server.getPlayerList().getPlayer(m) != null;
                members.add(new S2COpenGroupManage.MemberEntry(m, nameFor(server, m), online));
            }

            // the claim may be in any dimension
            Claim claim = ClaimManager.getByGroupAnywhere(server, g.id());
            boolean hasClaim = claim != null;
            BlockPos claimCenter = hasClaim ? claim.center() : BlockPos.ZERO;
            List<S2COpenGroupManage.BuildCount> builds = buildCounts(server, g);
            S2COpenGroupManage pkt = new S2COpenGroupManage(
                g.name(), g.tag(), g.color(), g.owner(), viewer.getUUID().equals(g.owner()), members, hasClaim, claimCenter, builds
            );
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), pkt);
        }
    }

    public static void sendInvites(ServerPlayer viewer) {
        MinecraftServer server = viewer.getServer();
        if (server != null) {
            GroupData data = GroupData.get(server);
            List<S2COpenInvites.InviteEntry> list = new ArrayList<>();

            for (UUID gid : data.getInvites(viewer.getUUID())) {
                Group g = data.getById(gid);
                if (g != null) {
                    list.add(new S2COpenInvites.InviteEntry(gid, g.name(), g.tag(), g.color(), g.owner(), nameFor(server, g.owner())));
                }
            }

            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new S2COpenInvites(list));
        }
    }

    public static void sendOnline(ServerPlayer viewer) {
        MinecraftServer server = viewer.getServer();
        if (server != null) {
            GroupData data = GroupData.get(server);
            List<S2COnlinePlayers.Entry> list = new ArrayList<>();

            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (data.getByMember(p.getUUID()) == null) {
                    list.add(new S2COnlinePlayers.Entry(p.getUUID(), p.getGameProfile().getName()));
                }
            }

            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new S2COnlinePlayers(list));
        }
    }

    /** Pieces of the group's claimed building, per tier. */
    private static List<S2COpenGroupManage.BuildCount> buildCounts(MinecraftServer server, Group g) {
        int[] counts = new int[Tier.values().length];
        for (ServerLevel level : server.getAllLevels()) {
            Claim claim = ClaimManager.getByGroup(level, g.id());
            Building b = claim == null ? null : StructureService.buildingOfClaim(level, claim.anchor());
            if (b != null) {
                for (Piece p : StructureStore.get(level).piecesOf(b)) {
                    counts[p.tier.ordinal()]++;
                }
            }
        }
        List<S2COpenGroupManage.BuildCount> out = new ArrayList<>();
        for (Tier t : Tier.values()) {
            if (counts[t.ordinal()] > 0) {
                out.add(new S2COpenGroupManage.BuildCount("tier.crac." + t.id, counts[t.ordinal()]));
            }
        }
        return out;
    }

    public static String nameFor(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            return p.getGameProfile().getName();
        } else {
            if (server.getProfileCache() != null) {
                Optional<GameProfile> opt = server.getProfileCache().get(id);
                if (opt.isPresent()) {
                    return opt.get().getName();
                }
            }

            return id.toString().substring(0, 8);
        }
    }
}
