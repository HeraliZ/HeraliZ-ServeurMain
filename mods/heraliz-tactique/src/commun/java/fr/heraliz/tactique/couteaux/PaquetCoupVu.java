package fr.heraliz.tactique.couteaux;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur -> joueurs qui voient le frappeur : un coup de couteau accepté, rapide ou puissant (14/10). Chez eux, le coup en
 * 3e personne de TaCZ : grand coup de haut en bas pour le coup puissant, deux gestes en alternance pour le coup rapide
 * (avant : le paquet « mêlée » de TaCZ, qui tirait l'un des trois gestes au hasard). Branché par la partie client
 * ({@link ReseauCouteaux#COUP_VU_CLIENT}).
 */
public record PaquetCoupVu(int entite, boolean lourd) {
   public static void encode(PaquetCoupVu p, FriendlyByteBuf buf) {
      buf.writeVarInt(p.entite);
      buf.writeBoolean(p.lourd);
   }

   public static PaquetCoupVu decode(FriendlyByteBuf buf) {
      return new PaquetCoupVu(buf.readVarInt(), buf.readBoolean());
   }

   public static void handle(PaquetCoupVu p, Supplier<NetworkEvent.Context> contexte) {
      NetworkEvent.Context c = contexte.get();
      if (c.getDirection().getReceptionSide().isClient()) {
         c.enqueueWork(() -> ReseauCouteaux.COUP_VU_CLIENT.accept(p));
      }
      c.setPacketHandled(true);
   }
}
