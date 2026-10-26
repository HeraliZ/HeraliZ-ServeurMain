package com.mrcrayfish.backpacked.util;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BlockEntityUtil {
   public static void sendUpdatePacket(BlockEntity blockEntity) {
      Packet<ClientGamePacketListener> packet = blockEntity.getUpdatePacket();
      if (packet != null) {
         sendUpdatePacket(blockEntity.getLevel(), blockEntity.getBlockPos(), packet);
      }
   }

   public static void sendUpdatePacket(BlockEntity blockEntity, CompoundTag compound) {
      Packet<ClientGamePacketListener> packet = ClientboundBlockEntityDataPacket.create(blockEntity, blockEntity1 -> compound);
      sendUpdatePacket(blockEntity.getLevel(), blockEntity.getBlockPos(), packet);
   }

   public static void sendUpdatePacket(BlockEntity blockEntity, ServerPlayer player) {
      sendUpdatePacket(blockEntity, blockEntity.getUpdateTag(), player);
   }

   public static void sendUpdatePacket(BlockEntity blockEntity, CompoundTag compound, ServerPlayer player) {
      Packet<ClientGamePacketListener> packet = ClientboundBlockEntityDataPacket.create(blockEntity, blockEntity1 -> compound);
      player.connection.send(packet);
   }

   private static void sendUpdatePacket(Level level, BlockPos pos, Packet<ClientGamePacketListener> packet) {
      if (level instanceof ServerLevel serverLevel) {
         List<ServerPlayer> players = serverLevel.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false);
         players.forEach(player -> player.connection.send(packet));
      }
   }
}
