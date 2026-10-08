package fr.heralisz.tactical.network.message;

import fr.heraliz.tactique.pont.Crochet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

public record SShieldDisable() {
   /** Fusion (heraliz_tactique) : traitement côté client (code d'origine), branché par fr.heralisz.tactical.client.PartieClient. */
   public static final Crochet<SShieldDisable> RECEPTION_CLIENT = new Crochet<>();

   public static void encode(SShieldDisable message, FriendlyByteBuf buf) {
   }

   public static SShieldDisable decode(FriendlyByteBuf buf) {
      return new SShieldDisable();
   }

   public static void handle(SShieldDisable message, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      if (context.getDirection().getReceptionSide().isClient()) {
         context.enqueueWork(() -> RECEPTION_CLIENT.accept(message));
      }

      context.setPacketHandled(true);
   }
}
