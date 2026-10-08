package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.registries.ForgeRegistries;

public record C2SAdminPlacable(List<String> ids, List<Integer> hp, List<Boolean> enabled) {
    public static final int MAX_HP = 1000000;
    public static final int MAX_ENTRIES = 4096;


    public static void encode(C2SAdminPlacable m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.ids.size());

        for (int i = 0; i < m.ids.size(); i++) {
            buf.writeUtf(m.ids.get(i), 256);
            buf.writeVarInt(m.hp.get(i));
            buf.writeBoolean(m.enabled.get(i));
        }
    }

    public static C2SAdminPlacable decode(FriendlyByteBuf buf) {
        int n = PacketLimits.readCount(buf, MAX_ENTRIES, 3);
        List<String> ids = new ArrayList<>(n);
        List<Integer> hp = new ArrayList<>(n);
        List<Boolean> enabled = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            ids.add(buf.readUtf(256));
            hp.add(buf.readVarInt());
            enabled.add(buf.readBoolean());
        }

        return new C2SAdminPlacable(ids, hp, enabled);
    }

    public static void handle(C2SAdminPlacable msg, Supplier<Context> ctx) {
        // traitement : C2SAdminPlacableTraitement.traiter (src/serveur), branché par la partie serveur
        fr.heraliz.crac.network.NetworkHandler.recevoirServeur(msg, ctx);
    }
}
