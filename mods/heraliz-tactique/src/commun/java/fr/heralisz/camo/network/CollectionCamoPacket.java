package fr.heralisz.camo.network;

import fr.heraliz.tactique.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> serveur : poser sur l'arme tenue un camo de la collection à vie ({@link #APPLIQUER}, {@code camo} = son id) ou
 * retirer le camo à vie qu'elle porte ({@link #RETIRER}). Tout est revérifié côté serveur (possession, délai après une
 * mort, arme compatible).
 */
public record CollectionCamoPacket(int action, String camo) {
   /** Traitement côté serveur, branché par fr.heralisz.camo.serveur.PartieServeur. */
   public static final Crochet<CamoNetwork.Recu<CollectionCamoPacket>> RECEPTION_SERVEUR = new Crochet<>();

   public static final int APPLIQUER = 0, RETIRER = 1;

   public static void encode(CollectionCamoPacket p, FriendlyByteBuf buf) {
      buf.writeVarInt(p.action);
      buf.writeUtf(p.camo, 64);
   }

   public static CollectionCamoPacket decode(FriendlyByteBuf buf) {
      return new CollectionCamoPacket(buf.readVarInt(), buf.readUtf(64));
   }

   public static void handle(CollectionCamoPacket p, Supplier<NetworkEvent.Context> ctx) {
      NetworkEvent.Context c = ctx.get();
      c.enqueueWork(() -> RECEPTION_SERVEUR.accept(new CamoNetwork.Recu<>(p, c.getSender())));
      c.setPacketHandled(true);
   }
}
