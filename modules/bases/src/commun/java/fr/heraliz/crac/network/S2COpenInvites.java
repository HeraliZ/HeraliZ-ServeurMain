package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.group.GroupNames;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

public record S2COpenInvites(List<S2COpenInvites.InviteEntry> invites) {
    public static void encode(S2COpenInvites m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.invites.size());

        for (S2COpenInvites.InviteEntry e : m.invites) {
            buf.writeUUID(e.groupId());
            buf.writeUtf(e.groupName(), GroupNames.WIRE_MAX_NAME);
            buf.writeUtf(e.groupTag(), 8);
            buf.writeInt(e.groupColor());
            buf.writeUUID(e.inviterId());
            buf.writeUtf(e.inviterName(), 32);
        }
    }

    static final int MAX_INVITES = 1024;

    public static S2COpenInvites decode(FriendlyByteBuf buf) {
        int n = PacketLimits.readCount(buf, MAX_INVITES, 39);
        List<S2COpenInvites.InviteEntry> list = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            list.add(new S2COpenInvites.InviteEntry(buf.readUUID(), buf.readUtf(GroupNames.WIRE_MAX_NAME), buf.readUtf(8), buf.readInt(), buf.readUUID(), buf.readUtf(32)));
        }

        return new S2COpenInvites(list);
    }

    public static void handle(S2COpenInvites msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }


    public record InviteEntry(UUID groupId, String groupName, String groupTag, int groupColor, UUID inviterId, String inviterName) {
    }
}
