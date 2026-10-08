package fr.heralisz.camo.network;

import fr.heralisz.camo.HeraliszCamo;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class CamoNetwork {
   private static final String VERSION = "2";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(HeraliszCamo.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

   public static void init() {
      CHANNEL.registerMessage(0, ApplyCamoPacket.class, ApplyCamoPacket::encode, ApplyCamoPacket::decode, ApplyCamoPacket::handle);
      CHANNEL.registerMessage(1, DecorPacket.class, DecorPacket::encode, DecorPacket::decode, DecorPacket::handle);
   }

   /**
    * Fusion (heraliz_tactique) : un paquet client -> serveur et son expéditeur (NetworkEvent.Context.getSender(), peut être
    * null), passé au traitement côté serveur par le crochet RECEPTION_SERVEUR du paquet.
    */
   public record Recu<P>(P paquet, @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer joueur) {
   }

   private CamoNetwork() {}
}
