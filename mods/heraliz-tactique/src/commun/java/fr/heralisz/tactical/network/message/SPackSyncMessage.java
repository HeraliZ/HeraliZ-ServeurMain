package fr.heralisz.tactical.network.message;

import java.util.Map;
import java.util.function.Supplier;
import fr.heralisz.tactical.network.DataType;
import fr.heralisz.tactical.resource.CommonNetworkCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent.Context;

public record SPackSyncMessage(Map<DataType, Map<ResourceLocation, String>> cache) {
   public static void encode(SPackSyncMessage message, FriendlyByteBuf buf) {
      buf.writeMap(
         message.cache(), FriendlyByteBuf::writeEnum, (buf1, map) -> buf1.writeMap(map, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeUtf)
      );
   }

   public static SPackSyncMessage decode(FriendlyByteBuf buf) {
      Map<DataType, Map<ResourceLocation, String>> map = buf.readMap(
         buf1 -> (DataType)buf1.readEnum(DataType.class), buf2 -> buf2.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readUtf)
      );
      return new SPackSyncMessage(map);
   }

   public static void handle(SPackSyncMessage message, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      if (context.getDirection().getReceptionSide().isClient()) {
         context.enqueueWork(() -> doSync(message));
      }

      context.setPacketHandled(true);
   }

   // Fusion (heraliz_tactique) : commun (CommonNetworkCache est commun), @OnlyIn(CLIENT) retiré (jamais appelé hors client).
   private static void doSync(SPackSyncMessage message) {
      CommonNetworkCache.INSTANCE.fromNetwork(message.cache);
   }
}
