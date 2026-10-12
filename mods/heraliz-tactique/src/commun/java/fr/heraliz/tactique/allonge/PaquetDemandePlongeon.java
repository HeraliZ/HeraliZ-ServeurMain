package fr.heraliz.tactique.allonge;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Joueur -> serveur : « je plonge » (touche « Ramper » en avançant, pendant un saut ou avec la touche de saut enfoncée).
 * Rien d'autre n'est envoyé : direction, élan et conditions sont décidés par le serveur seul
 * ({@link ReseauAllonge#PLONGEON_SERVEUR}, branché par la partie serveur), qui répond par {@link PaquetReponsePlongeon}.
 */
public record PaquetDemandePlongeon() {
   public static void encode(PaquetDemandePlongeon paquet, FriendlyByteBuf buf) {
   }

   public static PaquetDemandePlongeon decode(FriendlyByteBuf buf) {
      return new PaquetDemandePlongeon();
   }

   public static void handle(PaquetDemandePlongeon paquet, Supplier<NetworkEvent.Context> contexte) {
      NetworkEvent.Context c = contexte.get();
      if (c.getDirection().getReceptionSide().isServer()) {
         c.enqueueWork(() -> {
            ServerPlayer joueur = c.getSender();
            if (joueur != null) {
               ReseauAllonge.PLONGEON_SERVEUR.accept(joueur);
            }
         });
      }
      c.setPacketHandled(true);
   }
}
