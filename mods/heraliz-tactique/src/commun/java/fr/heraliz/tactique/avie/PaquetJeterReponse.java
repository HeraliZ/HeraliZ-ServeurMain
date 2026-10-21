package fr.heraliz.tactique.avie;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Joueur -> serveur : réponse à « Jeter cet objet ? » (oui : jeté ; non : rendu à l'inventaire). */
public record PaquetJeterReponse(boolean jeter) {
   /** Réponse reçue par le serveur. */
   public record Recue(ServerPlayer joueur, boolean jeter) {
   }

   public static void encode(PaquetJeterReponse p, FriendlyByteBuf buf) {
      buf.writeBoolean(p.jeter);
   }

   public static PaquetJeterReponse decode(FriendlyByteBuf buf) {
      return new PaquetJeterReponse(buf.readBoolean());
   }

   public static void handle(PaquetJeterReponse p, Supplier<NetworkEvent.Context> contexte) {
      NetworkEvent.Context c = contexte.get();
      if (c.getDirection().getReceptionSide().isServer()) {
         c.enqueueWork(() -> {
            ServerPlayer joueur = c.getSender();
            if (joueur != null) ObjetsAVie.REPONSE_SERVEUR.accept(new Recue(joueur, p.jeter));
         });
      }
      c.setPacketHandled(true);
   }
}
