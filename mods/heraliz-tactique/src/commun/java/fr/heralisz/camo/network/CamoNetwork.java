package fr.heralisz.camo.network;

import fr.heralisz.camo.HeraliszCamo;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class CamoNetwork {
   /** 3 : collection de camos à vie (paquets 2 et 3). */
   private static final String VERSION = "3";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(HeraliszCamo.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

   public static void init() {
      CHANNEL.registerMessage(0, ApplyCamoPacket.class, ApplyCamoPacket::encode, ApplyCamoPacket::decode, ApplyCamoPacket::handle);
      CHANNEL.registerMessage(1, DecorPacket.class, DecorPacket::encode, DecorPacket::decode, DecorPacket::handle);
      CHANNEL.registerMessage(2, CollectionSyncPacket.class, CollectionSyncPacket::encode, CollectionSyncPacket::decode,
         CollectionSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(3, CollectionCamoPacket.class, CollectionCamoPacket::encode, CollectionCamoPacket::decode,
         CollectionCamoPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
   }

   /**
    * Fusion (heraliz_tactique) : un paquet client -> serveur et son expéditeur (NetworkEvent.Context.getSender(), peut être
    * null), passé au traitement côté serveur par le crochet RECEPTION_SERVEUR du paquet.
    */
   public record Recu<P>(P paquet, @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer joueur) {
   }

   private CamoNetwork() {}
}
