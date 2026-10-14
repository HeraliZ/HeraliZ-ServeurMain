package fr.heraliz.tactique.armes;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/** Serveur -> joueur : dégâts des armes contre les joueurs ({@link DegatsJoueurs}), à la connexion. */
public record PaquetDegatsArmes(Map<ResourceLocation, DegatsJoueurs.Reglage> reglages) {
   public static void encode(PaquetDegatsArmes p, FriendlyByteBuf buf) {
      buf.writeVarInt(p.reglages.size());
      p.reglages.forEach((id, r) -> {
         buf.writeResourceLocation(id);
         buf.writeFloat(r.degats());
         buf.writeFloat(r.tete());
      });
   }

   public static PaquetDegatsArmes decode(FriendlyByteBuf buf) {
      int n = Math.min(buf.readVarInt(), 4096);
      Map<ResourceLocation, DegatsJoueurs.Reglage> m = new HashMap<>(n * 2);
      for (int i = 0; i < n; i++) {
         ResourceLocation id = buf.readResourceLocation();
         m.put(id, new DegatsJoueurs.Reglage(buf.readFloat(), buf.readFloat()));
      }
      return new PaquetDegatsArmes(m);
   }

   public static void handle(PaquetDegatsArmes p, Supplier<NetworkEvent.Context> contexte) {
      NetworkEvent.Context c = contexte.get();
      if (c.getDirection().getReceptionSide().isClient()) {
         c.enqueueWork(() -> DegatsJoueurs.remplacer(p.reglages));
      }
      c.setPacketHandled(true);
   }
}
