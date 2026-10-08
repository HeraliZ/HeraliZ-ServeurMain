package fr.heralisz.tactical.network.message;

import fr.heraliz.tactique.pont.Crochet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

public record SShieldShake() {
   /** Fusion (heraliz_tactique) : traitement côté client (code d'origine), branché par fr.heralisz.tactical.client.PartieClient. */
   public static final Crochet<SShieldShake> RECEPTION_CLIENT = new Crochet<>();

   public static void encode(SShieldShake message, FriendlyByteBuf buf) {
   }

   public static SShieldShake decode(FriendlyByteBuf buf) {
      return new SShieldShake();
   }

   public static void handle(SShieldShake message, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      if (context.getDirection().getReceptionSide().isClient()) {
         context.enqueueWork(() -> RECEPTION_CLIENT.accept(message));
      }

      context.setPacketHandled(true);
   }
}
