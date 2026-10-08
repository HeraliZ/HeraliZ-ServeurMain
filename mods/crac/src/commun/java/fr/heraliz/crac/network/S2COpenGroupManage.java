package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.group.GroupNames;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

public record S2COpenGroupManage(
    String name,
    String tag,
    int color,
    UUID owner,
    boolean viewerIsOwner,
    List<S2COpenGroupManage.MemberEntry> members,
    boolean hasClaim,
    BlockPos claimCenter,
    List<S2COpenGroupManage.BuildCount> builds
) {
    static final int MAX_MEMBERS = 1024;
    static final int MAX_BUILD_ROWS = 64;

    public static void encode(S2COpenGroupManage m, FriendlyByteBuf buf) {
        buf.writeUtf(m.name, GroupNames.WIRE_MAX_NAME);
        buf.writeUtf(m.tag, 8);
        buf.writeInt(m.color);
        buf.writeUUID(m.owner);
        buf.writeBoolean(m.viewerIsOwner);
        buf.writeVarInt(m.members.size());

        for (S2COpenGroupManage.MemberEntry e : m.members) {
            buf.writeUUID(e.id());
            buf.writeUtf(e.name(), 32);
            buf.writeBoolean(e.online());
        }

        buf.writeBoolean(m.hasClaim);
        buf.writeBlockPos(m.claimCenter);
        buf.writeVarInt(m.builds.size());

        for (S2COpenGroupManage.BuildCount b : m.builds) {
            buf.writeUtf(b.label(), 32);
            buf.writeVarInt(b.count());
        }
    }

    public static S2COpenGroupManage decode(FriendlyByteBuf buf) {
        String name = buf.readUtf(GroupNames.WIRE_MAX_NAME);
        String tag = buf.readUtf(8);
        int color = buf.readInt();
        UUID owner = buf.readUUID();
        boolean isOwner = buf.readBoolean();
        int n = PacketLimits.readCount(buf, MAX_MEMBERS, 18);
        List<S2COpenGroupManage.MemberEntry> members = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            members.add(new S2COpenGroupManage.MemberEntry(buf.readUUID(), buf.readUtf(32), buf.readBoolean()));
        }

        boolean hasClaim = buf.readBoolean();
        BlockPos center = buf.readBlockPos();
        int bn = PacketLimits.readCount(buf, MAX_BUILD_ROWS, 2);
        List<S2COpenGroupManage.BuildCount> builds = new ArrayList<>(bn);

        for (int i = 0; i < bn; i++) {
            builds.add(new S2COpenGroupManage.BuildCount(buf.readUtf(32), buf.readVarInt()));
        }

        return new S2COpenGroupManage(name, tag, color, owner, isOwner, members, hasClaim, center, builds);
    }

    public static void handle(S2COpenGroupManage msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }


    public record BuildCount(String label, int count) {
    }

    public record MemberEntry(UUID id, String name, boolean online) {
    }
}
