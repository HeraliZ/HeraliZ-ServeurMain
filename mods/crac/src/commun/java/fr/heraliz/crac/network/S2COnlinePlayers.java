package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

public record S2COnlinePlayers(List<S2COnlinePlayers.Entry> players) {
    public static void encode(S2COnlinePlayers m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.players.size());

        for (S2COnlinePlayers.Entry e : m.players) {
            buf.writeUUID(e.id());
            buf.writeUtf(e.name(), 32);
        }
    }

    static final int MAX_PLAYERS = 65536;

    public static S2COnlinePlayers decode(FriendlyByteBuf buf) {
        int n = PacketLimits.readCount(buf, MAX_PLAYERS, 17);
        List<S2COnlinePlayers.Entry> list = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            list.add(new S2COnlinePlayers.Entry(buf.readUUID(), buf.readUtf(32)));
        }

        return new S2COnlinePlayers(list);
    }

    public static void handle(S2COnlinePlayers msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }


    public record Entry(UUID id, String name) {
    }
}
