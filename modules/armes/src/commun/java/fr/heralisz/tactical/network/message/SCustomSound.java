package fr.heralisz.tactical.network.message;

import fr.heraliz.tactique.pont.Crochet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent.Context;

public record SCustomSound(SCustomSound.SoundType type, ResourceLocation id, String key, Vec3 pos, float volume, float pitch) {
   /** Fusion (heraliz_tactique) : traitement côté client (code d'origine), branché par fr.heralisz.tactical.client.PartieClient. */
   public static final Crochet<SCustomSound> RECEPTION_CLIENT = new Crochet<>();

   public static void encode(SCustomSound message, FriendlyByteBuf buf) {
      buf.writeEnum(message.type());
      buf.writeResourceLocation(message.id());
      buf.writeUtf(message.key());
      buf.writeDouble(message.pos().x);
      buf.writeDouble(message.pos().y);
      buf.writeDouble(message.pos().z);
      buf.writeFloat(message.volume());
      buf.writeFloat(message.pitch());
   }

   public static SCustomSound decode(FriendlyByteBuf buf) {
      return new SCustomSound(
         (SCustomSound.SoundType)buf.readEnum(SCustomSound.SoundType.class),
         buf.readResourceLocation(),
         buf.readUtf(),
         new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
         buf.readFloat(),
         buf.readFloat()
      );
   }

   public static void handle(SCustomSound message, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      if (context.getDirection().getReceptionSide().isClient()) {
         context.enqueueWork(() -> RECEPTION_CLIENT.accept(message));
      }

      context.setPacketHandled(true);
   }

   public enum SoundType {
      MELEE,
      THROWABLE;
   }
}
