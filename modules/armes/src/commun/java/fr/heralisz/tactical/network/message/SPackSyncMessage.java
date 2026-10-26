package fr.heralisz.tactical.network.message;

import java.util.HashMap;
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

   /** Plafonds de lecture : une taille annoncée plus grande est refusée avant toute réservation de mémoire. */
   private static final int MAX_ENTREES = 65536;
   private static final int MAX_TEXTE = 262144;

   public static SPackSyncMessage decode(FriendlyByteBuf buf) {
      int types = buf.readVarInt();
      if (types < 0 || types > DataType.values().length) {
         throw new IllegalArgumentException("SPackSyncMessage : " + types + " types annoncés");
      }
      Map<DataType, Map<ResourceLocation, String>> map = new HashMap<>();
      for (int i = 0; i < types; i++) {
         DataType type = buf.readEnum(DataType.class);
         int n = buf.readVarInt();
         if (n < 0 || n > MAX_ENTREES) {
            throw new IllegalArgumentException("SPackSyncMessage : " + n + " entrées annoncées");
         }
         Map<ResourceLocation, String> sous = new HashMap<>();
         for (int j = 0; j < n; j++) {
            sous.put(buf.readResourceLocation(), buf.readUtf(MAX_TEXTE));
         }
         map.put(type, sous);
      }
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
