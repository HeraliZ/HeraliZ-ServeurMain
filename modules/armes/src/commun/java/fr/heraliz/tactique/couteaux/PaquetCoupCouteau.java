package fr.heraliz.tactique.couteaux;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Joueur -> serveur : « je frappe avec mon couteau » (coup rapide ou puissant). Rien d'autre n'est envoyé : couteau en
 * main, cadence, cibles et dégâts sont décidés par le serveur seul ({@link ReseauCouteaux#COUP_SERVEUR}, branché par la
 * partie serveur).
 */
public record PaquetCoupCouteau(boolean lourd) {
   /** Demande reçue par le serveur. */
   public record Demande(ServerPlayer joueur, boolean lourd) {
   }

   public static void encode(PaquetCoupCouteau paquet, FriendlyByteBuf buf) {
      buf.writeBoolean(paquet.lourd);
   }

   public static PaquetCoupCouteau decode(FriendlyByteBuf buf) {
      return new PaquetCoupCouteau(buf.readBoolean());
   }

   public static void handle(PaquetCoupCouteau paquet, Supplier<NetworkEvent.Context> contexte) {
      NetworkEvent.Context c = contexte.get();
      if (c.getDirection().getReceptionSide().isServer()) {
         c.enqueueWork(() -> {
            ServerPlayer joueur = c.getSender();
            if (joueur != null) {
               ReseauCouteaux.COUP_SERVEUR.accept(new Demande(joueur, paquet.lourd));
            }
         });
      }
      c.setPacketHandled(true);
   }
}
