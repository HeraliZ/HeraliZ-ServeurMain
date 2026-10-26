package fr.heraliz.crac.network;

import fr.heraliz.crac.heraliz.Txt;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.group.GroupSync;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Traitement serveur du paquet C2SInviteResponse (corps d'origine, docs/METHODE_CLIENT_SERVEUR.md §6) : le paquet commun ne garde
 * que son format ; ce traitement est appelé par serveur/PartieServeur.
 */
public final class C2SInviteResponseTraitement {
    private C2SInviteResponseTraitement() {
    }

    static final long COOLDOWN_MS = 250L;
    public static void traiter(C2SInviteResponse msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null && PacketThrottle.allow(player, PacketThrottle.Kind.INVITE_RESPONSE, COOLDOWN_MS)) {
                GroupData data = GroupData.get(player.getServer());
                UUID self = player.getUUID();
                if (data.hasInvite(self, msg.groupId())) {
                    data.removeInvite(self, msg.groupId());
                    if (!msg.accept()) {
                        GroupSync.sendInvites(player);
                    } else if (data.getByMember(self) != null) {
                        player.displayClientMessage(Txt.red("crac.group.already_in"), false);
                    } else {
                        Group g = data.getById(msg.groupId());
                        if (g == null) {
                            player.displayClientMessage(Txt.red("crac.group.gone"), false);
                        } else if (g.isFull()) {
                            player.displayClientMessage(Txt.red("crac.group.that_full"), false);
                        } else {
                            data.addMember(g, self);
                            // only the joiner's view of claims changes (their group's claim becomes "own")
                            C2SRequestNearbyClaimsTraitement.pushTo(player.getServer(), List.of(self));
                            player.displayClientMessage(
                                Txt.green("crac.group.joined", g.name()),
                                false
                            );
                            GroupSync.sendManage(player, g);
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }}
