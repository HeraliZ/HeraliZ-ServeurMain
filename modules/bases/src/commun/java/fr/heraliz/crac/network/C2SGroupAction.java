package fr.heraliz.crac.network;

import fr.heraliz.crac.heraliz.Txt;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.group.Group;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;
import org.jetbrains.annotations.Nullable;

/** Group management action. {@code type} is null when the client sent an unknown action. */
public record C2SGroupAction(@Nullable C2SGroupAction.Type type, UUID target) {

    private static final UUID NIL = new UUID(0L, 0L);

    public static void encode(C2SGroupAction m, FriendlyByteBuf buf) {
        buf.writeEnum(m.type);
        buf.writeUUID(m.target == null ? NIL : m.target);
    }

    public static C2SGroupAction decode(FriendlyByteBuf buf) {
        return new C2SGroupAction(PacketLimits.readEnumOrNull(buf, C2SGroupAction.Type.class), buf.readUUID());
    }











    public static enum Type {
        REQUEST_ONLINE,
        KICK,
        ADD,
        DISBAND,
        REFRESH,
        LEAVE,
        /** Hand the group over to a member (the owner stays a member). */
        PROMOTE;
    }

    public static void handle(C2SGroupAction msg, Supplier<Context> ctx) {
        // traitement : C2SGroupActionTraitement.traiter (src/serveur), branché par la partie serveur
        fr.heraliz.crac.network.NetworkHandler.recevoirServeur(msg, ctx);
    }
}
