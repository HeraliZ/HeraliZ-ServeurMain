package fr.heralisz.tactical.network.message;

import fr.heraliz.tactique.pont.Crochet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

public record SResetMeleeSyncMessage() {
   /** Fusion (heraliz_tactique) : traitement côté client (code d'origine), branché par fr.heralisz.tactical.client.PartieClient. */
   public static final Crochet<SResetMeleeSyncMessage> RECEPTION_CLIENT = new Crochet<>();

   public static void encode(SResetMeleeSyncMessage message, FriendlyByteBuf buf) {
   }

   public static SResetMeleeSyncMessage decode(FriendlyByteBuf buf) {
      return new SResetMeleeSyncMessage();
   }

   public static void handle(SResetMeleeSyncMessage message, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      if (context.getDirection().getReceptionSide().isClient()) {
         context.enqueueWork(() -> RECEPTION_CLIENT.accept(message));
      }

      context.setPacketHandled(true);
   }
}
