package fr.heraliz.tactique.allonge;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur -> joueur : réponse à une demande de plongeon ({@link PaquetDemandePlongeon}).
 * <ul>
 *    <li>{@link #ACCEPTE} : l'élan est donné par le serveur (vitesse de Minecraft, qui suit ce paquet) ; le joueur met son
 *    corps à plat et s'allongera en retombant ;</li>
 *    <li>{@link #ATTENTE_ALLONGE} : délai pour se rallonger pas écoulé, {@code attenteMs} restantes ;</li>
 *    <li>{@link #ATTENTE_PLONGEON} : délai entre deux plongeons pas écoulé, {@code attenteMs} restantes ;</li>
 *    <li>{@link #REFUSE} : refusé sans message (dans l'eau, ne court pas en avant, saut trop ancien...).</li>
 * </ul>
 * Traitement chez le joueur : {@link ReseauAllonge#REPONSE_PLONGEON_CLIENT}, branché par la partie client.
 */
public record PaquetReponsePlongeon(int motif, long attenteMs) {
   public static final int ACCEPTE = 0;
   public static final int REFUSE = 1;
   public static final int ATTENTE_ALLONGE = 2;
   public static final int ATTENTE_PLONGEON = 3;

   public static void encode(PaquetReponsePlongeon paquet, FriendlyByteBuf buf) {
      buf.writeVarInt(paquet.motif());
      buf.writeVarLong(paquet.attenteMs());
   }

   public static PaquetReponsePlongeon decode(FriendlyByteBuf buf) {
      return new PaquetReponsePlongeon(buf.readVarInt(), buf.readVarLong());
   }

   public static void handle(PaquetReponsePlongeon paquet, Supplier<NetworkEvent.Context> contexte) {
      NetworkEvent.Context c = contexte.get();
      if (c.getDirection().getReceptionSide().isClient()) {
         c.enqueueWork(() -> ReseauAllonge.REPONSE_PLONGEON_CLIENT.accept(paquet));
      }
      c.setPacketHandled(true);
   }
}
