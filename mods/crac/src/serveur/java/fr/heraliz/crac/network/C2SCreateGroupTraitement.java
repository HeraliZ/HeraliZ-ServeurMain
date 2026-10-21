package fr.heraliz.crac.network;

import fr.heraliz.crac.heraliz.Txt;
import java.util.function.Supplier;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.group.GroupNames;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Traitement serveur du paquet C2SCreateGroup (corps d'origine, docs/METHODE_CLIENT_SERVEUR.md §6) : le paquet commun ne garde
 * que son format ; ce traitement est appelé par serveur/PartieServeur.
 */
public final class C2SCreateGroupTraitement {
    private C2SCreateGroupTraitement() {
    }

    static final long COOLDOWN_MS = 1000L;
    public static void traiter(C2SCreateGroup msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && fr.heraliz.crac.heraliz.BasesClans.actif()) {
                // une base par clan (1.16.0) : plus de groupes faits à la main
                player.displayClientMessage(net.minecraft.network.chat.Component.translatableWithFallback("crac.group.clans_seulement",
                "Les bases sont celles des clans : crée ou rejoins un clan (touche K) ; les membres et leurs droits se règlent dans le clan.").withStyle(net.minecraft.ChatFormatting.RED), false);
            } else if (player != null && player.getServer() != null && PacketThrottle.allow(player, PacketThrottle.Kind.GROUP_CREATE, COOLDOWN_MS)) {
                int maxLength = Config.groupNameMaxLength.get();
                String name = GroupNames.cleanName(msg.name(), maxLength);
                String tag = GroupNames.cleanTag(msg.tag());
                if (name == null) {
                    fail(player, Txt.k("crac.group.bad_name", maxLength));
                } else if (tag == null) {
                    fail(player, "crac.group.bad_tag");
                } else {
                    GroupData data = GroupData.get(player.getServer());
                    if (data.getByMember(player.getUUID()) != null) {
                        fail(player, "crac.group.already_in");
                    } else if (data.tagTaken(tag)) {
                        fail(player, "crac.group.tag_taken");
                    } else {
                        int color = msg.color() & 16777215;
                        Group g = data.create(name, tag, color, player.getUUID());
                        if (g == null) {
                            fail(player, "crac.group.create_failed");
                        } else {
                            player.displayClientMessage(
                                Txt.green("crac.group.created", g.name(), g.tag()),
                                false
                            );
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }    private static void fail(ServerPlayer player, String reason) {
        player.displayClientMessage(Txt.red(reason), false);
    }}
