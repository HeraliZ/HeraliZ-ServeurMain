package fr.heraliz.tactique.avie;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** Serveur -> joueur : « Jeter cet objet ? » (l'objet est mis de côté par le serveur en attendant la réponse). */
public record PaquetJeterQuestion(ItemStack objet) {
   public static void encode(PaquetJeterQuestion p, FriendlyByteBuf buf) {
      buf.writeItem(p.objet);
   }

   public static PaquetJeterQuestion decode(FriendlyByteBuf buf) {
      return new PaquetJeterQuestion(buf.readItem());
   }

   public static void handle(PaquetJeterQuestion p, Supplier<NetworkEvent.Context> contexte) {
      NetworkEvent.Context c = contexte.get();
      if (c.getDirection().getReceptionSide().isClient()) {
         c.enqueueWork(() -> ObjetsAVie.QUESTION_CLIENT.accept(p));
      }
      c.setPacketHandled(true);
   }
}
