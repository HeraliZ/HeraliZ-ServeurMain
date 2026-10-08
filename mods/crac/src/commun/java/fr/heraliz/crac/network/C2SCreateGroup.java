package fr.heraliz.crac.network;

import fr.heraliz.crac.heraliz.Txt;
import java.util.function.Supplier;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupNames;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public record C2SCreateGroup(String name, String tag, int color) {


    public static void encode(C2SCreateGroup msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name, GroupNames.WIRE_MAX_NAME);
        buf.writeUtf(msg.tag, GroupNames.WIRE_MAX_TAG);
        buf.writeInt(msg.color);
    }

    public static C2SCreateGroup decode(FriendlyByteBuf buf) {
        return new C2SCreateGroup(buf.readUtf(GroupNames.WIRE_MAX_NAME), buf.readUtf(GroupNames.WIRE_MAX_TAG), buf.readInt());
    }

    public static void handle(C2SCreateGroup msg, Supplier<Context> ctx) {
        // traitement : C2SCreateGroupTraitement.traiter (src/serveur), branché par la partie serveur
        fr.heraliz.crac.network.NetworkHandler.recevoirServeur(msg, ctx);
    }
}
