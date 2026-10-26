package fr.heraliz.crac.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class PlacementSync {
    private PlacementSync() {
    }

    public static void revert(ServerPlayer player, ServerLevel level, BlockPos clicked, Direction face) {
        BlockPos target = face == null ? clicked.above() : clicked.relative(face);
        send(player, level, clicked);
        send(player, level, target);
        send(player, level, target.above());
        player.containerMenu.broadcastFullState();
    }

    public static void revert(ServerPlayer player, ServerLevel level, BlockPos pos) {
        send(player, level, pos);
        send(player, level, pos.above());
        player.containerMenu.broadcastFullState();
    }

    private static void send(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
    }
}
