package fr.heraliz.crac.network;

import fr.heraliz.crac.heraliz.Txt;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.group.Group;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public record C2SInviteResponse(UUID groupId, boolean accept) {


    public static void encode(C2SInviteResponse m, FriendlyByteBuf buf) {
        buf.writeUUID(m.groupId);
        buf.writeBoolean(m.accept);
    }

    public static C2SInviteResponse decode(FriendlyByteBuf buf) {
        return new C2SInviteResponse(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(C2SInviteResponse msg, Supplier<Context> ctx) {
        // traitement : C2SInviteResponseTraitement.traiter (src/serveur), branché par la partie serveur
        fr.heraliz.crac.network.NetworkHandler.recevoirServeur(msg, ctx);
    }
}
