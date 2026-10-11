package fr.heraliz.tactique.allonge;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur -> joueur : le serveur a refusé que le joueur se rallonge (délai pas encore écoulé de son côté). TaCZ n'envoie
 * jamais l'état « allongé » au joueur : sans ce paquet, le joueur resterait couché chez lui et debout pour les autres.
 * Le joueur se relève au tick suivant et attend {@code restantMs} (traitement : {@link ReseauAllonge#REFUS_CLIENT},
 * branché par la partie client).
 */
public record PaquetRefusAllonge(long restantMs) {
   public static void encode(PaquetRefusAllonge paquet, FriendlyByteBuf buf) {
      buf.writeVarLong(paquet.restantMs());
   }

   public static PaquetRefusAllonge decode(FriendlyByteBuf buf) {
      return new PaquetRefusAllonge(buf.readVarLong());
   }

   public static void handle(PaquetRefusAllonge paquet, Supplier<NetworkEvent.Context> contexte) {
      NetworkEvent.Context c = contexte.get();
      if (c.getDirection().getReceptionSide().isClient()) {
         c.enqueueWork(() -> ReseauAllonge.REFUS_CLIENT.accept(paquet));
      }
      c.setPacketHandled(true);
   }
}
