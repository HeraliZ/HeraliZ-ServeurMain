package fr.heraliz.crac.network;

import fr.heraliz.crac.heraliz.Txt;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.event.ClaimBlockHandler;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.group.GroupSync;
import fr.heraliz.crac.raid.RaidBlockManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;
import org.jetbrains.annotations.Nullable;

/**
 * Traitement serveur du paquet C2SGroupAction (corps d'origine, docs/METHODE_CLIENT_SERVEUR.md §6) : le paquet commun ne garde
 * que son format ; ce traitement est appelé par serveur/PartieServeur.
 */
public final class C2SGroupActionTraitement {
    private C2SGroupActionTraitement() {
    }

    /** Each action may rebuild the manage screen (member names, piece counts): one per player every 250 ms. */
    static final long COOLDOWN_MS = 250L;
    public static void traiter(C2SGroupAction msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null && msg.type() != null
                && PacketThrottle.allow(player, PacketThrottle.Kind.GROUP, COOLDOWN_MS)) {
                process(player, player.getServer(), msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }    private static void process(ServerPlayer player, MinecraftServer server, C2SGroupAction msg) {
        GroupData data = GroupData.get(server);
        Group g = data.getByMember(player.getUUID());
        if (g == null) {
            return;
        }
        switch (msg.type()) {
            case REQUEST_ONLINE -> GroupSync.sendOnline(player);
            case REFRESH -> GroupSync.sendManage(player, g);
            case DISBAND -> {
                if (!g.isOwner(player.getUUID())) {
                    deny(player);
                    return;
                }
                String problem = disbandProblem(server, g);
                if (problem != null) {
                    player.displayClientMessage(Txt.red(problem), false);
                    return;
                }
                List<ServerLevel> released = new ArrayList<>();
                for (ServerLevel level : server.getAllLevels()) {
                    if (ClaimManager.releaseClaim(level, g.id())) {
                        released.add(level);
                    }
                }
                data.disband(g);
                // the claim vanished for everyone in its level; a group without a claim changes nobody's claim view
                for (ServerLevel level : released) {
                    C2SRequestNearbyClaimsTraitement.pushAll(level);
                }
                player.displayClientMessage(Txt.yellow("crac.group.disbanded"), false);
            }
            case LEAVE -> {
                if (g.isOwner(player.getUUID())) {
                    player.displayClientMessage(Txt.red("crac.group.owner_leave"), false);
                    return;
                }
                if (data.removeMember(g, player.getUUID())) {
                    C2SRequestNearbyClaimsTraitement.pushTo(server, List.of(player.getUUID()));
                    player.displayClientMessage(Txt.yellow("crac.group.left"), false);
                }
            }
            case KICK -> {
                if (!g.isOwner(player.getUUID())) {
                    deny(player);
                    return;
                }
                // only a real membership change refreshes anything
                if (!msg.target().equals(g.owner()) && data.removeMember(g, msg.target())) {
                    C2SRequestNearbyClaimsTraitement.pushTo(server, List.of(msg.target()));
                    ServerPlayer kicked = server.getPlayerList().getPlayer(msg.target());
                    if (kicked != null) {
                        kicked.displayClientMessage(
                            Txt.yellow("crac.group.kicked", g.name()),
                            false
                        );
                    }
                    GroupSync.sendManage(player, g);
                }
            }
            case ADD -> invite(player, server, data, g, msg.target());
            case PROMOTE -> {
                if (!g.isOwner(player.getUUID())) {
                    deny(player);
                    return;
                }
                if (!data.transferOwnership(g, msg.target())) {
                    player.displayClientMessage(Txt.red("crac.group.not_member"), false);
                    return;
                }
                String name = GroupSync.nameFor(server, msg.target());
                player.displayClientMessage(
                    Txt.green("crac.group.now_owns", name),
                    false
                );
                ServerPlayer promoted = server.getPlayerList().getPlayer(msg.target());
                if (promoted != null) {
                    promoted.displayClientMessage(
                        Txt.green("crac.group.you_own", g.name()),
                        false
                    );
                }
                GroupSync.sendManage(player, g);
            }
        }
    }    /**
     * Why the owner may not disband the group now, or null. Disbanding breaks the Claim Block, which its raid block
     * forbids just like mining it; an admin can still disband the group.
     */
    @Nullable
    public static String disbandProblem(MinecraftServer server, Group g) {
        ServerLevel level = ClaimManager.levelOfGroupClaim(server, g.id());
        Claim claim = level == null ? null : ClaimManager.getByGroup(level, g.id());
        if (claim != null && RaidBlockManager.isRaidBlocked(level, claim, claim.anchor())) {
            return ClaimBlockHandler.RAID_BLOCKED_REMOVAL;
        }
        return null;
    }    private static void invite(ServerPlayer player, MinecraftServer server, GroupData data, Group g, UUID targetId) {
        if (!g.isOwner(player.getUUID())) {
            deny(player);
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null || target == player) {
            player.displayClientMessage(Txt.red("crac.group.not_online"), false);
        } else if (g.isFull()) {
            player.displayClientMessage(Txt.red("crac.group.full"), false);
        } else if (data.getByMember(targetId) != null) {
            player.displayClientMessage(Txt.red("crac.group.target_in_group"), false);
        } else {
            switch (data.invite(targetId, g.id())) {
                case ALREADY_INVITED -> player.displayClientMessage(Txt.red("crac.group.already_invited"), false);
                case PLAYER_FULL -> player.displayClientMessage(Txt.red("crac.group.player_full"), false);
                case GROUP_FULL -> player.displayClientMessage(
                    Txt.red("crac.group.group_full_invites"), false
                );
                case OK -> {
                    target.displayClientMessage(
                        Txt.gray("crac.group.invited_you", Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.BLUE),
                            Component.literal(g.name()).withStyle(ChatFormatting.WHITE)),
                        false
                    );
                    player.displayClientMessage(Txt.green("crac.group.invite_sent"), false);
                    GroupSync.sendManage(player, g);
                }
            }
        }
    }    private static void deny(ServerPlayer player) {
        player.displayClientMessage(Txt.red("crac.group.owner_only"), false);
    }}
