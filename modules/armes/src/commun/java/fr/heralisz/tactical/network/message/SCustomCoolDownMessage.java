package fr.heralisz.tactical.network.message;

import fr.heraliz.tactique.pont.Crochet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent.Context;

public record SCustomCoolDownMessage(ResourceLocation id, int duration) {
   /** Fusion (heraliz_tactique) : traitement côté client (code d'origine), branché par fr.heralisz.tactical.client.PartieClient. */
   public static final Crochet<SCustomCoolDownMessage> RECEPTION_CLIENT = new Crochet<>();

   public static void encode(SCustomCoolDownMessage message, FriendlyByteBuf buf) {
      buf.writeResourceLocation(message.id);
      buf.writeInt(message.duration);
   }

   public static SCustomCoolDownMessage decode(FriendlyByteBuf buf) {
      return new SCustomCoolDownMessage(buf.readResourceLocation(), buf.readInt());
   }

   public static void handle(SCustomCoolDownMessage message, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      if (context.getDirection().getReceptionSide().isClient()) {
         context.enqueueWork(() -> RECEPTION_CLIENT.accept(message));
      }

      context.setPacketHandled(true);
   }
}
