package fr.heraliz.crac.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public record C2SAdminAction(int op, UUID a, UUID b, int num, boolean flag) {
    public static final int KICK_MEMBER = 1;
    public static final int DISBAND_GROUP = 2;
    public static final int UNCLAIM = 3;
    public static final int WIPE_CLAIM = 4;
    public static final int TP_TO_PLAYER = 5;
    public static final int SET_OWNER = 6;
    public static final int REFRESH = 0;






    private static final UUID NIL = new UUID(0L, 0L);


    public static C2SAdminAction of(int op) {
        return new C2SAdminAction(op, NIL, NIL, 0, false);
    }

    public static C2SAdminAction ofUuid(int op, UUID a) {
        return new C2SAdminAction(op, a, NIL, 0, false);
    }

    public static C2SAdminAction ofUuids(int op, UUID a, UUID b) {
        return new C2SAdminAction(op, a, b, 0, false);
    }

    public static C2SAdminAction ofInt(int op, int num) {
        return new C2SAdminAction(op, NIL, NIL, num, false);
    }

    public static C2SAdminAction ofFlag(int op, boolean flag) {
        return new C2SAdminAction(op, NIL, NIL, 0, flag);
    }

    public static void encode(C2SAdminAction m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.op);
        buf.writeUUID(m.a);
        buf.writeUUID(m.b);
        buf.writeVarInt(m.num);
        buf.writeBoolean(m.flag);
    }

    public static C2SAdminAction decode(FriendlyByteBuf buf) {
        return new C2SAdminAction(buf.readVarInt(), buf.readUUID(), buf.readUUID(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(C2SAdminAction msg, Supplier<Context> ctx) {
        // traitement : C2SAdminActionTraitement.traiter (src/serveur), branché par la partie serveur
        fr.heraliz.crac.network.NetworkHandler.recevoirServeur(msg, ctx);
    }
}
