package fr.heraliz.casino.net;

import fr.heraliz.casino.block.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Action d'un joueur sur une machine (client -> serveur) : miser, lancer, tirer une carte, encaisser...
 */
public record ActionPacket(BlockPos pos, int action, long[] args) {
    private static final int MAX_ARGS = 64;

    public static void encode(ActionPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.args.length);
        for (long a : p.args) buf.writeVarLong(a);
    }

    public static ActionPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int action = buf.readVarInt();
        int n = Math.min(MAX_ARGS, Math.max(0, buf.readVarInt()));
        long[] args = new long[n];
        for (int i = 0; i < n; i++) args[i] = buf.readVarLong();
        return new ActionPacket(pos, action, args);
    }

    public static void handle(ActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || player.isSpectator()) return;
        if (!player.level().isLoaded(p.pos)) return;
        if (player.level().getBlockEntity(p.pos) instanceof MachineBlockEntity be && be.isNear(player)) {
            be.handleAction(player, p.action, p.args);
        }
    }
}
