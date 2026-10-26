package fr.heraliz.reanimation.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
   private static final String PROTOCOL = "4";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation("reanimation", "main"), () -> "4", "4"::equals, "4"::equals
   );

   private Network() {
   }

   public static void register() {
      int id = 0;
      CHANNEL.messageBuilder(SyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(SyncPacket::encode)
         .decoder(SyncPacket::new)
         .consumerMainThread(SyncPacket::handle)
         .add();
      CHANNEL.messageBuilder(ActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(ActionPacket::encode)
         .decoder(ActionPacket::new)
         .consumerMainThread(ActionPacket::handle)
         .add();
      CHANNEL.messageBuilder(UnlocksPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(UnlocksPacket::encode)
         .decoder(UnlocksPacket::new)
         .consumerMainThread(UnlocksPacket::handle)
         .add();
   }

   public static void sendTo(ServerPlayer player, Object packet) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
   }

   public static void sendToServer(Object packet) {
      CHANNEL.sendToServer(packet);
   }
}
