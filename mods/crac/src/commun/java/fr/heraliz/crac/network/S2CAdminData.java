package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

public record S2CAdminData(
    boolean ok,
    List<S2CAdminData.GroupInfo> groups,
    List<S2CAdminData.ClaimInfo> claims,
    List<S2CAdminData.PlayerInfo> players,
    List<S2CAdminData.ConfigInfo> config,
    List<S2CAdminData.PlacableInfo> placables
) {
    /** Longest config value shown in the panel (a long list joined with ", " can pass the old 4096). */
    public static final int MAX_VALUE_CHARS = 32767;
    static final int MAX_ROWS = 65536;

    public static void encode(S2CAdminData m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.ok);
        buf.writeVarInt(m.groups.size());

        for (S2CAdminData.GroupInfo g : m.groups) {
            buf.writeUUID(g.id());
            buf.writeUtf(g.name(), 64);
            buf.writeUtf(g.tag(), 16);
            buf.writeVarInt(g.claimTier());
            buf.writeVarInt(g.members().size());

            for (S2CAdminData.MemberInfo mem : g.members()) {
                buf.writeUUID(mem.id());
                buf.writeUtf(mem.name(), 48);
                buf.writeBoolean(mem.online());
                buf.writeBoolean(mem.owner());
            }
        }

        buf.writeVarInt(m.claims.size());

        for (S2CAdminData.ClaimInfo c : m.claims) {
            buf.writeUUID(c.groupId());
            buf.writeUtf(c.groupName(), 64);
            buf.writeVarInt(c.tier());
            buf.writeVarInt(c.centerX());
            buf.writeVarInt(c.centerZ());
            buf.writeVarInt(c.radius());
            buf.writeVarInt(c.pieces());
            buf.writeUtf(c.dimension(), 128);
        }

        buf.writeVarInt(m.players.size());

        for (S2CAdminData.PlayerInfo p : m.players) {
            buf.writeUUID(p.id());
            buf.writeUtf(p.name(), 48);
            buf.writeUtf(p.groupName(), 64);
            buf.writeVarInt(p.x());
            buf.writeVarInt(p.y());
            buf.writeVarInt(p.z());
            buf.writeUtf(p.dimension(), 128);
        }

        buf.writeVarInt(m.config.size());

        for (S2CAdminData.ConfigInfo c : m.config) {
            buf.writeUtf(c.path(), 128);
            buf.writeVarInt(c.type());
            buf.writeUtf(c.value(), MAX_VALUE_CHARS);
        }

        buf.writeVarInt(m.placables.size());

        for (S2CAdminData.PlacableInfo p : m.placables) {
            buf.writeUtf(p.id(), 256);
            buf.writeVarInt(p.hp());
            buf.writeBoolean(p.enabled());
        }
    }

    public static S2CAdminData decode(FriendlyByteBuf buf) {
        boolean ok = buf.readBoolean();
        int gn = PacketLimits.readCount(buf, MAX_ROWS, 20);
        List<S2CAdminData.GroupInfo> groups = new ArrayList<>(gn);

        for (int i = 0; i < gn; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(64);
            String tag = buf.readUtf(16);
            int tier = buf.readVarInt();
            int mn = PacketLimits.readCount(buf, MAX_ROWS, 19);
            List<S2CAdminData.MemberInfo> members = new ArrayList<>(mn);

            for (int j = 0; j < mn; j++) {
                members.add(new S2CAdminData.MemberInfo(buf.readUUID(), buf.readUtf(48), buf.readBoolean(), buf.readBoolean()));
            }

            groups.add(new S2CAdminData.GroupInfo(id, name, tag, tier, members));
        }

        int cn = PacketLimits.readCount(buf, MAX_ROWS, 22);
        List<S2CAdminData.ClaimInfo> claims = new ArrayList<>(cn);

        for (int i = 0; i < cn; i++) {
            claims.add(new S2CAdminData.ClaimInfo(
                buf.readUUID(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(128)
            ));
        }

        int pn = PacketLimits.readCount(buf, MAX_ROWS, 22);
        List<S2CAdminData.PlayerInfo> players = new ArrayList<>(pn);

        for (int i = 0; i < pn; i++) {
            players.add(
                new S2CAdminData.PlayerInfo(
                    buf.readUUID(), buf.readUtf(48), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(128)
                )
            );
        }

        int kn = PacketLimits.readCount(buf, MAX_ROWS, 3);
        List<S2CAdminData.ConfigInfo> config = new ArrayList<>(kn);

        for (int i = 0; i < kn; i++) {
            config.add(new S2CAdminData.ConfigInfo(buf.readUtf(128), buf.readVarInt(), buf.readUtf(MAX_VALUE_CHARS)));
        }

        int bn = PacketLimits.readCount(buf, MAX_ROWS, 3);
        List<S2CAdminData.PlacableInfo> placables = new ArrayList<>(bn);

        for (int i = 0; i < bn; i++) {
            placables.add(new S2CAdminData.PlacableInfo(buf.readUtf(256), buf.readVarInt(), buf.readBoolean()));
        }

        return new S2CAdminData(ok, groups, claims, players, config, placables);
    }

    public static void handle(S2CAdminData msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }


    /** {@code pieces}: number of pieces of the building the claim is attached to. */
    /** {@code dimension}: id of the level holding the claim. */
    public record ClaimInfo(UUID groupId, String groupName, int tier, int centerX, int centerZ, int radius, int pieces, String dimension) {
    }

    public record ConfigInfo(String path, int type, String value) {
    }

    public record GroupInfo(UUID id, String name, String tag, int claimTier, List<S2CAdminData.MemberInfo> members) {
    }

    public record MemberInfo(UUID id, String name, boolean online, boolean owner) {
    }

    public record PlacableInfo(String id, int hp, boolean enabled) {
    }

    public record PlayerInfo(UUID id, String name, String groupName, int x, int y, int z, String dimension) {
    }
}
