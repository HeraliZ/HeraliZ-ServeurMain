package fr.heraliz.crac.network;

import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.admin.AdminManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Traitement serveur du paquet C2SAdminAction (corps d'origine, docs/METHODE_CLIENT_SERVEUR.md §6) : le paquet commun ne garde
 * que son format ; ce traitement est appelé par serveur/PartieServeur.
 */
public final class C2SAdminActionTraitement {
    private C2SAdminActionTraitement() {
    }

    /** A refresh rebuilds the whole snapshot: keep even a trusted admin from flooding it. */
    static final long COOLDOWN_MS = 150L;
    /** a = group, b = the member who becomes its owner. */
    public static void traiter(C2SAdminAction msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && AdminManager.isAuthed(player) && PacketThrottle.allow(player, PacketThrottle.Kind.ADMIN, COOLDOWN_MS)) {
                MinecraftServer server = player.server;
                switch (msg.op()) {
                    case C2SAdminAction.KICK_MEMBER:
                        AdminManager.log(player, "kicked " + AdminManager.playerLabel(server, msg.b()) + " from " + AdminManager.groupLabel(server, msg.a()));
                        AdminManager.kickMember(player, msg.a(), msg.b());
                        break;
                    case C2SAdminAction.DISBAND_GROUP:
                        AdminManager.log(player, "disbanded " + AdminManager.groupLabel(server, msg.a()));
                        AdminManager.disbandGroup(player, msg.a());
                        break;
                    case C2SAdminAction.UNCLAIM:
                        AdminManager.log(player, "removed the claim of " + AdminManager.groupLabel(server, msg.a()));
                        AdminManager.unclaim(player, msg.a());
                        break;
                    case C2SAdminAction.WIPE_CLAIM:
                        String group = AdminManager.groupLabel(server, msg.a());
                        int removed = AdminManager.wipeClaim(player, msg.a());
                        AdminManager.log(player, "wiped the claim of " + group + " (" + removed + " pieces/blocks removed)");
                        player.displayClientMessage(Component.translatableWithFallback("crac.admin.wiped",
                            "§6[Admin] %s blocs ou structures effacés.", String.valueOf(removed)), false);
                        break;
                    case C2SAdminAction.TP_TO_PLAYER:
                        AdminManager.log(player, "teleported to " + AdminManager.playerLabel(server, msg.b()));
                        AdminManager.teleportTo(player, msg.b());
                        break;
                    case C2SAdminAction.SET_OWNER:
                        if (AdminManager.setOwner(player, msg.a(), msg.b())) {
                            AdminManager.log(player, "made " + AdminManager.playerLabel(server, msg.b()) + " the owner of " + AdminManager.groupLabel(server, msg.a()));
                        } else {
                            player.displayClientMessage(Component.translatableWithFallback("crac.admin.not_member",
                                "§c[Admin] Ce joueur n'est pas membre du groupe."), false);
                        }
                        break;
                    default:
                        break;
                }

                AdminManager.sendSnapshot(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }}
