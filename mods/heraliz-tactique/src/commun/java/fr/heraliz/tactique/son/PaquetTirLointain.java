package fr.heraliz.tactique.son;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur -> joueur : un tir silencieux lointain à jouer (voir {@link SonLointain}). Position déjà floue, volume et
 * hauteur déjà calculés par le serveur ; l'arme sert seulement à trouver le son de son silencieux.
 */
public record PaquetTirLointain(ResourceLocation arme, ResourceLocation affichage, double x, double y, double z, float volume,
                                float hauteur) {
   public static void encode(PaquetTirLointain p, FriendlyByteBuf buf) {
      buf.writeResourceLocation(p.arme());
      buf.writeResourceLocation(p.affichage());
      buf.writeFloat((float) p.x());
      buf.writeFloat((float) p.y());
      buf.writeFloat((float) p.z());
      buf.writeFloat(p.volume());
      buf.writeFloat(p.hauteur());
   }

   public static PaquetTirLointain decode(FriendlyByteBuf buf) {
      return new PaquetTirLointain(buf.readResourceLocation(), buf.readResourceLocation(), buf.readFloat(), buf.readFloat(),
         buf.readFloat(), buf.readFloat(), buf.readFloat());
   }

   public static void handle(PaquetTirLointain paquet, Supplier<NetworkEvent.Context> contexte) {
      NetworkEvent.Context c = contexte.get();
      if (c.getDirection().getReceptionSide().isClient()) {
         c.enqueueWork(() -> ReseauSon.RECEPTION_CLIENT.accept(paquet));
      }
      c.setPacketHandled(true);
   }
}
