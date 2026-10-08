package fr.heraliz.crac.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public record C2SAdminLogin(String password) {


    public static void encode(C2SAdminLogin m, FriendlyByteBuf buf) {
        buf.writeUtf(m.password, 128);
    }

    public static C2SAdminLogin decode(FriendlyByteBuf buf) {
        return new C2SAdminLogin(buf.readUtf(128));
    }

    public static void handle(C2SAdminLogin msg, Supplier<Context> ctx) {
        // traitement : C2SAdminLoginTraitement.traiter (src/serveur), branché par la partie serveur
        fr.heraliz.crac.network.NetworkHandler.recevoirServeur(msg, ctx);
    }
}
