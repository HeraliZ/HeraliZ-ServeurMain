package fr.heralisz.tactical.network.message;

import fr.heraliz.tactique.pont.Crochet;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

public record SSplashParticle(BlockPos blockPos, int color) {
   /** Fusion (heraliz_tactique) : traitement côté client (code d'origine), branché par fr.heralisz.tactical.client.PartieClient. */
   public static final Crochet<SSplashParticle> RECEPTION_CLIENT = new Crochet<>();

   public static void encode(SSplashParticle message, FriendlyByteBuf buf) {
      buf.writeBlockPos(message.blockPos);
      buf.writeInt(message.color);
   }

   public static SSplashParticle decode(FriendlyByteBuf buf) {
      return new SSplashParticle(buf.readBlockPos(), buf.readInt());
   }

   public static void handle(SSplashParticle message, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      if (context.getDirection().getReceptionSide().isClient()) {
         context.enqueueWork(() -> RECEPTION_CLIENT.accept(message));
      }

      context.setPacketHandled(true);
   }
}
