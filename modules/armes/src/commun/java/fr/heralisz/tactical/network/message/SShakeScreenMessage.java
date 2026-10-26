package fr.heralisz.tactical.network.message;

import fr.heraliz.tactique.pont.Crochet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent.Context;

public record SShakeScreenMessage(double time, double radius, double amplitude, Vec3 position) {
   /** Fusion (heraliz_tactique) : traitement côté client (code d'origine), branché par fr.heralisz.tactical.client.PartieClient. */
   public static final Crochet<SShakeScreenMessage> RECEPTION_CLIENT = new Crochet<>();

   public static void encode(SShakeScreenMessage message, FriendlyByteBuf buffer) {
      buffer.writeDouble(message.time);
      buffer.writeDouble(message.radius);
      buffer.writeDouble(message.amplitude);
      buffer.writeDouble(message.position.x);
      buffer.writeDouble(message.position.y);
      buffer.writeDouble(message.position.z);
   }

   public static SShakeScreenMessage decode(FriendlyByteBuf buffer) {
      return new SShakeScreenMessage(
         buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
      );
   }

   public static void handle(SShakeScreenMessage message, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      if (context.getDirection().getReceptionSide().isClient()) {
         context.enqueueWork(() -> RECEPTION_CLIENT.accept(message));
      }

      context.setPacketHandled(true);
   }
}
