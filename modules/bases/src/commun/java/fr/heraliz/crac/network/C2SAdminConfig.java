package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.network.NetworkEvent.Context;

public record C2SAdminConfig(List<String> paths, List<String> values) {
    /** Most edits in one packet; the panel splits bigger batches. */
    public static final int MAX_EDITS = 256;
    public static final int MAX_PATH_CHARS = 128;
    public static final int MAX_VALUE_CHARS = 8192;

    public static void encode(C2SAdminConfig m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.paths.size());

        for (int i = 0; i < m.paths.size(); i++) {
            buf.writeUtf(m.paths.get(i), MAX_PATH_CHARS);
            buf.writeUtf(m.values.get(i), MAX_VALUE_CHARS);
        }
    }

    public static C2SAdminConfig decode(FriendlyByteBuf buf) {
        int n = PacketLimits.readCount(buf, MAX_EDITS, 2);
        List<String> paths = new ArrayList<>(n);
        List<String> values = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            paths.add(buf.readUtf(MAX_PATH_CHARS));
            values.add(buf.readUtf(MAX_VALUE_CHARS));
        }

        return new C2SAdminConfig(paths, values);
    }

    public static void handle(C2SAdminConfig msg, Supplier<Context> ctx) {
        // traitement : C2SAdminConfigTraitement.traiter (src/serveur), branché par la partie serveur
        fr.heraliz.crac.network.NetworkHandler.recevoirServeur(msg, ctx);
    }
}
